package com.enrique.tiago_app.communication

import android.util.Log
import kotlinx.coroutines.*

/**
 * SessionManager
 * Almacena el ID de la sesión lógica y mantiene viva la conexión (Heartbeat).
 */
class SessionManager {
    private val TAG = "SessionManager"

    // ==========================================
    // 1. GESTIÓN DE LA SESIÓN LÓGICA
    // ==========================================

    private var _sessionId: String? = null

    // Propiedad pública de solo lectura.
    // Devuelve un String vacío si es null (útil para la cabecera antes de tener sesión).
    fun getSessionId(): String {
        return _sessionId ?: ""
    }

    fun saveSessionId(id: String) {
        Log.d(TAG, "Sesión lógica establecida: $id")
        _sessionId = id
    }

    fun clearSession() {
        Log.d(TAG, "Limpiando sesión actual...")
        _sessionId = null
        stopHeartbeat() // Si borramos la sesión, dejamos de latir
    }

    fun hasValidSession(): Boolean {
        return _sessionId != null
    }

    // ==========================================
    // 2. WATCHDOG (Heartbeat / Ping)
    // ==========================================

    private var heartbeatJob: Job? = null

    /**
     * Inicia el latido del corazón.
     * @param scope El ámbito de corrutinas (normalmente lo proveerá el Director).
     * @param sendPingAction Una función (Lambda) que el Director le pasa para que sepa cómo enviar el Ping.
     */
    fun startHeartbeat(scope: CoroutineScope, sendPingAction: suspend () -> Unit) {
        // Si ya hay un corazón latiendo, no hacemos nada
        if (heartbeatJob?.isActive == true) return

        Log.d(TAG, "Iniciando latido de red (Heartbeat) a 1Hz...")

        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) { // Mientras la corrutina no sea cancelada
                try {
                    sendPingAction() // Ejecutamos la función que nos pasó el Director
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo al enviar el latido: ${e.message}")
                }

                // Esperamos 1 segundo exacto antes del siguiente latido.
                // (Recuerda que en tu backend configuramos el timeout en 3.0s)
                delay(1000L)
            }
        }
    }

    /**
     * Detiene el latido. Se debe llamar al desconectar o si hay un error crítico.
     */
    fun stopHeartbeat() {
        if (heartbeatJob?.isActive == true) {
            Log.d(TAG, "Deteniendo latido de red (Heartbeat).")
            heartbeatJob?.cancel()
            heartbeatJob = null
        }
    }
}