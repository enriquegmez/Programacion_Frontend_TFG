/**
 * @file SessionManager.kt
 * @brief Gestor de estado de sesión y mecanismo de conexión (Watchdog).
 * @details Almacena el identificador único proporcionado por el servidor (backend)
 *          y mantiene la sesión viva mediante el envío periódico de latidos (Heartbeats).
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.communication

import android.util.Log
import kotlinx.coroutines.*

/**
 * @class SessionManager
 * @brief Controla el ciclo de vida lógico de la comunicación con el robot.
 */
class SessionManager {

    private val TAG = "SessionManager"

    // ========================================================================
    // 1. GESTIÓN DE LA SESIÓN LÓGICA
    // ========================================================================

    /**
     * @property _sessionId Identificador único (UUID) asignado por el backend tras el Handshake.
     */
    private var _sessionId: String? = null

    /**
     * @brief Recupera de forma segura el ID de sesión actual.
     * @return El UUID de la sesión, o un String vacío ("") si la sesión aún no se ha
     *         establecido (útil para inicializar cabeceras en el primer envío).
     */
    fun getSessionId(): String {
        return _sessionId ?: ""
    }

    /**
     * @brief Registra y almacena el identificador de sesión lógico.
     * @param id Cadena de texto con el UUID generado por el servidor.
     */
    fun saveSessionId(id: String) {
        Log.d(TAG, "Sesión lógica establecida con éxito: $id")
        _sessionId = id
    }

    /**
     * @brief Elimina el estado de la sesión y detiene los procesos dependientes.
     * @details Esta función se debe llamar tras una desconexión (física o lógica)
     *          para evitar enviar latidos de una sesión caducada.
     */
    fun clearSession() {
        Log.d(TAG, "Eliminando estado de la sesión actual...")
        _sessionId = null
        stopHeartbeat()
    }

    // ========================================================================
    // 2. WATCHDOG (Heartbeat / Ping)
    // ========================================================================

    private var heartbeatJob: Job? = null

    /**
     * @brief Inicia el proceso en segundo plano (Watchdog) para mantener la conexión activa.
     * @details Crea una corrutina en el hilo de I/O que emite una señal a 1Hz.
     *          El backend tiene un timeout configurado en 3.0s, por lo que este latido
     *          es crítico para evitar la desconexión por inactividad.
     * @param scope El ámbito de corrutinas (CoroutineScope) que rige el ciclo de vida del Job.
     * @param sendPingAction Función inyectada que encapsula la lógica de red para enviar
     *        el Ping.
     */
    fun startHeartbeat(scope: CoroutineScope, sendPingAction: suspend () -> Unit) {
        // Prevención de instanciación múltiple del Job
        if (heartbeatJob?.isActive == true) return

        Log.d(TAG, "Iniciando latido de red (Heartbeat) a 1Hz...")

        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) { // Condición de salida: cancelación externa de la corrutina
                try {
                    // Ejecutamos la acción inyectada por el Director de Protocolo
                    sendPingAction()
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo en la emisión del latido (Heartbeat): ${e.message}")
                }

                // Frecuencia de 1Hz: Esperamos 1000ms exactos antes de la siguiente iteración
                delay(1000L)
            }
        }
    }

    /**
     * @brief Cancela y destruye el proceso de latido en segundo plano.
     * @details Obligatorio llamar durante la desconexión o al detectar un error crítico
     *          en la red para no desperdiciar recursos del sistema.
     */
    fun stopHeartbeat() {
        if (heartbeatJob?.isActive == true) {
            Log.d(TAG, "Deteniendo corrutina de latido (Heartbeat) de forma controlada.")
            heartbeatJob?.cancel()
            heartbeatJob = null
        }
    }
}