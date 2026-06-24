package com.enrique.tiago_app.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ==========================================
// ESTRUCTURAS DE CABECERA Y ENVOLTORIO
// ==========================================

sealed interface Payload

@Serializable
data class MessageHeader(
    @SerialName("msg_id") val msgId: Long,
    @SerialName("type") val type: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("timestamp") val timestamp: Double
)

@Serializable
data class RobotMessage(
    @SerialName("header") val header: MessageHeader,
    // Usamos JsonElement para permitir deserialización dinámica según el header.type
    @SerialName("payload") val payload: Payload
)

// ==========================================
// PAYLOADS DE PETICIÓN (Requests)
// ==========================================

@Serializable
data class CommandReqPayload(
    @SerialName("action") val action: String,
    @SerialName("param1") val param1: String? = null,
    @SerialName("param2") val param2: String? = null,
    @SerialName("param3") val param3: Boolean? = null
): Payload

@Serializable
data class QueryReqPayload(
    @SerialName("resource_type") val resourceType: String
): Payload

@Serializable
data class ActionReqPayload(
    @SerialName("type") val type: String,
    @SerialName("target") val target: String
): Payload

// ¡NUEVO! Payload para detener la acción
@Serializable
data class StopActionReqPayload(
    @SerialName("type") val type: String,
    @SerialName("target") val target: String
): Payload
@Serializable
data class ControlModeReqPayload(
    @SerialName("event") val event: String,
    @SerialName("type") val type: String,
    @SerialName("topic") val topic: String? = null
): Payload

@Serializable
data class ControlData(
    @SerialName("v") val v: Float? = 0.0f,
    @SerialName("w") val w: Float? = 0.0f,
    @SerialName("joint_name") val jointName: String? = null,
    @SerialName("joint_value") val jointValue: Float? = null
)

@Serializable
data class ControlReqPayload(
    @SerialName("data") val data: ControlData
): Payload

@Serializable
data class StreamReqPayload(
    @SerialName("resource") val resource: String,
    @SerialName("topic") val topic: String? = null,
    @SerialName("quality_level") val qualityLevel: String? = null
): Payload

@Serializable
data class StopStreamReqPayload(
    @SerialName("resource") val resource: String,
    @SerialName("topic") val topic: String? = null // ¡NUEVO! Para poder parar un sensor específico
): Payload

@Serializable
data class AsyncNotifyPayload(
    @SerialName("type") val type: String,
    @SerialName("details") val details: String,
    @SerialName("severity") val severity: String? = null
): Payload

@Serializable
data class ProtocolErrorPayload(
    @SerialName("error_code") val errorCode: Int,
    @SerialName("description") val description: String
): Payload

// Las data classes no pueden estar vacías en Kotlin,
// así que usamos una clase serializable simple o un objeto.
@Serializable
class EmptyPayload(): Payload

// ==========================================
// PAYLOADS DE RESPUESTA (Responses)
// ==========================================
sealed interface QueryDataResult
data class RobotInfoResult(val info: RobotCapabilitiesData) : QueryDataResult
data class ActionListResult(val actions: List<String>) : QueryDataResult
data class NetworkInfoResult(val networkData: Map<String, List<String>>) : QueryDataResult
data class SensorListResult(val sensors: List<SensorInfo>) : QueryDataResult // ¡NUEVO!
data class HostInfoResult(val telemetry: HostTelemetryData) : QueryDataResult // ¡NUEVO!

@Serializable
data class QueryRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null,
    // ¡MODIFICADO! Usamos JsonElement porque el backend puede enviar un Objeto (ROBOT_INFO) o un Array de Strings (ACTIONS)
    @SerialName("data") val rawData: JsonElement? = null
): Payload {
    // @Transient hace que la librería JSON lo ignore por completo.
    // Es una variable normal de Kotlin que rellenaremos desde el Traductor.
    @Transient
    var parsedData: QueryDataResult? = null
}

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
    // ¡NUEVO! Variable oculta que rellenará el traductor con clases puras de Kotlin
    @Transient
    var parsedSensorData: SensorStreamData? = null
}

@Serializable
data class GenericRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null
): Payload


// ==========================================
// MODELOS PARA LA RADIOGRAFÍA DEL ROBOT (QueryResp -> ROBOT_INFO)
// ==========================================

