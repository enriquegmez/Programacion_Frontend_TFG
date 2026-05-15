package com.enrique.tiago_app.logic

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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

// IMPORTS DE TU CAPA DE COMUNICACIÓN (Asegúrate de que la ruta es correcta)
import com.enrique.tiago_app.communication.WebSocketClient
import com.enrique.tiago_app.communication.SessionManager
import com.enrique.tiago_app.protocol.MessageCodec

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
    private val tag = "ProtocolDirector"

    // La libreta donde recordamos qué enviamos
    private val pendingRequests = ConcurrentHashMap<Long, RobotMessage>()

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
        // 1. ¡Feedback Inmediato! El circulito empieza a girar ya mismo.
        stateManager.notifyConnectingPhysical()

        scope.launch {
            try {
                webSocketClient.connect(ip, port)
            } catch (e: Exception) {
                // Si la red física falla (ej: no hay WiFi), abortamos y quitamos la carga
                Log.e(tag, "Fallo inmediato al intentar conectar físicamente: ${e.message}")
                stateManager.triggerFullReset()
            }
        }
    }

    fun disconnectFromServer() {
        scope.launch {
            webSocketClient.disconnect()
            sessionManager.clearSession()
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

    fun sendStartMovement() {
        val payload = ControlModeReqPayload(
            event = AppConstants.ControlEvent.START,
            type = "TELEOP",
            topic = AppConstants.Robot.DEFAULT_CMD_VEL_TOPIC
        )
        dispatchMessage(AppConstants.MsgType.CONTROL_MODE_REQ, payload)
    }

    fun sendStopMovement() {
        val payload = ControlModeReqPayload(
            event = AppConstants.ControlEvent.STOP,
            type = "TELEOP"
        )
        dispatchMessage(AppConstants.MsgType.CONTROL_MODE_REQ, payload)
    }

    fun sendJoystickVelocity(v: Float, w: Float) {
        val payload = ControlReqPayload(
            data = ControlData(v = v, w = w, joints = emptyList())
        )
        dispatchMessage(AppConstants.MsgType.CONTROL_REQ, payload)
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
            Log.i(tag, "Enviando mensaje [$type] ID: $finalId")
            webSocketClient.send(jsonString)
        }
    }

    // ==========================================
    // CEREBRO DE RECEPCIÓN (Inbound)
    // ==========================================
    private fun handleIncomingMessage(rawJson: String) {
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
                Log.e(tag, "¡NOTIFICACIÓN DE EMERGENCIA! El robot se ha desconectado del servidor.")

                // 1. Informamos al usuario con un popup
                stateManager.showSystemAlert("⚠️ Se ha perdido la conexión con el robot Tiago. Operación abortada.")

                // 2. Reseteamos la máquina de estados local para volver al Menú
                stateManager.triggerSessionReset()
            }
            return
        }

        // 2. EXTRAER DE LA LIBRETA (Tu optimización)
        // Al usar .remove(), sacamos el mensaje de la lista para SIEMPRE en un solo paso.
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

        if (reqMsg != null) {
            commitAndCheckSync(reqMsg, respMsg)

            // Si el backend nos confirmó el 'END', cortamos el cable físicamente
            if (reqMsg.payload is CommandReqPayload && reqMsg.payload.action == AppConstants.Action.END) {
                if ((respMsg.payload as? GenericRespPayload)?.success == true) {
                    Log.i(tag, "Desconexión limpia confirmada (END). Cortando WebSocket.")
                    disconnectFromServer()
                }
            }
        } else {
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

