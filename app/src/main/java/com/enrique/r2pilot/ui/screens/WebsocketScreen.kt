/**
 * @file WebsocketScreen.kt
 * @brief Interfaz de acceso y configuración de la topología de red.
 * @details Actúa como punto de entrada de la aplicación. Recoge los parámetros físicos
 *          de red (IP y Puerto) necesarios para establecer la pasarela de comunicación bidireccional
 *          (WebSocket) con el servidor que corre en el robot.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

// --- IMPORTS DE ARQUITECTURA Y COMPONENTES ---
import com.enrique.r2pilot.ui.components.PrimaryActionButton
import com.enrique.r2pilot.ui.logic.MainViewModel
import com.enrique.r2pilot.utils.AppConstants

/**
 * @brief Pantalla principal de conexión.
 * @param viewModel Instancia de la capa lógica de negocio (MainViewModel) que inyecta
 *                  el estado reactivo y expone los manejadores de eventos.
 */
@Composable
fun WebsocketScreen(viewModel: MainViewModel) {
    // ========================================================================
    // 1. OBSERVADORES DE ESTADO
    // ========================================================================
    // La vista reacciona instantáneamente a los cambios del modelo subyacente.
    val ip by viewModel.ipAddress.collectAsState()
    val port by viewModel.port.collectAsState()
    val globalState by viewModel.globalState.collectAsState()

    // ========================================================================
    // 2. VARIABLES DE ESTADO DERIVADO
    // ========================================================================
    // Determina si hay un proceso de handshaking en curso.
    val isConnecting = (globalState == AppConstants.GlobalState.ESPERANDO_CONEXION_BACKEND)

    // Validamos la IP (permitimos ipv4, ipv6 y dominios
    val isIpValid = ip.isNotBlank() && !ip.contains(" ")

    // Validamos el puerto (debe ser un número entre 1 y 65535)
    val portInt = port.toIntOrNull()
    val isPortValid = portInt != null && portInt in 1..65535

    // El botón solo se activará si no está cargando Y los formatos son correctos
    val canConnect = !isConnecting && isIpValid && isPortValid

    // ========================================================================
    // 3. ESTRUCTURA DE LA INTERFAZ
    // ========================================================================
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // --- CABECERA ---
        Text(
            text = "Conexión Websocket",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- FORMULARIO DE ENRUTAMIENTO: Dirección IP ---
        OutlinedTextField(
            value = ip,
            onValueChange = { newValue -> viewModel.onIpChange(newValue) },
            label = { Text("Dirección IP") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isConnecting,

            // Lógica visual de error
            isError = !isIpValid && ip.isNotEmpty(), // Se pone rojo si es inválido (y no está vacío)
            supportingText = {
                if (!isIpValid && ip.isNotEmpty()) {
                    Text("Formato de IP incorrecto (Ej: 192.168.1.10)")
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- FORMULARIO DE ENRUTAMIENTO: Puerto TCP ---
        OutlinedTextField(
            value = port,
            onValueChange = { newValue -> viewModel.onPortChange(newValue) },
            label = { Text("Puerto") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f),
            enabled = !isConnecting,

            // Lógica visual de error
            isError = !isPortValid && port.isNotEmpty(),
            supportingText = {
                if (!isPortValid && port.isNotEmpty()) {
                    Text("Debe ser un número entre 1 y 65535")
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- DESPACHO DE ACCIÓN ---
        PrimaryActionButton(
            text = "Abrir WebSocket",
            onClick = { viewModel.connectToWebSocket() },
            modifier = Modifier.fillMaxWidth(0.8f),

            // Usamos nuestra variable combinada
            enabled = canConnect,
            loading = isConnecting,
            onContainer = Color.White
        )
    }
}