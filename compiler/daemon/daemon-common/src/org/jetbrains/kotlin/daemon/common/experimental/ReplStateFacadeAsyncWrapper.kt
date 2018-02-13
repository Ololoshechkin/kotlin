/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import io.ktor.network.sockets.Socket
import org.jetbrains.kotlin.cli.common.repl.ILineId
import org.jetbrains.kotlin.daemon.common.CompileService
import org.jetbrains.kotlin.daemon.common.ReplStateFacade

class ReplStateFacadeAsyncWrapper(val rmiReplStateFacade: ReplStateFacade): ReplStateFacadeClientSide {

    override fun attachToServer() {} // done by rmi

    suspend override fun getId() = rmiReplStateFacade.getId()

    suspend override fun getHistorySize() = rmiReplStateFacade.getHistorySize()

    suspend override fun historyGet(index: Int) = rmiReplStateFacade.historyGet(index)

    suspend override fun historyReset() = rmiReplStateFacade.historyReset()

    suspend override fun historyResetTo(id: ILineId) = rmiReplStateFacade.historyResetTo(id)

}

fun ReplStateFacade.toClient() = ReplStateFacadeAsyncWrapper(this)
fun CompileService.CallResult<ReplStateFacade>.toClient()= when (this) {
    is CompileService.CallResult.Good -> CompileService.CallResult.Good(this.result.toClient())
    is CompileService.CallResult.Dying -> this
    is CompileService.CallResult.Error -> this
    is CompileService.CallResult.Ok -> this
}