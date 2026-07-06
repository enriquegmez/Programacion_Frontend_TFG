package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import com.enrique.tiago_app.logic.ProtocolDirector
import com.enrique.tiago_app.utils.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class JointControlViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    val capabilities = director.robotCapabilities

    private val _activeJoints = MutableStateFlow<Set<String>>(emptySet())
    val activeJoints: StateFlow<Set<String>> = _activeJoints.asStateFlow()

    private val _jointValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val jointValues: StateFlow<Map<String, Float>> = _jointValues.asStateFlow()

    private var isControlActive = false

    // ==========================================
    // MEMORIA DEL ESCUDO (Más limpia y robusta)
    // ==========================================
    private val lastTimes = mutableMapOf<String, Long>()
    private val lastValues = mutableMapOf<String, Float>()
    private val lastSentTimes = mutableMapOf<String, Long>()

    private val lockedJoints = mutableSetOf<String>()

    private val MAX_SPEED_RAD_S = 2.0f
    // Máximo salto permitido en 1 solo "frame". 0.15 rads = ~8.5 grados.
    // Es imposible arrastrar tanto de golpe, por lo que detecta "toques" instantáneos.
    private val MAX_JUMP_RAD = 0.30f

    private val SEND_INTERVAL_MS = 50L

    fun toggleJoint(jointName: String, isChecked: Boolean) {
        val currentActive = _activeJoints.value.toMutableSet()
        if (isChecked) {
            currentActive.add(jointName)
            if (currentActive.size == 1 && !isControlActive) {
                director.sendStartMovement(customTopic = "", type = AppConstants.ControlType.JOINT)
                isControlActive = true
            }
        } else {
            currentActive.remove(jointName)
            // Limpieza total
            lastTimes.remove(jointName)
            lastValues.remove(jointName)
            lastSentTimes.remove(jointName)
            lockedJoints.remove(jointName)

            if (currentActive.isEmpty() && isControlActive) {
                director.sendStopMovement(type = AppConstants.ControlType.JOINT)
                isControlActive = false
            }
        }
        _activeJoints.value = currentActive
    }

    fun updateJointValue(jointName: String, newValue: Float) {
        // ==========================================
        // ESCUDO DE SEGURIDAD EXTRAS: Ignorar si es pasiva
        // ==========================================
        val isActuated = capabilities.value?.capabilities?.controlableJoints
            ?.find { it.name == jointName }?.isActuated ?: true
        if (!isActuated) return // Si es pasiva, salimos de inmediato

        if (lockedJoints.contains(jointName)) return

        val currentTime = System.currentTimeMillis()
        val currentValues = _jointValues.value.toMutableMap()

        val lastTime = lastTimes[jointName] ?: currentTime

        // ==========================================
        // ¡NUEVO! EL ARREGLO DEL PRIMER TOQUE
        // Si no tenemos historial, buscamos la posición inicial real de la articulación.
        // ==========================================
        val initialValue = capabilities.value?.capabilities?.controlableJoints
            ?.find { it.name == jointName }
            ?.let { it.currentValue ?: ((it.min + it.max) / 2f) }
            ?: newValue

        // Ahora usamos ese initialValue en vez de newValue como último recurso
        val lastVal = lastValues[jointName] ?: currentValues[jointName] ?: initialValue

        val dt = (currentTime - lastTime) / 1000f
        val jumpDistance = abs(newValue - lastVal)

        var isViolation = false

        // 1. ANTIMISILES: Detectar "Toques" directos en la barra (Saltos sin tiempo)
        if (jumpDistance > MAX_JUMP_RAD) {
            isViolation = true
        }
        // 2. RADAR DE VELOCIDAD: Analizar arrastres continuos cada 100ms
        else if (dt >= 0.1f) {
            val speed = jumpDistance / dt
            if (speed > MAX_SPEED_RAD_S) {
                isViolation = true
            } else {
                // Es un movimiento seguro, avanzamos nuestra memoria base
                lastTimes[jointName] = currentTime
                lastValues[jointName] = newValue
            }
        }
        // 3. INICIALIZACIÓN: El primer instante en que se toca
        else if (lastTime == currentTime) {
            lastTimes[jointName] = currentTime
            lastValues[jointName] = newValue
        }

        // --- GESTIÓN DE INFRACCIONES ---
        if (isViolation) {
            lockedJoints.add(jointName)
            director.stateManager.showSystemAlert(
                "⚠️ Movimiento bloqueado.\n\nHas movido la barra demasiado rápido o has tocado en un extremo de golpe. Levanta el dedo para continuar.",
                title="Aviso de parada"
            )

            currentValues[jointName] = lastVal
            _jointValues.value = currentValues
            director.sendJointCommand(jointName, lastVal)
            return
        }

        // --- MOVIMIENTO NORMAL ---
        currentValues[jointName] = newValue
        _jointValues.value = currentValues

        val lastSent = lastSentTimes[jointName] ?: 0L
        if (currentTime - lastSent >= SEND_INTERVAL_MS) {
            director.sendJointCommand(jointName, newValue)
            lastSentTimes[jointName] = currentTime
        }
    }

    fun onJointDragFinished(jointName: String) {
        lockedJoints.remove(jointName)
        // ¡ESTO ARREGLA EL PROBLEMA DE SER RESTRICTIVO!
        // Borramos también la posición vieja para no arrastrar "fantasmas"
        lastTimes.remove(jointName)
        lastValues.remove(jointName)
    }

    fun onScreenDisposed() {
        if (isControlActive) {
            director.sendStopMovement(type = AppConstants.ControlType.JOINT)
            isControlActive = false
            _activeJoints.value = emptySet()
        }
        _jointValues.value = emptyMap()
        lastTimes.clear()
        lastValues.clear()
        lastSentTimes.clear()
        lockedJoints.clear()
    }
}