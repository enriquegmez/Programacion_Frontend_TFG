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

    private val tag = "TIAGO_ProtocolDirector"

    // Estados observables por la Interfaz de Usuario (UI)
    private val _globalState = MutableStateFlow(AppConstants.GlobalState.IDLE)
    val globalState: StateFlow<String> = _globalState.asStateFlow()

    private val _movementState = MutableStateFlow(AppConstants.MovementState.IDLE)
    val movementState: StateFlow<String> = _movementState.asStateFlow()

    // ¡NUEVO! Estado observable de la cámara
    private val _monitorState = MutableStateFlow(AppConstants.MonitorState.IDLE)
    val monitorState: StateFlow<String> = _monitorState.asStateFlow()
    // Estado para avisos emergentes (Alertas al usuario)
    private val _systemAlert = MutableStateFlow<String?>(null)

    val systemAlert: StateFlow<String?> = _systemAlert.asStateFlow()

    fun clearSystemAlert() {
        _systemAlert.value = null
    }

    fun showSystemAlert(message: String) {
        _systemAlert.value = message
    }

    // ==========================================
    // 1. VALIDACIÓN ANTES DE ENVIAR (can_transition)
    // ==========================================
    fun canSendMessage(msg: RobotMessage): Pair<Boolean, String> {
        val type = msg.header.type

        // 0. Mensajes de red transversales
        if (type == AppConstants.MsgType.PING_REQ || type == AppConstants.MsgType.ACK) return Pair(true, "")

        // 1. Guardia de Tráfico: Comprueba solo el carril correspondiente
        val (isBusy, busyReason) = checkSubsystemLock(type)
        if (isBusy) return Pair(false, busyReason)

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

                // Submáquina de Monitorización
                if (type == AppConstants.MsgType.STREAM_REQ || type == AppConstants.MsgType.STOP_STREAM_REQ) {
                    when (_monitorState.value) {
                        AppConstants.MonitorState.IDLE -> {
                            if (type == AppConstants.MsgType.STREAM_REQ) return Pair(true, "")
                            return Pair(false, "Comando denegado. El estado actual es IDLE (No hay stream que detener).")
                        }
                        AppConstants.MonitorState.RECIBIENDO_STREAM -> {
                            if (type == AppConstants.MsgType.STOP_STREAM_REQ) return Pair(true, "")
                            return Pair(false, "Comando denegado. El estado actual es RECIBIENDO_STREAM.")
                        }
                        else -> return Pair(false, "Estado de monitorización no válido para envío.")
                    }
                }
            }
        }
        return Pair(false, "Mensaje '$type' no soportado en el estado ${_globalState.value}.")
    }

    // ==========================================
    // 2. COMMIT AL ENVIAR (Pone la App en 'Cargando...')
    // ==========================================

    fun notifyConnectingPhysical() {
        if (_globalState.value == AppConstants.GlobalState.IDLE) {
            transitionGlobal(AppConstants.GlobalState.ESPERANDO_CONEXION_BACKEND)
        }
    }
    fun commitRequestSent(reqMsg: RobotMessage) {
        val type = reqMsg.header.type

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
        // ¡NUEVO! Peticiones de cámara
        else if (type == AppConstants.MsgType.STREAM_REQ) {
            transitionMonitor(AppConstants.MonitorState.ESPERANDO_RECIBIR_STREAM)
        } else if (type == AppConstants.MsgType.STOP_STREAM_REQ) {
            transitionMonitor(AppConstants.MonitorState.ESPERANDO_DEJAR_DE_RECIBIR_STREAM)
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
                // 📢 Lanzamos el aviso para la interfaz gráfica
                _systemAlert.value = "⚠️ Se ha perdido la conexión con el robot físico o los nodos ROS 2 se han caído. Parada de emergencia activada."
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
            val isCorrectPayload = respMsg.payload is GenericRespPayload // ¡ESCUDO!
            when (action) {
                AppConstants.Action.CONNECT -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_INICIO_SESION) return false
                    if (success && isCorrectPayload) {
                        transitionGlobal(AppConstants.GlobalState.SESION_INICIADA)
                        transitionMovement(AppConstants.MovementState.IDLE)
                    } else {
                        val errorReason = if (!isCorrectPayload) "Respuesta del servidor con formato incorrecto."
                        else (respMsg.payload as? GenericRespPayload)?.details ?: "Robot no detectado en la red."
                        showSystemAlert("Error de conexión: $errorReason")
                        transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                    }
                }

                AppConstants.Action.DISCONNECT -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_CIERRE_SESION) return false
                    if (success && isCorrectPayload) {
                        transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                        transitionMovement(AppConstants.MovementState.IDLE)
                    } else {
                        transitionGlobal(AppConstants.GlobalState.SESION_INICIADA)
                        val errorReason = if (!isCorrectPayload) "Respuesta del servidor con formato incorrecto."
                        else (respMsg.payload as? GenericRespPayload)?.details ?: "Error interno del servidor."
                        showSystemAlert("No se pudo desconectar del robot: $errorReason")
                    }
                }

                AppConstants.Action.END -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_DESCONEXION_BACKEND) return false
                    if (success && isCorrectPayload) {
                        triggerFullReset()
                    } else {
                        transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                        val errorReason = if (!isCorrectPayload) "Respuesta del servidor con formato incorrecto."
                        else (respMsg.payload as? GenericRespPayload)?.details ?: "El servidor rechazó el cierre."
                        showSystemAlert("Error al cerrar la sesión: $errorReason")
                    }
                }
            }
            return true
        }
        else if (reqMsg.payload is ControlModeReqPayload) {
            val event = reqMsg.payload.event
            val isCorrectPayload = respMsg.payload is GenericRespPayload // ¡ESCUDO!
            when (event) {
                AppConstants.ControlEvent.START -> {
                    if (_movementState.value != AppConstants.MovementState.ESPERANDO_PERMISO_ENVIO_INFO) return false
                    if (success && isCorrectPayload) {
                        transitionMovement(AppConstants.MovementState.ENVIANDO_INFO)
                    }
                    else {
                        transitionMovement(AppConstants.MovementState.IDLE)
                        // 2. Extraemos el mensaje de error de Python (o ponemos uno por defecto)
                            val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                            else (respMsg.payload as? GenericRespPayload)?.details ?: "El topic introducido no es válido."
                            // 3. Mostramos el Popup
                            showSystemAlert("No se pudo iniciar el control: $errorReason")

                    }
                }

                AppConstants.ControlEvent.STOP -> {
                    if (_movementState.value != AppConstants.MovementState.ESPERANDO_TERMINAR_ENVIO_INFO) return false
                    if (success && isCorrectPayload) {
                        transitionMovement(AppConstants.MovementState.IDLE)
                    }
                    else {
                        transitionMovement(AppConstants.MovementState.ENVIANDO_INFO)
                        val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                        else (respMsg.payload as? GenericRespPayload)?.details ?: "El hardware no responde."
                        showSystemAlert("⚠️ Peligro: No se pudo desactivar el control del joystick: $errorReason")
                    }
                }
            }
            return true
        }
        else if (reqMsg.header.type == AppConstants.MsgType.CONTROL_REQ) {
            if (_movementState.value != AppConstants.MovementState.ENVIANDO_INFO) return false
            val isCorrectPayload = respMsg.payload is GenericRespPayload
            if (!success || !isCorrectPayload) {
                Log.w(tag, "Error enviando velocidad al backend. Forzando subestado a IDLE.")
                transitionMovement(AppConstants.MovementState.IDLE)

                // ¡EL NUEVO POPUP!
                val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                else (respMsg.payload as? GenericRespPayload)?.details ?: "Conexión inestable."
                    showSystemAlert("Control interrumpido: $errorReason")
            }
            return true
        }

        // ==========================================
        // ¡NUEVO! Respuestas de Vídeo
        // ==========================================
        else if (reqMsg.header.type == AppConstants.MsgType.STREAM_REQ) {
            if (_monitorState.value != AppConstants.MonitorState.ESPERANDO_RECIBIR_STREAM) return false
            // 1. Comprobamos que el payload es del tipo correcto
            val streamPayload = respMsg.payload as? StreamRespPayload
            val isCorrectPayload = streamPayload != null

            // 2. ¡NUEVO! Si dicen que hay éxito, exigimos que la URL no sea nula
            val hasValidUrl = streamPayload?.streamUrl != null
            if (success && isCorrectPayload && hasValidUrl) {
                transitionMonitor(AppConstants.MonitorState.RECIBIENDO_STREAM)
            } else {
                transitionMonitor(AppConstants.MonitorState.IDLE)
                val errorReason = if (!isCorrectPayload) {
                    "El servidor respondió con un formato incorrecto."
                } else if (success) {
                    "El servidor indicó éxito pero no proporcionó ninguna URL."
                } else {
                    streamPayload.details ?: "Error desconocido al contactar con la cámara."
                }
                showSystemAlert("Error de vídeo: $errorReason")
            }
            return true
        }
        else if (reqMsg.header.type == AppConstants.MsgType.STOP_STREAM_REQ) {
            if (_monitorState.value != AppConstants.MonitorState.ESPERANDO_DEJAR_DE_RECIBIR_STREAM) return false
            val isCorrectPayload = respMsg.payload is GenericRespPayload // ¡ESCUDO!
            if (success && isCorrectPayload) {
                transitionMonitor(AppConstants.MonitorState.IDLE)
            } else {
                transitionMonitor(AppConstants.MonitorState.RECIBIENDO_STREAM)
                val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                else (respMsg.payload as? GenericRespPayload)?.details ?: "Error interno."
                showSystemAlert("No se pudo detener el vídeo: $errorReason")
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
        transitionMonitor(AppConstants.MonitorState.IDLE)
    }

    // Llamado si se corta el WebSocket o hacemos 'END'
    fun triggerFullReset() {
        Log.i(tag, "Cierre completo. Volviendo a DESCONECTADO (Pantalla Inicial).")
        transitionGlobal(AppConstants.GlobalState.IDLE)
        transitionMovement(AppConstants.MovementState.IDLE)
        transitionMonitor(AppConstants.MonitorState.IDLE)
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

    private fun transitionMonitor(newState: String) {
        if (_monitorState.value != newState) {
            Log.d(tag, "UI Monitor State: ${_monitorState.value} -> $newState")
            _monitorState.value = newState
        }
    }

    /**
     * Guardia de Tráfico: Asocia cada tipo de mensaje a su submáquina correspondiente
     * y comprueba si ese subsistema está ocupado ("ESPERANDO_...").
     */
    private fun checkSubsystemLock(msgType: String): Pair<Boolean, String> {
        return when (msgType) {
            // Carril Global
            AppConstants.MsgType.COMMAND_REQ, AppConstants.MsgType.QUERY_REQ -> {
                if (_globalState.value.startsWith("ESPERANDO_")) Pair(true, "Bloqueado: Servidor procesando sesión.") else Pair(false, "")
            }
            // Carril de Movimiento
            AppConstants.MsgType.CONTROL_MODE_REQ, AppConstants.MsgType.CONTROL_REQ -> {
                if (_movementState.value.startsWith("ESPERANDO_")) Pair(true, "Bloqueado: Petición de movimiento en curso.") else Pair(false, "")
            }
            // Carril de Vídeo
            AppConstants.MsgType.STREAM_REQ, AppConstants.MsgType.STOP_STREAM_REQ -> {
                if (_monitorState.value.startsWith("ESPERANDO_")) Pair(true, "Bloqueado: Petición de cámara en curso.") else Pair(false, "")
            }
            else -> Pair(false, "")
        }
    }
}