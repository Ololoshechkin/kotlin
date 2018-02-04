/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.ByteWriteChannelWrapper
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.Client
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.Server
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.Server.Message
import java.io.Serializable
import java.rmi.Remote
import java.rmi.RemoteException


interface CompilerServicesFacadeBase : Remote {
    /**
     * Reports different kind of diagnostic messages from compile daemon to compile daemon clients (jps, gradle, ...)
     */
    @Throws(RemoteException::class)
    fun report(category: Int, severity: Int, message: String?, attachment: Serializable?)

}

interface CompilerServicesFacadeBaseServerSide : CompilerServicesFacadeBase, Server {
    class ReportMessage(
        val category: Int,
        val severity: Int,
        val message: String?,
        val attachment: Serializable?
    ) : Message<CompilerServicesFacadeBaseServerSide> {
        suspend override fun process(server: CompilerServicesFacadeBaseServerSide, output: ByteWriteChannelWrapper) =
            server.report(category, severity, message, attachment)
    }
}

interface CompilerServicesFacadeBaseClientSide : CompilerServicesFacadeBase, Client

enum class ReportCategory(val code: Int) {
    COMPILER_MESSAGE(0),
    EXCEPTION(1),
    DAEMON_MESSAGE(2),
    IC_MESSAGE(3),
    OUTPUT_MESSAGE(4);

    companion object {
        fun fromCode(code: Int): ReportCategory? =
            ReportCategory.values().firstOrNull { it.code == code }
    }
}

enum class ReportSeverity(val code: Int) {
    ERROR(0),
    WARNING(1),
    INFO(2),
    DEBUG(3);

    companion object {
        fun fromCode(code: Int): ReportSeverity? =
            ReportSeverity.values().firstOrNull { it.code == code }
    }
}

fun CompilerServicesFacadeBase.report(
    category: ReportCategory,
    severity: ReportSeverity,
    message: String? = null,
    attachment: Serializable? = null
) {
    report(category.code, severity.code, message, attachment)
}
