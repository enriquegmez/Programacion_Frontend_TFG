package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import com.enrique.tiago_app.logic.ProtocolDirector
import com.enrique.tiago_app.utils.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class JointControlViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // Extraemos la lista de articulaciones leída del robot
    val capabilities = director.robotCapabilities

    // Guardamos qué articulaciones tienen el "tick" puesto (Set de nombres)
    private val _activeJoints = MutableStateFlow<Set<String>>(emptySet())
    val activeJoints: StateFlow<Set<String>> = _activeJoints.asStateFlow()

    // Guardamos el valor actual de cada slider (Map de nombre -> valor)
    private val _jointValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val jointValues: StateFlow<Map<String, Float>> = _jointValues.asStateFlow()

    // Controla si ya hemos enviado el START al backend
    private var isControlActive = false

    fun toggleJoint(jointName: String, isChecked: Boolean) {
        val currentActive = _activeJoints.value.toMutableSet()

        if (isChecked) {
            currentActive.add(jointName)
            // Si es la primera articulación que activamos, abrimos la puerta en el backend
            if (currentActive.size == 1 && !isControlActive) {
                director.sendStartMovement(customTopic = "", type = AppConstants.ControlType.JOINT)
                isControlActive = true
            }
        } else {
            currentActive.remove(jointName)
            // Si quitamos el tick de la última, cerramos la puerta
            if (currentActive.isEmpty() && isControlActive) {
                director.sendStopMovement(type = AppConstants.ControlType.JOINT)
                isControlActive = false
            }
        }
        _activeJoints.value = currentActive
    }

    fun updateJointValue(jointName: String, newValue: Float) {
        // Actualizamos el estado de la UI
        val currentValues = _jointValues.value.toMutableMap()
        currentValues[jointName] = newValue
        _jointValues.value = currentValues

        // Enviamos la orden física al robot
        director.sendJointCommand(jointName, newValue)
    }

    // Se llamará cuando el usuario abandone la pantalla (botón atrás o menú lateral)
    fun onScreenDisposed() {
        if (isControlActive) {
            director.sendStopMovement(type = AppConstants.ControlType.JOINT)
            isControlActive = false
            _activeJoints.value = emptySet()
        }

        // ¡NUEVO! Vaciamos la memoria local de los sliders.
        // Así, la próxima vez que entremos, leerá la posición real del robot.
        _jointValues.value = emptyMap()
    }
}