package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enrique.tiago_app.ui.components.*
import com.enrique.tiago_app.ui.logic.MainViewModel
import com.enrique.tiago_app.ui.theme.MonoData
import com.enrique.tiago_app.ui.theme.MonoLabel

@Composable
fun DashboardView(mainViewModel: MainViewModel) {
    val robotData by mainViewModel.robotCapabilities.collectAsState()

    // ¡MAGIA! Observamos la telemetría real del PC tal y como haces en el Lobby
    val telemetry by mainViewModel.hostTelemetry.collectAsState()

    val scrollState = rememberScrollState()
    val cs = MaterialTheme.colorScheme

    if (robotData == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = cs.primary)
                Spacer(Modifier.height(16.dp))
                Text("Escaneando hardware del robot...", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val identity = robotData?.identity
    val status = robotData?.status
    val caps = robotData?.capabilities ?: return

    val batPct = status?.batteryPct ?: 0.0
    val charging = status?.isCharging == true

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // --- HERO: anillo de batería + estado de conexión ---
        SteelCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetricRing(
                    progress = (batPct / 100.0).toFloat(),
                    valueText = batPct.toInt().toString(),
                    label = "Batería",
                    color = if (charging) cs.secondary else cs.tertiary,
                    diameter = 104.dp, stroke = 9.dp,
                    valueStyle = MonoData.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold),
                )
                Spacer(Modifier.width(18.dp))
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("UNIDAD CONECTADA", style = MonoLabel, color = cs.onSurfaceVariant)
                    Text(identity?.hostname ?: "—", style = MonoData, color = cs.onSurface, fontWeight = FontWeight.ExtraBold)

                    ScrollableRowSeguro {
                        StatusPill(
                            text = if (charging) "Cargando" else "En línea",
                            color = if (charging) cs.secondary else cs.tertiary
                        )
                        when (status?.eStopActive) {
                            true -> StatusPill("¡E-STOP ACTIVADO!", cs.error)
                            false -> StatusPill("E-Stop seguro", cs.tertiary)
                            null -> StatusPill("E-Stop ?", cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        // --- MÉTRICAS DEL HOST (Con datos reales de tu backend) ---
        if (telemetry != null) {
            val safeCpu = telemetry!!.cpuPct?.toInt() ?: 0
            val safeRam = telemetry!!.ramPct?.toInt() ?: 0
            val safeTemp = telemetry!!.tempC?.toInt() ?: 0

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HostMetricCard("CPU", safeCpu, "%", cs.primary, threshold = 85, modifier = Modifier.weight(1f))
                HostMetricCard("RAM", safeRam, "%", cs.primary, threshold = 85, modifier = Modifier.weight(1f))
                HostMetricCard("Temp", safeTemp, "°", cs.tertiary, threshold = 90, modifier = Modifier.weight(1f))
            }

        }

        // --- IDENTIDAD DE RED ---
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = cs.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, cs.outline)
        ) {
            Column(Modifier.padding(16.dp)) {
                SectionTitle("Identidad de Red (ROS 2)", icon = Icons.Default.Language)
                Spacer(Modifier.height(11.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniField("Hostname", identity?.hostname ?: "—", Modifier.weight(1f))
                    MiniField("Domain ID", (identity?.domainId ?: 0).toString(), Modifier.weight(1f))
                }
            }
        }

        // --- CAPACIDADES como AssistChips ---
        val capList = listOf(
            "Base Móvil" to caps.hasBase,
            "Brazo" to caps.hasManipulator,
            "Cabeza" to caps.hasHead,
            "Torso" to caps.hasTorso,
            "Gripper" to caps.hasGripper,
            "LiDAR" to caps.hasLidar,
            "IMU" to caps.hasImu,
            "Odometría" to caps.hasOdom,
            "Nav2" to caps.hasNav,
            "MoveIt" to caps.hasMoveit,
            "PlayMotion" to (caps.hasPlayMotion ?: false),
            "Sensor F/T" to (caps.hasFtSensor ?: false),
        )
        val okCount = capList.count { it.second }
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Capacidades detectadas", style = MaterialTheme.typography.titleMedium)
                Text("$okCount/${capList.size}", style = MonoLabel, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(10.dp))

            WrapRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalGap = 8.dp,
                verticalGap = 8.dp
            ) {
                capList.forEach { (name, ok) -> CapabilityChip(name, ok) }
            }
        }

        // --- CÁMARAS ---
        Surface(shape = MaterialTheme.shapes.large, color = cs.primaryContainer) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Videocam, null, tint = cs.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Cámaras físicas · ${caps.cameras.size}", fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer)
                }
                Spacer(Modifier.height(10.dp))

                WrapRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalGap = 8.dp,
                    verticalGap = 8.dp
                ) {
                    caps.cameras.forEach { CapabilityChip(it.name, true) }
                }
            }
        }
    }
}

@Composable
private fun HostMetricCard(label: String, value: Int, unit: String, color: androidx.compose.ui.graphics.Color, threshold: Int, modifier: Modifier = Modifier) {

    // Si el valor supera el umbral, el anillo se pinta en rojo (alerta).
    val isHigh = value >= threshold
    val ringColor = if (isHigh) MaterialTheme.colorScheme.error else color


    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(Modifier.padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
            MetricRing(
                progress = value / 100f,
                valueText = "$value$unit",
                label = label,
                color = ringColor,
                diameter = 58.dp, stroke = 6.dp,
                valueStyle = MonoData.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun MiniField(label: String, value: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(top = 8.dp)) { // Margen superior para dejar espacio al título
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), // Un poco más transparente para resaltar el texto
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            // El texto de valor interior ahora es más pequeño
            Text(
                text = value,
                style = MonoData.copy(fontSize = 14.sp), // Tamaño reducido (ajústalo si lo necesitas aún más pequeño)
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp)
            )
        }

        // Título que "corta" la línea superior
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 10.dp)
                .offset(y = (-8).dp) // Sube el texto justo a la altura de la línea
                .background(MaterialTheme.colorScheme.surface) // Fondo igual al de su contenedor para tapar la línea
                .padding(horizontal = 4.dp) // Pequeño respiro a los lados del texto
        )
    }
}

@Composable
private fun ScrollableRowSeguro(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        content()
    }
}