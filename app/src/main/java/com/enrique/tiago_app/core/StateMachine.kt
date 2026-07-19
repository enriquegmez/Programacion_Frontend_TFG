/**
 * @file StateMachine.kt
 * @brief Máquina de estados finita (FSM) reactiva para el control de la interfaz de usuario.
 * @details Este componente evalúa la validez de las transiciones de estado
 *          basadas en los mensajes enviados y recibidos.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.core

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
import com.enrique.tiago_app.protocol.QueryReqPayload
import com.enrique.tiago_app.protocol.StopStreamReqPayload
import com.enrique.tiago_app.protocol.StreamReqPayload
import com.enrique.tiago_app.protocol.AlertData

/**
 * @class ProtocolStateManager
 * @brief Valida la semántica de red garantizando la sincronización con la Interfaz.
 */
class ProtocolStateManager {

    private val tag = "ProtocolStateManager"

    // ========================================================================
    // 1. ESTADOS OBSERVABLES POR LA INTERFAZ DE USUARIO (UI)
    // ========================================================================

    private val _globalState = MutableStateFlow(AppConstants.GlobalState.IDLE)
    val globalState: StateFlow<String> = _globalState.asStateFlow()

    private val _movementState = MutableStateFlow(AppConstants.MovementState.IDLE)
    val movementState: StateFlow<String> = _movementState.asStateFlow()

    private val _monitorState = MutableStateFlow(AppConstants.MonitorState.IDLE)
    val monitorState: StateFlow<String> = _monitorState.asStateFlow()

    private val _systemAlert = MutableStateFlow<AlertData?>(null)
    val systemAlert: StateFlow<AlertData?> = _systemAlert.asStateFlow()

    private val activeStreams = mutableSetOf<String>()

    var isRobotSessionActive = false

    /**
     * @brief Limpia el estado de alerta activa del sistema.
     */
    fun clearSystemAlert() {
        _systemAlert.value = null
    }

    /**
     * @brief Provoca que la interfaz visualice un cuadro de alerta emergente.
     * @param message Información detallada para el usuario final.
     * @param title Título o encabezado en la alerta visual (Opcional).
     */
    fun showSystemAlert(message: String, title: String = "Aviso de Desconexión") {
        _systemAlert.value = AlertData(title, message)
    }

    // ========================================================================
    // 2. VALIDACIÓN PRE-ENVÍO (PRECONDICIONES)
    // ========================================================================

