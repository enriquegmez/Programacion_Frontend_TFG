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
    private var msgIdCounter: Long = 1L

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
                    // Si el cable físico se corta, limpiamos toda la sesión lógica y la UI
                    Log.w(tag, "Detectada caída física del WebSocket. Reseteando protocolo.")
                    sessionManager.clearSession()
                    stateManager.triggerFullReset()
                }
            }
        }
    }

    // ==========================================
    // MÉTODOS FÍSICOS (Conexión Base)
    // ==========================================

    fun connectToServer(ip: String, port: Int) {
        scope.launch {
            webSocketClient.connect(ip, port)
            // Cuando arranca la conexión, encendemos el corazón (Heartbeat)
            sessionManager.startHeartbeat(scope) {
                sendPing() // Le decimos que el latido consiste en ejecutar esta función
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
        val currentId = msgIdCounter++
        val sessionId = sessionManager.getSessionId() // Ahora sabemos que devuelve un String no nulo

        val header = MessageHeader(
            msgId = currentId,
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

        if (type != AppConstants.MsgType.PING_REQ && type != AppConstants.MsgType.CONTROL_REQ) {
            pendingRequests[currentId] = msg
        }

        stateManager.commitRequestSent(msg)

        // Lanzamos una corrutina porque `webSocketClient.send` es una función `suspend`
        scope.launch {
            val jsonString = codec.encode(msg)
            Log.i(tag, "Enviando mensaje [$type] ID: $currentId")
            webSocketClient.send(jsonString)
        }
    }

    // ==========================================
    // CEREBRO DE RECEPCIÓN (Inbound)
    // ==========================================
    private fun handleIncomingMessage(rawJson: String) {
        val respMsg = codec.decode(rawJson)

        if (respMsg.header.type == AppConstants.MsgType.PROTOCOL_ERROR) {
            Log.e(tag, "El servidor ha reportado un error de protocolo.")
            return
        }

        if (respMsg.header.type == AppConstants.MsgType.ASYNC_NOTIFY && respMsg.payload is AsyncNotifyPayload) {
            if (respMsg.payload.type == AppConstants.AsyncNotify.TYPE_SESSION_ID) {
                val newSessionId = respMsg.payload.details.substringAfter(":")
                Log.i(tag, "Sesión asignada por el servidor: $newSessionId")
                sessionManager.saveSessionId(newSessionId)
                return
            }
        }

        val reqMsg = pendingRequests.remove(respMsg.header.msgId)

        if (reqMsg != null) {
            stateManager.commitResponseReceived(reqMsg, respMsg)

            // Si el backend nos confirmó el 'END', cortamos el cable físicamente
            if (reqMsg.payload is CommandReqPayload && reqMsg.payload.action == AppConstants.Action.END) {
                if ((respMsg.payload as? GenericRespPayload)?.success == true) {
                    Log.i(tag, "Desconexión limpia confirmada (END). Cortando WebSocket.")
                    disconnectFromServer()
                }
            }
        } else {
            val dummyReq = RobotMessage(
                header = MessageHeader(0, respMsg.header.type, "", 0.0),
                payload = EmptyPayload()
            )
            stateManager.commitResponseReceived(dummyReq, respMsg)
        }
    }
}