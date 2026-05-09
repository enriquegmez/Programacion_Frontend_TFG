package com.enrique.tiago_app.logic

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Importamos modelos y constantes puros (NADA de JSON)
import com.enrique.tiago_app.protocol.RobotMessage
import com.enrique.tiago_app.protocol.CommandReqPayload
import com.enrique.tiago_app.protocol.ControlModeReqPayload
import com.enrique.tiago_app.protocol.GenericRespPayload
import com.enrique.tiago_app.protocol.AsyncNotifyPayload
import com.enrique.tiago_app.protocol.MessageCodec
import com.enrique.tiago_app.utils.AppConstants.GlobalState
import com.enrique.tiago_app.utils.AppConstants.MovementState
import com.enrique.tiago_app.utils.AppConstants.MsgType
import com.enrique.tiago_app.utils.AppConstants.Action

/**
 * ProtocolStateManager
 * El Semáforo del Frontend.
 * Recibe el MessageCodec por inyección para delegar la traducción sin tocar JSON.
 */
class ProtocolStateManager(private val codec: MessageCodec) {
    private val tag = "ProtocolStateManager"

    private val _globalState = MutableStateFlow(GlobalState.IDLE)
    val globalState: StateFlow<String> = _globalState.asStateFlow()

    private val _movementState = MutableStateFlow(MovementState.IDLE)
    val movementState: StateFlow<String> = _movementState.asStateFlow()

    // ==========================================
    // 1. VALIDACIÓN ANTES DE ENVIAR (can_transition)
    // ==========================================
    fun canSendMessage(msg: RobotMessage): Pair<Boolean, String> {
        val type = msg.header.type

        // 0. Mensajes transversales
        if (type == MsgType.PING_REQ || type == "ACK") return Pair(true, "")

        // 1. Bloqueo transitorio
        if (_globalState.value.startsWith("ESPERANDO_") ||
            _movementState.value.startsWith("ESPERANDO_")) {
            return Pair(false, "Bloqueado: Esperando respuesta del servidor.")
        }

        // 2. Reglas
        when (_globalState.value) {
            GlobalState.IDLE -> return Pair(false, "Conexión no establecida.")

            GlobalState.CONEXION_BACKEND -> {
                if (type == MsgType.COMMAND_REQ) {
                    // Usamos TU traductor centralizado
                    val payloadObj = codec.decodePayload<CommandReqPayload>(msg.payload)
                    val action = payloadObj?.action

                    if (action == Action.CONNECT || action == Action.END) return Pair(true, "")
                    return Pair(false, "Acción '$action' denegada. Se espera 'connect' o 'end'.")
                }
            }

            GlobalState.SESION_INICIADA -> {
                if (type == MsgType.COMMAND_REQ) {
                    val payloadObj = codec.decodePayload<CommandReqPayload>(msg.payload)
                    if (payloadObj?.action == Action.DISCONNECT) return Pair(true, "")
                    return Pair(false, "Acción '${payloadObj?.action}' denegada en SESION_INICIADA.")
                }

                if (type == "CONTROL_MODE_REQ" || type == "CONTROL_REQ") {
                    when (_movementState.value) {
                        MovementState.IDLE -> {
                            if (type == "CONTROL_MODE_REQ") {
                                val payloadObj = codec.decodePayload<ControlModeReqPayload>(msg.payload)
                                if (payloadObj?.event == "START") return Pair(true, "")
                            }
                            return Pair(false, "Comando denegado. El estado es IDLE.")
                        }
                        MovementState.ENVIANDO_INFO -> {
                            if (type == "CONTROL_REQ") return Pair(true, "")
                            if (type == "CONTROL_MODE_REQ") {
                                val payloadObj = codec.decodePayload<ControlModeReqPayload>(msg.payload)
                                if (payloadObj?.event == "STOP") return Pair(true, "")
                            }
                            return Pair(false, "Comando denegado. El estado es ENVIANDO_INFO.")
                        }
                        else -> return Pair(false, "Estado de movimiento no válido para envío.")
                    }
                }
            }
            else -> {}
        }
        return Pair(false, "Mensaje '$type' no soportado en estado ${_globalState.value}.")
    }