    /**
     * @brief Comprueba si un mensaje específico tiene permitido transmitirse basado en el estado actual.
     * @param msg Objeto envoltorio RobotMessage listo para transmitirse.
     * @return Par de variables que contienen un boolean confirmando viabilidad de envío, y
     *         opcionalmente la descripción del fallo.
     */
    fun canSendMessage(msg: RobotMessage): Pair<Boolean, String> {
        val type = msg.header.type

        // --- 1. MENSAJES DE RED TRANSVERSALES ---
        // Los PING y ACK pueden enviarse siempre para mantener viva la conexión
        if (type == AppConstants.MsgType.PING_REQ || type == AppConstants.MsgType.ACK) return Pair(
            true,
            ""
        )

        // --- 2. GUARDIA DE TRÁFICO (Carriles ocupados) ---
        // Comprueba si el subsistema específico del mensaje ya está esperando una respuesta
        val (isBusy, busyReason) = checkSubsystemLock(type)
        if (isBusy) return Pair(false, busyReason)

        // --- 3. EVALUACIÓN SEGÚN EL ESTADO GLOBAL ---
        when (_globalState.value) {

            AppConstants.GlobalState.IDLE -> {
                return Pair(false, "Conexión no establecida.")
            }

            AppConstants.GlobalState.CONEXION_BACKEND -> {
                // Solo se permiten acciones preparatorias (Lobby/Sala de Espera)
                if (msg.payload is CommandReqPayload) {
                    val action = msg.payload.action
                    if (action == AppConstants.Action.CONNECT ||
                        action == AppConstants.Action.END ||
                        action == AppConstants.Action.REBOOT ||
                        action == AppConstants.Action.SHUTDOWN ||
                        action == AppConstants.Action.CHANGE_VARS
                    ) return Pair(true, "")
                    return Pair(false, "Acción '$action' denegada.")
                }

                if (type == AppConstants.MsgType.QUERY_REQ && msg.payload is QueryReqPayload) {
                    if (msg.payload.resourceType == AppConstants.Resource.HOST_INFO) return Pair(
                        true,
                        ""
                    )
                    return Pair(
                        false,
                        "Consulta denegada. Solo se permite HOST_INFO en Sala de Espera."
                    )
                }
            }

            AppConstants.GlobalState.SESION_INICIADA -> {
                // 3.1 Comandos de Desconexión
                if (msg.payload is CommandReqPayload) {
                    if (msg.payload.action == AppConstants.Action.DISCONNECT) return Pair(true, "")
                    return Pair(
                        false,
                        "Acción '${msg.payload.action}' denegada en SESION_INICIADA."
                    )
                }

                // 3.2 Consultas genéricas
                if (type == AppConstants.MsgType.QUERY_REQ) {
                    return Pair(true, "")
                }

                // --- 4. SUBMÁQUINA DE MOVIMIENTO ---
                if (type == AppConstants.MsgType.CONTROL_MODE_REQ || type == AppConstants.MsgType.CONTROL_REQ ||
                    type == AppConstants.MsgType.ACTION_REQ || type == AppConstants.MsgType.STOP_ACTION_REQ
                ) {
                    when (_movementState.value) {
                        AppConstants.MovementState.IDLE -> {
                            if (type == AppConstants.MsgType.ACTION_REQ) return Pair(true, "")
                            if (msg.payload is ControlModeReqPayload) {
                                if (msg.payload.event == AppConstants.ControlEvent.START) return Pair(
                                    true,
                                    ""
                                )
                            }
                            return Pair(false, "Comando denegado. El estado actual es IDLE.")
                        }

                        AppConstants.MovementState.ENVIANDO_INFO -> {
                            if (type == AppConstants.MsgType.CONTROL_REQ) return Pair(true, "")
                            if (msg.payload is ControlModeReqPayload) {
                                if (msg.payload.event == AppConstants.ControlEvent.STOP) return Pair(
                                    true,
                                    ""
                                )
                            }
                            return Pair(
                                false,
                                "Comando denegado. El estado actual es ENVIANDO_INFO."
                            )
                        }

                        AppConstants.MovementState.ESPERANDO_EJECUTAR_ACCION -> {
                            // Excepción: Permitir cancelar una acción que ya está en marcha
                            if (type == AppConstants.MsgType.STOP_ACTION_REQ) return Pair(true, "")
                            return Pair(false, "Comando denegado. Ya hay una acción ejecutándose.")
                        }

                        else -> return Pair(false, "Estado de movimiento no válido para envío.")
                    }
                }

                // --- 5. SUBMÁQUINA DE MONITORIZACIÓN  ---
                if (type == AppConstants.MsgType.STREAM_REQ || type == AppConstants.MsgType.STOP_STREAM_REQ) {
                    when (_monitorState.value) {
                        AppConstants.MonitorState.IDLE -> {
                            if (type == AppConstants.MsgType.STREAM_REQ) return Pair(true, "")
                            return Pair(
                                false,
                                "Comando denegado. El estado actual es IDLE (No hay stream que detener)."
                            )
                        }

                        AppConstants.MonitorState.RECIBIENDO_STREAM -> {
                            if (type == AppConstants.MsgType.STOP_STREAM_REQ || type == AppConstants.MsgType.STREAM_REQ) return Pair(
                                true,
                                ""
                            )
                            return Pair(false, "Comando denegado en RECIBIENDO_STREAM.")
                        }

                        AppConstants.MonitorState.ESPERANDO_DEJAR_DE_RECIBIR_STREAM -> {
                            // Excepción: Permitir encolar detenciones de streams
                            if (type == AppConstants.MsgType.STOP_STREAM_REQ) return Pair(true, "")
                            return Pair(false, "Comando denegado. Ya se está deteniendo un stream.")
                        }

                        else -> return Pair(false, "Estado de monitorización no válido para envío.")
                    }
                }
            }
        }
        return Pair(false, "Mensaje '$type' no soportado en el estado ${_globalState.value}.")
    }

