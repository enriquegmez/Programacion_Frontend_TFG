@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.enrique.tiago_app.protocol

// Importamos lo básico
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

// LOS DOS IMPORTS CORREGIDOS:
import kotlin.OptIn
import kotlinx.serialization.InternalSerializationApi

// Ahora sí aplicamos la etiqueta sin errores:
@OptIn(InternalSerializationApi::class)
@Serializable
data class RobotMessage(
    val header: MessageHeader,
    val payload: kotlinx.serialization.json.JsonElement
)

@Serializable
data class MessageHeader(
    @SerialName("msg_id")
    val msgId: Int,
    val timestamp: Double,
    val type: String,
    @SerialName("session_id")
    val sessionId: String
)

@Serializable
data class ConnectPayload(
    val action: String
)