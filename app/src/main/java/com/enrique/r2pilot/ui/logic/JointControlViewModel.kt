/**
 * @file JointControlViewModel.kt
 * @brief ViewModel para el control cinemático individual de articulaciones (Joints).
 * @details Actúa como una capa de seguridad física. Valida las entradas táctiles del usuario
 *          contra límites de velocidad y saltos bruscos antes de enviar comandos al robot.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.logic

import androidx.lifecycle.ViewModel
import com.enrique.r2pilot.core.ProtocolDirector
import com.enrique.r2pilot.utils.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * @class JointControlViewModel
 * @brief Orquesta el movimiento articulación por articulación con validación de seguridad.
 * @param director Inyección del núcleo de comunicaciones.
 */
class JointControlViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // --- CAPACIDADES DEL ROBOT ---
    // Observamos las capacidades para saber si una articulación es controlable o pasiva.
    val capabilities = director.robotCapabilities

    // --- ESTADO DE LA UI ---
    private val _activeJoints = MutableStateFlow<Set<String>>(emptySet())
    val activeJoints: StateFlow<Set<String>> = _activeJoints.asStateFlow()

    private val _jointValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val jointValues: StateFlow<Map<String, Float>> = _jointValues.asStateFlow()

    private var isControlActive = false

    // --- MEMORIA DE SEGURIDAD (Estado interno) ---
    private val lastTimes = mutableMapOf<String, Long>()
    private val lastValues = mutableMapOf<String, Float>()
    private val lastSentTimes = mutableMapOf<String, Long>()
    private val lockedJoints = mutableSetOf<String>()

    // --- CONSTANTES DE SEGURIDAD (Safety Thresholds) ---
    private val MAX_SPEED_RAD_S = 2.0f      // Límite de velocidad angular (rad/s)
    private val MAX_JUMP_RAD = 0.30f       // Máximo salto permitido (evita "toques" accidentales)
    private val SEND_INTERVAL_MS = 50L      // 20 Hz de actualización para suavidad

    /**
     * @brief Activa o desactiva el control sobre una articulación específica.
     * @details Gestiona el "Handshake" con el robot al iniciar/detener el modo Joint.
     */
    fun toggleJoint(jointName: String, isChecked: Boolean) {
        val currentActive = _activeJoints.value.toMutableSet()
        if (isChecked) {
            currentActive.add(jointName)
            // Si es la primera articulación, iniciamos la sesión de control
            if (currentActive.size == 1 && !isControlActive) {
                director.sendStartMovement(customTopic = "", type = AppConstants.ControlType.JOINT)
                isControlActive = true
            }
        } else {
            currentActive.remove(jointName)
            //Eliminado de memoria de seguridad al liberar el joint
            lastTimes.remove(jointName)
            lastValues.remove(jointName)
            lastSentTimes.remove(jointName)
            lockedJoints.remove(jointName)

            // Si no quedan joints activos, cerramos la sesión de control
            if (currentActive.isEmpty() && isControlActive) {
                director.sendStopMovement(type = AppConstants.ControlType.JOINT)
                isControlActive = false
            }
        }
        _activeJoints.value = currentActive
    }

    /**
     * @brief Procesa el movimiento táctil con validación de seguridad.
     * @details Implementa tres filtros: 1) Ignora joints pasivos, 2) Anti-teletransporte,
     *          3) Limitador de velocidad (Radar).
     */
    fun updateJointValue(jointName: String, newValue: Float) {

        // 1. FILTRO DE ACTUACIÓN: Si el robot reporta que no es controlable, ignoramos.
        val isActuated = capabilities.value?.capabilities?.controlableJoints
            ?.find { it.name == jointName }?.isActuated ?: true
        if (!isActuated) return

        // 2. FILTRO DE BLOQUEO: Si el joint entró en infracción, no lo liberamos hasta que el usuario suelte.
        if (lockedJoints.contains(jointName)) return

        val currentTime = System.currentTimeMillis()
        val currentValues = _jointValues.value.toMutableMap()
        val lastTime = lastTimes[jointName] ?: currentTime

        // --- CORRECCIÓN DE NULABILIDAD ---
        // Extraemos las propiedades de forma segura asumiendo límites estándar si el robot falla al reportarlos
        val initialValue: Float = capabilities.value?.capabilities?.controlableJoints
            ?.find { it.name == jointName }
            ?.let { joint ->
                val safeMin = joint.min ?: -3.14f // Valor por defecto seguro
                val safeMax = joint.max ?: 3.14f  // Valor por defecto seguro
                joint.currentValue ?: ((safeMin + safeMax) / 2f)
            }
            ?: newValue

        // Al forzar a initialValue a ser Float, lastVal ya es un Float estricto (no-nulo)
        val lastVal: Float = lastValues[jointName] ?: currentValues[jointName] ?: initialValue

        val dt = (currentTime - lastTime) / 1000f

        // Al ser lastVal un Float estricto, la resta y el abs() compilan perfectamente
        val jumpDistance = abs(newValue - lastVal)

        var isViolation = false

        // --- LÓGICA DE DETECCIÓN DE INFRACCIONES ---

        // A. Anti-misiles: Salto brusco (típico de fallos en el cálculo del gesto)
        if (jumpDistance > MAX_JUMP_RAD) {
            isViolation = true
        }
        // B. Radar de velocidad: Si supera los radianes/segundo permitidos
        else if (dt >= 0.1f) {
            val speed = jumpDistance / dt
            if (speed > MAX_SPEED_RAD_S) {
                isViolation = true
            } else {
                lastTimes[jointName] = currentTime
                lastValues[jointName] = newValue
            }
        }
        else if (lastTime == currentTime) {
            lastTimes[jointName] = currentTime
            lastValues[jointName] = newValue
        }

        // --- GESTIÓN DE ERROR Y BLOQUEO ---
        if (isViolation) {
            lockedJoints.add(jointName)
            director.stateManager.showSystemAlert(
                "Movimiento bloqueado.\n\nHas movido la barra demasiado rápido. Levanta el dedo para continuar.",
                title="Aviso de parada"
            )

            currentValues[jointName] = lastVal
            _jointValues.value = currentValues
            director.sendJointCommand(jointName, lastVal)
            return
        }

        // --- PUBLICACIÓN NORMAL ---
        currentValues[jointName] = newValue
        _jointValues.value = currentValues

        // Rate-limiting: Solo enviamos paquetes cada 50ms para no saturar
        val lastSent = lastSentTimes[jointName] ?: 0L
        if (currentTime - lastSent >= SEND_INTERVAL_MS) {
            director.sendJointCommand(jointName, newValue)
            lastSentTimes[jointName] = currentTime
        }
    }

    /**
     * @brief Libera el estado de bloqueo tras levantar el dedo.
     */
    fun onJointDragFinished(jointName: String) {
        lockedJoints.remove(jointName)
        lastTimes.remove(jointName)
        lastValues.remove(jointName)
    }

    /**
     * @brief Limpieza total al abandonar la pantalla de control.
     */
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