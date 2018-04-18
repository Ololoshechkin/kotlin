/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import org.jetbrains.kotlin.daemon.common.CompilationOptions

import org.jetbrains.kotlin.cli.common.repl.ReplCodeLine
import org.jetbrains.kotlin.daemon.common.*
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.Client
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.DefaultClientRMIWrapper
import java.io.File

class CompileServiceAsyncWrapper(
    val rmiCompileService: CompileService,
    override val serverPort: Int
) : CompileServiceClientSide, Client<CompileServiceServerSide> by DefaultClientRMIWrapper() {

    override suspend fun compile(
        sessionId: Int,
        compilerArguments: Array<out String>,
        compilationOptions: CompilationOptions,
        servicesFacade: CompilerServicesFacadeBaseClientSide,
        compilationResults: CompilationResultsClientSide
    ) = rmiCompileService.compile(
        sessionId,
        compilerArguments,
        compilationOptions,
        servicesFacade.toRMI(),
        compilationResults.toRMI()
    )

    override suspend fun leaseReplSession(
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

    override suspend fun replCreateState(sessionId: Int) =
        rmiCompileService.replCreateState(sessionId).toClient()

    override suspend fun getUsedMemory() =
        rmiCompileService.getUsedMemory()

    override suspend fun getDaemonOptions() =
        rmiCompileService.getDaemonOptions()


    override suspend fun getDaemonInfo() =
        rmiCompileService.getDaemonInfo()


    override suspend fun getDaemonJVMOptions() =
        rmiCompileService.getDaemonJVMOptions()

    override suspend fun registerClient(aliveFlagPath: String?) =
        rmiCompileService.registerClient(aliveFlagPath)

    override suspend fun getClients() =
        rmiCompileService.getClients()


    override suspend fun leaseCompileSession(aliveFlagPath: String?) =
        rmiCompileService.leaseCompileSession(aliveFlagPath)


    override suspend fun releaseCompileSession(sessionId: Int) =
        rmiCompileService.releaseCompileSession(sessionId)


    override suspend fun shutdown() =
        rmiCompileService.shutdown()


    override suspend fun scheduleShutdown(graceful: Boolean) =
        rmiCompileService.scheduleShutdown(graceful)

    override suspend fun clearJarCache() =
        rmiCompileService.clearJarCache()


    override suspend fun releaseReplSession(sessionId: Int) =
        rmiCompileService.releaseReplSession(sessionId)


    override suspend fun replCheck(sessionId: Int, replStateId: Int, codeLine: ReplCodeLine) =
        rmiCompileService.replCheck(sessionId, replStateId, codeLine)


    override suspend fun replCompile(
        sessionId: Int,
        replStateId: Int,
        codeLine: ReplCodeLine
    ) =
        rmiCompileService.replCompile(sessionId, replStateId, codeLine)


    override suspend fun checkCompilerId(expectedCompilerId: CompilerId) =
        rmiCompileService.checkCompilerId(expectedCompilerId)

}

fun CompileService.toClient(serverPort: Int) = CompileServiceAsyncWrapper(this, serverPort)
