/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.client.experimental

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.kotlin.daemon.common.SOCKET_ANY_FREE_PORT
import org.jetbrains.kotlin.daemon.common.experimental.RemoteOutputStreamAsyncServerSide
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.ByteWriteChannelWrapper
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.Server
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.openIO
import java.io.OutputStream
import java.net.InetSocketAddress


class RemoteOutputStreamServerSideImpl(val out: OutputStream, port: Int = SOCKET_ANY_FREE_PORT) : RemoteOutputStreamAsyncServerSide {

    suspend override fun processMessage(
        msg: Server.AnyMessage<RemoteOutputStreamAsyncServerSide>,
        output: ByteWriteChannelWrapper
    ) = when (msg) {
        is Server.EndConnectionMessage<RemoteOutputStreamAsyncServerSide> -> Server.State.CLOSED
        is Server.Message<RemoteOutputStreamAsyncServerSide> -> Server.State.WORKING.also {
            msg.process(this, output)
        }
        else -> Server.State.ERROR
    }

    suspend override fun attachClient(client: Socket) {
        async {
            val (input, output) = client.openIO()
            loop@ while (true) {
                when (processMessage(input.nextObject() as Server.AnyMessage<RemoteOutputStreamAsyncServerSide>, output)) {
                    Server.State.CLOSED -> break@loop
                    Server.State.ERROR -> {
                        // TODO: debug message "Server error: invalid message"
                    }
                }
            }
        }
    }

    override suspend fun close() {
        out.close()
    }

    override suspend fun write(data: ByteArray, offset: Int, length: Int) {
        out.write(data, offset, length)
    }

    override suspend fun write(dataByte: Int) {
        out.write(dataByte)
    }

    init {
        runBlocking {
            aSocket().tcp().bind(InetSocketAddress(port)).use {
                while (true) {
                    attachClient(it.accept())
                }
            }
        }
    }

}
