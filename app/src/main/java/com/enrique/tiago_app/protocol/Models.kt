package com.enrique.tiago_app.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ==========================================
// ESTRUCTURAS DE CABECERA Y ENVOLTORIO
// ==========================================

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
    @SerialName("payload") val payload: JsonElement
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
)

@Serializable
data class QueryReqPayload(
    @SerialName("resource_type") val resourceType: String
)

@Serializable
data class ActionReqPayload(
    @SerialName("type") val type: String,
    @SerialName("target") val target: String
)

@Serializable
data class ControlModeReqPayload(
    @SerialName("event") val event: String,
    @SerialName("type") val type: String,
    @SerialName("topic") val topic: String? = null
)

@Serializable
data class ControlData(
    @SerialName("v") val v: Float? = 0.0f,
    @SerialName("w") val w: Float? = 0.0f,
    @SerialName("joints") val joints: List<Float> = emptyList()
)

@Serializable
data class ControlReqPayload(
    @SerialName("data") val data: ControlData
)

@Serializable
data class StreamReqPayload(
    @SerialName("resource") val resource: String,
    @SerialName("frequency") val frequency: Int,
    @SerialName("state") val state: String
)

@Serializable
data class StopStreamReqPayload(
    @SerialName("resource") val resource: String
)

@Serializable
data class AsyncNotifyPayload(
    @SerialName("type") val type: String,
    @SerialName("details") val details: String,
    @SerialName("severity") val severity: String? = null
)

@Serializable
data class ProtocolErrorPayload(
    @SerialName("error_code") val errorCode: Int,
    @SerialName("description") val description: String
)

// Las data classes no pueden estar vacías en Kotlin,
// así que usamos una clase serializable simple o un objeto.
@Serializable
class EmptyPayload()

// ==========================================
// PAYLOADS DE RESPUESTA (Responses)
// ==========================================

@Serializable
data class CommandRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null
)

@Serializable
data class QueryRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null,
    @SerialName("data") val data: List<String>? = null
)

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
)

@Serializable
data class StreamRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null,
    @SerialName("stream_data") val streamData: JsonObject? = null,
    @SerialName("stream_url") val streamUrl: String? = null
)

@Serializable
data class GenericRespPayload(
    @SerialName("success") val success: Boolean,
    @SerialName("code") val code: Int,
    @SerialName("resp_type") val respType: String,
    @SerialName("details") val details: String? = null,
    @SerialName("resp_data") val respData: JsonObject? = null
)