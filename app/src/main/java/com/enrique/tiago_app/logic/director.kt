package com.enrique.tiago_app.logic

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

// IMPORTS DE TU CAPA DE COMUNICACIÓN (Asegúrate de que la ruta es correcta)
import com.enrique.tiago_app.communication.WebSocketClient
import com.enrique.tiago_app.communication.SessionManager
import com.enrique.tiago_app.protocol.MessageCodec
import com.enrique.tiago_app.protocol.NetworkInfoResult
import com.enrique.tiago_app.protocol.RobotCapabilitiesData



/**
 * ProtocolDirector
 * Orquesta la red, el traductor, las sesiones y la máquina de estados.
 */
class ProtocolDirector(
    private val scope: CoroutineScope, // ¡NUEVO! Inyectamos el ámbito de corrutinas
    private val webSocketClient: WebSocketClient,
    private val codec: MessageCodec,
    val stateManager: ProtocolStateManager,
    private val sessionManager: SessionManager
) {
    //private val tag = "ProtocolDirector"
    private val tag = "TIAGO_ProtocolDirector"

    // La libreta donde recordamos qué enviamos
    private val pendingRequests = ConcurrentHashMap<Long, RobotMessage>()

    // ¡NUEVO! Canal para emitir la URL del vídeo cuando el servidor nos la dé
    private val _cameraStreamUrl = MutableSharedFlow<String>()
    val cameraStreamUrl = _cameraStreamUrl.asSharedFlow()

    // ¡NUEVO! Estado observable de la radiografía del robot
    private val _robotCapabilities = MutableStateFlow<RobotCapabilitiesData?>(null)
    val robotCapabilities: StateFlow<RobotCapabilitiesData?> = _robotCapabilities.asStateFlow()

    // ¡NUEVO! Estado observable de la telemetría del PC (Lobby)
    private val _hostTelemetry = MutableStateFlow<HostTelemetryData?>(null)
    val hostTelemetry: StateFlow<HostTelemetryData?> = _hostTelemetry.asStateFlow()

    // ¡NUEVO! Variables observables para las Acciones
    private val _availableActions = MutableStateFlow<List<String>>(emptyList())
    val availableActions: StateFlow<List<String>> = _availableActions.asStateFlow()

    private val _actionFeedback = MutableSharedFlow<ActionFeedbackPayload>()
    val actionFeedback = _actionFeedback.asSharedFlow()

    // ¡NUEVO! Variables observables para la red
    private val _rosTopics = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val rosTopics: StateFlow<Map<String, List<String>>> = _rosTopics.asStateFlow()

    private val _rosServices = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val rosServices: StateFlow<Map<String, List<String>>> = _rosServices.asStateFlow()

    private val _rosActions = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val rosActions: StateFlow<Map<String, List<String>>> = _rosActions.asStateFlow()

    // ¡NUEVO! Variables observables para los Sensores
    // ¡NUEVO! Variables observables para los Sensores
    private val _availableSensors = MutableStateFlow<List<SensorInfo>>(emptyList())
    val availableSensors: StateFlow<List<SensorInfo>> = _availableSensors.asStateFlow()

    // ¡NUEVO! Memoria para saber si ya hemos buscado en esta sesión
    private val _hasScannedSensors = MutableStateFlow(false)
    val hasScannedSensors: StateFlow<Boolean> = _hasScannedSensors.asStateFlow()

    // Un mapa en tiempo real. Clave: topic ("/scan"). Valor: Los datos puros de Kotlin
    private val _activeSensorData = MutableStateFlow<Map<String, SensorStreamData>>(emptyMap())
    val activeSensorData: StateFlow<Map<String, SensorStreamData>> = _activeSensorData.asStateFlow()

    fun clearNetworkInfo() {
        _rosTopics.value = emptyMap()
        _rosServices.value = emptyMap()
        _rosActions.value = emptyMap()
    }

    // Este lo usaremos al DESCONECTARNOS del robot (Borra todo)
    fun clearSensorData() {
        _availableSensors.value = emptyList()
        _activeSensorData.value = emptyMap()
        _hasScannedSensors.value = false // Reseteamos la búsqueda
    }

    // ¡NUEVO! Este lo usaremos al SALIR de la pestaña (Conserva el menú)
    fun clearActiveSensorData() {
        _activeSensorData.value = emptyMap()
    }

    init {
        // 1. Escuchar los mensajes entrantes (El SharedFlow que hiciste)
        scope.launch {
            webSocketClient.incomingMessages.collect { rawJson ->
                handleIncomingMessage(rawJson)
            }
        }

        // 2. Escuchar el estado físico de la conexión (El StateFlow que hiciste)
        scope.launch {
            webSocketClient.isConnected.collect { connected ->
                if (!connected) {
                    if (stateManager.globalState.value != AppConstants.GlobalState.IDLE) {
                        Log.e(tag, "¡Caída de red detectada! El servidor se ha desconectado de forma abrupta.")

                        // 1. Limpiamos cualquier petición que se haya quedado a medias para evitar bloqueos
                        pendingRequests.clear()

                        // 2. Apagamos el latido y limpiamos sesión local
                        sessionManager.stopHeartbeat()
                        sessionManager.clearSession()

                        // 3. Reseteamos la máquina de estados a la fuerza (vuelve a LoginScreen)
                        stateManager.triggerFullReset()

                        // 4. Le sacamos el pop-up rojo al usuario explicándole qué ha pasado
                        stateManager.showSystemAlert("⚠️ Se ha perdido la conexión física con el servidor (¿Fallo de Wi-Fi o servidor apagado?).")
                    } else {
                        // Si ya estábamos en IDLE (desconectados limpiamente), solo limpiamos en silencio
                        sessionManager.stopHeartbeat()
                        sessionManager.clearSession()
                        pendingRequests.clear()
                    }
                }
            }
        }
    }

    // ==========================================
    // MÉTODOS FÍSICOS (Conexión Base)
    // ==========================================

    fun connectToServer(ip: String, port: Int) {
        // 1. ¡Feedback Inmediato! El circulito empieza a girar
        stateManager.notifyConnectingPhysical()

        // 2. Mandamos al cartero a intentar conectar en un hilo independiente.
        // Como su función 'connect' atrapa los errores por dentro, la dejamos a su aire.
        scope.launch {
            webSocketClient.connect(ip, port)
        }

        // 3. EL CRONÓMETRO VIGÍA
        scope.launch {
            try {
                // Esperamos un máximo de 5 segundos a que la variable 'isConnected' pase a ser TRUE
                val connectionSuccess = withTimeoutOrNull(5000L) {
                    webSocketClient.isConnected.first { it } // Espera hasta que sea true
                    true
                }

                if (connectionSuccess == null) {
                    // Si el cronómetro llega a 0 y nunca fue true...
                    Log.e(tag, "Timeout: El servidor en $ip:$port no respondió o rechazó la conexión.")

                    webSocketClient.disconnect() // Forzamos abortar cualquier intento de Ktor
                    stateManager.triggerFullReset() // Apagamos el spinner

                    // 📢 Lanzamos el popup
                    stateManager.showSystemAlert("Error: El servidor en la IP $ip y puerto $port no está abierto o no es accesible.")
                }
            } catch (e: Exception) {
                stateManager.triggerFullReset()
                stateManager.showSystemAlert("Error interno al evaluar la red: ${e.message}")
            }
        }
    }

    // ==========================================
    // LIMPIEZA TOTAL DE MEMORIA
    // ==========================================
    private fun clearAllSessionData() {
        clearSensorData()
        clearRobotCapabilities()
        clearNetworkInfo()
        _hostTelemetry.value = null
    }

    fun disconnectFromServer() {
        scope.launch {
            webSocketClient.disconnect()
            sessionManager.clearSession()
            clearAllSessionData() // ¡Limpieza total!
        }
    }

    // ==========================================
    // MÉTODOS LÓGICOS (Botones de la UI)
    // ==========================================

    fun sendPing() {
        val payload = EmptyPayload()
        dispatchMessage(AppConstants.MsgType.PING_REQ, payload)
    }

    fun sendConnectToRobot() {
        val payload = CommandReqPayload(action = AppConstants.Action.CONNECT)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    fun sendDisconnectFromRobot() {
        val payload = CommandReqPayload(action = AppConstants.Action.DISCONNECT)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    fun sendEndProtocol() {
        val payload = CommandReqPayload(action = AppConstants.Action.END)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    // ¡NUEVO! Solicita la radiografía completa del robot
    fun sendRequestRobotInfo() {
        val payload = QueryReqPayload(
            resourceType = AppConstants.Resource.ROBOT_INFO
        )
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    // ¡NUEVO! Limpia la memoria del robot cuando nos desconectamos
    fun clearRobotCapabilities() {
        _robotCapabilities.value = null
        _availableActions.value = emptyList()
    }

    fun sendStartMovement(customTopic: String = "", type: String = "TELEOP") {
        val payload = ControlModeReqPayload(
            event = AppConstants.ControlEvent.START,
            type = type,
            topic = customTopic.ifBlank { null }
        )
        dispatchMessage(AppConstants.MsgType.CONTROL_MODE_REQ, payload)
    }

    fun sendStopMovement(type: String = "TELEOP") {
        val payload = ControlModeReqPayload(
            event = AppConstants.ControlEvent.STOP,
            type = type
        )
        dispatchMessage(AppConstants.MsgType.CONTROL_MODE_REQ, payload)
    }

    fun sendJoystickVelocity(v: Float, w: Float) {
        val payload = ControlReqPayload(
            data = ControlData(v = v, w = w)
        )
        dispatchMessage(AppConstants.MsgType.CONTROL_REQ, payload)
    }

    // ==========================================
    // API UNIVERSAL DE STREAMS (Cámaras y Sensores)
    // ==========================================

    /**
     * Abre el flujo de datos de cualquier recurso continuo.
     * @param resource "camera", "sensors", etc.
     * @param topic El topic de ROS 2 (ej: "/scan" o "/head_camera/rgb/image_raw")
     * @param quality "low", "medium", "high" (Opcional, los sensores lo ignoran)
     */
    fun sendStartStream(resource: String, topic: String, quality: String? = null) {
        val payload = StreamReqPayload(
            resource = resource,
            topic = topic,
            qualityLevel = quality
        )
        dispatchMessage(AppConstants.MsgType.STREAM_REQ, payload)
    }

    /**
     * Detiene el flujo de datos de un topic específico.
     */
    fun sendStopStream(resource: String, topic: String) {
        val payload = StopStreamReqPayload(
            resource = resource,
            topic = topic
        )
        dispatchMessage(AppConstants.MsgType.STOP_STREAM_REQ, payload)

        // AUTOMATIZACIÓN: Si lo que acabamos de parar era un sensor, lo borramos
        // de la memoria RAM local para que la gráfica de Compose desaparezca al instante.
        if (resource.equals(AppConstants.Resource.SENSORS, ignoreCase = true)) {
            _activeSensorData.value = _activeSensorData.value - topic
        }
    }

    // ==========================================
    // ¡NUEVO! MÉTODOS DE ACCIONES (PlayMotion)
    // ==========================================
    fun sendQueryActionsReq() {
        val payload = QueryReqPayload(resourceType = AppConstants.Resource.MOVEMENTS)
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    fun sendActionReq(target: String) {
        val payload = ActionReqPayload(
            type = AppConstants.ActionType.EXEC_ACTION,
            target = target
        )
        dispatchMessage(AppConstants.MsgType.ACTION_REQ, payload)
    }

    fun sendStopActionReq(target: String) {
        val payload = StopActionReqPayload(
            type = AppConstants.ActionType.EXEC_ACTION,
            target = target
        )
        dispatchMessage(AppConstants.MsgType.STOP_ACTION_REQ, payload)
    }

    // ==========================================
    // ¡NUEVO! MÉTODOS DE SENSORES
    // ==========================================
    fun sendQuerySensorsReq() {
        _hasScannedSensors.value = true // ¡Anotamos que ya hemos buscado!
        val payload = QueryReqPayload(resourceType = AppConstants.Resource.SENSORS)
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    // ==========================================
    // ¡NUEVO! MÉTODOS DE RED Y ARTICULACIONES
    // ==========================================

    /**
     * Pide al backend la lista de topics, servicios o acciones.
     * @param resourceType Debe ser Resource.TOPICS, Resource.SERVICES o Resource.ACTIONS
     */
    /**
     * Pide al backend la lista de topics, servicios o acciones.
     * @param resourceType Debe ser Resource.TOPICS, Resource.SERVICES o Resource.ACTIONS
     */
    fun requestNetworkInfo(resourceType: String) {
        if (stateManager.globalState.value != AppConstants.GlobalState.SESION_INICIADA) return

        val payload = QueryReqPayload(resourceType = resourceType)
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    /**
     * Envía la posición deseada de una articulación (Slider).
     * No levanta el Watchdog estricto de las ruedas en el backend.
     */
    fun sendJointCommand(jointName: String, value: Float) {
        if (stateManager.movementState.value != AppConstants.MovementState.ENVIANDO_INFO) return

        val payload = ControlReqPayload(
            data = ControlData(
                jointName = jointName,
                jointValue = value
            )
        )
        dispatchMessage(AppConstants.MsgType.CONTROL_REQ, payload)
    }

    // ==========================================
    // ¡NUEVO! MÉTODOS DE LA SALA DE ESPERA (LOBBY)
    // ==========================================
    fun requestHostTelemetry() {
        val payload = QueryReqPayload(resourceType = AppConstants.Resource.HOST_INFO)
        dispatchMessage(AppConstants.MsgType.QUERY_REQ, payload)
    }

    fun sendChangeVars(domainId: String, dds: String, useDiscovery: Boolean) {
        val payload = CommandReqPayload(
            action = AppConstants.Action.CHANGE_VARS,
            param1 = domainId,
            param2 = dds,
            param3 = useDiscovery // ¡Inyectamos la casilla aquí!
        )
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    fun sendRebootRobot() {
        val payload = CommandReqPayload(action = AppConstants.Action.REBOOT)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    fun sendShutdownRobot() {
        val payload = CommandReqPayload(action = AppConstants.Action.SHUTDOWN)
        dispatchMessage(AppConstants.MsgType.COMMAND_REQ, payload)
    }

    // ==========================================
    // CEREBRO DE ENVÍO (Outbound)
    // ==========================================
    private fun dispatchMessage(type: String, payload: Payload) {
        val finalId = codec.getNextMsgId() // Usando el AtomicLong que corregimos
        val sessionId = sessionManager.getSessionId() // Ahora sabemos que devuelve un String no nulo

        val header = MessageHeader(
            msgId = finalId,
            type = type,
            sessionId = sessionId,
            timestamp = 0.0
        )
        val msg = RobotMessage(header, payload)

        val (canSend, reason) = stateManager.canSendMessage(msg)
        if (!canSend) {
            Log.w(tag, "Envío bloqueado por el Semáforo: $reason")
            return
        }
        Log.e(tag, "✅ MÓVIL ENVÍA: $type") // Añade este chivato

        // Averiguamos si es el "Primer Ping" (el de Handshake)
        val isFirstPing = type == AppConstants.MsgType.PING_REQ &&
                stateManager.globalState.value == AppConstants.GlobalState.ESPERANDO_CONEXION_BACKEND

        // Guardamos todo EXCEPTO los CONTROL_REQ y los PING_REQ rutinarios.
        if (type != AppConstants.MsgType.CONTROL_REQ && (type != AppConstants.MsgType.PING_REQ || isFirstPing)) {
            pendingRequests[finalId] = msg
        }

        scope.launch {
            kotlinx.coroutines.delay(5000) // Damos 5 segundos de margen

            val staleMsg = pendingRequests.remove(finalId)
            if (staleMsg != null) {
                Log.e(
                    tag,
                    "TIMEOUT CRÍTICO: El paquete [$type] ID $finalId se perdió. Provocando colapso de red por seguridad."
                )

                // 🐛 EL ARREGLO MAESTRO A TU DUDA:
                // No reseteamos el estado a mano. Cortamos la conexión física.
                // Esto obligará al backend a resetearse, y nuestro propio código
                // del Error 2 (el vigilante) se encargará de resetear el frontend y avisar al usuario.
                disconnectFromServer()
            }
        }

        stateManager.commitRequestSent(msg)

        // Lanzamos una corrutina porque `webSocketClient.send` es una función `suspend`
        scope.launch {
            val jsonString = codec.encode(msg)
            if (type != AppConstants.MsgType.PING_REQ && type != AppConstants.MsgType.CONTROL_REQ) {
                Log.i(tag, "Enviando mensaje [$type] ID: $finalId")
            }
            webSocketClient.send(jsonString)
        }
    }

    // ==========================================
    // CEREBRO DE RECEPCIÓN (Inbound)
    // ==========================================
    private fun handleIncomingMessage(rawJson: String) {
        // ¡EL CHIVATO!
        if (rawJson.contains("torso_lift_joint")) {
            Log.d("CHIVATO_JSON", "Paquete crudo: $rawJson")
        }

        val respMsg: RobotMessage
        try {
            respMsg = codec.decode(rawJson)
        } catch (e: Exception) {
            // Si el backend manda algo que Kotlin no puede traducir a nuestras data classes,
            // lo registramos en el Logcat y abortamos silenciosamente sin que la app crashee.
            Log.e(tag, "Mensaje ignorado por formato inválido o desconocido: ${e.message}")
            return
        }

        // 0. ERRORES DE PROTOCOLO
        if (respMsg.header.type == AppConstants.MsgType.PROTOCOL_ERROR) {
            val errorDesc = (respMsg.payload as? ProtocolErrorPayload)?.description ?: "Error desconocido"
            Log.e(tag, "El servidor ha reportado un error de protocolo: $errorDesc")

            // Usamos tu idea: buscamos la petición fallida por su msgId
            val failedReq = pendingRequests.remove(respMsg.header.msgId)

            if (failedReq != null) {
                // Encontramos quién tuvo la culpa. Le pasamos el error al Semáforo.
                // Tu StateManager ya está preparado: verá que 'success' es falso (porque es ProtocolErrorPayload)
                // y revertirá la interfaz al estado anterior de forma segura.
                commitAndCheckSync(failedReq, respMsg)
            } else {
                // ¡AQUÍ ESTÁ LA MAGIA DE LA SINCRONIZACIÓN!
                // Es un error incomprensible (basura JSON) o un pánico del servidor.
                // Cortamos la conexión física por lo sano.
                // El servidor lo detectará y ambos os resetearéis a cero al mismo tiempo.
                Log.e(tag, "Error crítico inmanejable. Cortando WebSocket para forzar sincronización.")
                disconnectFromServer()
            }
            return // Ahora SÍ es seguro hacer el return aquí.
        }

        // 1. RECEPCIÓN DEL SESSION ID (Handshake inicial)
        if (respMsg.header.type == AppConstants.MsgType.ASYNC_NOTIFY && respMsg.payload is AsyncNotifyPayload) {
            if (respMsg.payload.type == AppConstants.AsyncNotify.TYPE_SESSION_ID) {
                val newSessionId = respMsg.payload.details.substringAfter(":")
                Log.i(tag, "Sesión asignada por el servidor: $newSessionId")
                sessionManager.saveSessionId(newSessionId)
                // ¡AHORA SÍ! Ya tenemos ID oficial, arrancamos el corazón.
                // Esto provocará que se llame a sendPing() inmediatamente.
                sessionManager.startHeartbeat(scope) {
                    sendPing()
                }
                return
            }
            // 🚨 NUEVA LÓGICA: Error 2 Solucionado
            else if (respMsg.payload.type== AppConstants.AsyncNotify.TYPE_EMERGENCY_STOP) {
                Log.e(tag, "¡NOTIFICACIÓN DE EMERGENCIA! Motivo: ${respMsg.payload.details}")

                // 1. Leemos los detalles que nos manda Python y elegimos el mensaje adecuado
                val alertMessage = when (respMsg.payload.details) {
                    "MULTIPLE_ROBOTS_DETECTED" -> {
                        "⚠️ ALERTA DE SEGURIDAD\n\nSe han detectado múltiples robots (o simuladores) en la misma red Wi-Fi. Por seguridad para evitar accidentes cruzados, la teleoperación ha sido abortada."
                    }
                    "ROBOT_CONNECTION_LOST" -> {
                        "⚠️ CONEXIÓN PERDIDA\n\nSe ha perdido la comunicación con los nodos del robot."
                    }
                    else -> {
                        "⚠️ PARADA DE EMERGENCIA\n\nEl servidor ha abortado la conexión por seguridad."
                    }
                }

                // 2. Informamos al usuario con el popup dinámico
                stateManager.showSystemAlert(alertMessage)

                // 3. Reseteamos la máquina de estados local para volver al Menú / Login
                stateManager.triggerSessionReset()
            }
            return
        }

        // 2. EXTRAER DE LA LIBRETA (Tu optimización)
        // Al usar .remove(), sacamos el mensaje de la lista para SIEMPRE en un solo paso.
        // Si es la primera respuesta a un ID, lo saca y desactiva el timeout.
        // Si es un feedback posterior, devolverá null porque ya se sacó.
        val reqMsg = pendingRequests.remove(respMsg.header.msgId)

        // 3. LA VÍA RÁPIDA DE LOS ACKs
        if (respMsg.header.type == AppConstants.MsgType.ACK) {
            if (reqMsg != null && reqMsg.header.type == AppConstants.MsgType.PING_REQ) {
                // Era el primer ping de todos (el único que guardamos).
                // Se lo pasamos al Semáforo para que quite la pantalla de carga.
                commitAndCheckSync(reqMsg, respMsg)
            }

            // Si reqMsg es null -> Era un ping rutinario.
            // Si reqMsg NO es PING_REQ -> Era un ACK de otra cosa (y ya se eliminó de la lista arriba).
            // En cualquier caso, salimos sin hacer ruido.
            return
        }

        // ==========================================
        // 5. ¡LÓGICA REFINADA PARA EL FEEDBACK CONTINUO Y SENSORES!
        // ==========================================
        if (respMsg.header.type == AppConstants.MsgType.RESP) {

            // 5.1. Feedback de Movimientos
            if (respMsg.payload is ActionFeedbackPayload) {
                scope.launch { _actionFeedback.emit(respMsg.payload) }
                val originalOrDummyReq = reqMsg ?: RobotMessage(
                    header = MessageHeader(respMsg.header.msgId, AppConstants.MsgType.ACTION_REQ, "", 0.0),
                    payload = EmptyPayload()
                )
                commitAndCheckSync(originalOrDummyReq, respMsg)
                return
            }

            // 5.2. Lluvia de datos de Sensores
            val streamResp = respMsg.payload as? StreamRespPayload
            if (streamResp != null && streamResp.parsedSensorData != null) {

                // ¡EL FILTRO DE FANTASMAS (Mantiene pura la Máquina de Estados)!
                // Si la máquina de estados ya ha cerrado la persiana (IDLE), significa que este
                // dato es un eco de la red. Lo tiramos a la basura silenciosamente.
                if (stateManager.monitorState.value == AppConstants.MonitorState.IDLE) {
                    return // Salimos del handleIncomingMessage sin hacer nada
                }

                val newData = streamResp.parsedSensorData!!

                // Actualizamos el mapa inmutable. (Compose lo detectará y redibujará la gráfica)
                _activeSensorData.value = _activeSensorData.value + (newData.topic to newData)

                // Creamos la petición fantasma (pero con sus datos reales)
                val originalOrDummyReq = reqMsg ?: RobotMessage(
                    header = MessageHeader(respMsg.header.msgId, AppConstants.MsgType.STREAM_REQ, "", 0.0),
                    payload = StreamReqPayload(resource = AppConstants.Resource.SENSORS, topic = newData.topic)
                )

                // Ahora sí, se lo pasamos al Semáforo (que lo aprobará porque no está en IDLE)
                commitAndCheckSync(originalOrDummyReq, respMsg)
                return
            }
        }

        // 6. RESPUESTAS NORMALES (Las que solo responden una vez)
        // 6. RESPUESTAS NORMALES (Las que solo responden una vez)
        if (reqMsg != null) {

            // ========================================================
            // ¡EL ESCUDO ARQUITECTÓNICO PARA LA MÁQUINA DE ESTADOS!
            // Si llega la confirmación de un STOP_ACTION, pero el robot ya
            // nos avisó (por feedback) de que había terminado y estamos en IDLE,
            // tiramos la carta a la basura para no molestar al semáforo.
            // ========================================================
            if (reqMsg.header.type == AppConstants.MsgType.STOP_ACTION_REQ &&
                stateManager.movementState.value == AppConstants.MovementState.IDLE) {
                Log.i(tag, "Confirmación de STOP_ACTION recibida, pero el robot ya terminó. Ignorado por seguridad de red.")
                return
            }

            commitAndCheckSync(reqMsg, respMsg)

            // ¡NUEVO! Si era una petición de vídeo y fue un éxito, extraemos la URL mágica
            if (reqMsg.header.type == AppConstants.MsgType.STREAM_REQ) {
                val streamResp = respMsg.payload as? StreamRespPayload
                if (streamResp?.success == true && streamResp.streamUrl != null) {
                    scope.launch {
                        // Emitimos la URL por la tubería para que la UI la pinte
                        _cameraStreamUrl.emit(streamResp.streamUrl)
                    }
                }
            }

            if (reqMsg.header.type == AppConstants.MsgType.QUERY_REQ) {
                val queryResp = respMsg.payload as? QueryRespPayload

                // ¡LA MAGIA! Leemos la petición original que sacamos de pendingRequests
                val originalReq = reqMsg.payload as? QueryReqPayload

                if (queryResp?.success == true) {
                    when (val data = queryResp.parsedData) {
                        is RobotInfoResult -> {
                            _robotCapabilities.value = data.info
                        }
                        is HostInfoResult -> {
                            _hostTelemetry.value = data.telemetry
                        }
                        is ActionListResult -> {
                            _availableActions.value = data.actions
                        }
                        // ¡NUEVO! Guardar el menú de sensores
                        is SensorListResult -> _availableSensors.value = data.sensors
                        is NetworkInfoResult -> {
                            // Miramos qué recurso habíamos pedido originalmente
                            when (originalReq?.resourceType) {
                                AppConstants.Resource.TOPICS -> _rosTopics.value = data.networkData
                                AppConstants.Resource.SERVICES -> _rosServices.value = data.networkData
                                AppConstants.Resource.ACTIONS -> _rosActions.value = data.networkData
                            }
                        }
                        null -> {
                            Log.w(tag, "QueryResp sin datos válidos (No es ni Lista ni Objeto)")
                        }
                    }
                }
            }

            // Si el backend nos confirmó el 'END', cortamos el cable físicamente
            if (reqMsg.payload is CommandReqPayload) {
                val action = reqMsg.payload.action
                val isSuccess = (respMsg.payload as? GenericRespPayload)?.success == true

                if (isSuccess) {
                    when (action) {
                        AppConstants.Action.END -> {
                            Log.i(tag, "Desconexión limpia confirmada (END). Cortando WebSocket.")
                            disconnectFromServer()
                        }
                        AppConstants.Action.DISCONNECT -> {
                            Log.i(tag, "Desconexión lógica confirmada (DISCONNECT). Limpiando caché.")
                            // ¡AQUÍ ESTÁ LA MAGIA QUE FALTABA!
                            // Ahora sí llamamos a las funciones que ya tenías definidas:
                            clearSensorData()
                            clearRobotCapabilities()
                            clearNetworkInfo()
                        }
                    }
                }
            }
        } else {
            // 7. RESPUESTAS HUÉRFANAS (Ej: Rechazos de joystick por lag)
            val payload = respMsg.payload

            if (respMsg.header.type == AppConstants.MsgType.RESP &&
                payload is GenericRespPayload &&
                payload.respType == AppConstants.RespType.CONTROL_RESP &&
                !payload.success // ¡Solo si ha fallado!
            ) {
                Log.w(tag, "Detectado rechazo de velocidad en el Backend. Forzando detención.")

                // Fabricamos la petición fantasma para engañar al Semáforo
                val dummyControlReq = RobotMessage(
                    header = MessageHeader(respMsg.header.msgId, AppConstants.MsgType.CONTROL_REQ, "", 0.0),
                    payload = EmptyPayload() // Al semáforo solo le importa el header.type
                )

                // Al pasar esto, el StateManager ejecutará el aborto a IDLE
                commitAndCheckSync(dummyControlReq, respMsg)

            } else {
                if (respMsg.header.type == AppConstants.MsgType.RESP &&
                    payload is GenericRespPayload &&
                    payload.respType != AppConstants.RespType.CONTROL_RESP)
                    Log.w(tag, "Recibida respuesta huérfana inmanejable (sin petición pendiente): ${respMsg.header.type}")
            }
        }
    }
    private fun commitAndCheckSync(reqMsg: RobotMessage, respMsg: RobotMessage){
        val isSyncOk = stateManager.commitResponseReceived(reqMsg, respMsg)
        if (!isSyncOk) {
            Log.e(tag, "Desincronización crítica de estados detectada en respuesta a ${reqMsg.header.type}. Cortando conexión por seguridad.")
            disconnectFromServer()
        }
    }
}

