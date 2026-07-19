/**
 * @file WebSocketClient.kt
 * @brief Capa de transporte asíncrona para la comunicación bidireccional.
 * @details Gestiona el túnel WebSocket utilizando el cliente HTTP Ktor.
 *          Funciona como un componente puramente de red (Capa 7 OSI):
 *          envía y recibe cadenas de texto en crudo sin acoplarse a la
 *          semántica del protocolo.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.communication

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
 * @class WebSocketClient
 * @brief Cliente de red reactivo basado en WebSockets.
 * @details Mantiene el ciclo de vida de la conexión y expone flujos de datos (Flows)
 *          para que las capas superiores puedan encargarse del tratamiento de los mensajes.
 */
class WebSocketClient {

    private val TAG = "WebSocketClient"

    // ========================================================================
    // 1. MOTOR DE RED (Ktor CIO)
    // ========================================================================
    /*
     * Se utiliza el motor CIO (Coroutine-based I/O) por su alto rendimiento
     * en aplicaciones asíncronas de Android, configurando un Ping
     * automático cada 20 segundos para mantener el túnel vivo (Keep-Alive).
     */
    private val client = HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = 20.seconds
        }
    }

    private var session: DefaultClientWebSocketSession? = null

    // ========================================================================
    // 2. FLUJOS REACTIVOS
    // ========================================================================

    /**
     * @property incomingMessages Flujo de eventos con los mensajes en texto plano.
     * Implementa un buffer de 64 mensajes para evitar cuellos de botella en la UI.
     */
    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages

    /**
     * @property isConnected Flujo de estado que emite el estado real de la red.
     * Funciona como un Watchdog para que la UI se actualice reactivamente.
     */
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    // ========================================================================
    // 3. MÉTODOS DE CONTROL DEL CICLO DE VIDA
    // ========================================================================

    /**
     * @brief Establece la conexión física y bloquea la corrutina para escuchar el canal.
     * @details Si la conexión es exitosa, entra en un bucle infinito de lectura suspendida.
     *          Cualquier desconexión (manual o por pérdida de cobertura) romperá el bucle
     *          y ejecutará el bloque 'finally' garantizando la limpieza del estado.
     * @param ip Dirección IPv4 del servidor Websocket (backend).
     * @param port Puerto de escucha del servidor WebSocket.
     */
    suspend fun connect(ip: String, port: Int) {
        try {
            Log.d(TAG, "Intentando conectar a ws://$ip:$port...")

            // Prevención de conexiones duplicadas si el túnel ya está abierto
            if (_isConnected.value) return

            client.webSocket(method = HttpMethod.Get, host = ip, port = port, path = "/") {
                Log.d(TAG, "¡Túnel WebSocket abierto con Ktor CIO!")
                session = this
                _isConnected.value = true

                // Bucle de recepción reactiva
                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val textReceived = frame.readText()
                            // Se emite el payload crudo hacia la capa lógica (Director)
                            _incomingMessages.emit(textReceived)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Bucle de lectura interrumpido: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "La conexión Ktor falló (Host inaccesible o rechazado): ${e.message}")
        } finally {
            // Cierre seguro garantizado (Graceful degradation)
            Log.d(TAG, "Limpiando sesión Ktor y notificando desconexión a la UI...")
            session = null
            _isConnected.value = false
        }
    }

    /**
     * @brief Transmite un mensaje serializado hacia el backend físico.
     * @param message Cadena de texto (JSON) lista para enviar.
     */
    suspend fun send(message: String) {
        try {
            if (session?.isActive == true) {
                session?.send(Frame.Text(message))
            } else {
                Log.w(TAG, "Intento de envío bloqueado: El túnel WebSocket no está activo.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error físico al enviar paquete: ${e.message}")
        }
    }

    /**
     * @brief Rompe la conexión explícitamente mediante el protocolo estándar de WebSockets.
     * @details Envía la trama de cierre (código 1000 - NORMAL) avisando al servidor
     *          para que libere la sesión, evitando conexiones fantasma en el backend.
     */
    suspend fun disconnect() {
        if (session != null) {
            Log.d(TAG, "Iniciando secuencia de cierre controlado (Graceful Shutdown)...")
            try {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "Cierre explícito por cliente Android"))
            } catch (e: Exception) {
                Log.e(TAG, "Excepción durante el cierre del socket: ${e.message}")
            } finally {
                session = null
                _isConnected.value = false
            }
        }
    }
}