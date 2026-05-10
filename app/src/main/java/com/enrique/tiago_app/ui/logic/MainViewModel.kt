package com.enrique.tiago_app.ui.logic // Ajusta a tu paquete

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Importamos el Director y las Constantes
import com.enrique.tiago_app.logic.ProtocolDirector
import com.enrique.tiago_app.utils.AppConstants

/**
 * MainViewModel
 * El cerebro de navegación y conexión.
 * Sobrevive a los giros de pantalla y conecta la UI con el Director.
 */
class MainViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ==========================================
    // 1. ESTADO DE LA INTERFAZ (Textos)
    // ==========================================

    // Guardamos la IP con el valor por defecto de constants.py
    private val _ipAddress = MutableStateFlow(AppConstants.DEFAULT_SERVER_IP)
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    // Guardamos el Puerto
    private val _port = MutableStateFlow(AppConstants.DEFAULT_SERVER_PORT.toString())
    val port: StateFlow<String> = _port.asStateFlow()

    // ==========================================
    // 2. EL PASILLO DEL ESTADO GLOBAL
    // ==========================================

    // La UI observará esta variable para saber qué pantalla pintar.
    val globalState: StateFlow<String> = director.stateManager.globalState

    // ==========================================
    // 3. EVENTOS DEL USUARIO (Teclear)
    // ==========================================

    fun onIpChange(newIp: String) {
        _ipAddress.value = newIp
    }

    fun onPortChange(newPort: String) {
        _port.value = newPort
    }

    // ==========================================
    // 4. ACCIONES DE LOS BOTONES
    // ==========================================

    /**
     * Llamado por el botón "Conectar" en la Pantalla 1 (LoginScreen).
     * Abre el túnel físico WebSocket.
     */
    fun connectToWebSocket() {
        val portInt = _port.value.toIntOrNull() ?: AppConstants.DEFAULT_SERVER_PORT
        director.connectToServer(_ipAddress.value, portInt)
    }

    /**
     * Llamado por el botón "Conectar Robot" en la Pantalla 2 (MenuScreen).
     * Inicia la sesión lógica con el robot (Manda COMMAND_REQ[connect]).
     */
    fun connectToRobot() {
        director.sendConnectToRobot()
    }

    /**
     * ¡NUEVO! Llamado por el botón "Desconectar Robot" en la Pantalla 3 (ControlScreen).
     * Cierra la sesión lógica y devuelve a la app a la Pantalla 2.
     * (Manda COMMAND_REQ(disconnect)).
     */
    fun disconnectFromRobot() {
        director.sendDisconnectFromRobot()
    }

    /**
     * Llamado por el botón "Cerrar Conexión" en la Pantalla 2 (MenuScreen).
     * Cierra la sesión y corta el túnel físico.
     */
    fun closeEverything() {
        director.sendEndProtocol()
    }
}