/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import io.ktor.network.sockets.aSocket
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure.*
import org.jetbrains.kotlin.incremental.components.LookupInfo
import org.jetbrains.kotlin.load.kotlin.incremental.components.JvmPackagePartProto
import org.jetbrains.kotlin.modules.TargetId
import java.beans.Transient
import java.io.Serializable
import java.net.InetSocketAddress


interface CompilerCallbackServicesFacadeClientSide : CompilerCallbackServicesFacadeAsync, Client<CompilerServicesFacadeBaseServerSide>, CompilerServicesFacadeBaseClientSide

@Suppress("UNCHECKED_CAST")
class CompilerCallbackServicesFacadeClientSideImpl(serverPort: Int) : CompilerCallbackServicesFacadeClientSide,
    Client<CompilerServicesFacadeBaseServerSide> by DefaultClient(serverPort) {

    override suspend fun hasIncrementalCaches(): Boolean {
        sendMessage(CompilerCallbackServicesFacadeServerSide.HasIncrementalCachesMessage())
        return readMessage<Boolean>()
    }

    override suspend fun hasLookupTracker(): Boolean {
        sendMessage(CompilerCallbackServicesFacadeServerSide.HasLookupTrackerMessage())
        return readMessage<Boolean>()
    }

    override suspend fun hasCompilationCanceledStatus(): Boolean {
        sendMessage(CompilerCallbackServicesFacadeServerSide.HasCompilationCanceledStatusMessage())
        return readMessage<Boolean>()
    }

    override suspend fun incrementalCache_getObsoletePackageParts(target: TargetId): Collection<String> {
        sendMessage(CompilerCallbackServicesFacadeServerSide.IncrementalCache_getObsoletePackagePartsMessage(target))
        return readMessage<Collection<String>>()
    }

    override suspend fun incrementalCache_getObsoleteMultifileClassFacades(target: TargetId): Collection<String> {
        sendMessage(CompilerCallbackServicesFacadeServerSide.IncrementalCache_getObsoleteMultifileClassFacadesMessage(target))
        return readMessage<Collection<String>>()
    }

    override suspend fun incrementalCache_getPackagePartData(target: TargetId, partInternalName: String): JvmPackagePartProto? {
        sendMessage(CompilerCallbackServicesFacadeServerSide.IncrementalCache_getPackagePartDataMessage(target, partInternalName))
        return readMessage<JvmPackagePartProto?>()
    }

    override suspend fun incrementalCache_getModuleMappingData(target: TargetId): ByteArray? {
        sendMessage(CompilerCallbackServicesFacadeServerSide.IncrementalCache_getModuleMappingDataMessage(target))
        return readMessage<ByteArray?>()
    }

    override suspend fun incrementalCache_registerInline(target: TargetId, fromPath: String, jvmSignature: String, toPath: String) =
        sendMessage(
            CompilerCallbackServicesFacadeServerSide.IncrementalCache_registerInlineMessage(
                target,
                fromPath,
                jvmSignature,
                toPath
            )
        )

    override suspend fun incrementalCache_getClassFilePath(target: TargetId, internalClassName: String): String {
        sendMessage(CompilerCallbackServicesFacadeServerSide.IncrementalCache_getClassFilePathMessage(target, internalClassName))
        return readMessage<String>()
    }

    override suspend fun incrementalCache_close(target: TargetId) =
        sendMessage(CompilerCallbackServicesFacadeServerSide.IncrementalCache_closeMessage(target))

    override suspend fun incrementalCache_getMultifileFacadeParts(target: TargetId, internalName: String): Collection<String>? {
        sendMessage(CompilerCallbackServicesFacadeServerSide.IncrementalCache_getMultifileFacadePartsMessage(target, internalName))
        return readMessage<Collection<String>?>()
    }

    override suspend fun lookupTracker_requiresPosition(): Boolean {
        sendMessage(CompilerCallbackServicesFacadeServerSide.LookupTracker_requiresPositionMessage())
        return readMessage<Boolean>()
    }

    override suspend fun lookupTracker_record(lookups: Collection<LookupInfo>) =
        sendMessage(CompilerCallbackServicesFacadeServerSide.LookupTracker_recordMessage(lookups))

    override suspend fun lookupTracker_isDoNothing(): Boolean {
        sendMessage(CompilerCallbackServicesFacadeServerSide.LookupTracker_isDoNothingMessage())
        return readMessage<Boolean>()
    }

    override suspend fun compilationCanceledStatus_checkCanceled(): Void? {
        sendMessage(CompilerCallbackServicesFacadeServerSide.CompilationCanceledStatus_checkCanceledMessage())
        return null
    }

    override suspend fun report(category: Int, severity: Int, message: String?, attachment: Serializable?) {
        sendMessage(CompilerServicesFacadeBaseServerSide.ReportMessage(category, severity, message, attachment))
    }

}