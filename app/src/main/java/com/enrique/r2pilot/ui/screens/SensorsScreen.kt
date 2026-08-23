/**
 * @file SensorsScreen.kt
 * @brief Interfaz principal de telemetría y sensores en tiempo real.
 * @details Este módulo renderiza el estado interno y externo del robot mediante
 *          técnicas de dibujo de alto rendimiento (Canvas) y gráficos.
 *          Implementa un patrón de componentes co-localizados para
 *          mantener encapsulada la lógica visual específica de cada tipo de sensor de ROS 2.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enrique.r2pilot.protocol.*
import com.enrique.r2pilot.ui.logic.SensorViewModel
import com.enrique.r2pilot.ui.theme.MonoData
import com.enrique.r2pilot.ui.theme.MonoLabel
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.sin

/* ============================================================================
 *  PANTALLA DE SENSORES PRINCIPAL
 * ========================================================================== */

/**
 * @brief Orquestador visual de la pantalla de sensores.
 * @details Gestiona el ciclo de vida de la suscripción a tópicos (solicitando al
 *          ViewModel que detenga el tráfico de red al destruirse la vista mediante
 *          un DisposableEffect). Renderiza una interfaz adaptable (Normal/Compacta).
 * @param viewModel Lógica de negocio responsable del descubrimiento y suscripción de nodos ROS 2.
 * @param isCompact Flag que determina si la vista se renderiza en modo pantalla dividida (Split-Screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(viewModel: SensorViewModel, isCompact: Boolean = false) {

    val availableSensors by viewModel.availableSensors.collectAsState()
    val activeSensorTopics by viewModel.activeSensorTopics.collectAsState()
    val activeSensorData by viewModel.activeSensorData.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()
    val odomTrail by viewModel.odomTrail.collectAsState()
    val cs = MaterialTheme.colorScheme

    // Protección de recursos: Libera la red cuando la pantalla no es visible
    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenDisposed() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(if (isCompact) 8.dp else 16.dp)) {

        // --- ZONA 1: ESTADOS VACÍOS Y DESCUBRIMIENTO ---
        if (!hasSearched && availableSensors.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Sensors, null, tint = cs.primary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.fetchSensors() },
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Search, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Escanear Sensores de la Red", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (hasSearched && availableSensors.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SearchOff, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "No se ha detectado ningún sensor compatible en el robot.",
                        color = cs.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // --- ZONA 2: SELECTOR DE TÓPICOS (Chips) ---
            if (!isCompact) {
                Text("Selecciona las gráficas a mostrar:", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.onSurface)
                Spacer(modifier = Modifier.height(10.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableSensors.forEach { sensor ->
                    val isSelected = activeSensorTopics.contains(sensor.topic)
                    SensorSelectChip(
                        label = "${sensor.type}: ${sensor.topic.substringAfterLast("/")}",
                        icon = iconForSensor(sensor.type),
                        selected = isSelected,
                        onClick = { viewModel.toggleSensor(sensor.topic, !isSelected) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // --- ZONA 3: RENDERIZADO DE TARJETAS DE DATOS ---
            if (activeSensorTopics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Activa al menos un sensor para ver sus datos en tiempo real.",
                        color = cs.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 14.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(items = activeSensorTopics.toList(), key = { it }) { topic ->
                        val sensorInfo = availableSensors.find { it.topic == topic }
                        val currentData = activeSensorData[topic]
                        if (sensorInfo != null) {
                            SensorCard(
                                sensorInfo = sensorInfo,
                                data = currentData,
                                isCompact = isCompact,
                                odomTrail = odomTrail,
                                onTrailPoint = { x, y -> viewModel.addTrailPoint(x, y) },
                                onNewSegment = { viewModel.startNewTrailSegment() }
                            )
                        }
                    }
                }
            }
        }
    }
}

/* ============================================================================
 *  PIEZAS COMUNES DE ESTILO Y UTILIDADES
 * ========================================================================== */

/**
 * @brief Mapea el tipo de mensaje ROS 2 a una representación visual.
 * @param type Identificador del tipo de sensor (ej. "LaserScan", "Imu").
 * @return Icono vectorial Material de Jetpack Compose.
 */
private fun iconForSensor(type: String): ImageVector = when (type) {
    "LaserScan" -> Icons.Default.Radar
    "Imu" -> Icons.Default.Explore
    "BatteryState" -> Icons.Default.BatteryChargingFull
    "Range" -> Icons.Default.Straighten
    "PointCloud2" -> Icons.Default.Cloud
    "Odometry" -> Icons.Default.MyLocation
    "NavSatFix" -> Icons.Default.Satellite
    "Wrench" -> Icons.Default.FitnessCenter
    "Temperature" -> Icons.Default.Thermostat
    else -> Icons.Default.Sensors
}

