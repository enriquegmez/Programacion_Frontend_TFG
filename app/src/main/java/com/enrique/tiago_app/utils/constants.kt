package com.enrique.tiago_app.utils

/**
 * AppConstants
 * Centraliza todos los valores fijos del protocolo y la red.
 * Reflejo exacto del JSON Schema y constants.py del Backend.
 */
object AppConstants {

    const val DEFAULT_SERVER_IP = "192.168.68.83"
    const val DEFAULT_SERVER_PORT = 8765

    // ==========================================
    // ESTADOS (UI y Lógica)
    // ==========================================
    object GlobalState {
        const val IDLE = "IDLE" // Ajustado al nombre que usamos en la UI
        const val ESPERANDO_CONEXION_BACKEND = "ESPERANDO_CONEXION_BACKEND"
        const val CONEXION_BACKEND = "CONEXION_BACKEND"
        const val ESPERANDO_INICIO_SESION = "ESPERANDO_INICIO_SESION"
        const val SESION_INICIADA = "SESION_INICIADA"
        const val ESPERANDO_CIERRE_SESION = "ESPERANDO_CIERRE_SESION"
        const val ESPERANDO_DESCONEXION_BACKEND = "ESPERANDO_DESCONEXION_BACKEND"

        const val ESPERANDO_RECIBIR_INFORMACION_UNICA = "ESPERANDO_RECIBIR_INFORMACION_UNICA"
    }

    object MovementState {
        const val IDLE = "IDLE"
        const val ESPERANDO_PERMISO_ENVIO_INFO = "ESPERANDO_PERMISO_ENVIO_INFO"
        const val ENVIANDO_INFO = "ENVIANDO_INFO"
        const val ESPERANDO_TERMINAR_ENVIO_INFO = "ESPERANDO_TERMINAR_ENVIO_INFO"
        const val ESPERANDO_EJECUTAR_ACCION = "ESPERANDO_EJECUTAR_ACCION"
        const val ESPERANDO_DETENER_ACCION = "ESPERANDO_DETENER_ACCION"

    }

    object MonitorState {
        const val IDLE = "IDLE"
        const val ESPERANDO_RECIBIR_STREAM = "ESPERANDO_RECIBIR_STREAM"
        const val RECIBIENDO_STREAM = "RECIBIENDO_STREAM"
        const val ESPERANDO_DEJAR_DE_RECIBIR_STREAM = "ESPERANDO_DEJAR_DE_RECIBIR_STREAM"
    }

    // ==========================================
    // PROTOCOLO: Tipos de Mensaje (Header -> type)
    // ==========================================
    object MsgType {
        const val COMMAND_REQ = "COMMAND_REQ"
        const val QUERY_REQ = "QUERY_REQ"
        const val ACTION_REQ = "ACTION_REQ"
        const val STOP_ACTION_REQ = "STOP_ACTION_REQ"
        const val CONTROL_MODE_REQ = "CONTROL_MODE_REQ"
        const val CONTROL_REQ = "CONTROL_REQ"
        const val STREAM_REQ = "STREAM_REQ"
        const val STOP_STREAM_REQ = "STOP_STREAM_REQ"
        const val RESP = "RESP"
        const val ASYNC_NOTIFY = "ASYNC_NOTIFY"
        const val PROTOCOL_ERROR = "PROTOCOL_ERROR"
        const val PING_REQ = "PING_REQ"
        const val ACK = "ACK"
    }

    // ==========================================
    // PROTOCOLO: Tipos de Respuesta (Payload -> resp_type)
    // ==========================================
    object RespType {
        const val COMMAND_RESP = "COMMAND_RESP"
        const val QUERY_RESP = "QUERY_RESP"
        const val ACTION_FEEDBACK = "ACTION_FEEDBACK"
        const val STOP_ACTION_FEEDBACK = "STOP_ACTION_FEEDBACK"
        const val CONTROL_MODE_RESP = "CONTROL_MODE_RESP"
        const val CONTROL_RESP = "CONTROL_RESP"
        const val STREAM_RESP = "STREAM_RESP"
        const val STOP_STREAM_RESP = "STOP_STREAM_RESP"
    }

    // ==========================================
    // PROTOCOLO: Payloads Específicos
    // ==========================================
    object Action {
        // Valores en MINÚSCULAS para cumplir el JSON Schema
        const val CONNECT = "connect"
        const val DISCONNECT = "disconnect"
        const val CHANGE_VARS = "change_vars"
        const val END = "end"
        const val GET_HISTORY = "get_history"
        const val SSH = "ssh"
    }

    // ¡NUEVO! Tipos de recursos para QueryReq
    object Resource {
        const val ROBOT_INFO = "ROBOT_INFO"
        const val TELEOP = "TELEOP"
        const val CAMERAS = "CAMERAS"
        const val TOPICS = "TOPICS"
        const val SENSORS = "SENSORS"
        const val ACTIONS = "ACTIONS"
    }

    // ¡NUEVO! Tipos de acciones para ActionReq
    object ActionType {
        const val EXEC_ACTION = "EXEC_ACTION"
    }

    object ControlEvent {
        const val START = "START"
        const val STOP = "STOP"
    }

    object AsyncNotify {
        const val TYPE_SESSION_ID = "session_id"
        const val TYPE_EMERGENCY_STOP = "EMERGENCY_STOP"

        const val DETAILS_ROBOT_LOST = "ROBOT_CONNECTION_LOST"
    }

    // ==========================================
    // RED Y HARDWARE
    // ==========================================
    object StatusCode {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
        const val INTERNAL_ERROR = 500
    }

    object CameraQuality {
        const val LOW = "low"
        const val MEDIUM = "medium"
        const val HIGH = "high"
    }
}