@Serializable
data class RobotCapabilitiesData(
    @SerialName("identity") val identity: IdentityData? = null,
    @SerialName("status") val status: StatusData? = null,
    @SerialName("capabilities") val capabilities: CapabilitiesData? = null
)

@Serializable
data class IdentityData(
    @SerialName("hostname") val hostname: String,
    @SerialName("domain_id") val domainId: String
)

@Serializable
data class StatusData(
    @SerialName("battery_pct") val batteryPct: Double?,
    @SerialName("e_stop_active") val eStopActive: Boolean?,
    @SerialName("is_charging") val isCharging: Boolean?
)

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

@Serializable
data class JointLimit(
    @SerialName("name") val name: String,
    @SerialName("min") val min: Float,
    @SerialName("max") val max: Float,
    @SerialName("current_value") val currentValue: Float? = null // ¡NUEVO! Posición real
)

@Serializable
data class CameraDevice(
    @SerialName("name") val name: String
)

// ==========================================
// MODELOS PARA LOS SENSORES Y EL STREAMING
// ==========================================

@Serializable
data class SensorInfo(
    @SerialName("topic") val topic: String,
    @SerialName("type") val type: String
)

// Interfaz pura para agrupar los datos de los sensores
sealed interface SensorData

@Serializable
data class LaserScanData(
    @SerialName("angle_min") val angleMin: Float,
    @SerialName("angle_max") val angleMax: Float,
    @SerialName("angle_increment") val angleIncrement: Float,
    @SerialName("range_min") val rangeMin: Float,
    @SerialName("range_max") val rangeMax: Float,
    @SerialName("ranges") val ranges: List<Float>
) : SensorData

@Serializable
data class ImuData(
    @SerialName("orientation") val orientation: Vector4,
    @SerialName("angular_velocity") val angularVelocity: Vector3,
    @SerialName("linear_acceleration") val linearAcceleration: Vector3
) : SensorData

@Serializable
data class Vector3(@SerialName("x") val x: Float, @SerialName("y") val y: Float, @SerialName("z") val z: Float)
@Serializable
data class Vector4(@SerialName("x") val x: Float, @SerialName("y") val y: Float, @SerialName("z") val z: Float, @SerialName("w") val w: Float)

@Serializable
data class BatterySensorData(
    @SerialName("voltage") val voltage: Float,
    @SerialName("percentage") val percentage: Float,
    @SerialName("power_supply_status") val powerSupplyStatus: Int
) : SensorData

@Serializable
data class RangeSensorData(
    @SerialName("range") val range: Float,
    @SerialName("min_range") val minRange: Float,
    @SerialName("max_range") val maxRange: Float,
    @SerialName("field_of_view") val fieldOfView: Float
) : SensorData

@Serializable
data class PointCloud2Data(
    @SerialName("width") val width: Int,
    @SerialName("height") val height: Int,
    @SerialName("is_dense") val isDense: Boolean,
    @SerialName("note") val note: String
) : SensorData

// ==========================================
// ¡NUEVOS! MODELOS DE SENSORES UNIVERSALES
// ==========================================

@Serializable
data class Vector2(@SerialName("x") val x: Float, @SerialName("y") val y: Float)

@Serializable
data class OdometryData(
    @SerialName("position") val position: Vector2,
    @SerialName("linear_velocity") val linearVelocity: Float,
    @SerialName("angular_velocity") val angularVelocity: Float
) : SensorData

@Serializable
data class NavSatFixData(
    // Usamos Double porque las coordenadas GPS requieren muchísima precisión
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("altitude") val altitude: Double,
    @SerialName("status") val status: Int
) : SensorData

@Serializable
data class WrenchData(
    @SerialName("force") val force: Vector3,
    @SerialName("torque") val torque: Vector3
) : SensorData

@Serializable
data class TemperatureData(
    @SerialName("temperature") val temperature: Float
) : SensorData

// El objeto final que leerá el ViewModel (¡100% Kotlin puro!)
data class SensorStreamData(
    val topic: String,
    val type: String,
    val data: SensorData
)

// ¡NUEVO! Data class para atrapar la telemetría del PC en la Sala de Espera
@Serializable
data class HostTelemetryData(
    @SerialName("cpu_pct") val cpuPct: Double?,        // Ahora pueden ser nulos
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