/**
 * @brief Botón tipo "Chip" para habilitar/deshabilitar la suscripción a un tópico.
 * @param label Texto a mostrar (nombre corto del tópico).
 * @param icon Icono representativo del tipo de sensor.
 * @param selected Determina si el tópico está siendo escuchado activamente.
 * @param onClick Acción lambda disparada al interactuar con el chip.
 */
@Composable
private fun SensorSelectChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val container = if (selected) cs.secondaryContainer else cs.surface
    val border = if (selected) cs.secondary.copy(alpha = 0.5f) else cs.outline
    val content = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant
    Surface(
        shape = MaterialTheme.shapes.small,
        color = container,
        border = BorderStroke(1.dp, border),
        onClick = onClick
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * @brief Contenedor estandarizado para la información de un sensor.
 * @details Gestiona el estado de "Carga/Espera" cuando se suscribe a un tópico pero
 *          aún no ha llegado el primer paquete de datos (Data Stream Lifecycle).
 * @param sensorInfo Metadatos de descubrimiento del sensor.
 * @param data Último paquete de datos recibido (puede ser nulo si está pendiente).
 * @param isCompact Modificador de tamaño para layouts reducidos.
 * @param odomTrail Historial de posiciones XY para el componente de odometría.
 * @param onTrailPoint Callback para registrar una nueva posición en el mapa.
 * @param onNewSegment Callback para indicar una discontinuidad en la trazada (teletransporte/desconexión).
 */
@Composable
fun SensorCard(
    sensorInfo: SensorInfo,
    data: SensorStreamData?,
    isCompact: Boolean = false,
    odomTrail: List<Pair<Float, Float>> = emptyList(),
    onTrailPoint: (Float, Float) -> Unit = { _, _ -> },
    onNewSegment: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = cs.surface,
        border = BorderStroke(1.dp, cs.outline)
    ) {
        Column(modifier = Modifier.padding(if (isCompact) 8.dp else 16.dp).fillMaxWidth()) {
            // Cabecera de la tarjeta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        Modifier.size(if (isCompact) 24.dp else 30.dp).clip(RoundedCornerShape(8.dp)).background(cs.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(iconForSensor(sensorInfo.type), null, tint = cs.primary, modifier = Modifier.size(if (isCompact) 14.dp else 18.dp))
                    }
                    Spacer(Modifier.width(if (isCompact) 7.dp else 10.dp))
                    Text(
                        sensorInfo.topic,
                        style = MonoData.copy(fontSize = if (isCompact) 12.sp else 14.sp),
                        color = cs.onSurface,
                        maxLines = 1
                    )
                }
                Surface(shape = MaterialTheme.shapes.extraSmall, color = cs.primary.copy(alpha = 0.12f)) {
                    Text(
                        sensorInfo.type,
                        style = MonoLabel,
                        color = cs.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 14.dp))

            // Enrutador de Renderizado según el Tipo Polimórfico del Dato
            if (data == null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = cs.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Sensor activo · esperando primer mensaje…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }
            } else {
                when (val sensorData = data.data) {
                    is LaserScanData -> LaserScanView(sensorData, isCompact)
                    is BatterySensorData -> BatteryView(sensorData, sensorInfo.topic, isCompact)
                    is ImuData -> ImuView(sensorData, isCompact)
                    is RangeSensorData -> RangeView(sensorData, sensorInfo.topic, isCompact)
                    is PointCloud2Data -> PointCloudView(sensorData)
                    is OdometryData -> OdometryView(sensorData, sensorInfo.topic, isCompact, odomTrail, onTrailPoint, onNewSegment)
                    is NavSatFixData -> NavSatFixView(sensorData)
                    is WrenchData -> WrenchView(sensorData, isCompact)
                    is TemperatureData -> TemperatureView(sensorData, sensorInfo.topic, isCompact)
                }
            }
        }
    }
}

/* ============================================================================
 *  SISTEMA DE GRÁFICAS DE HISTORIAL
 * ========================================================================== */

/**
 * @brief Implementa un buffer circular asíncrono para generar historial temporal.
 * @details Toma el valor actual a intervalos fijos de tiempo en lugar
 *          de solo cuando llega un mensaje de ROS 2. Esto garantiza que la gráfica
 *          avance linealmente con el tiempo real, incluso si el valor no cambia.
 * @param value Valor numérico escalar actual de la señal a monitorizar.
 * @param maxPoints Capacidad máxima del buffer circular (ancho del gráfico).
 * @param sampleMs Frecuencia de muestreo asíncrono (polling) en milisegundos.
 * @return Lista inmutable reactiva (State) con los datos del historial listos para renderizar.
 */
