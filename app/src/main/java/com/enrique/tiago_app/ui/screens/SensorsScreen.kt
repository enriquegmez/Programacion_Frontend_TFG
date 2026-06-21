package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enrique.tiago_app.protocol.*
import com.enrique.tiago_app.ui.logic.SensorViewModel
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(viewModel: SensorViewModel) {

    val availableSensors by viewModel.availableSensors.collectAsState()
    val activeSensorTopics by viewModel.activeSensorTopics.collectAsState()
    val activeSensorData by viewModel.activeSensorData.collectAsState()

    // Variable local para saber si ya hemos pulsado el botón al menos una vez
    var hasSearched by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.onScreenDisposed()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // 1. ZONA SUPERIOR: Botón y Menú de Selección
        if (!hasSearched && availableSensors.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = {
                    hasSearched = true
                    viewModel.fetchSensors()
                }) {
                    Text("🔍 Escanear Sensores de la Red")
                }
            }
        } else if (hasSearched && availableSensors.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No se ha detectado ningún sensor compatible en el robot.",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Text("Selecciona las gráficas a mostrar:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(8.dp))

            // Fila con Scroll Horizontal Seguro
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableSensors.forEach { sensor ->
                    val isSelected = activeSensorTopics.contains(sensor.topic)
                    // Hacemos que el label sea bonito (ej: "/scan_front" -> "LaserScan: /scan_front")
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.toggleSensor(sensor.topic, !isSelected) },
                        label = { Text("${sensor.type}: ${sensor.topic.substringAfterLast("/")}") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // 2. ZONA DE GRÁFICAS Y TARJETAS
            if (activeSensorTopics.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Activa al menos un sensor arriba para ver sus datos en tiempo real.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Solo pintamos tarjetas para los topics que tienen el "tick" puesto
                    items(items = activeSensorTopics.toList()) { topic ->
                        val sensorInfo = availableSensors.find { it.topic == topic }
                        val currentData = activeSensorData[topic]

                        if (sensorInfo != null) {
                            SensorCard(sensorInfo = sensorInfo, data = currentData)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SensorCard(sensorInfo: SensorInfo, data: SensorStreamData?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            // Título de la Tarjeta
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = sensorInfo.topic, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(text = sensorInfo.type, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lógica de dibujado específica según el tipo de sensor
            if (data == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).size(24.dp))
                Text("Esperando datos...", fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                when (val sensorData = data.data) {
                    is LaserScanData -> LaserScanView(sensorData)
                    is BatterySensorData -> BatteryView(sensorData)
                    is ImuData -> ImuView(sensorData)
                    is RangeSensorData -> RangeView(sensorData)
                    is PointCloud2Data -> PointCloudView(sensorData)
                }
            }
        }
    }
}

// ==========================================
// VISTAS ESPECÍFICAS PARA CADA SENSOR
// ==========================================

@Composable
fun LaserScanView(scan: LaserScanData) {
    Text(text = "Rango: ${scan.rangeMin}m - ${scan.rangeMax}m", fontSize = 12.sp)
    Spacer(modifier = Modifier.height(8.dp))

    // ¡EL RADAR!
    Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        // Calculamos la escala para que el rango máximo quepa en la pantalla (dejando margen)
        val maxPixels = minOf(centerX, centerY) - 10f
        val scale = if (scan.rangeMax > 0f) maxPixels / scan.rangeMax else 10f

        // 1. Dibujamos el robot en el centro (Punto Rojo)
        drawCircle(color = Color.Red, radius = 8f, center = Offset(centerX, centerY))

        // 2. Dibujamos anillos de referencia grises
        drawCircle(color = Color.LightGray, radius = (scan.rangeMax * 0.5f) * scale, center = Offset(centerX, centerY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
        drawCircle(color = Color.Gray, radius = scan.rangeMax * scale, center = Offset(centerX, centerY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))

        // 3. Dibujamos los puntos del láser
        scan.ranges.forEachIndexed { index, range ->
            // Filtramos puntos fuera de rango
            if (range in scan.rangeMin..scan.rangeMax) {
                // Ángulo en radianes para este punto
                val angle = scan.angleMin + (index * scan.angleIncrement)

                // Conversión de Polar a Cartesiano (ROS asume que "Hacia adelante" es el eje X, así que rotamos -90 grados para la pantalla)
                val screenX = centerX - (range * sin(angle) * scale)
                val screenY = centerY - (range * cos(angle) * scale)

                drawCircle(
                    color = Color(0xFF00FF00), // Verde brillante
                    radius = 3f,
                    center = Offset(screenX.toFloat(), screenY.toFloat())
                )
            }
        }
    }
}

@Composable
fun BatteryView(battery: BatterySensorData) {
    val progress = battery.percentage / 100f
    val statusText = when (battery.powerSupplyStatus) {
        1 -> "Cargando"
        2 -> "Descargando"
        3 -> "Completa"
        else -> "Desconocido"
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.weight(1f).height(12.dp),
            color = if (progress < 0.2f) Color.Red else Color.Green,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text("${String.format("%.1f", battery.percentage)}%", fontWeight = FontWeight.Bold)
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text("Voltaje: ${String.format("%.2f", battery.voltage)}V | Estado: $statusText", fontSize = 12.sp)
}

@Composable
fun ImuView(imu: ImuData) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Aceleración Lineal", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text("X: ${String.format("%.2f", imu.linearAcceleration.x)}")
            Text("Y: ${String.format("%.2f", imu.linearAcceleration.y)}")
            Text("Z: ${String.format("%.2f", imu.linearAcceleration.z)}")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("Vel. Angular", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text("X: ${String.format("%.2f", imu.angularVelocity.x)}")
            Text("Y: ${String.format("%.2f", imu.angularVelocity.y)}")
            Text("Z: ${String.format("%.2f", imu.angularVelocity.z)}")
        }
    }
}

@Composable
fun RangeView(rangeData: RangeSensorData) {
    // Calculamos cómo de cerca está del objeto respecto al rango del sensor
    val rangeSpan = rangeData.maxRange - rangeData.minRange
    val currentSpan = rangeData.range - rangeData.minRange
    val progress = if (rangeSpan > 0) (currentSpan / rangeSpan).coerceIn(0f, 1f) else 0f

    Text("Distancia detectada: ${String.format("%.2f", rangeData.range)}m", fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = if (progress < 0.2f) Color.Red else MaterialTheme.colorScheme.primary, // Se pone rojo si está muy cerca
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Min: ${rangeData.minRange}m", fontSize = 10.sp)
        Text("Max: ${rangeData.maxRange}m", fontSize = 10.sp)
    }
}

@Composable
fun PointCloudView(pc: PointCloud2Data) {
    Text(
        text = "☁️ Nube de Puntos Espacial (${pc.width}x${pc.height})",
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = pc.note,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 12.sp,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
    )
}