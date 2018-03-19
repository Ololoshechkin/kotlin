package org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.actor
import java.io.Serializable
import java.net.InetSocketAddress
import java.util.logging.Logger

/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */
interface ServerBase

@Suppress("UNCHECKED_CAST")
interface Server<out T : ServerBase> : ServerBase {

    val serverPort: Int

    private val log: Logger
        get() = Logger.getLogger("default server")

    enum class State {
        WORKING, CLOSED, ERROR, DOWNING
    }

    suspend fun processMessage(msg: AnyMessage<in T>, output: ByteWriteChannelWrapper): State = when (msg) {
        is Server.Message<in T> -> Server.State.WORKING.also { msg.process(this as T, output) }
        is Server.EndConnectionMessage<in T> -> {
            println("!EndConnectionMessage!")
            Server.State.CLOSED
        }
        is Server.ServerDownMessage<in T> -> Server.State.DOWNING
        else -> Server.State.ERROR
    }

    suspend fun attachClient(client: Socket): Deferred<State> = async {
        val (input, output) = client.openIO(log)
        try {
            val bytes = input.readBytes(4)
            println("bytes : ${bytes.toList()}")
            if (bytes.zip(byteArrayOf(1, 2, 3, 4)).any { it.first != it.second }) {
                throw Exception();
            }
        } catch (e: Throwable) {
            println("BAD TOKEN")
            return@async Server.State.CLOSED
        }
        var finalState = Server.State.WORKING
        loop@
        while (true) {
            val state = processMessage(input.nextObject() as Server.AnyMessage<T>, output)
            when (state) {
                Server.State.WORKING -> continue@loop
                else -> {
                    finalState = state
                    break@loop
                }
            }
        }
        finalState
    }

    interface AnyMessage<ServerType : ServerBase> : Serializable

    interface Message<ServerType : ServerBase> : AnyMessage<ServerType> {
        suspend fun process(server: ServerType, output: ByteWriteChannelWrapper)
    }

    class EndConnectionMessage<ServerType : ServerBase> : AnyMessage<ServerType>

    class ServerDownMessage<ServerType : ServerBase> : AnyMessage<ServerType>

    fun runServer(): Deferred<Unit> {
        log.info("binding to address($serverPort)")
        val serverSocket = aSocket().tcp().bind(InetSocketAddress(serverPort))
        return async {
            serverSocket.use {
                log.info("accepting clientSocket...")
                while (true) {
                    val client = serverSocket.accept()
                    log.info("client accepted! (${client.remoteAddress})")
                    attachClient(client).invokeOnCompletion {
                        when (it) {
                            Server.State.DOWNING -> {
                                client.close()
                            }
                            else -> {
                                client.close()
                            }
                        }
                    }
                }
            }
        }
    }

}