@Composable
private fun rememberHistory(value: Number, maxPoints: Int = 60, sampleMs: Long = 200L): List<Float> {
    val history = remember { mutableStateListOf<Float>() }
    val latest = remember { mutableStateOf(value.toFloat()) }
    latest.value = value.toFloat()

    LaunchedEffect(Unit) {
        while (true) {
            history.add(latest.value)
            while (history.size > maxPoints) history.removeAt(0)
            kotlinx.coroutines.delay(sampleMs)
        }
    }
    return history
}

/**
 * @brief Motor de renderizado vectorial para minigráficas (Sparklines).
 * @details Dibuja curvas suaves, relleno bajo la curva y manejo de rangos dinámicos.
 * @param values Buffer de datos históricos.
 * @param color Tinte base para la traza y el sombreado.
 * @param modifier Ajustes de layout proporcionados por el padre.
 * @param height Altura estática del componente Canvas.
 * @param minFloor Valor mínimo forzado del eje Y (útil para porcentajes = 0).
 * @param maxCeil Valor máximo forzado del eje Y (útil para porcentajes = 100).
 * @param minSpan Rango dinámico mínimo para evitar amplificar el ruido numérico del sensor.
 * @param zeroAnchored Si es verdadero, fuerza que la línea del Cero quede anclada verticalmente en el centro.
 */
@Composable
private fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 56.dp,
    minFloor: Number? = null,
    maxCeil: Number? = null,
    minSpan: Float = 0f,
    zeroAnchored: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.isEmpty()) return@Canvas
        var minV = minFloor?.toFloat() ?: (values.min())
        var maxV = maxCeil?.toFloat() ?: (values.max())

        if (minFloor == null && maxCeil == null) {
            if (zeroAnchored) {
                val maxAbs = maxOf(values.maxOf { kotlin.math.abs(it) }, minSpan / 2f)
                minV = -maxAbs
                maxV = maxAbs
            } else if (minSpan > 0f) {
                val realSpan = maxV - minV
                if (realSpan < minSpan) {
                    val mid = (maxV + minV) / 2f
                    minV = mid - minSpan / 2f
                    maxV = mid + minSpan / 2f
                }
            }
        }

        val span = (maxV - minV).takeIf { it > 1e-6f } ?: 1f

        val n = values.size
        val stepX = if (n > 1) size.width / (n - 1) else size.width
        fun yFor(v: Float): Float {
            val norm = ((v - minV) / span).coerceIn(0f, 1f)
            return size.height * (1f - norm) * 0.8f + size.height * 0.1f
        }

        val baseY = if (zeroAnchored) yFor(0f) else size.height * 0.9f
        drawLine(
            cs.outline.copy(alpha = 0.5f),
            Offset(0f, baseY),
            Offset(size.width, baseY),
            1f
        )

        val fillPath = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, yFor(values[0]))
            if (n == 1) {
                lineTo(size.width, yFor(values[0]))
            } else {
                values.forEachIndexed { i, v -> lineTo(i * stepX, yFor(v)) }
            }
            lineTo(size.width, size.height)
            close()
        }
        drawPath(fillPath, color.copy(alpha = 0.12f))

        val linePath = Path().apply {
            moveTo(0f, yFor(values[0]))
            if (n == 1) {
                lineTo(size.width, yFor(values[0]))
            } else {
                values.forEachIndexed { i, v -> lineTo(i * stepX, yFor(v)) }
            }
        }
        drawPath(linePath, color, style = Stroke(width = 3f, cap = StrokeCap.Round))

        val lastX = if (n > 1) (n - 1) * stepX else size.width
        drawCircle(color, radius = 4f, center = Offset(lastX, yFor(values.last())))
    }
}

/**
 * @brief Componente UI estandarizado para pares Clave-Valor numéricos.
 * @param label Etiqueta descriptiva del dato.
 * @param value Cadena de texto formateada con la magnitud (idealmente fuente monoespaciada).
 * @param color Tinte opcional para resaltar magnitudes críticas.
 */
@Composable
private fun MetricLine(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoData.copy(fontSize = 15.sp), color = color)
    }
}

/* ============================================================================
 *  RUTINAS DE DIBUJO ESPECÍFICAS DE DOMINIO (SENSOR VIEWS)
 * ========================================================================== */

/**
 * @brief Visualizador topológico 2D del sensor LiDAR (LaserScan).
 * @details Transforma coordenadas polares (Array de distancias en varios ángulos) a
 *          coordenadas cartesianas en pantalla mediante trigonometría directa.
 * @param scan modelo del mensaje de escaneo láser de ROS 2.
 * @param isCompact Escala la vista para la interfaz multitarea.
 */
