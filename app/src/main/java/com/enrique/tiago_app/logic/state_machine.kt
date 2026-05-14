package com.enrique.tiago_app.logic

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.enrique.tiago_app.utils.AppConstants

import com.enrique.tiago_app.protocol.RobotMessage
import com.enrique.tiago_app.protocol.CommandReqPayload
import com.enrique.tiago_app.protocol.ControlModeReqPayload
import com.enrique.tiago_app.protocol.AsyncNotifyPayload
import com.enrique.tiago_app.protocol.GenericRespPayload
import com.enrique.tiago_app.protocol.QueryRespPayload
import com.enrique.tiago_app.protocol.ActionFeedbackPayload
import com.enrique.tiago_app.protocol.StreamRespPayload

/**
 * ProtocolStateManager
 * El Semáforo del Frontend.
 * Trabaja ÚNICAMENTE con objetos Kotlin puros, desconociendo por completo la existencia de JSON.
 */
class ProtocolStateManager {
    private val tag = "ProtocolStateManager"

    // Estados observables por la Interfaz de Usuario (UI)
    private val _globalState = MutableStateFlow(AppConstants.GlobalState.IDLE)
    val globalState: StateFlow<String> = _globalState.asStateFlow()

    private val _movementState = MutableStateFlow(AppConstants.MovementState.IDLE)
    val movementState: StateFlow<String> = _movementState.asStateFlow()

    // ==========================================
    // 1. VALIDACIÓN ANTES DE ENVIAR (can_transition)
    // ==========================================
    fun canSendMessage(msg: RobotMessage): Pair<Boolean, String> {
        val type = msg.header.type

        // 0. Mensajes de red transversales
        if (type == AppConstants.MsgType.PING_REQ || type == AppConstants.MsgType.ACK) return Pair(true, "")

        // 1. Bloqueo si hay una petición en vuelo
        if (_globalState.value.startsWith("ESPERANDO_") ||
            _movementState.value.startsWith("ESPERANDO_")) {
            return Pair(false, "Bloqueado: Esperando respuesta del servidor.")
        }

        // 2. Semáforo según Estado Global y Subestado
        when (_globalState.value) {
            AppConstants.GlobalState.IDLE -> {
                // Solo se permite PING_REQ, que ya se aprobó arriba
                return Pair(false, "Conexión no establecida.")
            }

            AppConstants.GlobalState.CONEXION_BACKEND -> {
                if (msg.payload is CommandReqPayload) {
                    val action = msg.payload.action
                    if (action == AppConstants.Action.CONNECT || action == AppConstants.Action.END) return Pair(true, "")
                    return Pair(false, "Acción '$action' denegada. Se espera 'connect' o 'end'.")
                }
            }

            AppConstants.GlobalState.SESION_INICIADA -> {
                if (msg.payload is CommandReqPayload) {
                    if (msg.payload.action == AppConstants.Action.DISCONNECT) return Pair(true, "")
                    return Pair(false, "Acción '${msg.payload.action}' denegada en SESION_INICIADA.")
                }

                // Submáquina de Movimiento
                if (type == AppConstants.MsgType.CONTROL_MODE_REQ || type == AppConstants.MsgType.CONTROL_REQ) {
                    when (_movementState.value) {
                        AppConstants.MovementState.IDLE -> {
                            if (msg.payload is ControlModeReqPayload) {
                                if (msg.payload.event == AppConstants.ControlEvent.START) return Pair(true, "")
                            }
                            return Pair(false, "Comando denegado. El estado actual es IDLE.")
                        }
                        AppConstants.MovementState.ENVIANDO_INFO -> {
                            if (type == AppConstants.MsgType.CONTROL_REQ) return Pair(true, "") // Joystick activo
                            if (msg.payload is ControlModeReqPayload) {
                                if (msg.payload.event == AppConstants.ControlEvent.STOP) return Pair(true, "")
                            }
                            return Pair(false, "Comando denegado. El estado actual es ENVIANDO_INFO.")
                        }
                        else -> return Pair(false, "Estado de movimiento no válido para envío.")
                    }
                }
            }
        }
        return Pair(false, "Mensaje '$type' no soportado en el estado ${_globalState.value}.")
    }

    // ==========================================
    // 2. COMMIT AL ENVIAR (Pone la App en 'Cargando...')
    // ==========================================
    fun commitRequestSent(reqMsg: RobotMessage) {
        val type = reqMsg.header.type

        if (type == AppConstants.MsgType.PING_REQ && _globalState.value == AppConstants.GlobalState.IDLE) {
            transitionGlobal(AppConstants.GlobalState.ESPERANDO_CONEXION_BACKEND)
            return
        }

        if (reqMsg.payload is CommandReqPayload) {
            when (reqMsg.payload.action) {
                AppConstants.Action.CONNECT -> transitionGlobal(AppConstants.GlobalState.ESPERANDO_INICIO_SESION)
                AppConstants.Action.DISCONNECT -> transitionGlobal(AppConstants.GlobalState.ESPERANDO_CIERRE_SESION)
                AppConstants.Action.END -> transitionGlobal(AppConstants.GlobalState.ESPERANDO_DESCONEXION_BACKEND)
            }
        } else if (reqMsg.payload is ControlModeReqPayload) {
            when (reqMsg.payload.event) {
                AppConstants.ControlEvent.START -> transitionMovement(AppConstants.MovementState.ESPERANDO_PERMISO_ENVIO_INFO)
                AppConstants.ControlEvent.STOP -> transitionMovement(AppConstants.MovementState.ESPERANDO_TERMINAR_ENVIO_INFO)
            }
        }
    }

