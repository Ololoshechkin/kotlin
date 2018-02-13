package org.jetbrains.kotlin.daemon.common.experimental.socketInfrastructure

import io.ktor.network.sockets.Socket
import java.io.Serializable

interface Client : Serializable {
    fun attachToServer()
}