@Composable
fun LaserScanView(scan: LaserScanData, isCompact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Text("Rango: ${scan.rangeMin}m – ${scan.rangeMax}m", style = MonoLabel, color = cs.onSurfaceVariant)
    Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))

    val nearColor = Color(0xFFFF5468)
    val midColor = cs.primary
    val farColor = cs.tertiary
    // =========================================================================
    // CONFIGURACIÓN DE DESFASE DEL SENSOR (URDF Frame offset)
    // =========================================================================

    // -> DESCOMENTAR PARA SIMULADOR GAZEBO (Si los sensores están montados en diagonal a 45°)
     //val orientationOffset = (Math.PI / 4).toFloat()

    // -> DESCOMENTAR PARA ROBOT REAL (LiDAR 360° central alineado a 0°)
    val orientationOffset = 0f

    // =========================================================================

    Canvas(modifier = Modifier.fillMaxWidth().height(if (isCompact) 118.dp else 240.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val maxPixels = minOf(centerX, centerY) - 24f
        val scale = if (scan.rangeMax > 0f) maxPixels / scan.rangeMax else 10f
        val center = Offset(centerX, centerY)

        fun project(theta: Float, range: Float): Offset {
            val a = theta + orientationOffset
            //orientación robot simulado
            //val sx = centerX + (range * sin(a) * scale)
            //orientación robot real
            val sx = centerX - (range * sin(a) * scale)
            val sy = centerY - (range * cos(a) * scale)
            return Offset(sx, sy)
        }

        // Cono de orientación visual (Frente del robot)
        val conePath = Path().apply {
            moveTo(centerX, centerY)
            val half = 0.45f // ~26° a cada lado
            val steps = 12
            for (i in 0..steps) {
                val a = -half + (2 * half) * (i / steps.toFloat())
                val x = centerX + sin(a) * maxPixels
                val y = centerY - cos(a) * maxPixels
                lineTo(x, y)
            }
            close()
        }
        drawPath(conePath, cs.primary.copy(alpha = 0.06f))

        val ringStep = when {
            scan.rangeMax <= 2f -> 0.5f
            scan.rangeMax <= 6f -> 1f
            else -> 2f
        }
        var r = ringStep
        while (r <= scan.rangeMax) {
            drawCircle(cs.outline.copy(alpha = 0.6f), radius = r * scale, center = center, style = Stroke(width = 1f))
            r += ringStep
        }

        drawLine(cs.outline.copy(alpha = 0.4f), Offset(centerX, centerY - maxPixels), Offset(centerX, centerY + maxPixels), 1f)
        drawLine(cs.outline.copy(alpha = 0.4f), Offset(centerX - maxPixels, centerY), Offset(centerX + maxPixels, centerY), 1f)

        scan.ranges.forEachIndexed { index, range ->
            if (range in scan.rangeMin..scan.rangeMax) {
                val theta = scan.angleMin + (index * scan.angleIncrement)
                val p = project(theta, range)
                val frac = (range / scan.rangeMax).coerceIn(0f, 1f)
                val dotColor = when {
                    frac < 0.33f -> nearColor
                    frac < 0.66f -> midColor
                    else -> farColor
                }
                drawCircle(dotColor, radius = 3f, center = p)
            }
        }

        val frontTip = Offset(centerX, centerY - maxPixels * 0.9f)
        drawLine(cs.primary, center, frontTip, strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(cs.primary, frontTip, Offset(frontTip.x - 7f, frontTip.y + 10f), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(cs.primary, frontTip, Offset(frontTip.x + 7f, frontTip.y + 10f), strokeWidth = 3f, cap = StrokeCap.Round)

        drawCircle(cs.primary, radius = 7f, center = center)
        drawCircle(Color.White, radius = 3f, center = center)
    }

    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot(nearColor, "Cerca")
        LegendDot(midColor, "Medio")
        LegendDot(farColor, "Lejos")
        Spacer(Modifier.weight(1f))
        Text("↑ Frente", style = MonoLabel, color = cs.primary)
    }
}

/**
 * @brief Elemento de diseño para las leyendas de gráficos de color.
 * @param color Tinte semántico a mostrar en el punto.
 * @param label Etiqueta descriptiva del color.
 */
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * @brief Unidad de Medición Inercial (IMU) y Horizonte Artificial.
 * @details Extrae los ángulos de Alabeo (Roll) y Cabeceo (Pitch) analizando la
 *          fuerza de la gravedad sobre la aceleración lineal, y dibuja un
 *          giroscopio simulado para entender la inclinación física del robot.
 * @param imu modelo del mensaje del IMU.
 * @param isCompact Modificador para la vista dividida.
 */
@Composable
fun ImuView(imu: ImuData, isCompact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val ax = imu.linearAcceleration.x
    val ay = imu.linearAcceleration.y
    val az = imu.linearAcceleration.z
    val roll = kotlin.math.atan2(ay.toDouble(), az.toDouble()).toFloat()
    val pitch = kotlin.math.atan2((-ax).toDouble(), kotlin.math.hypot(ay.toDouble(), az.toDouble())).toFloat()

    val animRoll by animateFloatAsState(roll, label = "roll")
    val animPitch by animateFloatAsState(pitch, label = "pitch")

    val histX = rememberHistory(ax)
    val histY = rememberHistory(ay)
    val histZ = rememberHistory(az)
    var showAccelCharts by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(if (isCompact) 70.dp else 120.dp).clip(CircleShape).background(cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val cx = w / 2f; val cy = h / 2f
                    val pitchOffset = (animPitch / (Math.PI.toFloat() / 2f)) * (h / 2f)
                    rotate(degrees = Math.toDegrees(animRoll.toDouble()).toFloat(), pivot = Offset(cx, cy)) {
                        drawRect(Color(0xFF34D0DE).copy(alpha = 0.35f), topLeft = Offset(-w, -h + cy + pitchOffset), size = Size(w * 3f, h * 2f))
                        drawRect(Color(0xFF8A92A3).copy(alpha = 0.5f), topLeft = Offset(-w, cy + pitchOffset), size = Size(w * 3f, h * 2f))
                        drawLine(Color.White, Offset(-w, cy + pitchOffset), Offset(w * 2f, cy + pitchOffset), 2f)
                    }
                    drawLine(cs.primary, Offset(cx - 20f, cy), Offset(cx - 6f, cy), 3f)
                    drawLine(cs.primary, Offset(cx + 6f, cy), Offset(cx + 20f, cy), 3f)
                    drawCircle(cs.primary, radius = 3f, center = Offset(cx, cy))
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MetricLine("Roll", String.format("%+.1f°", Math.toDegrees(roll.toDouble())))
                MetricLine("Pitch", String.format("%+.1f°", Math.toDegrees(pitch.toDouble())))
                Spacer(Modifier.height(2.dp))
                Text("Acel. lineal (m/s²)", style = MonoLabel, color = cs.onSurfaceVariant)
                Text(
                    String.format("X %.2f  Y %.2f  Z %.2f", ax, ay, az),
                    style = MonoData.copy(fontSize = 13.sp), color = cs.onSurface
                )
                Text("Vel. angular (rad/s)", style = MonoLabel, color = cs.onSurfaceVariant)
                Text(
                    String.format("X %.2f  Y %.2f  Z %.2f", imu.angularVelocity.x, imu.angularVelocity.y, imu.angularVelocity.z),
                    style = MonoData.copy(fontSize = 13.sp), color = cs.onSurface
                )
            }
        }

        Spacer(Modifier.height(if (isCompact) 4.dp else 10.dp))
        Surface(
            shape = MaterialTheme.shapes.small,
            color = cs.surfaceVariant.copy(alpha = 0.5f),
            onClick = { showAccelCharts = !showAccelCharts },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (showAccelCharts) "Ocultar gráficas de aceleración" else "Ver gráficas de aceleración",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    if (showAccelCharts) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = cs.onSurface
                )
            }
        }

        if (showAccelCharts) {
            Spacer(Modifier.height(10.dp))
            AccelAxisChart("Eje X", histX, cs.primary)
            Spacer(Modifier.height(8.dp))
            AccelAxisChart("Eje Y", histY, cs.tertiary)
            Spacer(Modifier.height(8.dp))
            AccelAxisChart("Eje Z", histZ, cs.secondary)
            Text(
                "El eje Z incluye la gravedad (~9.8). Cada gráfica usa su propia escala.",
                style = MonoLabel,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * @brief Subcomponente encapsulado para dibujar una métrica simple junto a su sparkline.
 * @param label Nombre del eje monitorizado.
 * @param history Array inmutable con las mediciones.
 * @param color Color de la traza para diferenciar visualmente los tres ejes.
 */
@Composable
private fun AccelAxisChart(label: String, history: List<Float>, color: Color) {
    val current = history.lastOrNull() ?: 0f
    MetricLine(label, String.format("%.2f m/s²", current), color)
    Sparkline(values = history, color = color, height = 40.dp, minSpan = 1f)
}

/**
 * @brief Sensor de Energía Interna.
 * @param battery Paquete de telemetría de energía de ROS 2.
 * @param topic Identificador ROS del origen.
 * @param isCompact Modificador para vistas reducidas.
 */
@Composable
fun BatteryView(battery: BatterySensorData, topic: String, isCompact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val progress = (battery.percentage / 100f).coerceIn(0f, 1f)
    val statusText = when (battery.powerSupplyStatus) {
        1 -> "Cargando"; 2 -> "Descargando"; 3 -> "Completa"; else -> "Desconocido"
    }
    val barColor = if (progress < 0.2f) cs.error else cs.tertiary
    val history = rememberHistory(battery.percentage)

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)),
            color = barColor,
            trackColor = cs.surfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text("${String.format("%.1f", battery.percentage)}%", style = MonoData.copy(fontSize = 16.sp), color = barColor)
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text("Voltaje: ${String.format("%.2f", battery.voltage)} V · $statusText", style = MonoLabel, color = cs.onSurfaceVariant)
    Spacer(modifier = Modifier.height(10.dp))
    Sparkline(values = history, color = barColor, height = if (isCompact) 34.dp else 56.dp, minFloor = 0f, maxCeil = 100f)
}

