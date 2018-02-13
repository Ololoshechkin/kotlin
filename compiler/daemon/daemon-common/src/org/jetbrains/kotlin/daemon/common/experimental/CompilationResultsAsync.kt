/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import io.ktor.network.sockets.Socket
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.kotlin.daemon.common.CompilationResults
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.*
import java.io.Serializable
import java.rmi.Remote
import java.rmi.RemoteException

interface CompilationResultsAsync {
    suspend fun add(compilationResultCategory: Int, value: Serializable)
}

interface CompilationResultsServerSide : CompilationResultsAsync, Server<CompilationResultsServerSide> {
    class AddMessage(
        val compilationResultCategory: Int,
        val value: Serializable
    ) : Server.Message<CompilationResultsServerSide> {
        suspend override fun process(server: CompilationResultsServerSide, output: ByteWriteChannelWrapper) {
            server.add(compilationResultCategory, value)
        }
    }
}

interface CompilationResultsClientSide : CompilationResultsAsync

class CompilationResultsClientSideImpl(socket: Socket) : CompilationResultsClientSide, Client {

    private lateinit var serverOutput: ByteWriteChannelWrapper

    suspend override fun add(compilationResultCategory: Int, value: Serializable) {
        serverOutput.writeObject(CompilationResultsServerSide.AddMessage(compilationResultCategory, value))
    }

    override fun attachToServer() {
        serverOutput = socket.openAndWrapWriteChannel()
    }

    init {
        attachToServer(socket)
    }

}

class CompilationResultsAsyncWrapper(val rmiImpl: CompilationResults) : CompilationResultsClientSide {

    suspend override fun add(compilationResultCategory: Int, value: Serializable) {
        rmiImpl.add(compilationResultCategory, value)
    }

}

class CompilationResultsRMIWrapper(val clientSide: CompilationResultsClientSide) : CompilationResults {

    override fun add(compilationResultCategory: Int, value: Serializable) = runBlocking {
        clientSide.add(compilationResultCategory, value)
    }

}

fun CompilationResults.toClient() =
    if (this is CompilationResultsRMIWrapper) this.clientSide
    else CompilationResultsAsyncWrapper(this)

fun CompilationResultsClientSide.toRMI() =
    if (this is CompilationResultsAsyncWrapper) this.rmiImpl
    else CompilationResultsRMIWrapper(this)

enum class CompilationResultCategory(val code: Int) {
    IC_COMPILE_ITERATION(0)
}