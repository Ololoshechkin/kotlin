package org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure

import io.ktor.network.sockets.Socket
import io.ktor.network.sockets.openReadChannel
import io.ktor.network.sockets.openWriteChannel
import kotlinx.coroutines.experimental.CompletableDeferred
import kotlinx.coroutines.experimental.channels.actor
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.io.ByteBuffer
import kotlinx.coroutines.experimental.io.ByteReadChannel
import kotlinx.coroutines.experimental.io.ByteWriteChannel
import kotlinx.coroutines.experimental.runBlocking
import kotlinx.io.core.readBytes
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.logging.Logger

private val DEFAULT_BYTE_ARRAY = byteArrayOf(0, 0, 0, 0)

class ByteReadChannelWrapper(readChannel: ByteReadChannel, private val log: Logger) {

    private interface ReadQuery

    private open class BytesQuery(val bytes: CompletableDeferred<ByteArray>) : ReadQuery

    private open class GivenLengthBytesQuery(val length: Int, val bytes: CompletableDeferred<ByteArray>) : ReadQuery

    private class SerObjectQuery(val obj: CompletableDeferred<Any?>) : ReadQuery

    private val readActor = actor<ReadQuery> {
        consumeEach { message ->
            if (!readChannel.isClosedForRead) {
                when (message) {
                    is GivenLengthBytesQuery -> message.bytes.complete(
                        readChannel.readPacket(
                            message.length
                        ).readBytes()
                    )
                    is BytesQuery -> message.bytes.complete(
                        readChannel.readPacket(
                            getLength(readChannel.readPacket(4).readBytes())
                        ).readBytes()
                    )
                    is SerObjectQuery -> message.obj.complete(
                        getObject(
                            getLength(readChannel.readPacket(4).readBytes()),
                            readChannel
                        )
                    )
                }
            }
        }
    }

    private fun getLength(packet: ByteArray): Int {
        val (b1, b2, b3, b4) = packet.map(Byte::toInt)
        return (0xFF and b1 shl 24 or (0xFF and b2 shl 16) or
                (0xFF and b3 shl 8) or (0xFF and b4)).also { log.info("   $it") }
    }

    /** reads exactly <tt>length</tt>  bytes.
     * after deafault timeout returns <tt>DEFAULT_BYTE_ARRAY</tt> */
    suspend fun readBytes(length: Int): ByteArray = runBlockingWithTimeout {
        val expectedBytes = CompletableDeferred<ByteArray>()
        readActor.send(GivenLengthBytesQuery(length, expectedBytes))
        expectedBytes.await()
    } ?: DEFAULT_BYTE_ARRAY

    /** first reads <t>length</t> token (4 bytes) and then -- reads <t>length</t> bytes.
     * after deafault timeout returns <tt>DEFAULT_BYTE_ARRAY</tt> */
    suspend fun nextBytes(): ByteArray = runBlockingWithTimeout {
        val expectedBytes = CompletableDeferred<ByteArray>()
        readActor.send(BytesQuery(expectedBytes))
        expectedBytes.await()
    } ?: DEFAULT_BYTE_ARRAY

    private suspend fun getObject(length: Int, readChannel: ByteReadChannel): Any? =
        if (length >= 0) {
            ObjectInputStream(
                ByteArrayInputStream(readChannel.readPacket(length).readBytes())
            ).use {
                it.readObject()
            }
        } else { // optimize for long strings!
            String(
                ByteArrayInputStream(
                    readChannel.readPacket(-length).readBytes()
                ).readBytes()
            )
        }

    /** first reads <t>length</t> token (4 bytes), then reads <t>length</t> bytes and returns deserialized object */
    suspend fun nextObject() = runBlocking {
        val obj = CompletableDeferred<Any?>()
        readActor.send(SerObjectQuery(obj))
        obj.await()
    }

}


class ByteWriteChannelWrapper(writeChannel: ByteWriteChannel, private val log: Logger) {

    private open class ByteData(val bytes: ByteArray) {
        open fun toByteArray(): ByteArray = bytes
    }

    private class ObjectWithLength(val lengthBytes: ByteArray, bytes: ByteArray) : ByteData(bytes) {
        override fun toByteArray() = lengthBytes + bytes
    }

    private val writeActor = actor<ByteData> {
        consumeEach { message ->
            if (!channel.isClosedForSend) {
                message.toByteArray().forEach {
                    writeChannel.writeByte(it)
                }
                writeChannel.flush()
            }
        }
    }

    suspend fun printBytesAndLength(length: Int, bytes: ByteArray) =
        writeActor.send(
            ObjectWithLength(
                getLengthBytes(length),
                bytes
            )
        )

    suspend fun printBytes(bytes: ByteArray) {
        writeActor.send(ByteData(bytes))
    }

    private suspend fun printObjectImpl(obj: Any?) =
        ByteArrayOutputStream().use { bos ->
            ObjectOutputStream(bos).use { objOut ->
                objOut.writeObject(obj)
                objOut.flush()
                val bytes = bos.toByteArray()
                printBytesAndLength(bytes.size, bytes)
            }
        }
            .also {
                log.info("sent object : $obj")
            }

    private suspend fun printString(s: String) = printBytesAndLength(-s.length, s.toByteArray())

    fun getLengthBytes(length: Int) =
        ByteBuffer
            .allocate(4)
            .putInt(length)
            .array()
            .also {
                log.info("printLength $length")
            }

    suspend fun writeObject(obj: Any?) {
        if (obj is String) printString(obj)
        else printObjectImpl(obj)
    }
}

fun ByteReadChannel.toWrapper(log: Logger) = ByteReadChannelWrapper(this, log)
fun ByteWriteChannel.toWrapper(log: Logger) = ByteWriteChannelWrapper(this, log)

fun Socket.openAndWrapReadChannel(log: Logger) = this.openReadChannel().toWrapper(log)
fun Socket.openAndWrapWriteChannel(log: Logger) = this.openWriteChannel().toWrapper(log)

data class IOPair(val input: ByteReadChannelWrapper, val output: ByteWriteChannelWrapper)

fun Socket.openIO(log: Logger) = IOPair(this.openAndWrapReadChannel(log), this.openAndWrapWriteChannel(log))