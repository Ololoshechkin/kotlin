/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure

import io.ktor.network.sockets.Socket

interface ServerBase

interface Server<T : ServerBase> : ServerBase {

    enum class State {
        WORKING, CLOSED, ERROR
    }

    suspend fun processMessage(msg: AnyMessage<T>, output: ByteWriteChannelWrapper): State

    suspend fun attachClient(client: Socket)

    interface AnyMessage<ServerType : ServerBase>

    interface Message<ServerType : ServerBase> : AnyMessage<ServerType> {
        suspend fun process(server: ServerType, output: ByteWriteChannelWrapper)
    }

    class EndConnectionMessage<ServerType : ServerBase> : AnyMessage<ServerType>

}