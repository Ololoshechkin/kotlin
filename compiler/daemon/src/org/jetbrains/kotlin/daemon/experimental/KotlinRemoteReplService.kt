/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.experimental

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.cli.common.messages.*
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.INFO
import org.jetbrains.kotlin.cli.common.repl.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.cli.jvm.repl.GenericReplCompiler
import org.jetbrains.kotlin.cli.jvm.repl.GenericReplCompilerState
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.daemon.common.CompileService
import org.jetbrains.kotlin.daemon.common.RemoteOperationsTracer
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import org.jetbrains.kotlin.script.KotlinScriptDefinitionFromAnnotatedTemplate
import org.jetbrains.kotlin.utils.PathUtil
import java.io.File
import java.io.PrintStream
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

interface KotlinJvmReplServiceAsync : ReplCompileAction, ReplCheckAction, CreateReplStageStateAction {

    suspend fun createRemoteState(port: Int = portForServers): RemoteReplStateFacadeClientSide

    val portForServers: Int

    suspend fun check(codeLine: ReplCodeLine): ReplCheckResult

    suspend fun <R> withValidReplState(stateId: Int, body: (IReplStageState<*>) -> R): CompileService.CallResult<R>

    suspend fun compile(codeLine: ReplCodeLine, verifyHistory: List<ReplCodeLine>?): ReplCompileResult

}

class AbstractKotlinJvmReplServiceAsync(
    disposable: Disposable,
    override val portForServers: Int,
    templateClasspath: List<File>,
    templateClassName: String,
    protected val messageCollector: MessageCollector
) : KotlinJvmReplServiceAsync {

    // TODO
    protected val configuration = CompilerConfiguration().apply {
        addJvmClasspathRoots(PathUtil.kotlinPathsForCompiler.let { listOf(it.stdlibPath, it.reflectPath, it.scriptRuntimePath) })
        addJvmClasspathRoots(templateClasspath)
        put(CommonConfigurationKeys.MODULE_NAME, "kotlin-script")
        languageVersionSettings = LanguageVersionSettingsImpl(
            LanguageVersion.LATEST_STABLE, ApiVersion.LATEST_STABLE, mapOf(AnalysisFlag.skipMetadataVersionCheck to true)
        )
    }

    protected fun makeScriptDefinition(templateClasspath: List<File>, templateClassName: String): KotlinScriptDefinition? {
        val classloader = URLClassLoader(templateClasspath.map { it.toURI().toURL() }.toTypedArray(), this::class.java.classLoader)

        try {
            val cls = classloader.loadClass(templateClassName)
            val def = KotlinScriptDefinitionFromAnnotatedTemplate(cls.kotlin, emptyMap())
            messageCollector.report(
                INFO, "New script definition $templateClassName: files pattern = \"${def.scriptFilePattern}\", " +
                        "resolver = ${def.dependencyResolver.javaClass.name}"
            )
            return def
        } catch (ex: ClassNotFoundException) {
            messageCollector.report(ERROR, "Cannot find script definition template class $templateClassName")
        } catch (ex: Exception) {
            messageCollector.report(ERROR, "Error processing script definition template $templateClassName: ${ex.message}")
        }
        return null
    }

    private val scriptDef = makeScriptDefinition(templateClasspath, templateClassName)

    private val replCompiler: ReplCompiler? by lazy {
        if (scriptDef == null) null
        else GenericReplCompiler(disposable, scriptDef, configuration, messageCollector)
    }

    protected val statesLock = ReentrantReadWriteLock()

    protected val stateIdCounter = AtomicInteger()


    override fun createState(lock: ReentrantReadWriteLock): IReplStageState<*> =
        replCompiler?.createState(lock) ?: throw IllegalStateException("repl compiler is not initialized properly")

    override suspend fun check(state: IReplStageState<*>, codeLine: ReplCodeLine): ReplCheckResult {
        return replCompiler?.check(state, codeLine) ?: ReplCheckResult.Error("Initialization error")
    }

    override fun compile(state: IReplStageState<*>, codeLine: ReplCodeLine): ReplCompileResult {
        return replCompiler?.compile(state, codeLine) ?: ReplCompileResult.Error("Initialization error")
    }

    // TODO: consider using values here for session cleanup
    protected val states = WeakHashMap<RemoteReplStateFacadeAsyncImpl, Boolean>() // used as (missing) WeakHashSet
    @Deprecated("remove after removal state-less check/compile/eval methods")
    protected val defaultStateFacade: RemoteReplStateFacadeImplType by lazy { createRemoteState() }

    abstract override fun createRemoteState(port: Int): RemoteReplStateFacadeImplType

    @Deprecated("Use check(state, line) instead")
    override fun check(codeLine: ReplCodeLine): ReplCheckResult = check(defaultStateFacade.state, codeLine)

    @Deprecated("Use compile(state, line) instead")
    override fun compile(codeLine: ReplCodeLine, verifyHistory: List<ReplCodeLine>?): ReplCompileResult =
        compile(defaultStateFacade.state, codeLine)

    override fun <R> withValidReplState(stateId: Int, body: (IReplStageState<*>) -> R): CompileService.CallResult<R> = statesLock.read {
        states.keys.firstOrNull { it.getId() == stateId }?.let {
            CompileService.CallResult.Good(body(it.state))
        }
                ?: CompileService.CallResult.Error("No REPL state with id $stateId found")
    }


}

