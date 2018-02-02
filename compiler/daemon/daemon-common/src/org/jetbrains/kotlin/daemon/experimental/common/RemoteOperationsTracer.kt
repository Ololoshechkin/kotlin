/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.daemon.experimental.common

import java.rmi.Remote
import java.rmi.RemoteException
import org.jetbrains.kotlin.daemon.experimental.socketInfrastructure.Server
import org.jetbrains.kotlin.daemon.experimental.socketInfrastructure.Server.Message
import io.ktor.network.sockets.Socket


interface RemoteOperationsTracer : Server, Remote {

    @Throws(RemoteException::class)
    fun before(id: String)

    @Throws(RemoteException::class)
    fun after(id: String)

    // Query messages:
    class BeforeMessage(val id: String) : Message<RemoteOperationsTracer> {
        suspend override fun process(server: RemoteOperationsTracer, output: ByteWriteChannelWrapper) =
            server.before(id)
    }

    class AfterMessage(val id: String) : Message<RemoteOperationsTracer> {
        suspend override fun process(server: RemoteOperationsTracer, output: ByteWriteChannelWrapper) =
            server.after(id)
    }

}
