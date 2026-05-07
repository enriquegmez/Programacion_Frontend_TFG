package com.enrique.tiago_app.utils

/**
 * AppConstants
 * Centraliza todos los valores fijos del protocolo y la red.
 * Debe ser un reflejo exacto de constants.py en el Backend.
 */
object AppConstants {

    // 1. Configuración de Red
    // "10.0.2.2" es la IP especial para que el emulador Android vea el localhost de tu PC.
    // Cámbiala por la IP de tu PC en la red local si usas un móvil físico.
    const val DEFAULT_SERVER_IP = "192.168.68.87"
    const val DEFAULT_SERVER_PORT = 8765

    // 2. Tipos de Mensaje (MsgType)
    object MsgType {
        const val COMMAND_REQ = "COMMAND_REQ"
        const val CONTROL_MODE_REQ = "CONTROL_MODE_REQ"
        const val CONTROL_REQ = "CONTROL_REQ"
        const val RESP = "RESP"
        const val ASYNC_NOTIFY = "ASYNC_NOTIFY"
        const val PROTOCOL_ERROR = "PROTOCOL_ERROR"
        const val PING_REQ = "PING_REQ"

        // Reservados para el futuro
        const val QUERY_REQ = "QUERY_REQ"
        const val STREAM_DATA = "STREAM_DATA"
    }

    // 3. Tipos de Respuesta (resp_type dentro del payload de un RESP)
    object RespType {
        const val COMMAND_RESP = "COMMAND_RESP"
        const val CONTROL_MODE_RESP = "CONTROL_MODE_RESP"
        const val ASYNC_NOTIFY = "ASYNC_NOTIFY"
    }

    // 4. Acciones del Gestor de Conexión (Action)
    object Action {
        const val CONNECT = "CONNECT"
        const val DISCONNECT = "DISCONNECT"
        const val END = "END"
    }

    // 5. Eventos para el Modo de Control (ControlEvent)
    object ControlEvent {
        const val START = "START"
        const val STOP = "STOP"
    }

    // 6. Códigos de Estado (StatusCode HTTP/WebSocket)
    object StatusCode {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
        const val INTERNAL_ERROR = 500
    }

    // 7. Notificaciones Asíncronas (Watchdog y Sesión)
    object AsyncDetails {
        const val ROBOT_CONNECTION_LOST = "ROBOT_CONNECTION_LOST"
        const val SESSION_ASSIGNED_PREFIX = "SESSION_ASSIGNED"
    }

    // 8. Constantes de Robótica y Hardware
    object Robot {
        const val DEFAULT_CMD_VEL_TOPIC = "cmd_vel"

        // Límites máximos permitidos (Reflejo del safety_filter.py)
        const val MAX_LINEAR_V = 0.5f  // m/s
        const val MAX_ANGULAR_W = 1.0f // rad/s

        // Intervalo de publicación del Joystick hacia el WebSocket
        const val JOYSTICK_PUBLISH_INTERVAL_MS = 100L // 10Hz (10 mensajes por segundo)
    }
}