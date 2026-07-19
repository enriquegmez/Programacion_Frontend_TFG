/**
 * @file JointControlScreen.kt
 * @brief Pantalla de teleoperación de bajo nivel (Articulaciones individuales).
 * @details Permite la manipulación cinemática directa de motores y actuadores (ej. cabeza, torso).
 *          Implementa una arquitectura de interfaz adaptativa y bloqueos de seguridad visuales
 *          basados en las restricciones físicas del hardware (JointLimits).
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// --- IMPORTS DE LA LÓGICA Y COMPONENTES ---
import com.enrique.tiago_app.protocol.JointLimit
import com.enrique.tiago_app.ui.components.SteelCard
import com.enrique.tiago_app.ui.logic.JointControlViewModel
import com.enrique.tiago_app.ui.theme.MonoData
import com.enrique.tiago_app.ui.theme.MonoLabel

/**
 * @brief Renderiza el panel principal de control de articulaciones.
 * @details Utiliza una selección basada en "Chips" para que el usuario decida qué
 *          articulaciones quiere añadir a su panel de control activo.
 * @param viewModel Instancia del [JointControlViewModel] que inyecta la telemetría y recibe comandos.
 * @param isCompact Modo de renderizado. Si es true, ajusta el diseño para entornos de pantalla dividida.
 */
@Composable
fun JointControlScreen(viewModel: JointControlViewModel, isCompact: Boolean = false) {
    // ========================================================================
    // 1. OBSERVADORES DE ESTADO
    // ========================================================================
    val robotInfo by viewModel.capabilities.collectAsState()
    val activeJoints by viewModel.activeJoints.collectAsState()
    val jointValues by viewModel.jointValues.collectAsState()
    val cs = MaterialTheme.colorScheme

    // ========================================================================
    // 2. GESTIÓN DEL CICLO DE VIDA DE LA VISTA
    // ========================================================================
    // DisposableEffect se dispara automáticamente cuando este @Composable sale
    // del árbol de la interfaz (ej. el usuario navega a otra pantalla).
    // Garantiza que liberamos recursos en el backend (Garbage Collection de Red).
    DisposableEffect(Unit) {
        onDispose { viewModel.onScreenDisposed() }
    }

    // Extracción segura del árbol cinemático
    val controlableJoints = robotInfo?.capabilities?.controlableJoints ?: emptyList()

    Column(Modifier.fillMaxSize().padding(if (isCompact) 8.dp else 16.dp)) {

        // ========================================================================
        // 3. CABECERA ADAPTATIVA
        // ========================================================================
        // No mostramos el texto en pantalla dividida para ahorrar espacio
        if (!isCompact) {
            Text("Selecciona las articulaciones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
        }

        // Manejo de estado de error físico (Robot sin motores detectados)
        if (controlableJoints.isEmpty()) {
            Text("No se han detectado articulaciones móviles en este robot.", color = cs.error)
            return // Bloquea el renderizado de la UI restante
        }

        // ========================================================================
        // 4. SELECTOR DE MOTORES (Scroll Horizontal)
        // ========================================================================
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            controlableJoints.forEach { joint ->
                val selected = activeJoints.contains(joint.name)
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.toggleJoint(joint.name, !selected) },
                    label = {
                        // Feedback visual de hardware: Si no está motorizado, muestra candado
                        val labelText = if (joint.isActuated) joint.name else "${joint.name} 🔒"
                        Text(labelText)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = cs.secondaryContainer,
                        selectedLabelColor = cs.onSecondaryContainer
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp)); HorizontalDivider(color = cs.outline); Spacer(Modifier.height(16.dp))

        // ========================================================================
        // 5. PANEL DE CONTROL (Deslizadores Activos)
        // ========================================================================
        if (activeJoints.isEmpty()) {
            // Manejo de estado vacío (Empty State)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Activa al menos una articulación arriba para controlarla.", color = cs.onSurfaceVariant)
            }
        } else {
            // LazyColumn para asegurar rendimiento fluido si el usuario activa +10 articulaciones
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Filtramos la lista maestra de articulaciones dejando solo las seleccionadas
                val active = controlableJoints.filter { activeJoints.contains(it.name) }
                items(active) { jl ->

                    // Calculamos un valor seguro para el centro si los límites existen. Si son null, usamos 0f.
                    val centerFallback = if (jl.min != null && jl.max != null) {
                        (jl.min + jl.max) / 2f
                    } else {
                        0f
                    }

                    JointSliderItem(
                        jointLimit = jl,
                        // Resolución de valor (Prioridad):
                        // 1. Estado en uso -> 2. Posición actual del robot -> 3. Centro geométrico -> 4. Cero absoluto
                        currentValue = jointValues[jl.name] ?: jl.currentValue ?: centerFallback,
                        onValueChange = { viewModel.updateJointValue(jl.name, it) },
                        onDragFinished = { viewModel.onJointDragFinished(jl.name) }
                    )
                }
            }
        }
    }
}

