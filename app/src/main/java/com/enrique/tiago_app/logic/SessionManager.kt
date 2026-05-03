package com.enrique.tiago_app.logic

import android.util.Log

class SessionManager {
    private val TAG = "SessionManager"

    // Usamos 'private set' para que nadie desde fuera pueda cambiar estas variables por accidente.
    // Solo se pueden leer desde fuera, pero modificarse desde dentro de esta clase.
    var isConnected: Boolean = false
        private set

    var currentSessionId: String? = null
        private set

    //Para cuando se inicie sesion llamar a esta funcion
    fun saveSession(sessionId: String) {
        currentSessionId = sessionId
        isConnected = true
        Log.i(TAG, "Sesión iniciada correctamente. ID asignado: $sessionId")
    }

    //Para cuando se cierre sesion llamar a esta funcion
    fun clearSession() {
        currentSessionId = null
        isConnected = false
        Log.i(TAG, "Sesión cerrada. Variables limpiadas.")
    }
}