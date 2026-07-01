package com.enrique.tiago_app.ui.logic // Ajusta a tu paquete

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope // ¡NUEVO! Para lanzar corrutinas en el ViewModel
import kotlinx.coroutines.launch // ¡NUEVO!
import kotlinx.coroutines.Job // ¡NUEVO! Para controlar el bucle de peticiones
import kotlinx.coroutines.delay // ¡NUEVO! Para esperar entre peticiones
import kotlinx.coroutines.isActive // ¡NUEVO! Para saber si el bucle sigue vivo

// Importamos el Director y las Constantes
import com.enrique.tiago_app.logic.ProtocolDirector
import com.enrique.tiago_app.utils.AppConstants
import com.enrique.tiago_app.protocol.RobotCapabilitiesData // ¡NUEVO! El modelo de datos

// ¡NUEVO! Enum para las pantallas del menú lateral
enum class AppScreen {
    DASHBOARD,    // La pantalla en blanco por defecto
    TELEOP,       // Tu joystick
    CAMERA,        // La nueva pantalla de cámara
    PLAY_MOTION,
    INVESTIGACION,
    ARTICULACIONES,
    SENSORES
}

/**
 * MainViewModel
 * El cerebro de navegación y conexión.
 * Sobrevive a los giros de pantalla y conecta la UI con el Director.
 */
class MainViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ¡NUEVO! Guardamos la referencia a la tarea repetitiva
    private var pollingJob: Job? = null

    // ==========================================
    // ¡NUEVO! SISTEMA DE ACTUALIZACIÓN CONTINUA (TICK-TOCK)
    // ==========================================
    private fun startPolling() {
        // Si el bucle ya está funcionando, no hacemos nada
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            // ¡NUEVO! Variable para alternar los mensajes y no pisarlos
            var askForRobotData = true

            // Bucle infinito que durará hasta que alguien llame a stopPolling()
            while (isActive) {
                // Solo pedimos si el semáforo está en verde (SESION_INICIADA)
                if (director.stateManager.globalState.value == AppConstants.GlobalState.SESION_INICIADA) {

                    if (askForRobotData) {
                        // TURNO 1: Pedir estado del Robot (Batería, e-stop, etc)
                        director.sendRequestRobotInfo()
                    } else {
                        // TURNO 2: Pedir telemetría del PC (CPU, RAM, Temp)
                        // IMPORTANTE: Pon aquí el nombre exacto de la función que tienes en tu ProtocolDirector
                        // (Suele ser algo como sendRequestHostTelemetry() o fetchTelemetry())
                        director.requestHostTelemetry()
                    }

                    // Invertimos el interruptor para el siguiente ciclo
                    askForRobotData = !askForRobotData
                }

                // Esperamos 2.5 segundos (la mitad de 5s).
                // Cada dato se actualiza cada 5 segundos, pero las peticiones están separadas en el tiempo.
                delay(2500)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // ==========================================
    // 1. ESTADO DE LA INTERFAZ (Textos)
    // ==========================================

    // Guardamos la IP con el valor por defecto de constants.py
    private val _ipAddress = MutableStateFlow(AppConstants.DEFAULT_SERVER_IP)
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    // Guardamos el Puerto
    private val _port = MutableStateFlow(AppConstants.DEFAULT_SERVER_PORT.toString())
    val port: StateFlow<String> = _port.asStateFlow()

    // ¡NUEVO! Guardamos la pantalla actual del menú lateral.
    // Empezará en DASHBOARD (la pantalla en blanco que pediste).
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // ==========================================
    // 2. EL PASILLO DEL ESTADO GLOBAL
    // ==========================================

    // La UI observará esta variable para saber qué pantalla pintar.
    val globalState: StateFlow<String> = director.stateManager.globalState

    // ¡NUEVO! Observamos las alertas del sistema (Desconexiones de emergencia)
    val systemAlert = director.stateManager.systemAlert

    // ¡NUEVO! Pasillo directo hacia la radiografía del robot.
    // Jetpack Compose leerá esto para pintar los menús o deshabilitar botones.
    val robotCapabilities: StateFlow<RobotCapabilitiesData?> = director.robotCapabilities

    // Añade esta línea para que el Dashboard pueda leer los sensores del PC
    val hostTelemetry = director.hostTelemetry

    init {
        // Vigilante del estado global
        viewModelScope.launch {
            director.stateManager.globalState.collect { state ->
                when (state) {
                    AppConstants.GlobalState.SESION_INICIADA,
                    AppConstants.GlobalState.ESPERANDO_RECIBIR_INFORMACION_UNICA -> {
                        // Mientras estemos en una sesión activa o actualizando info,
                        // nos aseguramos de que el motor de peticiones esté encendido.
                        startPolling()
                    }
                    AppConstants.GlobalState.IDLE,
                    AppConstants.GlobalState.CONEXION_BACKEND -> {
                        // Si nos desconectamos, apagamos el motor y limpiamos la pantalla.
                        stopPolling()
                        director.clearRobotCapabilities()

                        // ¡LA SOLUCIÓN MAESTRA!
                        // Reseteamos el menú al salir del robot. Así, cuando volvamos a entrar,
                        // nos recibirá el Dashboard limpio, sin interferir con las peticiones internas.
                        _currentScreen.value = AppScreen.DASHBOARD
                    }
                    else -> {
                        // En estados intermedios de conexión/desconexión, apagamos el polling por seguridad.
                        stopPolling()
                    }
                }
            }
        }
    }

    fun clearAlert() {
        director.stateManager.clearSystemAlert()
    }

    // ==========================================
    // 3. EVENTOS DEL USUARIO (Teclear)
    // ==========================================

    fun onIpChange(newIp: String) {
        _ipAddress.value = newIp
    }

    fun onPortChange(newPort: String) {
        _port.value = newPort
    }

    // ¡NUEVO! Para cuando el usuario haga clic en una opción del menú de las 3 rayitas
    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
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
     * ¡NUEVO! Llamado por el botón "Desconectar Robot" en la Pantalla 3 (ControlScreen).
     * Cierra la sesión lógica y devuelve a la app a la Pantalla 2.
     * (Manda COMMAND_REQ(disconnect)).
     */
    fun disconnectFromRobot() {
        director.sendDisconnectFromRobot()
    }
}