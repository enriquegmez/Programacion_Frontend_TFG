package com.enrique.tiago_app.protocol

import android.util.Log
import com.enrique.tiago_app.utils.AppConstants
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicLong

/**
 * MessageCodec (El Traductor - Frontera JSON)
 */
class MessageCodec {

    private val tag = "TIAGO_ProtocolDirector"

    @OptIn(ExperimentalSerializationApi::class)
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private val msgIdCounter = AtomicLong(1L)

    // Cambia la función getNextMsgId para que sea segura:
    fun getNextMsgId(): Long {
        return msgIdCounter.getAndIncrement()
    }

    fun decode(rawString: String): RobotMessage {
        return try {
            val jsonObj = jsonFormat.parseToJsonElement(rawString).jsonObject
            val header = jsonFormat.decodeFromJsonElement<MessageHeader>(jsonObj["header"]!!)
            val payloadJson = jsonObj["payload"]!!

            val payloadObj: Payload = when (header.type) {
                AppConstants.MsgType.COMMAND_REQ -> jsonFormat.decodeFromJsonElement<CommandReqPayload>(payloadJson)
                AppConstants.MsgType.QUERY_REQ -> jsonFormat.decodeFromJsonElement<QueryReqPayload>(payloadJson)
                AppConstants.MsgType.ACTION_REQ -> jsonFormat.decodeFromJsonElement<ActionReqPayload>(payloadJson)
                AppConstants.MsgType.STOP_ACTION_REQ -> jsonFormat.decodeFromJsonElement<StopActionReqPayload>(payloadJson)
                AppConstants.MsgType.CONTROL_MODE_REQ -> jsonFormat.decodeFromJsonElement<ControlModeReqPayload>(payloadJson)
                AppConstants.MsgType.CONTROL_REQ -> jsonFormat.decodeFromJsonElement<ControlReqPayload>(payloadJson)
                AppConstants.MsgType.STREAM_REQ -> jsonFormat.decodeFromJsonElement<StreamReqPayload>(payloadJson)
                AppConstants.MsgType.STOP_STREAM_REQ -> jsonFormat.decodeFromJsonElement<StopStreamReqPayload>(payloadJson)
                AppConstants.MsgType.ASYNC_NOTIFY -> jsonFormat.decodeFromJsonElement<AsyncNotifyPayload>(payloadJson)
                AppConstants.MsgType.PROTOCOL_ERROR -> jsonFormat.decodeFromJsonElement<ProtocolErrorPayload>(payloadJson)
                AppConstants.MsgType.PING_REQ, AppConstants.MsgType.ACK -> EmptyPayload()
                AppConstants.MsgType.RESP -> {
                    val respType = payloadJson.jsonObject["resp_type"]?.jsonPrimitive?.content
                    when (respType) {
                        AppConstants.RespType.QUERY_RESP -> {
                            // 1. Decodificamos la parte genérica (success, code, details)
                            val resp = jsonFormat.decodeFromJsonElement<QueryRespPayload>(payloadJson)

                            // 2. ¡MAGIA DETECTIVE! Inspeccionamos el campo "data" aquí mismo
                            val dataElement = payloadJson.jsonObject["data"]
                            resp.parsedData = when (dataElement) {
                                is JsonArray -> ActionListResult(jsonFormat.decodeFromJsonElement(dataElement))
                                is JsonObject -> {
                                    // Comprobamos si tiene las claves típicas de la radiografía
                                    if (dataElement.containsKey("capabilities") || dataElement.containsKey("identity") || dataElement.containsKey("status")) {
                                        RobotInfoResult(jsonFormat.decodeFromJsonElement(dataElement))
                                    } else {
                                        // Si no las tiene, asumimos que es nuestro nuevo mapa de red Map<String, List<String>>
                                        NetworkInfoResult(jsonFormat.decodeFromJsonElement(dataElement))
                                    }
                                }
                                else -> null
                            }
                            resp // Devolvemos el objeto ya traducido a puro Kotlin
                        }
                        AppConstants.RespType.ACTION_FEEDBACK -> jsonFormat.decodeFromJsonElement<ActionFeedbackPayload>(payloadJson)
                        AppConstants.RespType.STREAM_RESP -> jsonFormat.decodeFromJsonElement<StreamRespPayload>(payloadJson)
                        else -> jsonFormat.decodeFromJsonElement<GenericRespPayload>(payloadJson)
                    }
                }
                else -> EmptyPayload()
            }

            RobotMessage(header = header, payload = payloadObj)

        } catch (e: Exception) {
            Log.e(tag, "Error de formato JSON: ${e.message}")
            buildInternalErrorMsg(
                AppConstants.StatusCode.BAD_REQUEST,
                "Invalid JSON format or missing fields: ${e.message}"
            )
        }
    }

    fun encode(msg: RobotMessage): String {
        val finalHeader = msg.header.copy(
            timestamp = System.currentTimeMillis() / 1000.0
        )

        val headerJson = jsonFormat.encodeToJsonElement(finalHeader)

        val payloadJson = when (val p = msg.payload) {
            is CommandReqPayload -> jsonFormat.encodeToJsonElement(p)
            is QueryReqPayload -> jsonFormat.encodeToJsonElement(p)
            is ActionReqPayload -> jsonFormat.encodeToJsonElement(p)
            is StopActionReqPayload -> jsonFormat.encodeToJsonElement(p)
            is ControlModeReqPayload -> jsonFormat.encodeToJsonElement(p)
            is ControlReqPayload -> jsonFormat.encodeToJsonElement(p)
            is StreamReqPayload -> jsonFormat.encodeToJsonElement(p)
            is StopStreamReqPayload -> jsonFormat.encodeToJsonElement(p)
            is AsyncNotifyPayload -> jsonFormat.encodeToJsonElement(p)
            is ProtocolErrorPayload -> jsonFormat.encodeToJsonElement(p)
            is QueryRespPayload -> jsonFormat.encodeToJsonElement(p)
            is ActionFeedbackPayload -> jsonFormat.encodeToJsonElement(p)
            is StreamRespPayload -> jsonFormat.encodeToJsonElement(p)
            is GenericRespPayload -> jsonFormat.encodeToJsonElement(p)
            is EmptyPayload -> buildJsonObject {}
            else -> {
                Log.w(tag, "Tipo de payload no reconocido al codificar: ${p::class.simpleName}")
                buildJsonObject {}
            }
        }

        val finalJsonObj = buildJsonObject {
            put("header", headerJson)
            put("payload", payloadJson)
        }

        val finalJsonString = jsonFormat.encodeToString(finalJsonObj)
        if (finalHeader.type != AppConstants.MsgType.PING_REQ && finalHeader.type != AppConstants.MsgType.CONTROL_REQ)
            Log.d(tag, "Codificado (OUT): $finalJsonString")
        return finalJsonString
    }

    private fun buildInternalErrorMsg(code: Int, description: String): RobotMessage {
        val header = MessageHeader(
            msgId = -1L,
            type = AppConstants.MsgType.PROTOCOL_ERROR,
            sessionId = "",
            timestamp = System.currentTimeMillis() / 1000.0
        )
        val errorPayload = ProtocolErrorPayload(errorCode = code, description = description)
        return RobotMessage(header = header, payload = errorPayload)
    }
}