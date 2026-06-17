package com.enrique.tiago_app.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
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
    @SerialName("joints") val joints: List<Float> = emptyList()
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
    @SerialName("resource") val resource: String
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

@Serializable
data class QueryRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null,
    // ¡MODIFICADO! Ahora mapea directamente la radiografía completa del robot
    @SerialName("data") val data: RobotCapabilitiesData? = null
): Payload

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
): Payload

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
    @SerialName("battery_pct") val batteryPct: Double,
    @SerialName("e_stop_active") val eStopActive: Boolean
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
    @SerialName("has_moveit") val hasMoveit: Boolean
)

@Serializable
data class CameraDevice(
    @SerialName("name") val name: String
)