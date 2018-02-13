/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.daemon.common.experimental

import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.kotlin.daemon.common.IncrementalCompilerServicesFacade
import org.jetbrains.kotlin.daemon.common.RemoteInputStream
import org.jetbrains.kotlin.daemon.common.RemoteOutputStream

class RemoteOutputStreamAsyncWrapper(val rmiOutput: RemoteOutputStream) : RemoteOutputStreamAsyncClientSide {

    override fun attachToServer() {}

    suspend override fun close() =
        rmiOutput.close()

    suspend override fun write(data: ByteArray, offset: Int, length: Int) =
        rmiOutput.write(data, offset, length)

    suspend override fun write(dataByte: Int) =
        rmiOutput.write(dataByte)

}

class RemoteInputStreamAsyncWrapper(private val rmiInput: RemoteInputStream) : RemoteInputStreamClientSide {

    override fun attachToServer() {}

    suspend override fun close() = 
        rmiInput.close()

    suspend override fun read() = 
        rmiInput.read()

    suspend override fun read(length: Int) = 
        rmiInput.read(length)
}

fun RemoteOutputStream.toClient() = RemoteOutputStreamAsyncWrapper(this)
fun RemoteInputStream.toClient() = RemoteInputStreamAsyncWrapper(this)