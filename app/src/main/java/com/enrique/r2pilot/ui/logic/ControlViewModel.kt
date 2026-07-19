/**
 * @file ControlViewModel.kt
 * @brief ViewModel encargado de la teleoperación manual (Joystick) del robot.
 * @details Gestiona el ciclo de vida de los permisos motrices y ejecuta un bucle
 *          de publicación constante (10 Hz) para inyectar velocidades cinemáticas
 *          en la red de ROS 2 sin saturar el canal de WebSockets.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// --- IMPORTS DE LA ARQUITECTURA ---
import com.enrique.r2pilot.core.ProtocolDirector
import com.enrique.r2pilot.utils.AppConstants

/**
 * @class ControlViewModel
 * @brief Cerebro de la pantalla de Control Manual (Teleoperación).
 * @param director Inyección del núcleo de comunicaciones.
 */
class ControlViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ========================================================================
    // 1. ESTADO EXPUESTO A LA INTERFAZ GRÁFICA (UI)
    // ========================================================================

    /**
     * @brief Semáforo global de movimiento heredado del Director.
     * @details Le dice a la UI si el backend nos ha dado permiso para mover el robot.
     */
    val movementState: StateFlow<String> = director.stateManager.movementState

    /** @brief Tópico de ROS 2 donde se inyectarán los comandos. */
    private val _targetTopic = MutableStateFlow("")
    val targetTopic: StateFlow<String> = _targetTopic.asStateFlow()

    /** @brief Velocidad lineal (V) actual enviada al robot. Permite renderizar marcadores en vivo. */
    private val _liveV = MutableStateFlow(0f)
    val liveV: StateFlow<Float> = _liveV.asStateFlow()

    /** @brief Velocidad angular (W) actual enviada al robot. */
    private val _liveW = MutableStateFlow(0f)
    val liveW: StateFlow<Float> = _liveW.asStateFlow()


    // ========================================================================
    // 2. MEMORIA INTERNA DEL JOYSTICK (Búfer de Control)
    // ========================================================================

    // Variables crudas y rápidas para almacenar la última lectura del joystick.
    // No usamos StateFlow aquí porque el bucle de envío lee estos valores a alta frecuencia.
    private var currentV: Float = 0f
    private var currentW: Float = 0f

    /**
     * @brief Candado de seguridad local.
     * @details Garantiza que no se enviarán comandos aunque el servidor lo permita,
     *          salvo que el usuario haya encendido explícitamente el interruptor de la UI.
     */
    private var isJoystickEnabledLocal = false

    // Contador para la optimización de red (explicado en el bucle inferior)
    private var consecutiveZeros = 0

    // ========================================================================
    // 3. INICIALIZACIÓN Y BUCLE DE PUBLICACIÓN (10 Hz)
    // ========================================================================

    init {
        // Arrancamos el motor de publicación en cuanto se crea este ViewModel
        startTeleopLoop()
    }

    /**
     * @brief Hilo asíncrono que escupe paquetes de velocidad hacia el servidor.
     * @details Implementa un reloj a 10 Hz (100ms de delay). En robótica, los mandos (cmd_vel)
     *          deben enviarse continuamente para que el Watchdog del robot (sistema de hombre muerto)
     *          no frene por seguridad al creer que se ha perdido la conexión.
     */
    private fun startTeleopLoop() {
        viewModelScope.launch {
            while (isActive) { // Mientras la corrutina viva...
                // Solo disparamos si el servidor ROS 2 autorizó la sesión de movimiento (ENVIANDO_INFO)
                // Y si el usuario tiene el joystick encendido en la pantalla.
                if (movementState.value == AppConstants.MovementState.ENVIANDO_INFO && isJoystickEnabledLocal) {
                    // Si el joystick está suelto en el centro (0, 0):
                    if (currentV == 0f && currentW == 0f) {
                        consecutiveZeros++

                        // Mandamos los primeros 3 ceros de golpe para garantizar que el robot
                        // frena en seco y no pierde el paquete por latencia.
                        // Luego, bajamos el ritmo y mandamos un cero de mantenimiento solo cada 4 ciclos (400ms)
                        // para ahorrar ancho de banda de la red Wi-Fi.
                        if (consecutiveZeros <= 3 || consecutiveZeros % 4 == 0) {
                            director.sendJoystickVelocity(currentV, currentW)
                        }
                    } else {
                        // Si nos estamos moviendo, reseteamos el contador y mandamos datos a fuego (10 Hz).
                        consecutiveZeros = 0
                        director.sendJoystickVelocity(currentV, currentW)
                    }
                }

                // Pausa de 100 milisegundos (10 Hz exactos)
                delay(100L)
            }
        }
    }

    // ========================================================================
    // 4. EVENTOS DE INTERACCIÓN DEL USUARIO (UI -> ViewModel)
    // ========================================================================

    /**
     * @brief Actualiza el búfer de velocidades desde la interfaz del Joystick.
     * @param v Velocidad lineal normalizada (-1.0 a 1.0).
     * @param w Velocidad angular normalizada (-1.0 a 1.0).
     */
    fun updateJoystick(v: Float, w: Float) {
        currentV = v
        currentW = w

        // Actualizamos los StateFlows para que la pantalla dibuje las gráficas/números
        _liveV.value = v
        _liveW.value = w
    }

    /**
     * @brief Enciende o apaga el sistema de teleoperación local y avisa al servidor.
     * @param enable Estado del interruptor en la UI.
     */
    fun toggleTeleop(enable: Boolean) {
        // 1. Aplicamos el candado local de inmediato por seguridad
        isJoystickEnabledLocal = enable

        if (enable) {
            // Aseguramos que el movimiento parte de cero
            currentV = 0f
            currentW = 0f
            _liveV.value = 0f
            _liveW.value = 0f

            // Solicitamos permiso al backend para adueñarnos del tópico de movimiento
            director.sendStartMovement(_targetTopic.value)
        } else {
            // Si apagamos, forzamos los valores a cero para evitar "fugas" de velocidad
            currentV = 0f
            currentW = 0f
            _liveV.value = 0f
            _liveW.value = 0f

            // Avisamos al backend para que libere el multiplexor (TwistMux)
            director.sendStopMovement()
        }
    }

    /**
     * @brief Actualiza el topic de destino escrito en el cuadro de texto de configuración.
     * @param newTopic Nuevo texto ingresado por el usuario.
     */
    fun onTopicChange(newTopic: String) {
        _targetTopic.value = newTopic
    }
}