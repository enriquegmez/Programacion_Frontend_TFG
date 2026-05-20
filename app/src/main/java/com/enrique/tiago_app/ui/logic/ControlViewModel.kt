package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// IMPORTS DE TU ARQUITECTURA
import com.enrique.tiago_app.logic.ProtocolDirector
import com.enrique.tiago_app.utils.AppConstants

/**
 * ControlViewModel
 * Gestiona el envío regulado de teleoperación (10Hz) y los permisos de movimiento.
 */
class ControlViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ==========================================
    // 1. ESTADO PARA LA UI
    // ==========================================

    // El semáforo específico de la submáquina de movimiento
    val movementState: StateFlow<String> = director.stateManager.movementState

    private val _targetTopic = MutableStateFlow("")
    val targetTopic: StateFlow<String> = _targetTopic.asStateFlow()

    // ==========================================
    // 2. MEMORIA DEL JOYSTICK
    // ==========================================

    private var currentV: Float = 0f
    private var currentW: Float = 0f

    // ==========================================
    // 3. INICIALIZACIÓN Y BUCLE (10 Hz)
    // ==========================================

    init {
        startTeleopLoop()
    }

    /**
     * Bucle infinito ligado al ciclo de vida del ViewModel.
     * En robótica, es crucial enviar comandos a una frecuencia fija (ej: 10Hz).
     */
    private var consecutiveZeros = 0

    private fun startTeleopLoop() {
        viewModelScope.launch {
            while (isActive) {
                if (movementState.value == AppConstants.MovementState.ENVIANDO_INFO) {

                    if (currentV == 0f && currentW == 0f) {
                        consecutiveZeros++
                        // Cuando está parado, enviamos la velocidad 0.0 cada 4 ciclos (400ms -> 2.5Hz)
                        // Esto mantiene vivo el Watchdog del servidor ahorrando un 75% de batería y red
                        if (consecutiveZeros <= 3 || consecutiveZeros % 4 == 0) {
                            director.sendJoystickVelocity(currentV, currentW)
                        }
                    } else {
                        // El usuario está moviendo el joystick
                        consecutiveZeros = 0
                        // Enviamos a máxima velocidad (cada 100ms -> 10Hz)
                        director.sendJoystickVelocity(currentV, currentW)
                    }
                }

                // El bucle principal siempre gira a 10Hz para detectar tus dedos al instante
                delay(100L)
            }
        }
    }

    // ==========================================
    // 4. EVENTOS DESDE LA UI
    // ==========================================

    /**
     * Llamado cada vez que el usuario arrastra el dedo por el JoystickComponent.
     * Solo actualiza la memoria interna; el bucle se encargará de enviarlo.
     */
    fun updateJoystick(v: Float, w: Float) {
        currentV = v
        currentW = w
    }

    /**
     * Llamado por el Switch (Interruptor) de la pantalla.
     * Pide permiso al servidor para empezar a publicar o para frenar.
     */
    fun toggleTeleop(enable: Boolean) {
        if (enable) {
            // Queremos encender
            // ¡EL FIX! Borramos cualquier memoria residual de sesiones pasadas
            currentV = 0f
            currentW = 0f
            director.sendStartMovement(_targetTopic.value)
        } else {
            // Queremos apagar. Primero, por seguridad, reseteamos el joystick interno
            currentV = 0f
            currentW = 0f
            director.sendStopMovement()
        }
    }

    // 2. Añade esta función para que la UI pueda actualizar el texto
    fun onTopicChange(newTopic: String) {
        _targetTopic.value = newTopic
    }
}