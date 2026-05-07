package com.enrique.tiago_app.logic

import android.util.Log
import com.enrique.tiago_app.communication.SessionManager
import com.enrique.tiago_app.protocol.MessageCodec
import com.enrique.tiago_app.communication.WebSocketClient
import com.enrique.tiago_app.utils.AppConstants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class RobotRepository(
    private val webSocketClient: WebSocketClient,
    private val messageManager: MessageCodec,
    private val sessionManager: SessionManager
) {
    private val TAG = "RobotRepository"

    private val repoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 1. Emisora del Estado principal (Conectado/Desconectado)
    private val _connectionStatus = MutableStateFlow("Desconectado")
    val connectionStatus: StateFlow<String> = _connectionStatus

    // 2. ¡AQUÍ ESTÁ LA LÍNEA QUE FALTABA! (La emisora de la consola de texto)
    private val _lastLogs = MutableStateFlow("Esperando eventos...")
    val lastLogs: StateFlow<String> = _lastLogs

    init {
        repoScope.launch {
            webSocketClient.incomingMessages.collect { rawText ->
                handleIncomingMessage(rawText)
            }
        }
    }

    fun connect() {
        _connectionStatus.value = "Conectando con Ktor..."
        _lastLogs.value = "⏳ Abriendo puerto a ${AppConstants.DEFAULT_SERVER_IP}..."

        repoScope.launch {
            launch {
                webSocketClient.connect(AppConstants.DEFAULT_SERVER_IP, AppConstants.DEFAULT_SERVER_PORT)
                sessionManager.clearSession()
                _connectionStatus.value = "Desconectado"
                _lastLogs.value = "🚪 Túnel cerrado."
            }

            delay(500) // Damos un respiro a Ktor para abrir el puerto

            val connectJson = messageManager.buildConnectRequest(sessionId = "initial_ktor_handshake")
            webSocketClient.send(connectJson)
            _lastLogs.value = "📤 Enviado: Petición de conexión"
        }
    }

    fun disconnect() {
        repoScope.launch {
            webSocketClient.disconnect()
            sessionManager.clearSession()
            _connectionStatus.value = "Desconectado"
            _lastLogs.value = "🛑 Desconectado manualmente"
        }
    }

    private fun handleIncomingMessage(text: String) {
        // Mostramos el texto crudo en la pantalla
        _lastLogs.value = "📩 Recibido:\n$text"

        val robotMessage = messageManager.parseMessage(text)

        if (robotMessage != null) {
            if (robotMessage.header.type == AppConstants.MsgType.RESP) {
                sessionManager.saveSession(robotMessage.header.sessionId)
                _connectionStatus.value = "✅ Conectado (Sesión: ${sessionManager.currentSessionId})"
                Log.i(TAG, "Sesión establecida con éxito.")
            }
        } else {
            Log.e(TAG, "Se recibió un JSON no válido.")
        }
    }
}