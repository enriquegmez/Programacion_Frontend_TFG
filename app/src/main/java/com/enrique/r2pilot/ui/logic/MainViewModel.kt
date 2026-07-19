/**
 * @file MainViewModel.kt
 * @brief ViewModel principal y orquestador del ciclo de vida de la aplicación.
 * @details Gestiona la conectividad de bajo nivel (WebSockets), la navegación global
 *          y ejecuta el motor asíncrono de telemetría de fondo.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// --- IMPORTS DE LA ARQUITECTURA ---
import com.enrique.r2pilot.core.ProtocolDirector
import com.enrique.r2pilot.utils.AppConstants
import com.enrique.r2pilot.protocol.RobotCapabilitiesData

/**
 * @enum AppScreen
 * @brief Define el grafo de navegación interno una vez establecida la conexión con el robot.
 */
enum class AppScreen {
    DASHBOARD,      // Vista resumen general y telemetría del Host PC
    TELEOP,         // Control cinemático manual (Joystick cmd_vel)
    CAMERA,         // Streaming de vídeo en tiempo real
    PLAY_MOTION,    // Ejecución de rutinas de movimiento pregrabadas
    INVESTIGACION,  // Analizador del grafo de ROS 2 (Topics, Services, Actions)
    ARTICULACIONES, // Control de bajo nivel de los motores (Joints)
    SENSORES        // Monitorización de datos sensoriales (Láser, Sonar, etc.)
}

/**
 * @class MainViewModel
 * @brief Cerebro global de la App. Sobrevive a rotaciones de pantalla y mantiene el estado general.
 * @param director Inyección de dependencia del núcleo de comunicaciones.
 */
class MainViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ========================================================================
    // 1. MOTOR ASÍNCRONO DE TELEMETRÍA (BACKGROUND POLLING)
    // ========================================================================

    /** @brief Referencia a la corrutina en segundo plano para poder cancelarla bajo demanda. */
    private var pollingJob: Job? = null

    /**
     * @brief Inicia el bucle de peticiones de estado del sistema (Polling).
     * @details Implementa una estrategia de Multiplexación en el tiempo para evitar
     *          saturar el ancho de banda. Alterna entre pedir datos del robot y datos del PC
     *          cada 2.5 segundos, resultando en una actualización global cada 5 segundos.
     */
    private fun startPolling() {
        // Prevención de hilos duplicados: si ya está corriendo, abortamos la creación de uno nuevo.
        if (pollingJob?.isActive == true) return

        pollingJob = viewModelScope.launch {
            var askForRobotData = true // Interruptor de estado

            while (isActive) {
                // Solo emitimos peticiones si la sesión lógica de ROS 2 está autorizada
                if (director.stateManager.globalState.value == AppConstants.GlobalState.SESION_INICIADA) {

                    if (askForRobotData) {
                        // Pedimos estado dinámico del hardware (Batería, E-Stop, etc.)
                        director.sendRequestRobotInfo()
                    } else {
                        // Pedimos carga de procesamiento del PC anfitrión (CPU, RAM, Temp)
                        director.requestHostTelemetry()
                    }

                    // Invertimos la polaridad para el siguiente ciclo
                    askForRobotData = !askForRobotData
                }

                // Pausa asíncrona (no bloquea el hilo principal de UI)
                delay(2500)
            }
        }
    }

    /**
     * @brief Detiene y destruye el hilo de telemetría de forma segura.
     */
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    // ========================================================================
    // 2. ESTADO EXPUESTO A LA INTERFAZ GRÁFICA (UI State)
    // ========================================================================

    /** @brief Dirección IP del servidor puente (WebSocket). */
    private val _ipAddress = MutableStateFlow(AppConstants.DEFAULT_SERVER_IP)
    val ipAddress: StateFlow<String> = _ipAddress.asStateFlow()

    /** @brief Puerto de conexión TCP. */
    private val _port = MutableStateFlow(AppConstants.DEFAULT_SERVER_PORT.toString())
    val port: StateFlow<String> = _port.asStateFlow()

    /** @brief Pantalla actual renderizada en el contenedor principal de la UI. */
    private val _currentScreen = MutableStateFlow(AppScreen.DASHBOARD)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    // ========================================================================
    // 3. PASILLOS DIRECTOS DESDE EL DIRECTOR (Capa de Dominio -> UI)
    // ========================================================================

    /** @brief Máquina de estados global de la aplicación (IDLE, SESION_INICIADA, etc.). */
    val globalState: StateFlow<String> = director.stateManager.globalState

    /** @brief Canal de interrupciones del sistema (Errores críticos o paradas de emergencia). */
    val systemAlert = director.stateManager.systemAlert

    /** @brief Manifiesto estático de lo que el robot conectado puede y no puede hacer. */
    val robotCapabilities: StateFlow<RobotCapabilitiesData?> = director.robotCapabilities

    /** @brief Datos de rendimiento de la máquina que ejecuta el nodo ROS 2. */
    val hostTelemetry = director.hostTelemetry

    // ========================================================================
    // 4. INICIALIZACIÓN Y VIGILANCIA REACTIVA
    // ========================================================================

    init {
        // Observador continuo de la máquina de estados global
        viewModelScope.launch {
            director.stateManager.globalState.collect { state ->
                when (state) {
                    AppConstants.GlobalState.SESION_INICIADA,
                    AppConstants.GlobalState.ESPERANDO_RECIBIR_INFORMACION_UNICA -> {
                        // Al estabilizar la conexión lógica, arrancamos los "latidos" de telemetría
                        startPolling()
                    }
                    AppConstants.GlobalState.IDLE,
                    AppConstants.GlobalState.CONEXION_BACKEND -> {
                        // Al desconectar, apagamos motores asíncronos y borramos memoria
                        stopPolling()
                        director.clearRobotCapabilities()

                        // Reseteo de navegación: Fuerza a la app a volver a la vista inicial (Dashboard)
                        // para la próxima vez que se inicie sesión.
                        _currentScreen.value = AppScreen.DASHBOARD
                    }
                    else -> {
                        // En estados transitorios (conectando...), detenemos peticiones por seguridad
                        stopPolling()
                    }
                }
            }
        }
    }

    /**
     * @brief Limpia la alerta crítica actual en la pantalla para permitir continuar al usuario.
     */
    fun clearAlert() {
        director.stateManager.clearSystemAlert()
    }

    // ========================================================================
    // 5. EVENTOS DE INTERACCIÓN (Intentions)
    // ========================================================================

    /**
     * @brief Actualiza la dirección IP del servidor WebSocket en el estado.
     */
    fun onIpChange(newIp: String) {
        _ipAddress.value = newIp
    }

    /**
     * @brief Actualiza el puerto de red del servidor WebSocket en el estado.
     */
    fun onPortChange(newPort: String) {
        _port.value = newPort
    }

    /**
     * @brief Cambia la vista interna renderizada en la pantalla principal.
     * @param screen Destino dentro de AppScreen.
     */
    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
    }

    /**
     * @brief Inicia el handshake de bajo nivel con el servidor.
     * @details Establece el socket TCP hacia el puente Python-ROS2.
     */
    fun connectToWebSocket() {
        val portInt = _port.value.toIntOrNull() ?: AppConstants.DEFAULT_SERVER_PORT
        director.connectToServer(_ipAddress.value, portInt)
    }

    /**
     * @brief Inicia el cierre limpio de la sesión.
     * @details Solicita al backend destruir la instancia del robot antes de cortar el socket,
     *          liberando correctamente la memoria en el servidor.
     */
    fun disconnectFromRobot() {
        director.sendDisconnectFromRobot()
    }
}