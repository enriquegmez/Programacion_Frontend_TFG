/**
 * @file Constants.kt
 * @brief Diccionario centralizado de constantes, estados y reglas del protocolo.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.utils

/**
 * @object AppConstants
 * @brief Agrupa todas las variables estáticas y literales de la aplicación para evitar errores tipográficos.
 */
object AppConstants {

    /** @brief Dirección IP asignada por defecto a la interfaz de red del robot o servidor. */
    const val DEFAULT_SERVER_IP = "192.168.68.83"

    /** @brief Puerto de red estándar donde escucha el servidor WebSocket de Ktor/Python. */
    const val DEFAULT_SERVER_PORT = 8765

    // ========================================================================
    // 1. ESTADOS DE LA MÁQUINA (UI y Lógica)
    // ========================================================================

    /**
     * @brief Estados pertenecientes a la conexión a nivel Global y de Ciclo de Vida.
     */
    object GlobalState {
        const val IDLE = "IDLE" // Interfaz inactiva o desconectada de la red TCP/IP
        const val ESPERANDO_CONEXION_BACKEND = "ESPERANDO_CONEXION_BACKEND"
        const val CONEXION_BACKEND = "CONEXION_BACKEND"
        const val ESPERANDO_INICIO_SESION = "ESPERANDO_INICIO_SESION"
        const val SESION_INICIADA = "SESION_INICIADA"
        const val ESPERANDO_CIERRE_SESION = "ESPERANDO_CIERRE_SESION"
        const val ESPERANDO_DESCONEXION_BACKEND = "ESPERANDO_DESCONEXION_BACKEND"
        const val ESPERANDO_RECIBIR_INFORMACION_UNICA = "ESPERANDO_RECIBIR_INFORMACION_UNICA"
    }

    /**
     * @brief Estados paralelos dedicados en exclusiva al control cinemático.
     */
    object MovementState {
        const val IDLE = "IDLE"
        const val ESPERANDO_PERMISO_ENVIO_INFO = "ESPERANDO_PERMISO_ENVIO_INFO"
        const val ENVIANDO_INFO = "ENVIANDO_INFO"
        const val ESPERANDO_TERMINAR_ENVIO_INFO = "ESPERANDO_TERMINAR_ENVIO_INFO"
        const val ESPERANDO_EJECUTAR_ACCION = "ESPERANDO_EJECUTAR_ACCION"
        const val ESPERANDO_DETENER_ACCION = "ESPERANDO_DETENER_ACCION"
    }

    /**
     * @brief Estados paralelos dedicados a la recepción de flujos continuos (Cámaras y Sensores).
     */
    object MonitorState {
        const val IDLE = "IDLE"
        const val ESPERANDO_RECIBIR_STREAM = "ESPERANDO_RECIBIR_STREAM"
        const val RECIBIENDO_STREAM = "RECIBIENDO_STREAM"
        const val ESPERANDO_DEJAR_DE_RECIBIR_STREAM = "ESPERANDO_DEJAR_DE_RECIBIR_STREAM"
    }

    // ========================================================================
    // 2. PROTOCOLO: Identificadores de Cabecera (Header -> type)
    // ========================================================================

    /**
     * @brief Categorías de mensajes admitidos en la cabecera JSON (Tipos primarios).
     */
    object MsgType {
        const val COMMAND_REQ = "COMMAND_REQ"         // Órdenes de sistema
        const val QUERY_REQ = "QUERY_REQ"             // Peticiones de datos
        const val ACTION_REQ = "ACTION_REQ"           // Ejecución de PlayMotion
        const val STOP_ACTION_REQ = "STOP_ACTION_REQ" // Parada de PlayMotion
        const val CONTROL_MODE_REQ = "CONTROL_MODE_REQ"// Permiso para usar motores
        const val CONTROL_REQ = "CONTROL_REQ"         // Envío de métricas de joystick (Alta frecuencia)
        const val STREAM_REQ = "STREAM_REQ"           // Apertura de flujos
        const val STOP_STREAM_REQ = "STOP_STREAM_REQ" // Cierre de flujos
        const val RESP = "RESP"                       // Respuesta general del servidor
        const val ASYNC_NOTIFY = "ASYNC_NOTIFY"       // Eventos asíncronos iniciados por el backend
        const val PROTOCOL_ERROR = "PROTOCOL_ERROR"   // Error a nivel de red o parseo
        const val PING_REQ = "PING_REQ"               // Latido de red (Heartbeat) enviado por el móvil
        const val ACK = "ACK"                         // Confirmación rápida (Acknowledge) de recepción
    }

