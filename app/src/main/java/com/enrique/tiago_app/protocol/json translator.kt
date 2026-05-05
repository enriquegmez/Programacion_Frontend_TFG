package com.enrique.tiago_app.protocol

import android.util.Log
import com.enrique.tiago_app.utils.AppConstants
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * MessageCodec (El Traductor)
 */
class MessageCodec {
    // 1. Cambiamos 'private' por '@PublishedApi internal'
    @PublishedApi
    internal val TAG = "MessageCodec"

    // 2. Hacemos lo mismo con el motor JSON
    @OptIn(ExperimentalSerializationApi::class)
    @PublishedApi
    internal val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    // 3. Y con el contador de IDs
    @PublishedApi
    internal var msgIdCounter: Long = 1L

    @PublishedApi
    internal fun getNextMsgId(): Long {
        return msgIdCounter++
    }

    // ==========================================
    // 1. PARSER (De JSON recibido a Kotlin)
    // ==========================================

    /**
     * Convierte el string del WebSocket en un objeto RobotMessage estructurado.
     * Si hay un error JSON, devuelve un RobotMessage de tipo PROTOCOL_ERROR.
     */
    fun decode(rawString: String): RobotMessage {
        return try {
            jsonFormat.decodeFromString<RobotMessage>(rawString)
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico de formato JSON: ${e.message}")
            buildInternalErrorMsg(
                AppConstants.StatusCode.BAD_REQUEST,
                "Invalid JSON format or missing fields"
            )
        }
    }

    /**
     * Extrae el JsonElement y lo convierte a la Data Class específica (Ej: CommandRespPayload).
     * El Director usará esta función después de ver el header.type.
     */
    inline fun <reified T> decodePayload(payload: JsonElement): T? {
        return try {
            jsonFormat.decodeFromJsonElement<T>(payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error al decodificar el payload específico: ${e.message}")
            null
        }
    }

    private fun buildInternalErrorMsg(code: Int, description: String): RobotMessage {
        val header = MessageHeader(
            msgId = -1L,
            type = AppConstants.MsgType.PROTOCOL_ERROR,
            sessionId = "",
            timestamp = System.currentTimeMillis() / 1000.0
        )
        // Usamos la función inline reified para convertir la clase de error a JsonElement
        val errorPayload = ProtocolErrorPayload(errorCode = code, description = description)
        val payloadElement = jsonFormat.encodeToJsonElement(errorPayload)

        return RobotMessage(header = header, payload = payloadElement)
    }

    // ==========================================
    // 2. ENCODER (De Kotlin a String JSON)
    // ==========================================

    /**
     * Toma una cabecera y un payload específico, inyecta los datos temporales,
     * limpia los nulos y lo convierte en un string JSON válido.
     */
    inline fun <reified T> encode(header: MessageHeader, payloadObj: T): String {
        // 1. Clonamos la cabecera para autocompletar el ID y el timestamp (igual que en Python)
        val finalMsgId = if (header.msgId <= 0L) getNextMsgId() else header.msgId
        val finalHeader = header.copy(
            msgId = finalMsgId,
            timestamp = System.currentTimeMillis() / 1000.0
        )

        // 2. Convertimos el payload específico a JsonElement genérico
        val payloadElement = jsonFormat.encodeToJsonElement(payloadObj)

        // 3. Empaquetamos todo en el RobotMessage final
        val robotMessage = RobotMessage(header = finalHeader, payload = payloadElement)

        // 4. Lo convertimos a String (explicitNulls=false quitará los nulos por nosotros)
        val finalJson = jsonFormat.encodeToString(robotMessage)

        Log.d(TAG, "Codificado (OUT): $finalJson")
        return finalJson
    }
}