    // ========================================================================
    // 3. TRANSICIONES DE ESTADO TRAS TRANSMITIR (POSCONDICIONES)
    // ========================================================================

    /**
     * @brief Fueron iniciados los procesos de conexión TCP/IP subyacente. Se transiciona para mostrar cargando en UI.
     */
    fun notifyConnectingPhysical() {
        if (_globalState.value == AppConstants.GlobalState.IDLE) {
            transitionGlobal(AppConstants.GlobalState.ESPERANDO_CONEXION_BACKEND)
        }
    }

    /**
     * @brief Aplica los cambios de estado en las máquinas a partir del objeto emitido por el socket de cliente.
     * @param reqMsg Mensaje de tipo petición u orden originada en la máquina cliente.
     */
    fun commitRequestSent(reqMsg: RobotMessage) {
        val type = reqMsg.header.type

        // --- 1. COMANDOS GLOBALES DE CICLO DE VIDA ---
        if (reqMsg.payload is CommandReqPayload) {
            when (reqMsg.payload.action) {
                AppConstants.Action.CONNECT -> transitionGlobal(AppConstants.GlobalState.ESPERANDO_INICIO_SESION)
                AppConstants.Action.DISCONNECT -> transitionGlobal(AppConstants.GlobalState.ESPERANDO_CIERRE_SESION)
                AppConstants.Action.END,
                AppConstants.Action.REBOOT,
                AppConstants.Action.SHUTDOWN -> transitionGlobal(AppConstants.GlobalState.ESPERANDO_DESCONEXION_BACKEND)

                AppConstants.Action.CHANGE_VARS -> transitionGlobal(AppConstants.GlobalState.ESPERANDO_RECIBIR_INFORMACION_UNICA)
            }
        }
        // --- 2. PETICIONES DE INFORMACIÓN ---
        else if (type == AppConstants.MsgType.QUERY_REQ) {
            transitionGlobal(AppConstants.GlobalState.ESPERANDO_RECIBIR_INFORMACION_UNICA)
        }
        // --- 3. INICIO/FIN DE CONTROL CINEMÁTICO ---
        else if (reqMsg.payload is ControlModeReqPayload) {
            when (reqMsg.payload.event) {
                AppConstants.ControlEvent.START -> transitionMovement(AppConstants.MovementState.ESPERANDO_PERMISO_ENVIO_INFO)
                AppConstants.ControlEvent.STOP -> transitionMovement(AppConstants.MovementState.ESPERANDO_TERMINAR_ENVIO_INFO)
            }
        }
        // --- 4. ACCIONES PREGRABADAS (PLAYMOTION) ---
        else if (type == AppConstants.MsgType.ACTION_REQ) {
            transitionMovement(AppConstants.MovementState.ESPERANDO_EJECUTAR_ACCION)
        } else if (type == AppConstants.MsgType.STOP_ACTION_REQ) {
            transitionMovement(AppConstants.MovementState.ESPERANDO_DETENER_ACCION)
        }
        // --- 5. MONITORES CONTINUOS (CÁMARAS Y SENSORES) ---
        else if (type == AppConstants.MsgType.STREAM_REQ) {
            transitionMonitor(AppConstants.MonitorState.ESPERANDO_RECIBIR_STREAM)
        } else if (type == AppConstants.MsgType.STOP_STREAM_REQ) {
            transitionMonitor(AppConstants.MonitorState.ESPERANDO_DEJAR_DE_RECIBIR_STREAM)
        }
    }

