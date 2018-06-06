/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.client

import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.IReplStageState
import org.jetbrains.kotlin.cli.common.repl.ReplCheckResult
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.cli.common.repl.ReplCompileResult
import org.jetbrains.kotlin.cli.common.repl.experimental.ReplCompiler
import org.jetbrains.kotlin.daemon.client.experimental.KotlinRemoteReplCompilerClientAsync
import org.jetbrains.kotlin.daemon.client.impls.KotlinRemoteReplCompilerClientImpl
import org.jetbrains.kotlin.daemon.common.CompileService
import org.jetbrains.kotlin.daemon.common.CompileServiceClientSide
import org.jetbrains.kotlin.daemon.common.SOCKET_ANY_FREE_PORT
import org.jetbrains.kotlin.daemon.common.experimental.CompileServiceAsyncWrapper
import org.jetbrains.kotlin.daemon.common.experimental.CompileServiceClientSideImpl
import org.jetbrains.kotlin.daemon.common.experimental.toRMI
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock

interface KotlinRemoteReplCompilerClient : ReplCompiler {

    val sessionId: Int

    // dispose should be called at the end of the repl lifetime to free daemon repl session and appropriate resources
    suspend fun dispose()

    override suspend fun createState(lock: ReentrantReadWriteLock): IReplStageState<*>

    override suspend fun check(
        state: IReplStageState<*>,
        codeLine: ReplCodeLine
    ): ReplCheckResult

    override suspend fun compile(state: IReplStageState<*>, codeLine: ReplCodeLine): ReplCompileResult

    companion object {
        fun instantiate(
            compileService: CompileServiceClientSide,
            clientAliveFlagFile: File?,
            targetPlatform: CompileService.TargetPlatform,
            args: Array<out String>,
            messageCollector: MessageCollector,
            templateClasspath: List<File>,
            templateClassName: String,
            port: Int = SOCKET_ANY_FREE_PORT
        ): KotlinRemoteReplCompilerClient =
            when (compileService) {
                is CompileServiceClientSideImpl -> KotlinRemoteReplCompilerClientAsync(
                    compileService,
                    clientAliveFlagFile,
                    targetPlatform,
                    args,
                    messageCollector,
                    templateClasspath,
                    templateClassName
                )
                else -> KotlinRemoteReplCompilerWrapper(
                    KotlinRemoteReplCompilerClientImpl(
                        compileService.toRMI(),
                        clientAliveFlagFile,
                        targetPlatform,
                        args,
                        messageCollector,
                        templateClasspath,
                        templateClassName
                    )
                )
            }
    }

}