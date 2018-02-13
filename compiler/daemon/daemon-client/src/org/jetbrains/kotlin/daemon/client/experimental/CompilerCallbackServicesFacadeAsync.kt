/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.client.experimental

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.kotlin.daemon.common.experimental.CompilerServicesFacadeBaseServerSide
import org.jetbrains.kotlin.daemon.common.experimental.SOCKET_ANY_FREE_PORT
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.ByteWriteChannelWrapper
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.Server
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.openIO
import org.jetbrains.kotlin.incremental.components.LookupInfo
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.load.kotlin.incremental.components.IncrementalCompilationComponents
import org.jetbrains.kotlin.load.kotlin.incremental.components.JvmPackagePartProto
import org.jetbrains.kotlin.modules.TargetId
import org.jetbrains.kotlin.progress.CompilationCanceledStatus
import org.jetbrains.kotlin.utils.isProcessCanceledException
import java.io.Serializable
import java.net.InetSocketAddress

open class CompilerCallbackServicesFacadeServerSide(
    val incrementalCompilationComponents: IncrementalCompilationComponents? = null,
    val lookupTracker: LookupTracker? = null,
    val compilationCanceledStatus: CompilationCanceledStatus? = null,
    port: Int = SOCKET_ANY_FREE_PORT
) : CompilerServicesFacadeBaseServerSide {

    suspend override fun processMessage(
        msg: Server.AnyMessage<CompilerServicesFacadeBaseServerSide>,
        output: ByteWriteChannelWrapper
    ) = when(msg) {
        is Server.Message<CompilerServicesFacadeBaseServerSide> -> Server.State.WORKING.also {
            msg.process(this, output)
        }
        is Server.EndConnectionMessage -> Server.State.CLOSED
        else -> Server.State.ERROR
    }

    suspend override fun attachClient(client: Socket) {
        async {
            val (input, output) = client.openIO()
            loop@ while (true) {
                when (processMessage(input.nextObject() as Server.AnyMessage<CompilerServicesFacadeBaseServerSide>, output)) {
                    Server.State.CLOSED -> break@loop
                    Server.State.ERROR -> {
                        // TODO: debug message "Server error: invalid message"
                    }
                }
            }
        }
    }

    suspend override fun report(category: Int, severity: Int, message: String?, attachment: Serializable?) =
            TODO("report!!")

    fun hasIncrementalCaches(): Boolean = incrementalCompilationComponents != null

    fun hasLookupTracker(): Boolean = lookupTracker != null

    fun hasCompilationCanceledStatus(): Boolean = compilationCanceledStatus != null

    // TODO: consider replacing NPE with other reporting, although NPE here means most probably incorrect usage

    fun incrementalCache_getObsoletePackageParts(target: TargetId): Collection<String> =
        incrementalCompilationComponents!!.getIncrementalCache(target).getObsoletePackageParts()

    fun incrementalCache_getObsoleteMultifileClassFacades(target: TargetId): Collection<String> =
        incrementalCompilationComponents!!.getIncrementalCache(target).getObsoleteMultifileClasses()

    fun incrementalCache_getMultifileFacadeParts(target: TargetId, internalName: String): Collection<String>? =
        incrementalCompilationComponents!!.getIncrementalCache(target).getStableMultifileFacadeParts(internalName)

    fun incrementalCache_getPackagePartData(target: TargetId, partInternalName: String): JvmPackagePartProto? =
        incrementalCompilationComponents!!.getIncrementalCache(target).getPackagePartData(partInternalName)

    fun incrementalCache_getModuleMappingData(target: TargetId): ByteArray? =
        incrementalCompilationComponents!!.getIncrementalCache(target).getModuleMappingData()

    // todo: remove (the method it called was relevant only for old IC)
    fun incrementalCache_registerInline(target: TargetId, fromPath: String, jvmSignature: String, toPath: String) {
    }

    fun incrementalCache_getClassFilePath(target: TargetId, internalClassName: String): String =
        incrementalCompilationComponents!!.getIncrementalCache(target).getClassFilePath(internalClassName)

    fun incrementalCache_close(target: TargetId) {
        incrementalCompilationComponents!!.getIncrementalCache(target).close()
    }

    fun lookupTracker_requiresPosition() = lookupTracker!!.requiresPosition

    fun lookupTracker_record(lookups: Collection<LookupInfo>) {
        val lookupTracker = lookupTracker!!

        for (it in lookups) {
            lookupTracker.record(it.filePath, it.position, it.scopeFqName, it.scopeKind, it.name)
        }
    }

    private val lookupTracker_isDoNothing: Boolean = lookupTracker === LookupTracker.DO_NOTHING

    fun lookupTracker_isDoNothing(): Boolean = lookupTracker_isDoNothing

    fun compilationCanceledStatus_checkCanceled(): Void? {
        try {
            compilationCanceledStatus!!.checkCanceled()
            return null
        }
        catch (e: Exception) {
            // avoid passing exceptions that may have different serialVersionUID on across rmi border
            // removing dependency from openapi (this is obsolete part anyway, and will be removed soon)
            if (e.isProcessCanceledException())
                throw Exception("-TODO- RmiFriendlyCompilationCanceledException()") //RmiFriendlyCompilationCanceledException()
            else throw e
        }
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