internal class KeepFirstErrorMessageCollector(compilerMessagesStream: PrintStream) : MessageCollector {

    private val innerCollector = PrintingMessageCollector(compilerMessagesStream, MessageRenderer.WITHOUT_PATHS, false)

    internal var firstErrorMessage: String? = null
    internal var firstErrorLocation: CompilerMessageLocation? = null

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
        if (firstErrorMessage == null && severity.isError) {
            firstErrorMessage = message
            firstErrorLocation = location
        }
        innerCollector.report(severity, message, location)
    }

    override fun hasErrors(): Boolean = innerCollector.hasErrors()
    override fun clear() {
        innerCollector.clear()
    }
}

internal val internalRng = Random()

internal inline fun getValidId(counter: AtomicInteger, check: (Int) -> Boolean): Int {
    // fighting hypothetical integer wrapping
    var newId = counter.incrementAndGet()
    var attemptsLeft = 100
    while (!check(newId)) {
        attemptsLeft -= 1
        if (attemptsLeft <= 0)
            throw IllegalStateException("Invalid state or algorithm error")
        // assuming wrap, jumping to random number to reduce probability of further clashes
        newId = counter.addAndGet(internalRng.nextInt())
    }
    return newId
}


class KotlinJvmReplServiceAsyncRMI(
    disposable: Disposable,
    portForServers: Int,
    templateClasspath: List<File>,
    templateClassName: String,
    messageCollector: MessageCollector,
    operationsTracer: RemoteOperationsTracer?
) : AbstractKotlinJvmReplServiceAsync<RemoteReplStateFacadeImpl>(
    disposable,
    portForServers,
    templateClasspath,
    templateClassName,
    messageCollector,
    operationsTracer
) {
    override fun createRemoteState(port: Int): RemoteReplStateFacadeImpl = statesLock.write {
        val id = getValidId(stateIdCounter) { id -> states.none { it.key.getId() == id } }
        val stateFacade = RemoteReplStateFacadeImpl(id, createState().asState(GenericReplCompilerState::class.java), port)
        states.put(stateFacade, true)
        stateFacade
    }
}

class KotlinJvmReplServiceAsyncSockets(
    disposable: Disposable,
    portForServers: Int,
    templateClasspath: List<File>,
    templateClassName: String,
    messageCollector: MessageCollector,
    operationsTracer: RemoteOperationsTracer?
) : AbstractKotlinJvmReplServiceAsync<RemoteReplStateFacadeImpl>(
    disposable,
    portForServers,
    templateClasspath,
    templateClassName,
    messageCollector,
    operationsTracer
) {
    override fun createRemoteState(port: Int): RemoteReplStateFacadeImpl = statesLock.write {
        val id = getValidId(stateIdCounter) { id -> states.none { it.key.getId() == id } }
        val stateFacade = RemoteReplStateFacadeImpl(id, createState().asState(GenericReplCompilerState::class.java), port)
        states.put(stateFacade, true)
        stateFacade
    }
}