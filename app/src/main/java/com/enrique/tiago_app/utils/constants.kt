package com.enrique.tiago_app.utils

/**
 * AppConstants
 * Centraliza todos los valores fijos del protocolo y la red.
 * Debe ser un reflejo exacto de constants.py en el Backend.
 */
object AppConstants {

    // 1. Configuración de Red
    // "10.0.2.2" es la IP especial para que el emulador vea el localhost de tu PC.
    const val DEFAULT_SERVER_IP = "192.168.68.87"
    const val DEFAULT_SERVER_PORT = 8765

    // 2. Tipos de Mensaje (MsgType)
    object MsgType {
        const val COMMAND_REQ = "COMMAND_REQ"
        const val RESP = "RESP"
        const val PROTOCOL_ERROR = "PROTOCOL_ERROR"

        // Reservados para el futuro
        const val QUERY_REQ = "QUERY_REQ"
        const val STREAM_DATA = "STREAM_DATA"
    }

    // 3. Tipos de Respuesta (resp_type dentro del payload)
    object RespType {
        const val COMMAND_RESP = "COMMAND_RESP"
    }

    // 4. Acciones (Action)
    object Action {
        const val CONNECT = "connect"
    }

    // 5. Códigos de Estado (StatusCode)
    object StatusCode {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val INTERNAL_ERROR = 500
    }
}