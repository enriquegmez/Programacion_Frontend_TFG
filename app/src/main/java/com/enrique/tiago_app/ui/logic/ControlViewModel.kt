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

    // ¡NUEVO CANDADO! Sabe si el interruptor del teleop está encendido o no
    private var isJoystickEnabledLocal = false

    // ==========================================
    // 3. INICIALIZACIÓN Y BUCLE (10 Hz)
    // ==========================================

    init {
        startTeleopLoop()
    }

    private var consecutiveZeros = 0

    private fun startTeleopLoop() {
        viewModelScope.launch {
            while (isActive) {
                // ¡LA MAGIA! Solo disparamos si el semáforo global está en verde Y nuestro candado local está abierto.
                if (movementState.value == AppConstants.MovementState.ENVIANDO_INFO && isJoystickEnabledLocal) {

                    if (currentV == 0f && currentW == 0f) {
                        consecutiveZeros++
                        if (consecutiveZeros <= 3 || consecutiveZeros % 4 == 0) {
                            director.sendJoystickVelocity(currentV, currentW)
                        }
                    } else {
                        consecutiveZeros = 0
                        director.sendJoystickVelocity(currentV, currentW)
                    }
                }

                delay(100L)
            }
        }
    }

    // ==========================================
    // 4. EVENTOS DESDE LA UI
    // ==========================================

    fun updateJoystick(v: Float, w: Float) {
        currentV = v
        currentW = w
    }

    fun toggleTeleop(enable: Boolean) {
        // ¡ACTUALIZAMOS EL CANDADO!
        isJoystickEnabledLocal = enable

        if (enable) {
            currentV = 0f
            currentW = 0f
            director.sendStartMovement(_targetTopic.value)
        } else {
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