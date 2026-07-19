/**
 * @file Models.kt
 * @brief Definición de las estructuras de datos (Data Classes) del protocolo.
 * @details Contiene el mapeo exacto y fuertemente tipado de todos los mensajes JSON
 *          que se intercambian entre el backend (ROS 2/Python) y el frontend (Android/Kotlin).
 *          Utiliza kotlinx.serialization para la conversión automática.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject


/**
 * @class AlertData
 * @brief Estructura de datos utilizada por el ProtocolStateManager para generar avisos en la UI.
 * @property title Título breve y descriptivo de la alerta.
 * @property message Cuerpo principal detallado con el motivo de la alerta.
 */
data class AlertData(
    val title: String,
    val message: String
)

// ========================================================================
// 1. ESTRUCTURAS DE CABECERA Y ENVOLTORIO
// ========================================================================

/**
 * @interface Payload
 * @brief Interfaz sellada (Sealed Interface) que actúa como clase padre para todos los cuerpos de mensaje.
 */
sealed interface Payload

/**
 * @class MessageHeader
 * @brief Cabecera estándar obligatoria para todo mensaje del protocolo.
 * @property msgId Identificador único del mensaje para correlacionar peticiones y respuestas.
 * @property type Tipo de mensaje (ej. COMMAND_REQ, RESP, ASYNC_NOTIFY).
 * @property sessionId Identificador de la sesión lógica actual.
 * @property timestamp Marca de tiempo (Epoch) de la creación del mensaje.
 */
@Serializable
data class MessageHeader(
    @SerialName("msg_id") val msgId: Long,
    @SerialName("type") val type: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("timestamp") val timestamp: Double
)

/**
 * @class RobotMessage
 * @brief Envoltorio principal y universal de las comunicaciones.
 * @property header Cabecera del mensaje
 * @property payload Cuerpo dinámico del mensaje, analizado polimórficamente según el header.type.
 */
@Serializable
data class RobotMessage(
    @SerialName("header") val header: MessageHeader,
    @SerialName("payload") val payload: Payload
)

// ========================================================================
// 2. PAYLOADS DE PETICIÓN (Requests - Frontend a Backend)
// ========================================================================

/**
 * @class CommandReqPayload
 * @brief Petición para ejecutar comandos en el backend
 * @property action Acción solicitada (ej. connect, disconnect, shutdown).
 * @property param1 Parámetro opcional de configuración 1.
 * @property param2 Parámetro opcional de configuración 2.
 * @property param3 Parámetro booleano opcional.
 */
@Serializable
data class CommandReqPayload(
    @SerialName("action") val action: String,
    @SerialName("param1") val param1: String? = null,
    @SerialName("param2") val param2: String? = null,
    @SerialName("param3") val param3: Boolean? = null
): Payload

/**
 * @class QueryReqPayload
 * @brief Petición para consultar información estática.
 * @property resourceType El tipo de recurso que se quiere consultar.
 */
@Serializable
data class QueryReqPayload(
    @SerialName("resource_type") val resourceType: String
): Payload

/**
 * @class ActionReqPayload
 * @brief Petición para iniciar la ejecución de una acción pregrabada (PlayMotion).
 * @property type Tipo de acción.
 * @property target Nombre de la rutina que se debe reproducir en el robot.
 */
@Serializable
data class ActionReqPayload(
    @SerialName("type") val type: String,
    @SerialName("target") val target: String
): Payload

/**
 * @class StopActionReqPayload
 * @brief Petición de emergencia o interrupción voluntaria de una acción en curso.
 * @property type Tipo de acción a detener.
 * @property target Nombre de la rutina que se desea cancelar de inmediato.
 */
@Serializable
data class StopActionReqPayload(
    @SerialName("type") val type: String,
    @SerialName("target") val target: String
): Payload

/**
 * @class ControlModeReqPayload
 * @brief Petición para reclamar o liberar el control de los motores.
 * @property event Evento de control (START o STOP).
 * @property type Modalidad de control requerida (ej. TELEOP).
 * @property topic Topic de ROS 2 donde se aplicará la multiplexación (opcional).
 */
@Serializable
data class ControlModeReqPayload(
    @SerialName("event") val event: String,
    @SerialName("type") val type: String,
    @SerialName("topic") val topic: String? = null
): Payload

/**
 * @class ControlData
 * @brief Estructura interna con los valores cinemáticos o articulares.
 * @property v Velocidad lineal en metros/segundo.
 * @property w Velocidad angular en radianes/segundo.
 * @property jointName Nombre de la articulación específica.
 * @property jointValue Posición objetivo en radianes para la articulación.
 */
@Serializable
data class ControlData(
    @SerialName("v") val v: Float? = 0.0f,
    @SerialName("w") val w: Float? = 0.0f,
    @SerialName("joint_name") val jointName: String? = null,
    @SerialName("joint_value") val jointValue: Float? = null
)

