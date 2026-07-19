/**
 * @file Director.kt
 * @brief Orquestador central de la capa de comunicaciones y lógica.
 * @details  Actúa como puente entre la capa de transporte (WebSocketClient), el estado de la sesión
 *           (SessionManager.kt), y la interfaz de usuario (mediante flujos reactivos StateFlow/SharedFlow).
 *           Gestiona el enrutamiento bidireccional y el envío y recepción de mensajes según el uso
 *           del usuario de la interfaz.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// Constantes y Modelos
import com.enrique.tiago_app.utils.AppConstants
import com.enrique.tiago_app.protocol.RobotMessage
import com.enrique.tiago_app.protocol.MessageHeader
import com.enrique.tiago_app.protocol.Payload
import com.enrique.tiago_app.protocol.EmptyPayload
import com.enrique.tiago_app.protocol.CommandReqPayload
import com.enrique.tiago_app.protocol.ControlModeReqPayload
import com.enrique.tiago_app.protocol.ControlReqPayload
import com.enrique.tiago_app.protocol.ControlData
import com.enrique.tiago_app.protocol.AsyncNotifyPayload
import com.enrique.tiago_app.protocol.GenericRespPayload
import com.enrique.tiago_app.protocol.ProtocolErrorPayload
import com.enrique.tiago_app.protocol.StreamReqPayload
import com.enrique.tiago_app.protocol.StopStreamReqPayload
import com.enrique.tiago_app.protocol.StreamRespPayload
import com.enrique.tiago_app.protocol.QueryReqPayload
import com.enrique.tiago_app.protocol.QueryRespPayload
import com.enrique.tiago_app.protocol.ActionReqPayload
import com.enrique.tiago_app.protocol.StopActionReqPayload
import com.enrique.tiago_app.protocol.ActionFeedbackPayload
import com.enrique.tiago_app.protocol.RobotInfoResult
import com.enrique.tiago_app.protocol.ActionListResult
import com.enrique.tiago_app.protocol.HostInfoResult
import com.enrique.tiago_app.protocol.HostTelemetryData
import com.enrique.tiago_app.protocol.SensorInfo
import com.enrique.tiago_app.protocol.SensorStreamData
import com.enrique.tiago_app.protocol.SensorListResult

// Dependencias Inyectadas
import com.enrique.tiago_app.communication.WebSocketClient
import com.enrique.tiago_app.communication.SessionManager
import com.enrique.tiago_app.protocol.MessageCodec
import com.enrique.tiago_app.protocol.NetworkInfoResult
import com.enrique.tiago_app.protocol.RobotCapabilitiesData

/**
 * @class ProtocolDirector
 * @brief Coordina las transacciones de red y el estado global de la aplicación.
 */