    // ========================================================================
    // 4. TRANSICIONES DE ESTADO AL RECIBIR
    // ========================================================================

    /**
     * @brief Ajusta los parámetros de estado correspondientes cuando llega una respuesta nueva a través de la tubería de recepción asíncrona.
     * @param reqMsg Solicitud asincrónica original originada desde este terminal de hardware.
     * @param respMsg Nuevo mensaje recién analizado generado en la máquina remota del robot o backend.
     * @return `true` si el sistema resolvió satisfactoriamente los parámetros, o `false` para generar la muerte de los procesos si fue encontrado un problema insuperable.
     */
    fun commitResponseReceived(reqMsg: RobotMessage, respMsg: RobotMessage): Boolean {

        // --- 1. INTERCEPTACIÓN DE CONFIRMACIONES RÁPIDAS (ACK Y HANDSHAKE) ---
        if (reqMsg.header.type == AppConstants.MsgType.PING_REQ && respMsg.header.type == AppConstants.MsgType.ACK) {
            if (_globalState.value == AppConstants.GlobalState.ESPERANDO_CONEXION_BACKEND) {
                transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                return true
            }
            Log.e(
                tag,
                "Desincronización fatal: Recibido ACK inicial pero el estado es ${_globalState.value}"
            )
            return false
        }

        // --- 2. GESTIÓN DE NOTIFICACIONES ASÍNCRONAS (WATCHDOG Y ALERTAS DE SEGURIDAD) ---
        if (respMsg.payload is AsyncNotifyPayload) {
            if (respMsg.payload.type == AppConstants.AsyncNotify.TYPE_EMERGENCY_STOP &&
                respMsg.payload.details == AppConstants.AsyncNotify.DETAILS_ROBOT_LOST
            ) {
                showSystemAlert("Se ha perdido la conexión con el robot físico o los nodos ROS 2 se han caído. Parada de emergencia activada.")
                triggerSessionReset()
            }
            return true
        }

        // Extracción genérica del campo 'success' según el tipo de Payload
        val success = when (val p = respMsg.payload) {
            is GenericRespPayload -> p.success
            is QueryRespPayload -> p.success
            is ActionFeedbackPayload -> p.success
            is StreamRespPayload -> p.success
            else -> false
        }

        // --- 3. RESPUESTAS A COMANDOS DE CICLO DE VIDA (CONNECT/DISCONNECT/END) ---
        if (reqMsg.payload is CommandReqPayload) {
            val action = reqMsg.payload.action
            val isCorrectPayload = respMsg.payload is GenericRespPayload
            when (action) {
                AppConstants.Action.CONNECT -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_INICIO_SESION) return false
                    if (success && isCorrectPayload) {
                        isRobotSessionActive = true
                        transitionGlobal(AppConstants.GlobalState.SESION_INICIADA)
                        transitionMovement(AppConstants.MovementState.IDLE)
                    } else {
                        val errorReason =
                            if (!isCorrectPayload) "Respuesta del servidor con formato incorrecto."
                            else (respMsg.payload as? GenericRespPayload)?.details
                                ?: "Robot no detectado en la red."
                        showSystemAlert("Error de conexión: $errorReason")
                        transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                    }
                }

                AppConstants.Action.DISCONNECT -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_CIERRE_SESION) return false
                    if (success && isCorrectPayload) {
                        isRobotSessionActive = false
                        transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                        transitionMovement(AppConstants.MovementState.IDLE)
                    } else {
                        transitionGlobal(AppConstants.GlobalState.SESION_INICIADA)
                        val errorReason =
                            if (!isCorrectPayload) "Respuesta del servidor con formato incorrecto."
                            else (respMsg.payload as? GenericRespPayload)?.details
                                ?: "Error interno del servidor."
                        showSystemAlert("No se pudo desconectar del robot: $errorReason")
                    }
                }

                AppConstants.Action.END,
                AppConstants.Action.REBOOT,
                AppConstants.Action.SHUTDOWN -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_DESCONEXION_BACKEND) return false
                    if (success && isCorrectPayload) {
                        isRobotSessionActive = false
                        triggerFullReset()
                    } else {
                        transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                        val errorReason =
                            if (!isCorrectPayload) "Respuesta del servidor con formato incorrecto."
                            else (respMsg.payload as? GenericRespPayload)?.details
                                ?: "El servidor rechazó la operación."
                        showSystemAlert("Error de energía/cierre: $errorReason")
                    }
                }

                AppConstants.Action.CHANGE_VARS -> {
                    if (_globalState.value != AppConstants.GlobalState.ESPERANDO_RECIBIR_INFORMACION_UNICA) return false
                    transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
                    if (!success || !isCorrectPayload) {
                        val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                        else (respMsg.payload as? GenericRespPayload)?.details
                            ?: "Error guardando la configuración."
                        showSystemAlert("Error de red ROS 2: $errorReason")
                    }
                }
            }
            return true
        }

        // --- 4. RESPUESTAS A CONSULTAS DE INFORMACIÓN (QUERIES) ---
        else if (reqMsg.payload is QueryReqPayload) {
            if (_globalState.value != AppConstants.GlobalState.ESPERANDO_RECIBIR_INFORMACION_UNICA) return false

            val isCorrectPayload = respMsg.payload is QueryRespPayload

            // Retorno dinámico dependiendo de la ubicación virtual del usuario
            if (isRobotSessionActive) {
                transitionGlobal(AppConstants.GlobalState.SESION_INICIADA)
            } else {
                transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
            }

            if (!success || !isCorrectPayload) {
                val errorReason =
                    if (!isCorrectPayload) "Respuesta del servidor con formato incorrecto."
                    else (respMsg.payload as? QueryRespPayload)?.details
                        ?: "Error desconocido al obtener información."
                showSystemAlert("Aviso de escaneo: $errorReason")
            }
            return true
        }

        // --- 5. RESPUESTAS AL GESTOR DE ACCIONES PREGRABADAS (PLAYMOTION) ---
        else if (reqMsg.header.type == AppConstants.MsgType.ACTION_REQ) {
            if (_movementState.value != AppConstants.MovementState.ESPERANDO_EJECUTAR_ACCION &&
                _movementState.value != AppConstants.MovementState.ESPERANDO_DETENER_ACCION
            ) return false

            val actionFeedback = respMsg.payload as? ActionFeedbackPayload
            val isCorrectPayload = actionFeedback != null

            if (!isCorrectPayload) {
                transitionMovement(AppConstants.MovementState.IDLE)
                showSystemAlert("Error de formato al recibir el progreso de la acción.")
                return true
            }

            val doneExec = actionFeedback.doneExec ?: true

            if (!success) {
                transitionMovement(AppConstants.MovementState.IDLE)
                showSystemAlert("Error ejecutando el movimiento: ${actionFeedback?.details ?: "Fallo interno"}")
            } else if (success && doneExec) {
                transitionMovement(AppConstants.MovementState.IDLE)

                val detailsStr = actionFeedback?.details ?: ""
                if (detailsStr.contains("detenida", ignoreCase = true)) {
                    showSystemAlert(
                        message = "El movimiento se ha detenido correctamente.",
                        title = "Movimiento Terminado"
                    )
                } else {
                    showSystemAlert(
                        message = "¡Éxito!\n\nEl movimiento se ha completado correctamente.",
                        title = "Movimiento Terminado"
                    )
                }
            }

            return true
        } else if (reqMsg.header.type == AppConstants.MsgType.STOP_ACTION_REQ) {
            if (_movementState.value != AppConstants.MovementState.ESPERANDO_DETENER_ACCION) return false

            val isCorrectPayload = respMsg.payload is GenericRespPayload
            if (success && isCorrectPayload) {
                // Confirmación procesada correctamente, se espera el feedback de finalización
            } else {
                transitionMovement(AppConstants.MovementState.ESPERANDO_EJECUTAR_ACCION)
                val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                else (respMsg.payload as? GenericRespPayload)?.details
                    ?: "El robot no pudo detenerse."
                showSystemAlert("No se pudo detener la acción: $errorReason")
            }
            return true
        }

        // --- 6. RESPUESTAS DE CONTROL CINEMÁTICO (JOYSTICK Y MOTORES) ---
        else if (reqMsg.payload is ControlModeReqPayload) {
            val event = reqMsg.payload.event
            val isCorrectPayload = respMsg.payload is GenericRespPayload
            when (event) {
                AppConstants.ControlEvent.START -> {
                    if (_movementState.value != AppConstants.MovementState.ESPERANDO_PERMISO_ENVIO_INFO) return false
                    if (success && isCorrectPayload) {
                        transitionMovement(AppConstants.MovementState.ENVIANDO_INFO)
                    } else {
                        transitionMovement(AppConstants.MovementState.IDLE)
                        val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                        else (respMsg.payload as? GenericRespPayload)?.details
                            ?: "El topic introducido no es válido."
                        showSystemAlert("No se pudo iniciar el control: $errorReason")

                    }
                }

                AppConstants.ControlEvent.STOP -> {
                    if (_movementState.value != AppConstants.MovementState.ESPERANDO_TERMINAR_ENVIO_INFO) return false
                    if (success && isCorrectPayload) {
                        transitionMovement(AppConstants.MovementState.IDLE)
                    } else {
                        transitionMovement(AppConstants.MovementState.ENVIANDO_INFO)
                        val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                        else (respMsg.payload as? GenericRespPayload)?.details
                            ?: "El hardware no responde."
                        showSystemAlert("Peligro: No se pudo desactivar el control del joystick: $errorReason")
                    }
                }
            }
            return true
        } else if (reqMsg.header.type == AppConstants.MsgType.CONTROL_REQ) {
            if (_movementState.value != AppConstants.MovementState.ENVIANDO_INFO) return false
            val isCorrectPayload = respMsg.payload is GenericRespPayload
            if (!success || !isCorrectPayload) {
                Log.w(tag, "Error enviando velocidad al backend. Forzando subestado a IDLE.")
                transitionMovement(AppConstants.MovementState.IDLE)

                val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                else (respMsg.payload as? GenericRespPayload)?.details ?: "Conexión inestable."
                showSystemAlert("Control interrumpido: $errorReason", title = "Aviso de parada")
            }
            return true
        }

        // --- 7. RESPUESTAS DE RECURSOS CONTINUOS (CÁMARAS Y SENSORES LÁSER) ---
        else if (reqMsg.header.type == AppConstants.MsgType.STREAM_REQ) {

            if (_monitorState.value != AppConstants.MonitorState.ESPERANDO_RECIBIR_STREAM &&
                _monitorState.value != AppConstants.MonitorState.RECIBIENDO_STREAM &&
                _monitorState.value != AppConstants.MonitorState.ESPERANDO_DEJAR_DE_RECIBIR_STREAM
            ) return false

            val streamPayload = respMsg.payload as? StreamRespPayload
            val isCorrectPayload = streamPayload != null

            val hasValidUrl = streamPayload?.streamUrl != null
            val isSensorData =
                streamPayload?.streamData != null || (reqMsg.payload as? StreamReqPayload)?.resource?.uppercase() == AppConstants.Resource.SENSORS

            if (success && isCorrectPayload && (hasValidUrl || isSensorData)) {
                val reqPayload = reqMsg.payload as? StreamReqPayload
                val streamId = reqPayload?.topic ?: reqPayload?.resource ?: "unknown"
                activeStreams.add(streamId)

                transitionMonitor(AppConstants.MonitorState.RECIBIENDO_STREAM)
            } else {
                if (activeStreams.isEmpty()) {
                    transitionMonitor(AppConstants.MonitorState.IDLE)
                } else {
                    transitionMonitor(AppConstants.MonitorState.RECIBIENDO_STREAM)
                }

                val errorReason = if (!isCorrectPayload) {
                    "El servidor respondió con un formato incorrecto."
                } else if (success && !hasValidUrl && !isSensorData) {
                    "El servidor indicó éxito pero no proporcionó datos ni URL."
                } else {
                    streamPayload?.details ?: "Error desconocido al contactar con el sensor."
                }
                showSystemAlert("Error de Monitorización: $errorReason")
            }
            return true
        } else if (reqMsg.header.type == AppConstants.MsgType.STOP_STREAM_REQ) {

            if (_monitorState.value != AppConstants.MonitorState.ESPERANDO_DEJAR_DE_RECIBIR_STREAM &&
                _monitorState.value != AppConstants.MonitorState.RECIBIENDO_STREAM
            ) return false

            val isCorrectPayload = respMsg.payload is GenericRespPayload

            if (success && isCorrectPayload) {
                val reqPayload = reqMsg.payload as StopStreamReqPayload
                val streamId = reqPayload.topic ?: reqPayload.resource
                activeStreams.remove(streamId)

                if (activeStreams.isEmpty()) {
                    transitionMonitor(AppConstants.MonitorState.IDLE)
                } else {
                    transitionMonitor(AppConstants.MonitorState.RECIBIENDO_STREAM)
                }
            } else {
                transitionMonitor(AppConstants.MonitorState.RECIBIENDO_STREAM)
                val errorReason = if (!isCorrectPayload) "Formato incorrecto."
                else (respMsg.payload as? GenericRespPayload)?.details ?: "Error interno."
                showSystemAlert("No se pudo detener el sensor/vídeo: $errorReason")
            }
            return true
        }

        return false
    }

    // ========================================================================
    // 5. EVENTOS DE RECUPERACIÓN O RESET FORZADO DE ESTADOS
    // ========================================================================

    /**
     * @brief Devuelve la aplicación a la vista de Lobby temporal cuando se interrumpió la conectividad ROS de forma fatal
     * por el backend de red, manteniendo la conexión de base Ktor abierta.
     */
    fun triggerSessionReset() {
        Log.i(tag, "Línea caída o error de sesión. Volviendo a CONEXION_BACKEND.")
        transitionGlobal(AppConstants.GlobalState.CONEXION_BACKEND)
        transitionMovement(AppConstants.MovementState.IDLE)
        transitionMonitor(AppConstants.MonitorState.IDLE)
        activeStreams.clear()
        isRobotSessionActive = false
    }

    /**
     * @brief Limpia por completo la configuración tras cerrar el túnel Ktor de manera global retornando al punto de inicio.
     */
    fun triggerFullReset() {
        Log.i(tag, "Cierre completo. Volviendo a DESCONECTADO (Pantalla Inicial).")
        transitionGlobal(AppConstants.GlobalState.IDLE)
        transitionMovement(AppConstants.MovementState.IDLE)
        transitionMonitor(AppConstants.MonitorState.IDLE)
        activeStreams.clear()
        isRobotSessionActive = false
    }

    // ========================================================================
    // 6. FUNCIONES PRIVADAS
    // ========================================================================

    /**
     * @brief Transiciona de forma segura el estado global de la aplicación.
     * @param newState El nuevo estado global (ej. ESPERANDO_CONEXION_BACKEND, SESION_INICIADA).
     */
    private fun transitionGlobal(newState: String) {
        if (_globalState.value != newState) {
            Log.d(tag, "UI Global State: ${_globalState.value} -> $newState")
            _globalState.value = newState
        }
    }

    /**
     * @brief Transiciona de forma segura el estado del subsistema de movimiento.
     * @param newState El nuevo estado de movimiento (ej. ENVIANDO_INFO, ESPERANDO_EJECUTAR_ACCION).
     */
    private fun transitionMovement(newState: String) {
        if (_movementState.value != newState) {
            Log.d(tag, "UI Movement State: ${_movementState.value} -> $newState")
            _movementState.value = newState
        }
    }

    /**
     * @brief Transiciona de forma segura el estado del subsistema de monitorización.
     * @param newState El nuevo estado de monitorización (ej. RECIBIENDO_STREAM).
     */
    private fun transitionMonitor(newState: String) {
        if (_monitorState.value != newState) {
            Log.d(tag, "UI Monitor State: ${_monitorState.value} -> $newState")
            _monitorState.value = newState
        }
    }

    /**
     * @brief Guardia de Tráfico: Verifica si el subsistema objetivo está ocupado.
     * @details Asocia cada tipo de mensaje entrante a su carril lógico correspondiente (global, movimiento o monitorización)
     *          y comprueba si ese carril tiene una petición en vuelo (estados que comienzan por "ESPERANDO_").
     * @param msgType Tipo del mensaje que se intenta enviar, definido en AppConstants.MsgType.
     * @return Pair<Boolean, String> donde el primer valor es 'true' si el carril está bloqueado,
     *         y el segundo valor es el motivo del bloqueo.
     */
    private fun checkSubsystemLock(msgType: String): Pair<Boolean, String> {
        return when (msgType) {
            // --- CARRIL GLOBAL (Comandos y Consultas) ---
            AppConstants.MsgType.COMMAND_REQ, AppConstants.MsgType.QUERY_REQ -> {
                if (_globalState.value.startsWith("ESPERANDO_")) Pair(
                    true,
                    "Bloqueado: Servidor procesando sesión."
                ) else Pair(false, "")
            }
            // --- CARRIL DE MOVIMIENTO (Joystick y Acciones) ---
            AppConstants.MsgType.CONTROL_MODE_REQ, AppConstants.MsgType.CONTROL_REQ,
            AppConstants.MsgType.ACTION_REQ, AppConstants.MsgType.STOP_ACTION_REQ -> {
                // ¡EXCEPCIÓN MÁGICA! Permitimos que el mensaje de Stop pase si estamos actualmente esperando ejecutar una acción
                if (msgType == AppConstants.MsgType.STOP_ACTION_REQ && _movementState.value == AppConstants.MovementState.ESPERANDO_EJECUTAR_ACCION) {
                    Pair(false, "")
                } else if (_movementState.value.startsWith("ESPERANDO_")) {
                    Pair(true, "Bloqueado: Petición de movimiento en curso.")
                } else {
                    Pair(false, "")
                }
            }
            // --- CARRIL DE VÍDEO Y SENSORES ---
            AppConstants.MsgType.STREAM_REQ, AppConstants.MsgType.STOP_STREAM_REQ -> {
                // ¡EXCEPCIÓN MÁGICA! Permitimos múltiples paradas a la vez cuando salimos de la pestaña abruptamente
                if (msgType == AppConstants.MsgType.STOP_STREAM_REQ && _monitorState.value == AppConstants.MonitorState.ESPERANDO_DEJAR_DE_RECIBIR_STREAM) {
                    Pair(false, "")
                } else if (_monitorState.value.startsWith("ESPERANDO_")) {
                    Pair(true, "Bloqueado: Petición de cámara/sensor en curso.")
                } else {
                    Pair(false, "")
                }
            }
            // Tipos de mensaje no sujetos a bloqueos de subsistema (ej. PING_REQ)
            else -> Pair(false, "")
        }
    }
}