/**
 * @class ControlReqPayload
 * @brief Petición de alta frecuencia que transporta comandos de movimiento en tiempo real.
 * @property data El paquete cinemático contenido en la estructura [ControlData].
 */
@Serializable
data class ControlReqPayload(
    @SerialName("data") val data: ControlData
): Payload

/**
 * @class StreamReqPayload
 * @brief Petición para abrir una tubería de datos continuos (Streaming de Sensores o Vídeo).
 * @property resource Familia del recurso solicitado (camera, sensors).
 * @property topic Tópico específico dentro del ecosistema ROS 2.
 * @property qualityLevel Perfil de compresión o calidad de envío deseado.
 */
@Serializable
data class StreamReqPayload(
    @SerialName("resource") val resource: String,
    @SerialName("topic") val topic: String? = null,
    @SerialName("quality_level") val qualityLevel: String? = null
): Payload

/**
 * @class StopStreamReqPayload
 * @brief Petición para cerrar una tubería de datos continuos específica.
 * @property resource Familia del recurso que se detiene.
 * @property topic Tópico específico para detener un sensor concreto sin afectar al resto.
 */
@Serializable
data class StopStreamReqPayload(
    @SerialName("resource") val resource: String,
    @SerialName("topic") val topic: String? = null
): Payload

/**
 * @class AsyncNotifyPayload
 * @brief Mensaje asíncrono disparado proactivamente por el servidor (ej. Pérdida de conexión física).
 * @property type Tipo de notificación.
 * @property details Información descriptiva del evento.
 * @property severity Nivel de urgencia o gravedad.
 */
@Serializable
data class AsyncNotifyPayload(
    @SerialName("type") val type: String,
    @SerialName("details") val details: String,
    @SerialName("severity") val severity: String? = null
): Payload

/**
 * @class ProtocolErrorPayload
 * @brief Mensaje emitido cuando el servidor rechaza o no comprende una solicitud.
 * @property errorCode Código numérico de error (estilo HTTP).
 * @property description Detalles técnicos del fallo.
 */
@Serializable
data class ProtocolErrorPayload(
    @SerialName("error_code") val errorCode: Int,
    @SerialName("description") val description: String
): Payload

/**
 * @class EmptyPayload
 * @brief Objeto vacío utilizado para los PINGs (Latidos de red) y los ACKs (Confirmaciones rápidas).
 */
@Serializable
class EmptyPayload(): Payload

// ========================================================================
// 3. PAYLOADS DE RESPUESTA (Responses - Backend a Frontend)
// ========================================================================

/**
 * @interface QueryDataResult
 * @brief Interfaz de agrupación para los diferentes resultados purificados de las consultas (Queries).
 */
sealed interface QueryDataResult

/** @brief Resultado de consultar las capacidades y configuración del robot. */
data class RobotInfoResult(val info: RobotCapabilitiesData) : QueryDataResult

/** @brief Resultado de consultar la lista de movimientos pregrabados disponibles. */
data class ActionListResult(val actions: List<String>) : QueryDataResult

/** @brief Resultado de consultar la topología de la red de ROS 2. */
data class NetworkInfoResult(val networkData: Map<String, List<String>>) : QueryDataResult

/** @brief Resultado de solicitar el inventario de sensores activos. */
data class SensorListResult(val sensors: List<SensorInfo>) : QueryDataResult

/** @brief Resultado de solicitar la información del ordenador anfitrión (Host). */
data class HostInfoResult(val telemetry: HostTelemetryData) : QueryDataResult

/**
 * @class QueryRespPayload
 * @brief Respuesta general a una petición de tipo QUERY_REQ.
 * @details La deserialización ocurre en dos fases: kotlinx extrae `rawData` como un objeto crudo,
 *          y el MessageCodec lo traduce a la variable transitoria `parsedData`.
 */
@Serializable
data class QueryRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null,
    @SerialName("data") val rawData: JsonElement? = null
): Payload {
    @Transient
    var parsedData: QueryDataResult? = null
}

/**
 * @class ActionFeedbackPayload
 * @brief Respuesta que informa sobre el progreso o finalización de una acción de movimiento en curso.
 */
@Serializable
data class ActionFeedbackPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null,
    @SerialName("done_exec") val doneExec: Boolean? = null,
    @SerialName("progress") val progress: Int? = null,
    @SerialName("status") val status: String? = null
): Payload

/**
 * @class StreamRespPayload
 * @brief Respuesta a la solicitud de un stream o paquete individual de un stream activo.
 * @property parsedSensorData Instancia pura de Kotlin inyectada por el traductor.
 */