/**
 * @brief Componente visual interactivo para controlar una articulación individual.
 * @details Integra un deslizador (Slider) ergonómico. Si la articulación es pasiva
 *          o si el backend reporta límites físicos inválidos/ausentes, la interfaz
 *          se bloquea por seguridad para evitar daños en el hardware.
 * @param jointLimit Estructura de datos que contiene las propiedades cinemáticas (nombre, min, max, estado).
 * @param currentValue Valor radiánico o lineal actual del motor.
 * @param onValueChange Callback invocado en tiempo real durante el arrastre del deslizador.
 * @param onDragFinished Callback invocado al soltar el deslizador, utilizado para enviar el comando definitivo a ROS 2.
 */
@Composable
fun JointSliderItem(
    jointLimit: JointLimit,
    currentValue: Float,
    onValueChange: (Float) -> Unit,
    onDragFinished: () -> Unit
) {
    // ========================================================================
    // PROGRAMACIÓN DEFENSIVA: PROTECCIÓN CONTRA PAYLOADS CORRUPTOS Y NULOS
    // ========================================================================
    // Extraemos los valores (asumiendo que podrían llegar como null desde la red)
    val minVal = jointLimit.min
    val maxVal = jointLimit.max

    // Lógica de invalidación: Es inválido si falta algún dato (null) o si min >= max
    val invalidLimits = minVal == null || maxVal == null || minVal >= maxVal

    // Si es inválido, creamos un rango "falso" (-1f a 1f) solo para que Compose
    // pueda renderizar el componente apagado sin colapsar la app (Crash).
    // Si es válido, usamos los valores reales con total seguridad (!!).
    val safeMin = if (invalidLimits) -1f else minVal!!
    val safeMax = if (invalidLimits) 1f else maxVal!!

    // Sujeción (Clamp)
    val value = currentValue.coerceIn(safeMin, safeMax)

    // El motor solo es controlable si tiene actuador físico Y conocemos sus límites exactos
    val isControllable = jointLimit.isActuated && !invalidLimits

    val cs = MaterialTheme.colorScheme

    SteelCard {
        // --- CABECERA DEL MOTOR ---
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val titleText = when {
                !jointLimit.isActuated -> "${jointLimit.name} (Pasiva)"
                invalidLimits -> "${jointLimit.name} ⚠️ (Datos nulos/erróneos)" // Informamos al usuario
                else -> jointLimit.name
            }

            Text(
                text = titleText,
                fontWeight = FontWeight.Bold,
                color = if (invalidLimits) cs.error else Color.Black,
                style = MaterialTheme.typography.titleMedium
            )
            Text(if (invalidLimits) "---" else String.format("%.2f", value), style = MonoData)
        }

        Spacer(Modifier.height(6.dp))

        // --- DESLIZADOR ---
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onDragFinished,
            valueRange = safeMin..safeMax,
            enabled = isControllable, // Se bloquea automáticamente si vino null
            colors = SliderDefaults.colors(
                thumbColor = cs.primary,
                activeTrackColor = cs.primary,
                inactiveTrackColor = cs.surfaceContainerHighest
            )
        )

        // --- METADATOS ---
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Min " + if (invalidLimits) "?" else String.format("%.2f", safeMin), style = MonoLabel, color = cs.onSurfaceVariant)
            Text("Max " + if (invalidLimits) "?" else String.format("%.2f", safeMax), style = MonoLabel, color = cs.onSurfaceVariant)
        }
    }
}