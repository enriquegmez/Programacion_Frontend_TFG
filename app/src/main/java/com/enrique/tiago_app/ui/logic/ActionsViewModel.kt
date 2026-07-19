/**
 * @file ActionsViewModel.kt
 * @brief ViewModel dedicado a la gestión de animaciones pregrabadas (PlayMotion).
 * @details Actúa como intermediario (Brain) entre la interfaz gráfica y el Director.
 *          Aplica el patrón de Flujo Unidireccional de Datos (UDF), garantizando que la UI
 *          solo observe estados inmutables y dispare eventos que el ViewModel procesa.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Importamos el Director y los Modelos
import com.enrique.tiago_app.core.ProtocolDirector
import com.enrique.tiago_app.protocol.ActionFeedbackPayload

/**
 * @class PlayMotionViewModel
 * @brief Gestiona la selección, ejecución y monitorización de las acciones de movimiento.
 * @param director Inyección de dependencia del núcleo de comunicaciones.
 */
class PlayMotionViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ========================================================================
    // 1. PASILLOS DIRECTOS DESDE EL DIRECTOR (Capa de Dominio -> UI)
    // ========================================================================

    /**
     * @brief Lista reactiva con los nombres de las rutinas de movimiento disponibles.
     * @details Se mapea directamente desde el Director para no duplicar estados en memoria.
     */
    val availableActions: StateFlow<List<String>> = director.availableActions

    /**
     * @brief Estado actual de la máquina de estados de movimiento.
     * @details Refleja transiciones críticas como IDLE, ESPERANDO_EJECUTAR_ACCION o ESPERANDO_DETENER_ACCION.
     */
    val movementState: StateFlow<String> = director.stateManager.movementState


    // ========================================================================
    // 2. ESTADO INTERNO DE ESTA PANTALLA (Capa de Presentación)
    // ========================================================================

    /** @brief Estado mutable interno para la acción seleccionada por el usuario en la lista. */
    private val _selectedAction = MutableStateFlow<String?>(null)
    /** @brief Estado inmutable expuesto a Jetpack Compose. */
    val selectedAction: StateFlow<String?> = _selectedAction.asStateFlow()

    /** @brief Estado mutable interno con los datos de telemetría de la acción en curso. */
    private val _currentFeedback = MutableStateFlow<ActionFeedbackPayload?>(null)
    /** @brief Expone a la UI el progreso (%), estado ("running", "succeeded") y errores de la acción. */
    val currentFeedback: StateFlow<ActionFeedbackPayload?> = _currentFeedback.asStateFlow()

    init {
        // --- 1. RECOLECCIÓN DE FEEDBACK EN TIEMPO REAL ---
        // Abrimos una corrutina atada al ciclo de vida del ViewModel para interceptar
        // las respuestas tipo ACTION_FEEDBACK enviadas por el servidor de ROS 2.
        viewModelScope.launch {
            director.actionFeedback.collect { feedback ->
                _currentFeedback.value = feedback
            }
        }

        // --- 2. SISTEMA DE AUTOLIMPIEZA ---
        // Si el Director informa de que la lista de acciones se ha vaciado (típicamente
        // debido a una desconexión abrupta o pérdida de conexión física con el robot),
        // reseteamos inmediatamente la UI para no mostrar un "fantasma" de una acción anterior.
        viewModelScope.launch {
            availableActions.collect { actions ->
                if (actions.isEmpty()) {
                    clearSelection()
                }
            }
        }
    }

    // ========================================================================
    // 3. EVENTOS DEL USUARIO
    // ========================================================================

    /**
     * @brief Solicita al servidor el inventario actualizado de rutinas.
     * @details Desencadena un mensaje JSON tipo QUERY_REQ con resource="ACTIONS".
     */
    fun fetchAvailableActions() {
        director.sendQueryActionsReq()
    }

    /**
     * @brief Selecciona una acción de la lista para su futura ejecución.
     * @param actionName Nombre exacto de la rutina (ej. "wave", "nod").
     */
    fun selectAction(actionName: String) {
        _selectedAction.value = actionName

        // Al elegir un movimiento nuevo, eliminamos el feedback (barra de carga) del anterior
        _currentFeedback.value = null
    }

    /**
     * @brief Dispara la ejecución de la rutina pregrabada.
     * @details Desencadena un ACTION_REQ si hay una rutina seleccionada. Si no la hay,
     *          invoca al gestor de estados para levantar una alerta gráfica de aviso.
     */
    fun executeSelectedAction() {
        val target = _selectedAction.value
        if (!target.isNullOrBlank()) {
            _currentFeedback.value = null // Reseteamos la barra de progreso a 0
            director.sendActionReq(target)
        } else {
            // Protección contra clics en falso
            director.stateManager.showSystemAlert("Por favor, selecciona un movimiento de la lista primero.")
        }
    }

    /**
     * @brief Botón de emergencia para abortar de inmediato la ejecución motriz.
     * @details Desencadena un STOP_ACTION_REQ que el servidor inyecta directo al action server de ROS 2.
     */
    fun stopCurrentAction() {
        val target = _selectedAction.value
        if (!target.isNullOrBlank()) {
            director.sendStopActionReq(target)
        }
    }

    /**
     * @brief Restaura la pantalla a su estado inicial.
     * @details Se utiliza al cambiar de pestaña en la navegación o al cancelar una operación.
     */
    fun clearSelection() {
        _selectedAction.value = null
        _currentFeedback.value = null
    }
}