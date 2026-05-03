package com.enrique.tiago_app.protocol

import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import com.enrique.tiago_app.utils.AppConstants

/**
 * MessageManager (El Traductor)
 * Convierte Data Classes de Kotlin a texto JSON (Factory)
 * y texto JSON a Data Classes de Kotlin (Parser).
 */
class MessageCodec {
    private val TAG = "MessageManager"

    // Configuramos el motor de traducción JSON.
    // ignoreUnknownKeys = true evita que la app falle si el robot envía un dato que no esperábamos.
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Contador para asignar un msg_id único a cada mensaje saliente
    private var msgIdCounter = 0

    private fun getNextMsgId(): Int {
        return msgIdCounter++
    }

    // ==========================================
    // 1. FACTORY (De Kotlin a JSON para enviar)
    // ==========================================

    /**
     * Construye el JSON exacto para pedir la conexión al servidor.
     */
    fun buildConnectRequest(sessionId: String): String {
        // 1. Fabricamos la pegatina del sobre (Header)
        val header = MessageHeader(
            msgId = getNextMsgId(),
            timestamp = System.currentTimeMillis() / 1000.0,
            type = AppConstants.MsgType.COMMAND_REQ,
            sessionId = sessionId
        )

        // 2. Fabricamos el contenido (Payload) y lo pasamos a formato JsonElement
        val payloadObj = ConnectPayload(action = AppConstants.Action.CONNECT)
        val payloadJsonElement = jsonFormat.encodeToJsonElement(payloadObj)

        // 3. Lo metemos todo en el sobre final
        val robotMessage = RobotMessage(header, payloadJsonElement)

        // 4. Convertimos el sobre completo a un String de texto (JSON crudo)
        val finalJson = jsonFormat.encodeToString(robotMessage)

        Log.d(TAG, "Empaquetado listo para enviar: $finalJson")
        return finalJson
    }

    // ==========================================
    // 2. PARSER (De JSON recibido a Kotlin)
    // ==========================================

    /**
     * Lee un texto JSON del WebSocket y lo convierte en un objeto RobotMessage.
     */
    fun parseMessage(rawJson: String): RobotMessage? {
        return try {
            // La librería hace la magia de leer el texto y rellenar las variables
            val message = jsonFormat.decodeFromString<RobotMessage>(rawJson)
            Log.d(TAG, "Mensaje decodificado correctamente. Tipo: ${message.header.type}")
            message
        } catch (e: Exception) {
            Log.e(TAG, "Error crítico al decodificar el JSON del robot: ${e.message}")
            null
        }
    }
}