/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental


import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import org.jetbrains.kotlin.daemon.common.*
import java.io.File
import java.rmi.registry.LocateRegistry

/*
1) walkDaemonsAsync = walkDaemons + some async calls inside (also some used classes changed *** -> ***Async)
2) tryConnectToDaemonBySockets / tryConnectToDaemonByRMI

*/

internal val MAX_PORT_NUMBER = 0xffff

private const val ORPHANED_RUN_FILE_AGE_THRESHOLD_MS = 1000000L

data class DaemonWithMetadataAsync(val daemon: CompileServiceClientSide, val runFile: File, val jvmOptions: DaemonJVMOptions)

// TODO: write metadata into discovery file to speed up selection
// TODO: consider using compiler jar signature (checksum) as a CompilerID (plus java version, plus ???) instead of classpath checksum
//    would allow to use same compiler from taken different locations
//    reqs: check that plugins (or anything els) should not be part of the CP
suspend fun walkDaemonsAsync(
    registryDir: File,
    compilerId: CompilerId,
    fileToCompareTimestamp: File,
    filter: (File, Int) -> Boolean = { _, _ -> true },
    report: (DaemonReportCategory, String) -> Unit = { _, _ -> },
    useRMI: Boolean = true
): Deferred<List<DaemonWithMetadataAsync>> = async {
    // : Sequence<DaemonWithMetadataAsync>
    val classPathDigest = compilerId.compilerClasspath.map { File(it).absolutePath }.distinctStringsDigest().toHexString()
    val portExtractor = org.jetbrains.kotlin.daemon.common.makePortFromRunFilenameExtractor(classPathDigest)
    registryDir.walk().toList() // list, since walk returns Sequence and Sequence.map{...} is not inline => coroutines dont work
        .map { Pair(it, portExtractor(it.name)) }
        .filter { (file, port) -> port != null && filter(file, port) }
        .map { (file, port) ->
            // all actions process concurrently
            async {
                assert(port!! in 1..(org.jetbrains.kotlin.daemon.common.MAX_PORT_NUMBER - 1))
                val relativeAge = fileToCompareTimestamp.lastModified() - file.lastModified()
                report(
                    org.jetbrains.kotlin.daemon.common.DaemonReportCategory.DEBUG,
                    "found daemon on socketPort $port ($relativeAge ms old), trying to connect"
                )
                val daemon = tryConnectToDaemonAsync(port, report, useRMI)
                // cleaning orphaned file; note: daemon should shut itself down if it detects that the runServer file is deleted
                if (daemon == null) {
                    if (relativeAge - ORPHANED_RUN_FILE_AGE_THRESHOLD_MS <= 0) {
                        report(
                            org.jetbrains.kotlin.daemon.common.DaemonReportCategory.DEBUG,
                            "found fresh runServer file '${file.absolutePath}' ($relativeAge ms old), but no daemon, ignoring it"
                        )
                    } else {
                        report(
                            org.jetbrains.kotlin.daemon.common.DaemonReportCategory.DEBUG,
                            "found seemingly orphaned runServer file '${file.absolutePath}' ($relativeAge ms old), deleting it"
                        )
                        if (!file.delete()) {
                            report(
                                org.jetbrains.kotlin.daemon.common.DaemonReportCategory.INFO,
                                "WARNING: unable to delete seemingly orphaned file '${file.absolutePath}', cleanup recommended"
                            )
                        }
                    }
                }
                try {
                    daemon?.let {
                        DaemonWithMetadataAsync(it, file, it.getDaemonJVMOptions().get())
                    }
                } catch (e: Exception) {
                    report(
                        org.jetbrains.kotlin.daemon.common.DaemonReportCategory.INFO,
                        "ERROR: unable to retrieve daemon JVM options, assuming daemon is dead: ${e.message}"
                    )
                    null
                }
            }
        }
        .mapNotNull { it.await() } // await for completion of the last action
}

private inline fun tryConnectToDaemonByRMI(port: Int, report: (DaemonReportCategory, String) -> Unit): CompileServiceClientSide? {
    try {
        val daemon = LocateRegistry.getRegistry(
            LoopbackNetworkInterface.loopbackInetAddressName,
            port,
            LoopbackNetworkInterface.clientLoopbackSocketFactoryRMI
        )?.lookup(COMPILER_SERVICE_RMI_NAME)
        when (daemon) {
            null -> report(DaemonReportCategory.INFO, "daemon not found")
            is CompileService -> return daemon.toClient()
            else -> report(DaemonReportCategory.INFO, "Unable to cast compiler service, actual class received: ${daemon::class.java.name}")
        }
    } catch (e: Throwable) {
        report(DaemonReportCategory.INFO, "cannot connect to registry: " + (e.cause?.message ?: e.message ?: "unknown error"))
    }
    return null
}

private inline fun tryConnectToDaemonBySockets(port: Int, report: (DaemonReportCategory, String) -> Unit): CompileServiceClientSide? {
    try {
        return CompileServiceClientSideImpl(
            LoopbackNetworkInterface.loopbackInetAddressName,
            port
        ).also { it.connectToServer() }
    } catch (e: Throwable) {
        report(DaemonReportCategory.INFO, "cannot find or connect to socket")
    }
    return null
}

private fun tryConnectToDaemonAsync(
    port: Int,
    report: (DaemonReportCategory, String) -> Unit,
    useRMI: Boolean = true
): CompileServiceClientSide? =
    tryConnectToDaemonBySockets(port, report)
            ?: useRMI.takeIf { it }?.let { tryConnectToDaemonByRMI(port, report) }

private const val validFlagFileKeywordChars = "abcdefghijklmnopqrstuvwxyz0123456789-_"
