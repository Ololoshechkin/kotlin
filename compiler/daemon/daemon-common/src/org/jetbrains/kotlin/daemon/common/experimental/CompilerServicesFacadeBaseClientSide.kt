/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.Client
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.DefaultClient
import java.io.Serializable

interface CompilerServicesFacadeBaseClientSide : CompilerServicesFacadeBaseAsync, Client

class CompilerServicesFacadeBaseClientSideImpl(val serverPort: Int) :
    CompilerServicesFacadeBaseClientSide,
    Client by DefaultClient(serverPort) {

    override suspend fun report(category: Int, severity: Int, message: String?, attachment: Serializable?) {
        sendMessage(
            CompilerServicesFacadeBaseServerSide.ReportMessage(
                category, severity, message, attachment
            )
        )
    }

}