/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import io.ktor.network.sockets.Socket
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.kotlin.daemon.common.CompilerServicesFacadeBase
import java.io.Serializable


class CompilerServicesFacadeBaseAsyncWrapper(
    val rmiImpl: CompilerServicesFacadeBase
) : CompilerServicesFacadeBaseClientSide {

    override fun attachToServer() {} // done by rmi

    override suspend fun report(category: Int, severity: Int, message: String?, attachment: Serializable?) =
        rmiImpl.report(category, severity, message, attachment)

}

class CompilerServicesFacadeBaseRMIWrapper(val clientSide: CompilerServicesFacadeBaseClientSide) : CompilerServicesFacadeBase {

    override fun report(category: Int, severity: Int, message: String?, attachment: Serializable?) = runBlocking {
        clientSide.report(category, severity, message, attachment)
    }

}

fun CompilerServicesFacadeBase.toClient() =
    if (this is CompilerServicesFacadeBaseRMIWrapper) this.clientSide
    else CompilerServicesFacadeBaseAsyncWrapper(this)

fun CompilerServicesFacadeBaseClientSide.toRMI() =
    if (this is CompilerServicesFacadeBaseAsyncWrapper) this.rmiImpl
    else CompilerServicesFacadeBaseRMIWrapper(this)