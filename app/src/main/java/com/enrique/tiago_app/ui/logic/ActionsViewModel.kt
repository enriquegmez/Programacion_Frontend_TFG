package com.enrique.tiago_app.ui.logic // Ajusta a tu paquete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Importamos el Director y los Modelos
import com.enrique.tiago_app.logic.ProtocolDirector
import com.enrique.tiago_app.utils.AppConstants
import com.enrique.tiago_app.protocol.ActionFeedbackPayload

/**
 * PlayMotionViewModel
 * El cerebro exclusivo de la pantalla de Movimientos Predefinidos.
 * Gestiona la selección de acciones, el progreso y las paradas de emergencia.
 */
class PlayMotionViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ==========================================
    // 1. PASILLOS DIRECTOS DESDE EL DIRECTOR
    // ==========================================

    // Observamos la lista de acciones disponibles que nos mande el servidor
    val availableActions: StateFlow<List<String>> = director.availableActions

    // Observamos el estado del movimiento (IDLE, ESPERANDO_EJECUTAR_ACCION, ESPERANDO_DETENER_ACCION)
    val movementState: StateFlow<String> = director.stateManager.movementState


    // ==========================================
    // 2. ESTADO INTERNO DE ESTA PANTALLA
    // ==========================================

    // Guardamos qué movimiento ha tocado el usuario en la lista antes de darle a "Ejecutar"
    private val _selectedAction = MutableStateFlow<String?>(null)
    val selectedAction: StateFlow<String?> = _selectedAction.asStateFlow()

    // Guardamos el último feedback recibido (Progreso, estado de completado, errores...)
    private val _currentFeedback = MutableStateFlow<ActionFeedbackPayload?>(null)
    val currentFeedback: StateFlow<ActionFeedbackPayload?> = _currentFeedback.asStateFlow()


    init {
        // Recolectamos en tiempo real los feedbacks que emite el Director
        viewModelScope.launch {
            director.actionFeedback.collect { feedback ->
                _currentFeedback.value = feedback
            }
        }

        // Limpieza automática: Si el robot vuelve a IDLE (terminó la acción o se paró),
        // mantenemos el feedback unos segundos para que el usuario lea "Completado"
        // pero reseteamos la acción seleccionada si queremos.
        // Por ahora lo dejamos simple y dejamos que la UI decida qué mostrar.
    }


    // ==========================================
    // 3. EVENTOS DEL USUARIO (Botones de la UI)
    // ==========================================

    /**
     * Llamado por el botón "Obtener Movimientos" en la parte superior de la pantalla.
     * Pide al servidor la lista actualizada de animaciones (QueryReq -> ACTIONS).
     */
    fun fetchAvailableActions() {
        director.sendQueryActionsReq()
    }

    /**
     * Llamado cuando el usuario hace clic en una fila de la lista de movimientos.
     */
    fun selectAction(actionName: String) {
        _selectedAction.value = actionName
        // Limpiamos cualquier feedback anterior de otro movimiento
        _currentFeedback.value = null
    }

    /**
     * Llamado por el botón "Ejecutar Movimiento".
     * Manda la orden al robot y limpia el progreso.
     */
    fun executeSelectedAction() {
        val target = _selectedAction.value
        if (!target.isNullOrBlank()) {
            _currentFeedback.value = null // Reseteamos la barra de progreso a 0
            director.sendActionReq(target)
        } else {
            // Si por algún motivo se pulsa sin haber seleccionado, avisamos
            director.stateManager.showSystemAlert("Por favor, selecciona un movimiento de la lista primero.")
        }
    }

    /**
     * Llamado por el botón rojo de "Detener Acción" mientras el robot se mueve.
     */
    fun stopCurrentAction() {
        val target = _selectedAction.value
        if (!target.isNullOrBlank()) {
            director.sendStopActionReq(target)
        }
    }

    /**
     * Llamado si queremos limpiar manualmente la selección y los mensajes
     * (Por ejemplo, al pulsar un botón de "Volver" o "Cancelar").
     */
    fun clearSelection() {
        _selectedAction.value = null
        _currentFeedback.value = null
    }
}