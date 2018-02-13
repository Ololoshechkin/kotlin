/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import org.jetbrains.kotlin.cli.common.repl.ILineId
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.ByteWriteChannelWrapper
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.Server

interface ReplStateFacadeServerSide: ReplStateFacadeAsync, Server<ReplStateFacadeServerSide> {

    // Query messages:
    class GetIdMessage : Server.Message<ReplStateFacadeServerSide> {
        override suspend fun process(server: ReplStateFacadeServerSide, output: ByteWriteChannelWrapper) =
            output.writeObject(server.getId())
    }

    class GetHistorySizeMessage : Server.Message<ReplStateFacadeServerSide> {
        override suspend fun process(server: ReplStateFacadeServerSide, output: ByteWriteChannelWrapper) =
            output.writeObject(server.getHistorySize())
    }

    class HistoryGetMessage(val index: Int) : Server.Message<ReplStateFacadeServerSide> {
        override suspend fun process(server: ReplStateFacadeServerSide, output: ByteWriteChannelWrapper) =
            output.writeObject(server.historyGet(index))
    }

    class HistoryResetMessage : Server.Message<ReplStateFacadeServerSide> {
        override suspend fun process(server: ReplStateFacadeServerSide, output: ByteWriteChannelWrapper) =
            output.writeObject(server.historyReset())
    }

    class HistoryResetToMessage(val id: ILineId) : Server.Message<ReplStateFacadeServerSide> {
        override suspend fun process(server: ReplStateFacadeServerSide, output: ByteWriteChannelWrapper) =
            output.writeObject(server.historyResetTo(id))
    }
}