    // ==========================================
    // 2. COMMIT AL ENVIAR
    // ==========================================
    fun commitRequestSent(reqMsg: RobotMessage) {
        val type = reqMsg.header.type

        if (type == MsgType.PING_REQ && _globalState.value == GlobalState.IDLE) {
            transitionGlobal(GlobalState.ESPERANDO_CONEXION_BACKEND)
            return
        }

        if (type == MsgType.COMMAND_REQ) {
            val payloadObj = codec.decodePayload<CommandReqPayload>(reqMsg.payload)
            when (payloadObj?.action) {
                Action.CONNECT -> transitionGlobal(GlobalState.ESPERANDO_INICIO_SESION)
                Action.DISCONNECT -> transitionGlobal(GlobalState.ESPERANDO_CIERRE_SESION)
                Action.END -> transitionGlobal(GlobalState.ESPERANDO_DESCONEXION_BACKEND)
            }
        } else if (type == "CONTROL_MODE_REQ") {
            val payloadObj = codec.decodePayload<ControlModeReqPayload>(reqMsg.payload)
            when (payloadObj?.event) {
                "START" -> transitionMovement(MovementState.ESPERANDO_PERMISO_ENVIO_INFO)
                "STOP" -> transitionMovement(MovementState.ESPERANDO_TERMINAR_ENVIO_INFO)
            }
        }
    }

    // ==========================================
    // 3. COMMIT AL RECIBIR
    // ==========================================
    fun commitResponseReceived(reqMsg: RobotMessage, respMsg: RobotMessage) {
        if (reqMsg.header.type == MsgType.PING_REQ && respMsg.header.type == "ACK") {
            if (_globalState.value == GlobalState.ESPERANDO_CONEXION_BACKEND) {
                transitionGlobal(GlobalState.CONEXION_BACKEND)
            }
            return
        }

        if (respMsg.header.type == "ASYNC_NOTIFY") {
            val notifyObj = codec.decodePayload<AsyncNotifyPayload>(respMsg.payload)
            if (notifyObj?.type == "EMERGENCY_STOP" && notifyObj.details == "ROBOT_CONNECTION_LOST") {
                triggerSessionReset()
            }
            return
        }

        // Extraemos success de la respuesta genérica
        val respObj = codec.decodePayload<GenericRespPayload>(respMsg.payload)
        val success = respObj?.success ?: false
        val reqType = reqMsg.header.type

        if (reqType == MsgType.COMMAND_REQ) {
            val reqPayload = codec.decodePayload<CommandReqPayload>(reqMsg.payload)
            val action = reqPayload?.action
            if (success) {
                when (action) {
                    Action.CONNECT -> {
                        transitionGlobal(GlobalState.SESION_INICIADA)
                        transitionMovement(MovementState.IDLE)
                    }
                    Action.DISCONNECT -> {
                        transitionGlobal(GlobalState.CONEXION_BACKEND)
                        transitionMovement(MovementState.IDLE)
                    }
                    Action.END -> triggerFullReset()
                }
            } else {
                when (action) {
                    Action.CONNECT -> transitionGlobal(GlobalState.CONEXION_BACKEND)
                    Action.DISCONNECT -> transitionGlobal(GlobalState.SESION_INICIADA)
                    Action.END -> transitionGlobal(GlobalState.CONEXION_BACKEND)
                }
            }
        }
        else if (reqType == "CONTROL_MODE_REQ") {
            val reqPayload = codec.decodePayload<ControlModeReqPayload>(reqMsg.payload)
            val event = reqPayload?.event
            if (success) {
                when (event) {
                    "START" -> transitionMovement(MovementState.ENVIANDO_INFO)
                    "STOP" -> transitionMovement(MovementState.IDLE)
                }
            } else {
                when (event) {
                    "START" -> transitionMovement(MovementState.IDLE)
                    "STOP" -> transitionMovement(MovementState.ENVIANDO_INFO)
                }
            }
        }
        else if (reqType == "CONTROL_REQ") {
            if (!success) {
                Log.w(tag, "ControlErrorResp recibido. Forzando vuelta a IDLE de emergencia.")
                transitionMovement(MovementState.IDLE)
            }
        }
    }

    // ==========================================
    // EVENTOS DE RESET Y TRANSICIONES
    // ==========================================
    fun triggerSessionReset() {
        Log.i(tag, "Reset de sesión. Volviendo a CONEXION_BACKEND.")
        transitionGlobal(GlobalState.CONEXION_BACKEND)
        transitionMovement(MovementState.IDLE)
    }

    fun triggerFullReset() {
        Log.i(tag, "Reset total del protocolo. Volviendo a DESCONECTADO.")
        transitionGlobal(GlobalState.IDLE)
        transitionMovement(MovementState.IDLE)
    }

    private fun transitionGlobal(newState: String) {
        Log.d(tag, "Transición Global: ${_globalState.value} -> $newState")
        _globalState.value = newState
    }

    private fun transitionMovement(newState: String) {
        Log.d(tag, "Transición Movimiento: ${_movementState.value} -> $newState")
        _movementState.value = newState
    }
}