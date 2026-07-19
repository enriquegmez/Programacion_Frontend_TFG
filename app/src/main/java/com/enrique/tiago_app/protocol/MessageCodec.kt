/**
 * @file MessageCodec.kt
 * @brief Capa de traducción y serialización de datos.
 * @details Actúa como un conversor bidireccional. Transforma los flujos de texto JSON 
 *          recibidos por la red en estructuras de datos nativas (Data Classes de Kotlin)
 *          y viceversa. Garantiza la seguridad de tipos para el resto de la aplicación.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.protocol

import android.util.Log
import com.enrique.tiago_app.utils.AppConstants
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicLong

/**
 * @class MessageCodec
 * @brief Motor principal de codificación y decodificación del protocolo de comunicaciones.
 */
class MessageCodec {

    private val tag = "MessageCodec"

    /**
     * @property jsonFormat Configurador estricto del motor de serialización (kotlinx.serialization).
     * Ignora claves desconocidas para evitar bloqueos si el servidor envía campos extra en el futuro,
     * e incluye valores por defecto.
     */
    @OptIn(ExperimentalSerializationApi::class)
    private val jsonFormat = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * @property msgIdCounter Contador para asegurar que cada mensaje saliente tenga un ID único,
     * incluso si múltiples hilos intentan enviar mensajes al mismo tiempo.
     */
    private val msgIdCounter = AtomicLong(1L)

    /**
     * @brief Genera un identificador numérico único y correlativo para cada petición.
     * @details Utiliza operaciones atómicas (Thread-Safe) para evitar condiciones de carrera.
     * @return Siguiente número de ID disponible.
     */
    fun getNextMsgId(): Long {
        return msgIdCounter.getAndIncrement()
    }