    // ==========================================
    // 3. COMMIT AL RECIBIR (Resuelve el 'Cargando...')
    // ==========================================
    // ==========================================
    // 3. COMMIT AL RECIBIR (Resuelve el 'Cargando...')
    // ==========================================
    fun commitResponseReceived(reqMsg: RobotMessage, respMsg: RobotMessage): Boolean { // ¡NUEVO: Devuelve Boolean!

        // 1. Manejo del Primer Ping -> ACK
        if (reqMsg.header.type == AppConstants.MsgType.PING_REQ && respMsg.header.type == AppConstants.MsgType.ACK) {
            if (_globalState.value == AppConstants.GlobalState.ESPERANDO_CONEXION_BACKEND) {
                transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                return true
            }
            Log.e(tag, "Desincronización fatal: Recibido ACK inicial pero el estado es ${_globalState.value}")
            return false // ¡Bandera Roja!
        }

        // 2. Avisos Asíncronos (Watchdog)
        if (respMsg.payload is AsyncNotifyPayload) {
            if (respMsg.payload.type == AppConstants.AsyncNotify.TYPE_EMERGENCY_STOP &&
                respMsg.payload.details == AppConstants.AsyncNotify.DETAILS_ROBOT_LOST) {
                triggerSessionReset()
            }
            return true
        }

        val success = when (val p = respMsg.payload) {
            is GenericRespPayload -> p.success
            is QueryRespPayload -> p.success
            is ActionFeedbackPayload -> p.success
            is StreamRespPayload -> p.success
            else -> false
        }

        // 3. Lógica según lo que habíamos pedido
        if (reqMsg.payload is CommandReqPayload) {
            val action = reqMsg.payload.action
            when (action) {
                AppConstants.Action.CONNECT -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_INICIO_SESION) return false
                    if (success) {
                        transitionGlobal(AppConstants.GlobalState.SESION_INICIADA)
                        transitionMovement(AppConstants.MovementState.IDLE)
                    } else transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                }

                AppConstants.Action.DISCONNECT -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_CIERRE_SESION) return false
                    if (success) {
                        transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                        transitionMovement(AppConstants.MovementState.IDLE)
                    } else transitionGlobal(AppConstants.GlobalState.SESION_INICIADA)
                }

                AppConstants.Action.END -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_DESCONEXION_BACKEND) return false
                    if (success) triggerFullReset() else transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                }
            }
            return true
        }
        else if (reqMsg.payload is ControlModeReqPayload) {
            val event = reqMsg.payload.event
            when (event) {
                AppConstants.ControlEvent.START -> {
                    if (_movementState.value != AppConstants.MovementState.ESPERANDO_PERMISO_ENVIO_INFO) return false
                    if (success) transitionMovement(AppConstants.MovementState.ENVIANDO_INFO)
                    else transitionMovement(AppConstants.MovementState.IDLE)
                }

                AppConstants.ControlEvent.STOP -> {
                    if (_movementState.value != AppConstants.MovementState.ESPERANDO_TERMINAR_ENVIO_INFO) return false
                    if (success) transitionMovement(AppConstants.MovementState.IDLE)
                    else transitionMovement(AppConstants.MovementState.ENVIANDO_INFO)
                }
            }
            return true
        }
        else if (reqMsg.header.type == AppConstants.MsgType.CONTROL_REQ) {
            if (_movementState.value != AppConstants.MovementState.ENVIANDO_INFO) return false
            if (!success) {
                Log.w(tag, "Error enviando velocidad al backend. Forzando subestado a IDLE.")
                transitionMovement(AppConstants.MovementState.IDLE)
            }
            return true
        }

        return false // Si llega algo que no cuadra en ninguno de los if anteriores
    }

    // ==========================================
    // EVENTOS DE RESET DE EMERGENCIA
    // ==========================================

    // Llamado si falla el Watchdog de ROS2
    fun triggerSessionReset() {
        Log.i(tag, "Línea caída o error de sesión. Volviendo a CONEXION_BACKEND.")
        transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
        transitionMovement(AppConstants.MovementState.IDLE)
    }

    // Llamado si se corta el WebSocket o hacemos 'END'
    fun triggerFullReset() {
        Log.i(tag, "Cierre completo. Volviendo a DESCONECTADO (Pantalla Inicial).")
        transitionGlobal(AppConstants.GlobalState.IDLE)
        transitionMovement(AppConstants.MovementState.IDLE)
    }

    // ==========================================
    // PRIVADAS
    // ==========================================
    private fun transitionGlobal(newState: String) {
        if (_globalState.value != newState) {
            Log.d(tag, "UI Global State: ${_globalState.value} -> $newState")
            _globalState.value = newState
        }
    }

    private fun transitionMovement(newState: String) {
        if (_movementState.value != newState) {
            Log.d(tag, "UI Movement State: ${_movementState.value} -> $newState")
            _movementState.value = newState
        }
    }
}