/**
 * @brief Telémetro acústico o de infrarrojos 1D.
 * @param rangeData Paquete con distancia actual y límites absolutos del hardware.
 * @param topic Identificador de tópico ROS.
 * @param isCompact Escala la vista.
 */
@Composable
fun RangeView(rangeData: RangeSensorData, topic: String, isCompact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val rangeSpan = rangeData.maxRange - rangeData.minRange
    val currentSpan = rangeData.range - rangeData.minRange
    val progress = if (rangeSpan > 0) (currentSpan / rangeSpan).coerceIn(0f, 1f) else 0f
    val barColor = if (progress < 0.2f) cs.error else cs.primary
    val history = rememberHistory(rangeData.range)

    MetricLine("Distancia", "${String.format("%.2f", rangeData.range)} m", barColor)
    Spacer(modifier = Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
        color = barColor,
        trackColor = cs.surfaceVariant
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Min ${rangeData.minRange}m", style = MonoLabel, color = cs.onSurfaceVariant)
        Text("Max ${rangeData.maxRange}m", style = MonoLabel, color = cs.onSurfaceVariant)
    }
    Spacer(modifier = Modifier.height(10.dp))
    Sparkline(values = history, color = barColor, height = if (isCompact) 34.dp else 56.dp, minFloor = rangeData.minRange, maxCeil = rangeData.maxRange)
}