    /**
     * @brief Deserializa una cadena JSON cruda convirtiéndola en un objeto RobotMessage funcional.
     * @details Analiza primero la cabecera (Header) para determinar el tipo de mensaje, y posteriormente
     *          aplica el molde de datos (Data Class) correspondiente al cuerpo (Payload).
     * @param rawString Texto plano en formato JSON recibido a través del WebSocket.
     * @return Objeto RobotMessage fuertemente tipado. Retorna un mensaje de error interno si el JSON es inválido.
     */
    fun decode(rawString: String): RobotMessage {
        return try {
            // 1. Extracción genérica de los bloques principales
            val jsonObj = jsonFormat.parseToJsonElement(rawString).jsonObject
            val header = jsonFormat.decodeFromJsonElement<MessageHeader>(jsonObj["header"]!!)
            val payloadJson = jsonObj["payload"]!!

            // 2. Mapeo del Payload según el Tipo de Mensaje declarado en la Cabecera
            val payloadObj: Payload = when (header.type) {

                // --- A. PETICIONES (REQUESTS) Y ERRORES ---
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

                // Mensajes sin cuerpo de datos
                AppConstants.MsgType.PING_REQ, AppConstants.MsgType.ACK -> EmptyPayload()

                // --- B. RESPUESTAS COMPLEJAS (RESP) ---
                AppConstants.MsgType.RESP -> {
                    val respType = payloadJson.jsonObject["resp_type"]?.jsonPrimitive?.content

                    when (respType) {

                        // B.1 Resolutor de Consultas de Información (Queries)
                        AppConstants.RespType.QUERY_RESP -> {
                            val resp = jsonFormat.decodeFromJsonElement<QueryRespPayload>(payloadJson)
                            val dataElement = payloadJson.jsonObject["data"]

                            resp.parsedData = when (dataElement) {
                                is JsonArray -> {
                                    // Discriminador: Si es una lista de Strings es ActionList, sino SensorList
                                    if (dataElement.isEmpty() || dataElement[0] is JsonPrimitive) {
                                        ActionListResult(jsonFormat.decodeFromJsonElement(dataElement))
                                    } else {
                                        SensorListResult(jsonFormat.decodeFromJsonElement(dataElement))
                                    }
                                }
                                is JsonObject -> {
                                    // Discriminador por claves de diccionario para Host, Robot o Red
                                    if (dataElement.containsKey("cpu_pct") || dataElement.containsKey("ros_distro")) {
                                        HostInfoResult(jsonFormat.decodeFromJsonElement(dataElement))
                                    }
                                    else if (dataElement.containsKey("capabilities") || dataElement.containsKey("identity") || dataElement.containsKey("status")) {
                                        RobotInfoResult(jsonFormat.decodeFromJsonElement(dataElement))
                                    } else {
                                        NetworkInfoResult(jsonFormat.decodeFromJsonElement(dataElement))
                                    }
                                }
                                else -> null
                            }
                            resp
                        }

                        // B.2 Progreso de Acciones PlayMotion
                        AppConstants.RespType.ACTION_FEEDBACK -> jsonFormat.decodeFromJsonElement<ActionFeedbackPayload>(payloadJson)

                        // B.3 Traductor de Datos de Sensores y Streaming
                        AppConstants.RespType.STREAM_RESP -> {
                            val resp = jsonFormat.decodeFromJsonElement<StreamRespPayload>(payloadJson)

                            val rawStream = resp.streamData
                            if (rawStream != null) {
                                val topic = rawStream["topic"]?.jsonPrimitive?.content ?: ""
                                val type = rawStream["type"]?.jsonPrimitive?.content ?: ""
                                val rawDataObj = rawStream["data"]

                                if (rawDataObj != null) {
                                    // Parseo Polimórfico de Sensores según su tipología ROS 2
                                    val parsedSensor: SensorData? = when (type) {
                                        AppConstants.SensorType.LASER_SCAN -> jsonFormat.decodeFromJsonElement<LaserScanData>(rawDataObj)
                                        AppConstants.SensorType.IMU -> jsonFormat.decodeFromJsonElement<ImuData>(rawDataObj)
                                        AppConstants.SensorType.BATTERY -> jsonFormat.decodeFromJsonElement<BatterySensorData>(rawDataObj)
                                        AppConstants.SensorType.RANGE -> jsonFormat.decodeFromJsonElement<RangeSensorData>(rawDataObj)
                                        AppConstants.SensorType.POINT_CLOUD2 -> jsonFormat.decodeFromJsonElement<PointCloud2Data>(rawDataObj)
                                        AppConstants.SensorType.ODOMETRY -> jsonFormat.decodeFromJsonElement<OdometryData>(rawDataObj)
                                        AppConstants.SensorType.NAV -> jsonFormat.decodeFromJsonElement<NavSatFixData>(rawDataObj)
                                        AppConstants.SensorType.WRENCH-> jsonFormat.decodeFromJsonElement<WrenchData>(rawDataObj)
                                        AppConstants.SensorType.TEMPERATURE -> jsonFormat.decodeFromJsonElement<TemperatureData>(rawDataObj)
                                        else -> null
                                    }

                                    if (parsedSensor != null) {
                                        resp.parsedSensorData = SensorStreamData(topic, type, parsedSensor)
                                    }
                                }
                            }
                            resp
                        }

                        // B.4 Respuestas simples de confirmación
                        else -> jsonFormat.decodeFromJsonElement<GenericRespPayload>(payloadJson)
                    }
                }
                else -> EmptyPayload()
            }

            // 3. Ensamblaje final
            RobotMessage(header = header, payload = payloadObj)

        } catch (e: Exception) {
            Log.e(tag, "Error crítico de formato JSON en decodificación: ${e.message}")
            // Fallback: Genera un mensaje de error interno estructurado para no romper la máquina de estados
            buildInternalErrorMsg(
                AppConstants.StatusCode.BAD_REQUEST,
                "Invalid JSON format or missing fields: ${e.message}"
            )
        }
    }

    /**
     * @brief Serializa un objeto RobotMessage convirtiéndolo en texto JSON listo para ser enviado por red.
     * @details Inyecta la marca de tiempo (timestamp) exacta en el momento de la codificación.
     * @param msg Objeto de datos nativo a empaquetar.
     * @return Cadena de texto JSON.
     */
    fun encode(msg: RobotMessage): String {

        // 1. Inyección de Timestamp local
        val finalHeader = msg.header.copy(
            timestamp = System.currentTimeMillis() / 1000.0
        )

        val headerJson = jsonFormat.encodeToJsonElement(finalHeader)

        // 2. Conversión del Payload Polimórfico
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

        // 3. Ensamblaje Final del JSON
        val finalJsonObj = buildJsonObject {
            put("header", headerJson)
            put("payload", payloadJson)
        }

        val finalJsonString = jsonFormat.encodeToString(finalJsonObj)

        // Ocultar Pings y envíos de Joystick masivos del Logcat para no saturar la consola
        if (finalHeader.type != AppConstants.MsgType.PING_REQ && finalHeader.type != AppConstants.MsgType.CONTROL_REQ) {
            Log.d(tag, "Codificado (OUT): $finalJsonString")
        }

        return finalJsonString
    }

    /**
     * @brief Método de seguridad interno. Crea un mensaje falso de error de protocolo para alertar al sistema.
     * @details Se utiliza cuando la capa JSON colapsa al intentar leer basura o cadenas malformadas de la red.
     * @param code Código de estado HTTP/Protocolo (ej. 400).
     * @param description Detalles técnicos del fallo en la lectura.
     * @return RobotMessage encapsulando el ProtocolErrorPayload.
     */
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