class ProtocolDirector(
    private val scope: CoroutineScope,
    private val webSocketClient: WebSocketClient,
    private val codec: MessageCodec,
    val stateManager: ProtocolStateManager,
    private val sessionManager: SessionManager
) {
    private val tag = "ProtocolDirector"

    // ========================================================================
    // 1. ESTRUCTURAS DE DATOS Y FLUJOS REACTIVOS
    // ========================================================================

    /** 
     * @property pendingRequests Registro concurrente de peticiones en vuelo.
     * Correlaciona respuestas asíncronas mapeando el MsgID con el paquete original enviado.
     */
    private val pendingRequests = ConcurrentHashMap<Long, RobotMessage>()

    // -- Flujos de Eventos --

    private val _cameraStreamUrl = MutableSharedFlow<String>()
    val cameraStreamUrl = _cameraStreamUrl.asSharedFlow()

    private val _actionFeedback = MutableSharedFlow<ActionFeedbackPayload>()
    val actionFeedback = _actionFeedback.asSharedFlow()

    // -- Flujos de Estado (State Streams - Persistentes) --

    private val _robotCapabilities = MutableStateFlow<RobotCapabilitiesData?>(null)
    val robotCapabilities: StateFlow<RobotCapabilitiesData?> = _robotCapabilities.asStateFlow()

    private val _hostTelemetry = MutableStateFlow<HostTelemetryData?>(null)
    val hostTelemetry: StateFlow<HostTelemetryData?> = _hostTelemetry.asStateFlow()

    private val _availableActions = MutableStateFlow<List<String>>(emptyList())
    val availableActions: StateFlow<List<String>> = _availableActions.asStateFlow()

    private val _rosTopics = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val rosTopics: StateFlow<Map<String, List<String>>> = _rosTopics.asStateFlow()

    private val _rosServices = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val rosServices: StateFlow<Map<String, List<String>>> = _rosServices.asStateFlow()

    private val _rosActions = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val rosActions: StateFlow<Map<String, List<String>>> = _rosActions.asStateFlow()

    private val _availableSensors = MutableStateFlow<List<SensorInfo>>(emptyList())
    val availableSensors: StateFlow<List<SensorInfo>> = _availableSensors.asStateFlow()

    private val _hasScannedSensors = MutableStateFlow(false)
    val hasScannedSensors: StateFlow<Boolean> = _hasScannedSensors.asStateFlow()

    /**
     * @property _activeSensorData Mapa inmutable en tiempo real.
     * Clave: Tópico ROS 2 ("/scan"). Valor: Datos puros decodificados.
     */
    private val _activeSensorData = MutableStateFlow<Map<String, SensorStreamData>>(emptyMap())
    val activeSensorData: StateFlow<Map<String, SensorStreamData>> = _activeSensorData.asStateFlow()

    // ========================================================================
    // 2. INICIALIZACIÓN Y MONITORES DE RED
    // ========================================================================

    init {
        // Hilo 1: Consumidor de la tubería de recepción
        scope.launch {
            webSocketClient.incomingMessages.collect { rawJson ->
                handleIncomingMessage(rawJson)
            }
        }

        // Hilo 2: Monitor del estado físico de la red
        scope.launch {
            webSocketClient.isConnected.collect { connected ->
                if (!connected) {
                    if (stateManager.globalState.value != AppConstants.GlobalState.IDLE) {
                        Log.e(tag, "Fallo físico de red: Desconexión abrupta del servidor.")
                        pendingRequests.clear()
                        sessionManager.stopHeartbeat()
                        sessionManager.clearSession()
                        stateManager.triggerFullReset()
                        stateManager.showSystemAlert("Se ha perdido la conexión física con el servidor (¿Fallo de Wi-Fi o servidor apagado?).")
                    } else {
                        // Desconexión limpia (Graceful Shutdown)
                        sessionManager.stopHeartbeat()
                        sessionManager.clearSession()
                        pendingRequests.clear()
                    }
                }
            }
        }
    }

    // ========================================================================
    // 3. MÉTODOS DE CICLO DE VIDA DE CONEXIÓN Y LIMPIEZA
    // ========================================================================

    /**
     * @brief Inicia el proceso de conexión física al servidor WebSocket.
     * @details Implementa un mecanismo de timeout crítico de 5 segundos.
     * @param ip Dirección IP del servidor backend.
     * @param port Puerto de red del servidor WebSocket.
     */
    fun connectToServer(ip: String, port: Int) {
        stateManager.notifyConnectingPhysical()

        scope.launch {
            webSocketClient.connect(ip, port)
        }

        scope.launch {
            try {
                val connectionSuccess = withTimeoutOrNull(5000L) {
                    webSocketClient.isConnected.first { it }
                    true
                }

                if (connectionSuccess == null) {
                    Log.e(tag, "Timeout: El servidor $ip:$port no respondió o rechazó la conexión.")
                    webSocketClient.disconnect()
                    stateManager.triggerFullReset()
                    stateManager.showSystemAlert("Error: El servidor en la IP $ip y puerto $port no está abierto o no es accesible.")
                }
            } catch (e: Exception) {
                stateManager.triggerFullReset()
                stateManager.showSystemAlert("Error interno de red: ${e.message}")
            }
        }
    }

    /**
     * @brief Cierra el túnel de comunicación y elimina la memoria de la sesión.
     */
    fun disconnectFromServer() {
        scope.launch {
            webSocketClient.disconnect()
            sessionManager.clearSession()
            clearAllSessionData()
        }
    }

    /**
     * @brief Función auxiliar privada que ejecuta la limpieza total de los flujos de estado.
     */
    private fun clearAllSessionData() {
        clearSensorData()
        clearRobotCapabilities()
        clearNetworkInfo()
        _hostTelemetry.value = null
    }

    /**
     * @brief Elimina el registro de información de la red de ROS (Topics, Services, Actions).
     */
    fun clearNetworkInfo() {
        _rosTopics.value = emptyMap()
        _rosServices.value = emptyMap()
        _rosActions.value = emptyMap()
    }

    /**
     * @brief Elimina completamente los datos de los sensores y reinicia el estado de escaneo.
     */
    fun clearSensorData() {
        _availableSensors.value = emptyList()
        _activeSensorData.value = emptyMap()
        _hasScannedSensors.value = false
    }

    /**
     * @brief Elimina únicamente los datos vivos de los sensores, manteniendo la lista disponible.
     */
    fun clearActiveSensorData() {
        _activeSensorData.value = emptyMap()
    }

    /**
     * @brief Elimina las capacidades del robot conocidas por la aplicación móvil.
     */
    fun clearRobotCapabilities() {
        _robotCapabilities.value = null
        _availableActions.value = emptyList()
    }

    // ========================================================================
    // 4. API LÓGICA (Comandos hacia el backend)
    // ========================================================================

    /**
     * @brief Envía un latido rutinario (Ping) para mantener la sesión lógica activa.
     */
    fun sendPing() {
        val payload = EmptyPayload()
        dispatchMessage(AppConstants.MsgType.PING_REQ, payload)
    }

    /**
     * @brief Solicita al servidor backend establecer la comunicación con los nodos de ROS.
     */
    fun sendConnectToRobot() {
        val payload = CommandReqPayload(action = AppConstants.Action.CONNECT)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    /**
     * @brief Solicita al servidor backend cortar la comunicación con los nodos de ROS.
     */
    fun sendDisconnectFromRobot() {
        val payload = CommandReqPayload(action = AppConstants.Action.DISCONNECT)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    /**
     * @brief Solicita el cierre limpio del protocolo con el servidor backend.
     */
    fun sendEndProtocol() {
        val payload = CommandReqPayload(action = AppConstants.Action.END)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    /**
     * @brief Consulta las características del robot conectado.
     */
    fun sendRequestRobotInfo() {
        val payload = QueryReqPayload(resourceType = AppConstants.Resource.ROBOT_INFO)
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    /**
     * @brief Inicia una sesión de movimiento indicando el modo (e.g. TELEOP).
     * @param customTopic Topic opcional donde aplicar el control, útil si no se usa el por defecto.
     * @param type Modalidad de control requerida.
     */
    fun sendStartMovement(customTopic: String = "", type: String = "TELEOP") {
        val payload = ControlModeReqPayload(
            event = AppConstants.ControlEvent.START,
            type = type,
            topic = customTopic.ifBlank { null }
        )
        dispatchMessage(AppConstants.MsgType.CONTROL_MODE_REQ, payload)
    }

    /**
     * @brief Finaliza la sesión de movimiento actual.
     * @param type Modalidad de control que se debe detener.
     */
    fun sendStopMovement(type: String = "TELEOP") {
        val payload = ControlModeReqPayload(
            event = AppConstants.ControlEvent.STOP,
            type = type
        )
        dispatchMessage(AppConstants.MsgType.CONTROL_MODE_REQ, payload)
    }

    /**
     * @brief Envía comandos cinemáticos de velocidad angular y lineal.
     * @param v Velocidad lineal deseada.
     * @param w Velocidad angular deseada.
     */
    fun sendJoystickVelocity(v: Float, w: Float) {
        val payload = ControlReqPayload(data = ControlData(v = v, w = w))
        dispatchMessage(AppConstants.MsgType.CONTROL_REQ, payload)
    }

    /**
     * @brief Abre un flujo continuo de datos de un recurso específico (Cámara, Lidar...).
     * @param resource Categoría del recurso (camera, sensors).
     * @param topic Topic de ROS 2 donde se publica el flujo.
     * @param quality Nivel de compresión/calidad (opcional).
     */
    fun sendStartStream(resource: String, topic: String, quality: String? = null) {
        val payload = StreamReqPayload(resource = resource, topic = topic, qualityLevel = quality)
        dispatchMessage(AppConstants.MsgType.STREAM_REQ, payload)
    }

    /**
     * @brief Detiene la recepción de un flujo continuo de datos de un topic específico.
     * @param resource Categoría del recurso a detener.
     * @param topic Topic de ROS 2 asociado al recurso.
     */
    fun sendStopStream(resource: String, topic: String) {
        val payload = StopStreamReqPayload(resource = resource, topic = topic)
        dispatchMessage(AppConstants.MsgType.STOP_STREAM_REQ, payload)

        // Optimización reactiva: elimina inmediata del UI state si es un sensor
        if (resource.equals(AppConstants.Resource.SENSORS, ignoreCase = true)) {
            _activeSensorData.value = _activeSensorData.value - topic
        }
    }

    /**
     * @brief Consulta al backend la lista de rutinas de movimiento (PlayMotion) disponibles.
     */
    fun sendQueryActionsReq() {
        val payload = QueryReqPayload(resourceType = AppConstants.Resource.MOVEMENTS)
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    /**
     * @brief Ejecuta una acción de movimiento pregrabada (PlayMotion) en el robot.
     * @param target Nombre de la acción que se va a ejecutar.
     */
    fun sendActionReq(target: String) {
        val payload = ActionReqPayload(type = AppConstants.ActionType.EXEC_ACTION, target = target)
        dispatchMessage(AppConstants.MsgType.ACTION_REQ, payload)
    }

    /**
     * @brief Cancela de forma prematura una acción de movimiento que se está ejecutando.
     * @param target Nombre de la acción a detener.
     */
    fun sendStopActionReq(target: String) {
        val payload = StopActionReqPayload(type = AppConstants.ActionType.EXEC_ACTION, target = target)
        dispatchMessage(AppConstants.MsgType.STOP_ACTION_REQ, payload)
    }

    /**
     * @brief Pide la lista de sensores físicos disponibles en el robot.
     */
    fun sendQuerySensorsReq() {
        _hasScannedSensors.value = true
        val payload = QueryReqPayload(resourceType = AppConstants.Resource.SENSORS)
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    /**
     * @brief Consulta elementos específicos de la red de ROS 2.
     * @param resourceType Debe ser Resource.TOPICS, Resource.SERVICES o Resource.ACTIONS.
     */
    fun requestNetworkInfo(resourceType: String) {
        if (stateManager.globalState.value != AppConstants.GlobalState.SESION_INICIADA) return
        val payload = QueryReqPayload(resourceType = resourceType)
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    /**
     * @brief Comando directo de posición para una articulación individual del robot.
     * @param jointName Nombre de la articulación (ej: torso_lift_joint).
     * @param value Posición objetivo deseada.
     */
    fun sendJointCommand(jointName: String, value: Float) {
        if (stateManager.movementState.value != AppConstants.MovementState.ENVIANDO_INFO) return
        val payload = ControlReqPayload(data = ControlData(jointName = jointName, jointValue = value))
        dispatchMessage(AppConstants.MsgType.CONTROL_REQ, payload)
    }

    /**
     * @brief Consulta el rendimiento y telemetría de la máquina host donde corre el servidor.
     */
    fun requestHostTelemetry() {
        val payload = QueryReqPayload(resourceType = AppConstants.Resource.HOST_INFO)
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    /**
     * @brief Solicita cambios en las variables de entorno relativas al DDS y Dominio ROS.
     * @param domainId Identificador de red (ROS_DOMAIN_ID).
     * @param dds Middleware utilizado (RMW_IMPLEMENTATION).
     * @param useDiscovery Bandera para habilitar o deshabilitar el Discovery Server.
     */
    fun sendChangeVars(domainId: String, dds: String, useDiscovery: Boolean) {
        val payload = CommandReqPayload(
            action = AppConstants.Action.CHANGE_VARS,
            param1 = domainId,
            param2 = dds,
            param3 = useDiscovery
        )
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    /**
     * @brief Ordena el reinicio físico del nodo principal o del propio robot/servidor.
     */
    fun sendRebootRobot() {
        val payload = CommandReqPayload(action = AppConstants.Action.REBOOT)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    /**
     * @brief Ordena el apagado completo del sistema anfitrión.
     */
    fun sendShutdownRobot() {
        val payload = CommandReqPayload(action = AppConstants.Action.SHUTDOWN)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    // ========================================================================
    // 5. MOTOR DE ENVÍO Y RECEPCIÓN
    // ========================================================================

    /**
     * @brief Empaqueta, codifica y transmite mensajes tras pasar el semáforo de estado.
     * @details Añade los mensajes a la lista concurrente para gestionar tiempos de respuesta (timeouts).
     * @param type Tipo de mensaje definido en el protocolo.
     * @param payload Cuerpo del mensaje en formato data class.
     */
    private fun dispatchMessage(type: String, payload: Payload) {
        val finalId = codec.getNextMsgId()
        val sessionId = sessionManager.getSessionId()

        val header = MessageHeader(msgId = finalId, type = type, sessionId = sessionId, timestamp = 0.0)
        val msg = RobotMessage(header, payload)

        // Validación de coherencia contra la Máquina de Estados
        val (canSend, reason) = stateManager.canSendMessage(msg)
        if (!canSend) {
            Log.w(tag, "Envío bloqueado por el Semáforo de Estado: $reason")
            return
        }

        Log.e(tag, "MÓVIL ENVÍA: $type")

        val isFirstPing = type == AppConstants.MsgType.PING_REQ &&
                stateManager.globalState.value == AppConstants.GlobalState.ESPERANDO_CONEXION_BACKEND

        // Persistencia en memoria para timeout validation (Excepto flujos continuos)
        if (type != AppConstants.MsgType.CONTROL_REQ && (type != AppConstants.MsgType.PING_REQ || isFirstPing)) {
            pendingRequests[finalId] = msg
        }

        // Vigilante de Timeout (5000ms)
        scope.launch {
            kotlinx.coroutines.delay(5000)

            val staleMsg = pendingRequests.remove(finalId)
            if (staleMsg != null) {
                Log.e(tag, "TIMEOUT CRÍTICO: El paquete [$type] ID $finalId se perdió. Provocando colapso de red por seguridad.")
                // Desconexión forzada para obligar al servidor a resetearse y mantener la sincronía
                disconnectFromServer()
            }
        }

        stateManager.commitRequestSent(msg)

        scope.launch {
            val jsonString = codec.encode(msg)
            if (type != AppConstants.MsgType.PING_REQ && type != AppConstants.MsgType.CONTROL_REQ) {
                Log.i(tag, "Enviando mensaje [$type] ID: $finalId")
            }
            webSocketClient.send(jsonString)
        }
    }

    /**
     * @brief Gestiona las respuestas entrantes.
     * @details Convierte JSON crudo en objetos Kotlin y reacciona de acuerdo al protocolo establecido.
     * @param rawJson Cadena de texto recibida desde el canal WebSocket.
     */
    private fun handleIncomingMessage(rawJson: String) {
        val respMsg: RobotMessage
        try {
            respMsg = codec.decode(rawJson)
        } catch (e: Exception) {
            Log.e(tag, "Error de decodificación JSON (Mensaje ignorado): ${e.message}")
            return
        }

        // ============================
        // A) GESTIÓN DE ERRORES CRÍTICOS
        // ============================
        if (respMsg.header.type == AppConstants.MsgType.PROTOCOL_ERROR) {
            val errorDesc = (respMsg.payload as? ProtocolErrorPayload)?.description ?: "Error desconocido"
            Log.e(tag, "Protocol Error reportado por Backend: $errorDesc")

            val failedReq = pendingRequests.remove(respMsg.header.msgId)

            if (failedReq != null) {
                commitAndCheckSync(failedReq, respMsg)
            } else {
                Log.e(tag, "Error crítico huérfano. Cortando WebSocket para forzar sincronización global.")
                disconnectFromServer()
            }
            return
        }

        // ============================
        // B) NOTIFICACIONES ASÍNCRONAS
        // ============================
        if (respMsg.header.type == AppConstants.MsgType.ASYNC_NOTIFY && respMsg.payload is AsyncNotifyPayload) {
            if (respMsg.payload.type == AppConstants.AsyncNotify.TYPE_SESSION_ID) {
                val newSessionId = respMsg.payload.details.substringAfter(":")
                Log.i(tag, "Sesión lógica asignada: $newSessionId")
                sessionManager.saveSessionId(newSessionId)

                sessionManager.startHeartbeat(scope) { sendPing() }
                return
            }
            else if (respMsg.payload.type == AppConstants.AsyncNotify.TYPE_EMERGENCY_STOP) {
                Log.e(tag, "NOTIFICACIÓN DE EMERGENCIA. Motivo: ${respMsg.payload.details}")

                val alertMessage = when (respMsg.payload.details) {
                    "MULTIPLE_ROBOTS_DETECTED" -> "ALERTA DE SEGURIDAD\n\nSe han detectado múltiples robots (o simuladores) en la misma red Wi-Fi. Por seguridad, la teleoperación ha sido abortada."
                    "ROBOT_CONNECTION_LOST" -> "CONEXIÓN PERDIDA\n\nSe ha perdido la comunicación con los nodos del robot."
                    else -> "PARADA DE EMERGENCIA\n\nEl servidor ha abortado la conexión por seguridad."
                }

                stateManager.showSystemAlert(alertMessage)
                stateManager.triggerSessionReset()
            }
            return
        }

        // Extracción de la memoria
        val reqMsg = pendingRequests.remove(respMsg.header.msgId)

        // ============================
        // C) CONFIRMACIONES RÁPIDAS (ACK)
        // ============================
        if (respMsg.header.type == AppConstants.MsgType.ACK) {
            if (reqMsg != null && reqMsg.header.type == AppConstants.MsgType.PING_REQ) {
                commitAndCheckSync(reqMsg, respMsg)
            }
            return
        }

        // ============================
        // D) FLUJOS CONTINUOS (STREAMS & FEEDBACK)
        // ============================
        if (respMsg.header.type == AppConstants.MsgType.RESP) {
            // Acción en progreso
            if (respMsg.payload is ActionFeedbackPayload) {
                scope.launch { _actionFeedback.emit(respMsg.payload) }
                val originalOrDummyReq = reqMsg ?: RobotMessage(
                    header = MessageHeader(respMsg.header.msgId, AppConstants.MsgType.ACTION_REQ, "", 0.0),
                    payload = EmptyPayload()
                )
                commitAndCheckSync(originalOrDummyReq, respMsg)
                return
            }

            // Datos puros de Sensores
            val streamResp = respMsg.payload as? StreamRespPayload
            if (streamResp != null && streamResp.parsedSensorData != null) {

                // Descarte silencioso de ecos de red si la máquina ya cerró el monitor.
                if (stateManager.monitorState.value == AppConstants.MonitorState.IDLE) return

                val newData = streamResp.parsedSensorData!!
                _activeSensorData.value = _activeSensorData.value + (newData.topic to newData)

                val originalOrDummyReq = reqMsg ?: RobotMessage(
                    header = MessageHeader(respMsg.header.msgId, AppConstants.MsgType.STREAM_REQ, "", 0.0),
                    payload = StreamReqPayload(resource = AppConstants.Resource.SENSORS, topic = newData.topic)
                )

                commitAndCheckSync(originalOrDummyReq, respMsg)
                return
            }
        }

        // ============================
        // E) RESPUESTAS ÚNICAS (QUERIES & COMMANDS)
        // ============================
        if (reqMsg != null) {

            // Prevención arquitectónica: Descartar confirmaciones de parada si el robot ya notificó el fin previamente.
            if (reqMsg.header.type == AppConstants.MsgType.STOP_ACTION_REQ &&
                stateManager.movementState.value == AppConstants.MovementState.IDLE) {
                Log.i(tag, "Confirmación de STOP_ACTION ignorada por encontrarse ya en IDLE.")
                return
            }

            commitAndCheckSync(reqMsg, respMsg)

            // Extracción de metadatos (URLs de vídeo)
            if (reqMsg.header.type == AppConstants.MsgType.STREAM_REQ) {
                val streamResp = respMsg.payload as? StreamRespPayload
                if (streamResp?.success == true && streamResp.streamUrl != null) {
                    scope.launch { _cameraStreamUrl.emit(streamResp.streamUrl) }
                }
            }

            // Desempaquetado de consultas complejas
            if (reqMsg.header.type == AppConstants.MsgType.QUERY_REQ) {
                val queryResp = respMsg.payload as? QueryRespPayload
                val originalReq = reqMsg.payload as? QueryReqPayload

                if (queryResp?.success == true) {
                    when (val data = queryResp.parsedData) {
                        is RobotInfoResult -> _robotCapabilities.value = data.info
                        is HostInfoResult -> _hostTelemetry.value = data.telemetry
                        is ActionListResult -> _availableActions.value = data.actions
                        is SensorListResult -> _availableSensors.value = data.sensors
                        is NetworkInfoResult -> {
                            when (originalReq?.resourceType) {
                                AppConstants.Resource.TOPICS -> _rosTopics.value = data.networkData
                                AppConstants.Resource.SERVICES -> _rosServices.value = data.networkData
                                AppConstants.Resource.ACTIONS -> _rosActions.value = data.networkData
                            }
                        }
                        null -> Log.w(tag, "QueryResp sin cuerpo de datos válido.")
                    }
                }
            }

            // Resolución de comandos de ciclo de vida
            if (reqMsg.payload is CommandReqPayload) {
                val action = reqMsg.payload.action
                val isSuccess = (respMsg.payload as? GenericRespPayload)?.success == true

                if (isSuccess) {
                    when (action) {
                        AppConstants.Action.END -> {
                            Log.i(tag, "Secuencia END completada. Cortando red de forma controlada.")
                            disconnectFromServer()
                        }
                        AppConstants.Action.DISCONNECT -> {
                            Log.i(tag, "Secuencia DISCONNECT completada. Purgando cachés lógicas.")
                            clearSensorData()
                            clearRobotCapabilities()
                            clearNetworkInfo()
                        }
                    }
                }
            }
        } else {
            // ============================
            // F) RESPUESTAS HUÉRFANAS Y RECHAZOS DE LATENCIA
            // ============================
            val payload = respMsg.payload

            if (respMsg.header.type == AppConstants.MsgType.RESP &&
                payload is GenericRespPayload &&
                payload.respType == AppConstants.RespType.CONTROL_RESP &&
                !payload.success
            ) {
                Log.w(tag, "Rechazo de teleoperación por latencia en el Backend. Forzando aborto.")

                val dummyControlReq = RobotMessage(
                    header = MessageHeader(respMsg.header.msgId, AppConstants.MsgType.CONTROL_REQ, "", 0.0),
                    payload = EmptyPayload()
                )
                commitAndCheckSync(dummyControlReq, respMsg)
            } else {
                if (respMsg.header.type == AppConstants.MsgType.RESP &&
                    payload is GenericRespPayload &&
                    payload.respType != AppConstants.RespType.CONTROL_RESP)
                    Log.w(tag, "Respuesta huérfana inmanejable: ${respMsg.header.type}")
            }
        }
    }

    /**
     * @brief Transmite el estado actual a la máquina de estados y evalúa desincronizaciones críticas.
     * @details Si detecta una discordancia fatal, corta la conexión de red como medida de seguridad.
     * @param reqMsg Mensaje original solicitado.
     * @param respMsg Respuesta recibida desde el servidor.
     */
    private fun commitAndCheckSync(reqMsg: RobotMessage, respMsg: RobotMessage){
        val isSyncOk = stateManager.commitResponseReceived(reqMsg, respMsg)
        if (!isSyncOk) {
            Log.e(tag, "Desincronización de estado fatal en respuesta a ${reqMsg.header.type}. Abortando conexión.")
            disconnectFromServer()
        }
    }
}