/**
 * @brief Visualización integral de Cinemática y Posicionamiento Local.
 * @details Monitoriza la velocidad y levanta un componente interactivo ("TrailMap")
 *          que dibuja vectorialmente la trayectoria física real por la que se ha
 *          movido el chasis del robot utilizando el filtro predictivo (deadband)
 *          para suavizar ruidos inerciales estacionarios.
 * @param odom Paquete de información cinemática en formato nav_msgs.
 * @param topic Tópico ROS 2 fuente.
 * @param isCompact Layout denso para uso en split-screen.
 * @param odomTrail Array de puntos con el histórico de coordenadas.
 * @param onTrailPoint Función puente para delegar la persistencia al ViewModel.
 * @param onNewSegment Función puente para manejar fallos de conexión (tramos rotos).
 */
@Composable
fun OdometryView(
    odom: OdometryData,
    topic: String,
    isCompact: Boolean = false,
    odomTrail: List<Pair<Float, Float>> = emptyList(),
    onTrailPoint: (Float, Float) -> Unit = { _, _ -> },
    onNewSegment: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Text("Posición global (mapa)", style = MonoLabel, color = cs.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("X ${String.format("%.3f", odom.position.x)} m", style = MonoData.copy(fontSize = 14.sp), color = cs.onSurface)
        Text("Y ${String.format("%.3f", odom.position.y)} m", style = MonoData.copy(fontSize = 14.sp), color = cs.onSurface)
    }

    Spacer(modifier = Modifier.height(if (isCompact) 6.dp else 12.dp))
    val linVel = deadband(odom.linearVelocity.toFloat(), 0.01f)
    val linHist = rememberHistory(linVel)
    MetricLine("Vel. lineal", String.format("%.2f m/s", linVel), cs.primary)
    Sparkline(values = linHist, color = cs.primary, height = if (isCompact) 28.dp else 44.dp, minSpan = 0.4f, zeroAnchored = true)

    Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
    val angVel = deadband(odom.angularVelocity.toFloat(), 0.01f)
    val angHist = rememberHistory(angVel)
    MetricLine("Vel. angular", String.format("%.2f rad/s", angVel), cs.tertiary)
    Sparkline(values = angHist, color = cs.tertiary, height = if (isCompact) 28.dp else 44.dp, minSpan = 0.4f, zeroAnchored = true)

    LaunchedEffect(Unit) { onNewSegment() }
    LaunchedEffect(odom.position.x, odom.position.y) {
        onTrailPoint(odom.position.x.toFloat(), odom.position.y.toFloat())
    }
    val trail = remember(odomTrail) { odomTrail.map { Offset(it.first, it.second) } }
    var showTrail by remember { mutableStateOf(false) }

    Spacer(Modifier.height(if (isCompact) 6.dp else 10.dp))
    Surface(
        shape = MaterialTheme.shapes.small,
        color = cs.surfaceVariant.copy(alpha = 0.5f),
        onClick = { showTrail = !showTrail },
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (showTrail) "Ocultar recorrido" else "Ver recorrido del robot",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurface,
                fontWeight = FontWeight.Bold
            )
            Icon(
                if (showTrail) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null, tint = cs.onSurface
            )
        }
    }

    if (showTrail) {
        Spacer(Modifier.height(10.dp))
        TrailMap(trail, isCompact)
    }
}

