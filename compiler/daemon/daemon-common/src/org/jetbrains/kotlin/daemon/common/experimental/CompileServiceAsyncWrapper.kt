/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import io.ktor.network.sockets.Socket
import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.daemon.common.CompilationOptions
import org.jetbrains.kotlin.daemon.common.CompileService
import org.jetbrains.kotlin.daemon.common.CompilerId
import java.io.File

class CompileServiceRMIAsyncWrapper(val rmiCompileService: CompileService) : CompileServiceClientSide {

    suspend override fun compile(
        sessionId: Int,
        compilerArguments: Array<out String>,
        compilationOptions: CompilationOptions,
        servicesFacade: CompilerServicesFacadeBaseClientSide,
        compilationResults: CompilationResultsClientSide?
    ) = rmiCompileService.compile(
        sessionId,
        compilerArguments,
        compilationOptions,
        servicesFacade.toRMI(),
        compilationResults?.toRMI()
    )

    suspend override fun leaseReplSession(
        aliveFlagPath: String?,
        compilerArguments: Array<out String>,
        compilationOptions: CompilationOptions,
        servicesFacade: CompilerServicesFacadeBaseClientSide,
        templateClasspath: List<File>,
        templateClassName: String
    ) = rmiCompileService.leaseReplSession(
        aliveFlagPath,
        compilerArguments,
        compilationOptions,
        servicesFacade.toRMI(),
        templateClasspath,
        templateClassName
    )

    suspend override fun replCreateState(sessionId: Int) = rmiCompileService.replCreateState(sessionId).toClient()

    override fun attachToServer() {} // is done by RMI

    suspend override fun getUsedMemory() =
        rmiCompileService.getUsedMemory()


    suspend override fun getDaemonOptions() =
        rmiCompileService.getDaemonOptions()


    suspend override fun getDaemonInfo() =
        rmiCompileService.getDaemonInfo()


    suspend override fun getDaemonJVMOptions() =
        rmiCompileService.getDaemonJVMOptions()

    suspend override fun registerClient(aliveFlagPath: String?) =
        rmiCompileService.registerClient(aliveFlagPath)

    suspend override fun getClients() =
        rmiCompileService.getClients()


    suspend override fun leaseCompileSession(aliveFlagPath: String?) =
        rmiCompileService.leaseCompileSession(aliveFlagPath)


    suspend override fun releaseCompileSession(sessionId: Int) =
        rmiCompileService.releaseCompileSession(sessionId)


    suspend override fun shutdown() =
        rmiCompileService.shutdown()


    suspend override fun scheduleShutdown(graceful: Boolean) =
        rmiCompileService.scheduleShutdown(graceful)

    suspend override fun clearJarCache() =
        rmiCompileService.clearJarCache()


    suspend override fun releaseReplSession(sessionId: Int) =
        rmiCompileService.releaseReplSession(sessionId)


    suspend override fun replCheck(sessionId: Int, replStateId: Int, codeLine: ReplCodeLine) =
        rmiCompileService.replCheck(sessionId, replStateId, codeLine)


    suspend override fun replCompile(
        sessionId: Int,
        replStateId: Int,
        codeLine: ReplCodeLine
    ) =
        rmiCompileService.replCompile(sessionId, replStateId, codeLine)


    suspend override fun checkCompilerId(expectedCompilerId: CompilerId) =
        rmiCompileService.checkCompilerId(expectedCompilerId)

}


fun CompileService.toClient() = CompileServiceRMIAsyncWrapper(this)