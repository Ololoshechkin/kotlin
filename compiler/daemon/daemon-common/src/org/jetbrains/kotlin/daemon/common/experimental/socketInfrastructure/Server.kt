package org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import java.io.Serializable
import java.net.InetSocketAddress

/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
interface ServerBase

interface Server<out T : ServerBase> : ServerBase {

    enum class State {
        WORKING, CLOSED, ERROR
    }

    suspend fun processMessage(msg: AnyMessage<in T>, output: ByteWriteChannelWrapper): State

    suspend fun attachClient(client: Socket)

    interface AnyMessage<ServerType : ServerBase> : Serializable

    interface Message<ServerType : ServerBase> : AnyMessage<ServerType> {
        suspend fun process(server: ServerType, output: ByteWriteChannelWrapper)
    }

    class EndConnectionMessage<ServerType : ServerBase> : AnyMessage<ServerType>

    fun runServer() : Deferred<Unit>

}

@Suppress("UNCHECKED_CAST")
class DefaultServer<out ServerType : ServerBase>(val serverPort: Int, val self: ServerType) : Server<ServerType> {

    final override suspend fun processMessage(msg: Server.AnyMessage<in ServerType>, output: ByteWriteChannelWrapper) = when (msg) {
        is Server.Message<in ServerType> -> Server.State.WORKING.also { msg.process(self as ServerType, output) }
        is Server.EndConnectionMessage<in ServerType> -> Server.State.CLOSED
        else -> Server.State.ERROR
    }

    final override suspend fun attachClient(client: Socket) {
        async {
            val (input, output) = client.openIO()
            loop@ while (true) {
                when (processMessage(input.nextObject() as Server.AnyMessage<ServerType>, output)) {
                    Server.State.CLOSED -> break@loop
                    Server.State.ERROR -> {
                        // TODO println("Server error: invalid message")
                    }
                    else -> continue@loop
                }
            }
        }
    }

    final override fun runServer() = async {
        aSocket().tcp().bind(InetSocketAddress(serverPort)).use { serverSocket ->
            println("accepting clientSocket...")
            while (true) {
                val client = serverSocket.accept()
                println("client accepted! (${client.remoteAddress})")
                attachClient(client)
            }
        }.let {}
    }

}