/**
 * @brief Algoritmo vectorial para generar un minimapa autocentrado en tiempo real.
 * @details Convierte coordenadas espaciales del mundo virtual del robot a dimensiones
 *          escalables de píxeles, dibujando interpolaciones y aplicando saltos ("NaN")
 *          si el robot pierde la conexión.
 * @param trail Secuencia de puntos de odometría filtrados.
 * @param isCompact Modificador para vistas reducidas.
 */
@Composable
private fun TrailMap(trail: List<Offset>, isCompact: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = cs.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 130.dp else 190.dp)
                .padding(12.dp)
        ) {
            if (trail.isEmpty()) return@Canvas

            val real = trail.filter { !it.x.isNaN() }
            if (real.isEmpty()) return@Canvas
            val xs = real.map { it.x }; val ys = real.map { it.y }
            val minX = xs.min(); val maxX = xs.max()
            val minY = ys.min(); val maxY = ys.max()

            val minViewMeters = 2f
            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f
            val halfSpan = maxOf((maxX - minX), (maxY - minY), minViewMeters) / 2f * 1.15f

            val viewPx = minOf(size.width, size.height)
            val scale = viewPx / (halfSpan * 2f)

            fun toScreen(p: Offset): Offset {
                val sx = size.width / 2f - (p.y - cy) * scale
                val sy = size.height / 2f - (p.x - cx) * scale
                return Offset(sx, sy)
            }

            val gridColor = cs.outline.copy(alpha = 0.25f)
            var m = kotlin.math.ceil(cx - halfSpan).toInt()
            while (m <= cx + halfSpan) {
                val yLine = size.height / 2f - (m - cx) * scale
                drawLine(gridColor, Offset(0f, yLine), Offset(size.width, yLine), 1f)
                m++
            }
            m = kotlin.math.ceil(cy - halfSpan).toInt()
            while (m <= cy + halfSpan) {
                val xLine = size.width / 2f - (m - cy) * scale
                drawLine(gridColor, Offset(xLine, 0f), Offset(xLine, size.height), 1f)
                m++
            }
            drawLine(cs.outline.copy(alpha = 0.45f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1f)
            drawLine(cs.outline.copy(alpha = 0.45f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)

            if (trail.size >= 2) {
                for (i in 1 until trail.size) {
                    val p0 = trail[i - 1]; val p1 = trail[i]
                    if (p0.x.isNaN() || p1.x.isNaN()) continue
                    val alpha = 0.25f + 0.75f * (i / (trail.size - 1).toFloat())
                    drawLine(cs.primary.copy(alpha = alpha), toScreen(p0), toScreen(p1), strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }

            drawCircle(cs.tertiary, radius = 5f, center = toScreen(real.first()))
            val cur = toScreen(real.last())
            drawCircle(cs.primary.copy(alpha = 0.25f), radius = 11f, center = cur)
            drawCircle(cs.primary, radius = 6f, center = cur)
            drawCircle(Color.White, radius = 2.5f, center = cur)
        }
    }
}

/**
 * @brief Filtro de software tipo 'Banda Muerta' (Deadband).
 * @details Descarta fluctuaciones microscópicas de hardware (ej. 0.00000004 m/s)
 *          que ensuciarían las gráficas de velocidad indicando que el robot está en reposo.
 * @param value Valor numérico flotante en bruto proveniente de ROS 2.
 * @param threshold Umbral físico por el cual cualquier valor por debajo es considerado cero.
 * @return Señal estabilizada.
 */
private fun deadband(value: Float, threshold: Float): Float =
    if (value.absoluteValue < threshold) 0f else value

/**
 * @brief Representación del módulo de control térmico.
 * @param temp Dato desempaquetado de ROS 2 en formato Temperature.
 * @param topic Fuente del tópico para referenciado.
 * @param isCompact Modificador para vistas pequeñas.
 */
@Composable
fun TemperatureView(temp: TemperatureData, topic: String, isCompact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val t = temp.temperature
    val tempColor = when {
        t < 15f -> cs.primary
        t < 45f -> cs.tertiary
        t < 65f -> cs.secondary
        else -> cs.error
    }
    val progress = (t / 100f).coerceIn(0f, 1f)
    val history = rememberHistory(t.toFloat())

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Thermostat, null, tint = tempColor, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            MetricLine("Temperatura", "${String.format("%.1f", t)} °C", tempColor)
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                color = tempColor,
                trackColor = cs.surfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
    Sparkline(values = history, color = tempColor, height = if (isCompact) 34.dp else 56.dp, minSpan = 5f)
}

/**
 * @brief Lector de Posicionamiento Satelital Goblal (GPS/NavSat).
 * @param gps Representación del estado geoespacial y de cobertura satelital.
 */
@Composable
fun NavSatFixView(gps: NavSatFixData) {
    val cs = MaterialTheme.colorScheme
    val hasSignal = gps.status >= 0
    val statusColor = if (hasSignal) cs.tertiary else cs.error
    val statusText = if (hasSignal) "Señal satélite OK" else "Buscando satélites…"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(statusColor))
        Spacer(modifier = Modifier.width(8.dp))
        Text(statusText, fontWeight = FontWeight.Bold, color = statusColor, style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(modifier = Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GpsField("Latitud", String.format("%.6f°", gps.latitude))
        GpsField("Longitud", String.format("%.6f°", gps.longitude))
        GpsField("Altitud", String.format("%.1f m", gps.altitude))
    }
}

/**
 * @brief Componente hijo para agrupar variables de latitud/longitud uniformemente.
 * @param label Título referencial de coordenadas (ej. Latitud).
 * @param value Cadena mapeada en grados/metros.
 */
@Composable
private fun GpsField(label: String, value: String) {
    Column {
        Text(label, style = MonoLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MonoData.copy(fontSize = 13.sp), color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * @brief Vector físico 6D de fuerza y par (Torque).
 * @param wrench Objeto deserializado de la clase Wrench (nav_msgs).
 * @param isCompact Flag Split-Screen.
 */
@Composable
fun WrenchView(wrench: WrenchData, isCompact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Text("Esfuerzo (brazo/muñeca)", style = MonoLabel, color = cs.onSurfaceVariant)
    Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
    Text("Fuerza (N)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cs.onSurface)
    ZeroCenteredBar("X", wrench.force.x, 50f, isCompact)
    ZeroCenteredBar("Y", wrench.force.y, 50f, isCompact)
    ZeroCenteredBar("Z", wrench.force.z, 50f, isCompact)
    Spacer(modifier = Modifier.height(if (isCompact) 4.dp else 8.dp))
    Text("Torque (Nm)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = cs.onSurface)
    ZeroCenteredBar("X", wrench.torque.x, 10f, isCompact)
    ZeroCenteredBar("Y", wrench.torque.y, 10f, isCompact)
    ZeroCenteredBar("Z", wrench.torque.z, 10f, isCompact)
}

/**
 * @brief Barra de progreso visual bidireccional, capaz de representar fuerzas positivas y negativas.
 * @param label Nombre del eje (X, Y, Z).
 * @param value Valor flotante a representar de forma simétrica.
 * @param maxValue Límite de escala paramétrico (ej. Si la fuerza máxima del brazo es 50N).
 * @param isCompact Modificador gráfico.
 */
@Composable
fun ZeroCenteredBar(label: String, value: Number, maxValue: Float, isCompact: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val v = value.toFloat()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = if (isCompact) 1.dp else 2.dp)) {
        Text("$label:", style = MonoLabel, color = cs.onSurfaceVariant, modifier = Modifier.width(24.dp))
        Box(modifier = Modifier.weight(1f).height(if (isCompact) 10.dp else 12.dp).clip(RoundedCornerShape(3.dp)).background(cs.surfaceVariant)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val midX = size.width / 2f
                val normalized = (v / maxValue).coerceIn(-1f, 1f)
                val barWidth = (normalized.absoluteValue * midX)
                if (normalized > 0) {
                    drawRect(cs.primary, topLeft = Offset(midX, 0f), size = Size(barWidth, size.height))
                } else if (normalized < 0) {
                    drawRect(cs.error, topLeft = Offset(midX - barWidth, 0f), size = Size(barWidth, size.height))
                }
                drawLine(cs.onSurface.copy(alpha = 0.4f), Offset(midX, 0f), Offset(midX, size.height), 2f)
            }
        }
        Text(String.format(" %5.1f", v), style = MonoLabel, color = cs.onSurface, modifier = Modifier.width(48.dp))
    }
}

/**
 * @brief Componente descriptivo para datos espaciales crudos (Nubes de puntos).
 * @details Este tipo de sensores devuelven matrices  tridimensionales (Tensors) masivas que,
 *          por limitaciones gráficas en Android, no se renderizan, por lo que este nodo
 *          actúa como mero informador de que el sensor está activo y retransmitiendo.
 * @param pc Parámetros y metadatos de la nube.
 */
@Composable
fun PointCloudView(pc: PointCloud2Data) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Cloud, null, tint = cs.primary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text("Nube de puntos ${pc.width}×${pc.height}", fontWeight = FontWeight.Bold, color = cs.onSurface)
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(pc.note, color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}