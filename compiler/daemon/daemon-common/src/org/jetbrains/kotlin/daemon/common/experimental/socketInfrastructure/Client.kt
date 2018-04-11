package org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure

import com.sun.deploy.si.SingleInstanceManager.connectToServer
import io.ktor.network.sockets.Socket
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.kotlin.daemon.common.experimental.LoopbackNetworkInterface
import sun.net.ConnectionResetException
import java.beans.Transient
import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.concurrent.locks.ReentrantLock
import java.util.logging.Logger


interface Client<ServerType : ServerBase> : Serializable, AutoCloseable {
    @Throws(Exception::class)
    fun connectToServer()

    fun sendMessage(msg: Server.AnyMessage<out ServerType>)
    fun <T> readMessage(): T
}

@Suppress("UNCHECKED_CAST")
abstract class DefaultAuthorizableClient<ServerType : ServerBase>(
    val serverPort: Int,
    val serverHost: String = LoopbackNetworkInterface.loopbackInetAddressName
) : Client<ServerType> {

    val log: Logger
        @Transient get() = Logger.getLogger("default client($serverPort)")

    lateinit var input: ByteReadChannelWrapper
        @Transient get
        @Transient set

    lateinit var output: ByteWriteChannelWrapper
        @Transient get
        @Transient set

    private var socket: Socket? = null
        @Transient get
        @Transient set

    abstract suspend fun authorizeOnServer(serverOutputChannel: ByteWriteChannelWrapper): Boolean
    abstract suspend fun clientHandshake(input: ByteReadChannelWrapper, output: ByteWriteChannelWrapper, log: Logger): Boolean

    override fun close() {
//        try {
//            runBlocking {
//                runWithTimeout {
//                    output.writeObject(Server.EndConnectionMessage<ServerType>())
//                }
//            }
//        } catch (e: Throwable) {
//            log.info(e.message)
//        } finally {
//            socket?.close()
//        }
        socket?.close()
    }

    override fun sendMessage(msg: Server.AnyMessage<out ServerType>) = runBlocking {
        output.writeObject(msg)
    }

    override fun <T> readMessage(): T = runBlocking { input.nextObject() as T }

    override fun connectToServer() {
        runBlocking {
            log.info("connectToServer (port = $serverPort | host = $serverHost)")
            try {
                socket = LoopbackNetworkInterface.clientLoopbackSocketFactoryKtor.createSocket(
                    serverHost,
                    serverPort
                )
            } catch (e: Throwable) {
                log.info("EXCEPTION while connecting to server ($e)")
                close()
                throw e
            }
            log.info("connected (port = $serverPort, serv =$serverPort)")
            socket!!.openIO(log).also {
                log.info("OK serv.openIO() |port=$serverPort|")
                input = it.input
                output = it.output
                if (!clientHandshake(input, output, log)) {
                    log.info("failed handshake($serverPort)")
                    close()
                    throw ConnectionResetException("failed to establish connection with server (handshake failed)")
                }
                if (!authorizeOnServer(output)) {
                    log.info("failed authorization($serverPort)")
                    close()
                    throw ConnectionResetException("failed to establish connection with server (authorization failed)")
                }
            }
        }
    }

    @Throws(ClassNotFoundException::class, IOException::class)
    private fun readObject(aInputStream: ObjectInputStream) {
        aInputStream.defaultReadObject()
        connectToServer()
    }

    @Throws(IOException::class)
    private fun writeObject(aOutputStream: ObjectOutputStream) {
        aOutputStream.defaultWriteObject()
    }

}

class DefaultClient<ServerType : ServerBase>(
    serverPort: Int,
    serverHost: String = LoopbackNetworkInterface.loopbackInetAddressName
) : DefaultAuthorizableClient<ServerType>(serverPort, serverHost) {
    override suspend fun clientHandshake(input: ByteReadChannelWrapper, output: ByteWriteChannelWrapper, log: Logger) = true
    override suspend fun authorizeOnServer(output: ByteWriteChannelWrapper): Boolean = true
}

class DefaultClientRMIWrapper<ServerType : ServerBase> : Client<ServerType> {
    override fun connectToServer() {}
    override fun sendMessage(msg: Server.AnyMessage<out ServerType>) =
        throw UnsupportedOperationException("sendMessage is not supported for RMI wrappers")

    override fun <T> readMessage() = throw UnsupportedOperationException("readMessage is not supported for RMI wrappers")
    override fun close() {}
}