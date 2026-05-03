package com.enrique.tiago_app.communication

import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration.Companion.seconds

class WebSocketClient {
    private val TAG = "WebSocketClient(Ktor)"

    // 1. Configuramos el motor Ktor con el plugin de WebSockets
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 20.seconds // ¡Así de elegante se lee el tiempo en Ktor!
        }
    }

    // La sesión actual. Si es null, estamos desconectados.
    private var session: DefaultClientWebSocketSession? = null

    // 2. La "Emisora de Radio". Emitiremos por aquí los textos que lleguen.
    private val _incomingMessages = MutableSharedFlow<String>()
    val incomingMessages: SharedFlow<String> = _incomingMessages

    /**
     * Esta es una función 'suspend' (Corrutina).
     * Se queda "bloqueada" y viva dentro de su bloque mientras la conexión dure.
     */
    suspend fun connect(ip: String, port: Int) {
        try {
            Log.d(TAG, "Intentando conectar a ws://$ip:$port...")

            // Ktor abre el túnel aquí. Todo lo que hay entre llaves ocurre MIENTRAS está conectado.
            client.webSocket(method = HttpMethod.Get, host = ip, port = port, path = "/") {
                Log.d(TAG, "¡Túnel WebSocket abierto con Ktor!")
                session = this

                // Bucle infinito: escuchamos los mensajes mientras el túnel siga activo
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val textReceived = frame.readText()
                        Log.d(TAG, "Recibido: $textReceived")
                        // Emitimos el mensaje por la radio para que el Repositorio lo oiga
                        _incomingMessages.emit(textReceived)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "La conexión Ktor falló o se cerró: ${e.message}")
        } finally {
            // Si salimos del bloque, es que nos hemos desconectado
            Log.d(TAG, "Limpiando sesión Ktor...")
            session = null
        }
    }

    /**
     * Enviar texto plano al robot.
     */
    suspend fun send(message: String) {
        try {
            session?.send(Frame.Text(message))
            Log.d(TAG, "Enviado: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Error al enviar: ${e.message}")
        }
    }

    /**
     * Cierra el túnel de forma limpia.
     */
    suspend fun disconnect() {
        session?.close(CloseReason(CloseReason.Codes.NORMAL, "Cierre manual del usuario"))
        session = null
    }
}