    // ========================================================================
    // 3. PROTOCOLO: Tipos de Respuesta (Payload -> resp_type)
    // ========================================================================

    /**
     * @brief Subcategorías de respuesta enviadas por el servidor para enrutar el deserializador.
     */
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

    // ========================================================================
    // 4. PROTOCOLO: Parámetros y Valores Internos
    // ========================================================================

    /**
     * @brief Acciones de sistema enviadas a través de COMMAND_REQ.
     * @note Obligatorio en minúsculas por exigencias de validación del backend JSON Schema.
     */
    object Action {
        const val CONNECT = "connect"           // Conectar a nodos ROS
        const val DISCONNECT = "disconnect"     // Desconectar de nodos ROS
        const val CHANGE_VARS = "change_vars"   // Modificar .env (DDS, Dominio)
        const val REBOOT = "reboot"             // Reiniciar servidor Host
        const val SHUTDOWN = "shutdown"         // Apagar servidor Host
        const val END = "end"                   // Cerrar túnel WebSocket
    }

    /**
     * @brief Recursos que se pueden consultar al sistema mediante QUERY_REQ.
     */
    object Resource {
        const val HOST_INFO = "HOST_INFO"       // Telemetría del Servidor/PC
        const val ROBOT_INFO = "ROBOT_INFO"     // Capacidades del robot
        const val TELEOP = "TELEOP"
        const val CAMERAS = "CAMERAS"
        const val MOVEMENTS = "MOVEMENTS"       // Lista de acciones PlayMotion
        const val TOPICS = "TOPICS"             // Mapa de red ROS 2
        const val SERVICES = "SERVICES"
        const val ACTIONS = "ACTIONS"
        const val SENSORS = "SENSORS"           // Lista de sensores de hardware
    }

    /**
     * @brief Identificadores de sensores de ROS 2 que la aplicación es capaz de renderizar.
     */
    object SensorType {
        const val LASER_SCAN = "LaserScan"
        const val IMU = "Imu"
        const val BATTERY = "BatteryState"
        const val RANGE = "Range"
        const val POINT_CLOUD2 = "PointCloud2"
        const val ODOMETRY = "Odometry"
        const val NAV = "NavSatFix"
        const val WRENCH = "Wrench"
        const val TEMPERATURE = "Temperature"
    }

    /**
     * @brief Modalidades para la solicitud de control motriz (ControlModeReq).
     */
    object ControlType {
        const val TELEOP = "TELEOP" // Multiplexación de la base (Ruedas)
        const val JOINT = "JOINT"   // Multiplexación de articulación individual (ej. Torso)
    }

    /**
     * @brief Identificadores de tipo de acción en ejecución.
     */
    object ActionType {
        const val EXEC_ACTION = "EXEC_ACTION"
    }

    /**
     * @brief Peticiones de ciclo de vida para el uso exclusivo del joystick.
     */
    object ControlEvent {
        const val START = "START"
        const val STOP = "STOP"
    }

    /**
     * @brief Identificadores internos para eventos disparados por el Watchdog del servidor.
     */
    object AsyncNotify {
        const val TYPE_SESSION_ID = "session_id"
        const val TYPE_EMERGENCY_STOP = "EMERGENCY_STOP"
        const val DETAILS_ROBOT_LOST = "ROBOT_CONNECTION_LOST" // Caída física de los nodos
    }

    // ========================================================================
    // 5. RED Y CONFIGURACIONES DE HARDWARE
    // ========================================================================

    /**
     * @brief Códigos de estado tipo HTTP para evaluar el resultado de una operación.
     */
    object StatusCode {
        const val OK = 200
        const val BAD_REQUEST = 400
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
        const val METHOD_NOT_ALLOWED = 405
        const val INTERNAL_ERROR = 500
    }

    /**
     * @brief Perfiles de compresión para reducir el ancho de banda al solicitar flujos de vídeo.
     */
    object CameraQuality {
        const val LOW = "low"
        const val MEDIUM = "medium"
        const val HIGH = "high"
    }
}