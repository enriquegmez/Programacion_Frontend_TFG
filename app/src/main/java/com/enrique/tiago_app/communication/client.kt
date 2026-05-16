package com.enrique.tiago_app.communication

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

/**
 * WebSocketClient (El Cartero)
 * Capa de transporte puro. No sabe nada de JSONs ni del protocolo del robot.
 */
class WebSocketClient {
    //private val TAG = "WebSocketClient"
    private val TAG = "TIAGO_ProtocolDirector"

    // 1. Configuramos el motor Ktor
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 20.seconds
        }
    }

    private var session: DefaultClientWebSocketSession? = null

    // 2. La "Emisora de Radio" para los mensajes entrantes (JSON en texto plano)
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages

    // 3. NUEVO: El "Chivato" del estado de la conexión para que la UI reaccione
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Abre el túnel. Esta corrutina se queda viva leyendo mensajes hasta que se corte.
     */
    suspend fun connect(ip: String, port: Int) {
        try {
            Log.d(TAG, "Intentando conectar a ws://$ip:$port...")

            // Si ya hay una sesión activa, no hacemos nada
            if (_isConnected.value) return

            client.webSocket(method = HttpMethod.Get, host = ip, port = port, path = "/") {
                Log.d(TAG, "¡Túnel WebSocket abierto con Ktor!")
                session = this
                _isConnected.value = true

                // Bucle infinito: escuchamos los mensajes mientras el túnel siga activo
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val textReceived = frame.readText()
                            // Emitimos el mensaje por la radio para que el Director lo oiga
                            _incomingMessages.emit(textReceived)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Bucle de lectura interrumpido: ${e.message}")
                }
            }
        } catch (e: Exception) {
            // Captura errores como "Connection refused" si la IP está mal o el backend está apagado
            Log.e(TAG, "La conexión Ktor falló: ${e.message}")
        } finally {
            // Limpieza garantizada, ya sea por desconexión manual o caída de red
            Log.d(TAG, "Limpiando sesión Ktor y notificando desconexión...")
            session = null
            _isConnected.value = false
        }
    }

    /**
     * Enviar texto plano al servidor.
     */
    suspend fun send(message: String) {
        try {
            if (session?.isActive == true) {
                session?.send(Frame.Text(message))
            } else {
                Log.w(TAG, "Intento de envío bloqueado: El WebSocket no está conectado.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar: ${e.message}")
        }
    }

    /**
     * Cierra el túnel de forma limpia enviando el código de cierre estándar de WebSockets.
     */
    suspend fun disconnect() {
        if (session != null) {
            Log.d(TAG, "Enviando petición de cierre manual...")
            try {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "Cierre por parte del usuario"))
            } catch (e: Exception) {
                Log.e(TAG, "Error durante el cierre: ${e.message}")
            } finally {
                session = null
                _isConnected.value = false
            }
        }
    }
}