@Serializable
data class StreamRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null,
    @SerialName("stream_data") val streamData: JsonObject? = null,
    @SerialName("stream_url") val streamUrl: String? = null
): Payload {
    @Transient
    var parsedSensorData: SensorStreamData? = null
}

/**
 * @class GenericRespPayload
 * @brief Respuesta comodín para confirmaciones de comandos simples sin datos adicionales.
 */
@Serializable
data class GenericRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null
): Payload

// ========================================================================
// 4. MODELOS PARA LA INFORMACIÓN DEL ROBOT (QueryResp -> ROBOT_INFO)
// ========================================================================

/**
 * @class RobotCapabilitiesData
 * @brief Contenedor principal del estado físico y hardware del robot.
 */
@Serializable
data class RobotCapabilitiesData(
    @SerialName("identity") val identity: IdentityData? = null,
    @SerialName("status") val status: StatusData? = null,
    @SerialName("capabilities") val capabilities: CapabilitiesData? = null
)

/**
 * @class IdentityData
 * @brief Datos de red e identificación del robot en la red local.
 */
@Serializable
data class IdentityData(
    @SerialName("hostname") val hostname: String,
    @SerialName("domain_id") val domainId: String
)

/**
 * @class StatusData
 * @brief Datos críticos en tiempo real sobre la energía y la seguridad del robot.
 */
@Serializable
data class StatusData(
    @SerialName("battery_pct") val batteryPct: Double?,
    @SerialName("e_stop_active") val eStopActive: Boolean?,
    @SerialName("is_charging") val isCharging: Boolean?
)

/**
 * @class CapabilitiesData
 * @brief Inventario de las capacidades motrices y sensoriales detectadas por el backend.
 */
@Serializable
data class CapabilitiesData(
    @SerialName("has_base") val hasBase: Boolean,
    @SerialName("cameras") val cameras: List<CameraDevice> = emptyList(),
    @SerialName("teleop_topics") val teleopTopics: List<String> = emptyList(),
    @SerialName("camera_topics") val cameraTopics: List<String> = emptyList(),
    @SerialName("has_manipulator") val hasManipulator: Boolean,
    @SerialName("has_head") val hasHead: Boolean,
    @SerialName("has_torso") val hasTorso: Boolean,
    @SerialName("has_gripper") val hasGripper: Boolean,
    @SerialName("has_imu") val hasImu: Boolean,
    @SerialName("has_odometry") val hasOdom: Boolean,
    @SerialName("has_lidar") val hasLidar: Boolean,
    @SerialName("has_nav") val hasNav: Boolean,
    @SerialName("has_moveit") val hasMoveit: Boolean,
    @SerialName("has_ft_sensor") val hasFtSensor: Boolean,
    @SerialName("has_play_motion") val hasPlayMotion: Boolean,
    @SerialName("controlable_joints") val controlableJoints: List<JointLimit>? = null
)

/**
 * @class JointLimit
 * @brief Restricciones físicas y estado actual de una articulación controlable.
 */
@Serializable
data class JointLimit(
    @SerialName("name") val name: String,
    @SerialName("min") val min: Float? = null,
    @SerialName("max") val max: Float? = null,
    @SerialName("current_value") val currentValue: Float? = null,
    @SerialName("is_actuated") val isActuated: Boolean = true
)

/**
 * @class CameraDevice
 * @brief Descriptor de un dispositivo de captura de vídeo disponible.
 */
@Serializable
data class CameraDevice(
    @SerialName("name") val name: String
)

// ========================================================================
// 5. MODELOS PARA LOS SENSORES Y EL STREAMING
// ========================================================================

/**
 * @class SensorInfo
 * @brief Identificador básico de un sensor detectado en la red ROS.
 */
@Serializable
data class SensorInfo(
    @SerialName("topic") val topic: String,
    @SerialName("type") val type: String
)

/**
 * @interface SensorData
 * @brief Interfaz para agrupar polimórficamente todas las lecturas puras de los sensores.
 */
sealed interface SensorData

/**
 * @class LaserScanData
 * @brief Representación matemática de una lectura de escáner LiDAR (sensor_msgs/LaserScan).
 */
@Serializable
data class LaserScanData(
    @SerialName("angle_min") val angleMin: Float,
    @SerialName("angle_max") val angleMax: Float,
    @SerialName("angle_increment") val angleIncrement: Float,
    @SerialName("range_min") val rangeMin: Float,
    @SerialName("range_max") val rangeMax: Float,
    @SerialName("ranges") val ranges: List<Float>
) : SensorData

/**
 * @class ImuData
 * @brief Datos de inercia, aceleración lineal y velocidad angular (sensor_msgs/Imu).
 */
@Serializable
data class ImuData(
    @SerialName("orientation") val orientation: Vector4,
    @SerialName("angular_velocity") val angularVelocity: Vector3,
    @SerialName("linear_acceleration") val linearAcceleration: Vector3
) : SensorData

/** @brief Vector de tres dimensiones utilizado en cálculos espaciales y físicas. */
@Serializable
data class Vector3(@SerialName("x") val x: Float, @SerialName("y") val y: Float, @SerialName("z") val z: Float)

/** @brief Vector de cuatro dimensiones, utilizado principalmente para representar Cuaterniones de orientación. */
@Serializable
data class Vector4(@SerialName("x") val x: Float, @SerialName("y") val y: Float, @SerialName("z") val z: Float, @SerialName("w") val w: Float)

/**
 * @class BatterySensorData
 * @brief Estado del subsistema de energía y voltaje del robot.
 */
@Serializable
data class BatterySensorData(
    @SerialName("voltage") val voltage: Float,
    @SerialName("percentage") val percentage: Float,
    @SerialName("power_supply_status") val powerSupplyStatus: Int
) : SensorData

/**
 * @class RangeSensorData
 * @brief Datos de un sensor de rango unidireccional, como un sonar o un sensor de infrarrojos (sensor_msgs/Range).
 */
@Serializable
data class RangeSensorData(
    @SerialName("range") val range: Float,
    @SerialName("min_range") val minRange: Float,
    @SerialName("max_range") val maxRange: Float,
    @SerialName("field_of_view") val fieldOfView: Float
) : SensorData

/**
 * @class PointCloud2Data
 * @brief Metadatos de una nube de puntos tridimensional obtenida por cámaras RGB-D (sensor_msgs/PointCloud2).
 */
@Serializable
data class PointCloud2Data(
    @SerialName("width") val width: Int,
    @SerialName("height") val height: Int,
    @SerialName("is_dense") val isDense: Boolean,
    @SerialName("note") val note: String
) : SensorData

// --- SENSORES UNIVERSALES ---

/** @brief Vector de dos dimensiones utilizado en planos cartesianos simples. */
@Serializable
data class Vector2(@SerialName("x") val x: Float, @SerialName("y") val y: Float)

/**
 * @class OdometryData
 * @brief Datos de odometría que calculan la estimación de posición del robot basada en sus ruedas (nav_msgs/Odometry).
 */
@Serializable
data class OdometryData(
    @SerialName("position") val position: Vector2,
    @SerialName("linear_velocity") val linearVelocity: Float,
    @SerialName("angular_velocity") val angularVelocity: Float
) : SensorData

/**
 * @class NavSatFixData
 * @brief Lectura de posicionamiento global por satélite o GPS (sensor_msgs/NavSatFix).
 */
@Serializable
data class NavSatFixData(
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("altitude") val altitude: Double,
    @SerialName("status") val status: Int
) : SensorData

/**
 * @class WrenchData
 * @brief Datos de un sensor de fuerza-par, habitual en manipuladores y muñecas robóticas (geometry_msgs/Wrench).
 */
@Serializable
data class WrenchData(
    @SerialName("force") val force: Vector3,
    @SerialName("torque") val torque: Vector3
) : SensorData

/**
 * @class TemperatureData
 * @brief Lectura de temperatura del entorno o de un componente interno (sensor_msgs/Temperature).
 */
@Serializable
data class TemperatureData(
    @SerialName("temperature") val temperature: Float
) : SensorData

/**
 * @class SensorStreamData
 * @brief Objeto compuesto que unifica la identidad de un sensor y su lectura pura.
 * @details Esta clase NO ES serializable; es el resultado final del códec que consume de forma nativa la Interfaz Gráfica (UI).
 */
data class SensorStreamData(
    val topic: String,
    val type: String,
    val data: SensorData
)

// ========================================================================
// 6. MODELOS DE ESTADO DEL SISTEMA Y LA INTERFAZ (UI/HOST)
// ========================================================================

/**
 * @class HostTelemetryData
 * @brief Datos de rendimiento y telemetría de la máquina anfitrión (PC / Server).
 * @details Representa la información solicitada en la Sala de Espera para verificar el estado
 *          del entorno ROS 2 y los recursos del ordenador antes de conectarse al robot.
 */
@Serializable
data class HostTelemetryData(
    @SerialName("cpu_pct") val cpuPct: Double?,
    @SerialName("ram_used_gb") val ramUsedGb: Double?,
    @SerialName("ram_total_gb") val ramTotalGb: Double?,
    @SerialName("ram_pct") val ramPct: Double?,
    @SerialName("temp_c") val tempC: Double?,
    @SerialName("ros_distro") val rosDistro: String?,
    @SerialName("ros_domain_id") val rosDomainId: String?,
    @SerialName("current_dds") val currentDds: String?,
    @SerialName("available_dds") val availableDds: List<String>?,
    @SerialName("use_discovery") val useDiscovery: Boolean?
)