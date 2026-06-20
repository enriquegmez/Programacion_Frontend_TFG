package com.enrique.tiago_app.ui.screens // Ajusta a tu paquete

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Tus ViewModels
import com.enrique.tiago_app.ui.logic.ControlViewModel
import com.enrique.tiago_app.ui.logic.MainViewModel
import com.enrique.tiago_app.ui.logic.StreamViewModel
import com.enrique.tiago_app.ui.logic.AppScreen
import com.enrique.tiago_app.ui.logic.PlayMotionViewModel
import com.enrique.tiago_app.ui.logic.InvestigationViewModel
import com.enrique.tiago_app.utils.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    controlViewModel: ControlViewModel,
    streamViewModel: StreamViewModel, // ¡NUEVO! Añadimos el ViewModel del vídeo
    playMotionViewModel: PlayMotionViewModel,
    investigationViewModel: InvestigationViewModel // ¡NUEVO!
) {
    // Observamos en qué pantalla estamos
    val currentScreen by mainViewModel.currentScreen.collectAsState()

    // ¡NUEVO! Estado local para controlar si la pantalla está dividida o no
    var isSplitScreen by remember { mutableStateOf(false) }

    // Herramientas para el menú lateral
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // ¡NUEVO! Observamos si el joystick está mandando datos
    val movState by controlViewModel.movementState.collectAsState()
    val isTeleopActive = (movState == AppConstants.MovementState.ENVIANDO_INFO)

    // ¡NUEVO! Leemos los datos del robot para saber si habilitar los botones del menú
    val robotData by mainViewModel.robotCapabilities.collectAsState()
    val hasBase = robotData?.capabilities?.hasBase == true
    val hasCameras = robotData?.capabilities?.cameras?.isNotEmpty() == true

    val hasPlayMotion = robotData?.capabilities?.hasPlayMotion == true

    // ¡CAMBIO! Si salimos de la pantalla de Teleoperación, apagamos la división Y los motores
    LaunchedEffect(currentScreen) {
        if (currentScreen != AppScreen.TELEOP) {
            isSplitScreen = false // Apagamos el switch visual

            // Si el robot estaba en movimiento, mandamos la señal de STOP
            if (isTeleopActive) {
                controlViewModel.toggleTeleop(false)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(modifier = Modifier.width(300.dp)) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Panel de Control",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()

                // Opciones del Menú
                NavigationDrawerItem(
                    label = { Text("Dashboard (Inicio)") },
                    selected = currentScreen == AppScreen.DASHBOARD,
                    onClick = {
                        mainViewModel.navigateTo(AppScreen.DASHBOARD)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                // ¡NUEVO! Bloqueamos acceso si no hay base
                NavigationDrawerItem(
                    label = { Text(if (hasBase) "Teleoperación" else "Teleoperación (No Disp.)") },
                    selected = currentScreen == AppScreen.TELEOP,
                    onClick = {
                        if (hasBase) {
                            mainViewModel.navigateTo(AppScreen.TELEOP)
                            scope.launch { drawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    badge = { if (!hasBase) Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )

                // ¡NUEVO! Bloqueamos acceso si no hay cámaras
                NavigationDrawerItem(
                    label = { Text(if (hasCameras) "Cámara / Sensores" else "Cámaras (No Disp.)") },
                    selected = currentScreen == AppScreen.CAMERA,
                    onClick = {
                        if (hasCameras) {
                            mainViewModel.navigateTo(AppScreen.CAMERA)
                            scope.launch { drawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    badge = { if (!hasCameras) Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )

                // ¡NUEVO! Menú PlayMotion
                NavigationDrawerItem(
                    label = { Text(if (hasPlayMotion) "Movimientos Predefinidos" else "Movimientos (No Disp.)") },
                    selected = currentScreen == AppScreen.PLAY_MOTION,
                    onClick = {
                        if (hasPlayMotion) {
                            mainViewModel.navigateTo(AppScreen.PLAY_MOTION)
                            scope.launch { drawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    badge = { if (!hasPlayMotion) Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )

                // ¡NUEVO! Menú Investigación / Debug
                NavigationDrawerItem(
                    label = { Text("Investigación (ROS 2)") },
                    selected = currentScreen == AppScreen.INVESTIGACION, // Asegúrate de añadir esto a tu enum AppScreen
                    onClick = {
                        mainViewModel.navigateTo(AppScreen.INVESTIGACION)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) } // Puedes usar un icono de Search o Info
                )

                Spacer(Modifier.weight(1f)) // Empuja el botón de desconectar hacia abajo

                // Botón de desconexión general
                Button(
                    onClick = { mainViewModel.disconnectFromRobot() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Desconectar Robot")
                }
            }
        }
    ) {
        // EL ANDAMIO PRINCIPAL (Lo que se ve cuando el menú está cerrado)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(text = "Tiago App") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir Menú")
                        }
                    },
                    actions = {
                        // 1. EL INDICADOR DE BATERÍA GLOBAL
                        // Lo ponemos fuera del `if` para que se vea en todas las pantallas.
                        val batteryPct = robotData?.status?.batteryPct
                        if (batteryPct != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Cambiamos el color según el porcentaje
                                val batteryColor = when {
                                    batteryPct > 50 -> Color(0xFF4CAF50) // Verde
                                    batteryPct > 25 -> Color(0xFFFFA000) // Amarillo
                                    else -> MaterialTheme.colorScheme.error // Rojo (25 o menos)
                                }

                                val isCharging = robotData?.status?.isCharging == true

                                Icon(
                                    // Si está cargando pinta el Rayo, si no, la Pila. (Si no tiene sensor, isCharging será false siempre).
                                    imageVector = if (isCharging) Icons.Default.Bolt else Icons.Default.BatteryFull,
                                    contentDescription = "Batería",
                                    tint = if (isCharging) Color(0xFFFFEB3B) else batteryColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${batteryPct.toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = batteryColor
                                )
                                Spacer(modifier = Modifier.width(16.dp)) // Separación con el Switch (si lo hay)
                            }
                        }

                        // 2. EL INTERRUPTOR DE PANTALLA DIVIDIDA
                        // Solo se pinta si estamos en la pantalla de TELEOP
                        if (currentScreen == AppScreen.TELEOP) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Dividir",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Switch(
                                    checked = isSplitScreen,
                                    onCheckedChange = { isSplitScreen = it },
                                    modifier = Modifier.scale(0.8f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        ) { innerPadding ->
            // CONTENIDO VARIABLE SEGÚN EL MENÚ
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                // ¡NUEVO! Lógica de visualización
                if (isSplitScreen && currentScreen == AppScreen.TELEOP) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // MITAD SUPERIOR: CÁMARA O AVISO
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center // Centramos el contenido por si es texto
                        ) {
                            if (hasCameras) {
                                StreamView(
                                    streamViewModel = streamViewModel,
                                    cameraTopics = robotData?.capabilities?.cameraTopics
                                        ?: emptyList(),
                                    isCompact = true
                                )
                            } else {
                                // ¡NUEVO! Aviso elegante si no hay cámaras
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Sin Cámara",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "El robot no tiene cámaras disponibles",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            thickness = 3.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )

                        // MITAD INFERIOR: JOYSTICK
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            JoystickView(
                                controlViewModel = controlViewModel,
                                teleopTopics = robotData?.capabilities?.teleopTopics ?: emptyList(),
                                isCompact = true
                            )
                        }
                    }
                } else {
                    // MODO PANTALLA COMPLETA (El que teníamos antes)
                    when (currentScreen) {
                        AppScreen.DASHBOARD -> DashboardView(mainViewModel) // ¡AQUÍ ESTÁ LA MAGIA!
                        AppScreen.TELEOP -> JoystickView(controlViewModel = controlViewModel, teleopTopics = robotData?.capabilities?.teleopTopics ?: emptyList())
                        AppScreen.CAMERA -> StreamView(streamViewModel = streamViewModel, cameraTopics = robotData?.capabilities?.cameraTopics ?: emptyList())
                        AppScreen.PLAY_MOTION -> PlayMotionScreen(viewModel = playMotionViewModel)
                        AppScreen.INVESTIGACION -> InvestigationScreen(viewModel = investigationViewModel) // ¡NUEVO!
                    }
                }
            }
        }
    }
}

// ========================================================
// ¡NUEVO! EL COMPONENTE VISUAL DEL DASHBOARD
// ========================================================
@Composable
fun DashboardView(mainViewModel: MainViewModel) {
    val robotData by mainViewModel.robotCapabilities.collectAsState()

    // 1. ¡MUEVE ESTO AQUÍ ARRIBA!
    // Así la memoria del scroll sobrevive a las actualizaciones.
    val scrollState = rememberScrollState()

    // 2. Estado de Carga (Esperando JSON)
    if (robotData == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Escaneando hardware del robot...", style = MaterialTheme.typography.bodyLarge)
            }
        }
        return
    }

    val identity = robotData!!.identity
    val status = robotData!!.status
    val caps = robotData!!.capabilities!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            // 3. USA LA VARIABLE AQUÍ
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- SECCIÓN 1: ESTADO Y SALUD ---
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Salud del Sistema", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                // Batería
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val isCharging = status?.isCharging == true

                    Icon(
                        imageVector = if (isCharging) Icons.Default.Bolt else Icons.Default.BatteryFull,
                        contentDescription = "Batería",
                        tint = if (isCharging) Color(0xFFFFEB3B) else Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // El texto cambia mágicamente en cuanto enchufas el robot
                    Text(
                        text = if (isCharging) "Cargando... (${status?.batteryPct ?: "--"}%)"
                        else "Batería: ${status?.batteryPct ?: "--"}%"
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { ((status?.batteryPct ?: 0.0) / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF4CAF50)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Parada de Emergencia
                val isEStop = status?.eStopActive == true
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "E-Stop",
                        tint = if (isEStop) MaterialTheme.colorScheme.error else Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isEStop) "¡Parada de Emergencia ACTIVADA!" else "E-Stop Desactivado (Seguro)",
                        color = if (isEStop) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // --- SECCIÓN 2: IDENTIDAD EN RED ---
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = "Red")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Identidad de Red (ROS 2)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Hostname: ${identity?.hostname ?: "Desconocido"}")
                Text(text = "ROS Domain ID: ${identity?.domainId ?: "0"}")
            }
        }

        // --- SECCIÓN 3: CAPACIDADES HARDWARE ---
        Text("Capacidades Detectadas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

        // Lista de características usando un helper para simplificar código
        CapabilityRow("Base Móvil (Twist)", caps.hasBase)
        CapabilityRow("Manipulador (Brazo)", caps.hasManipulator)
        CapabilityRow("Cabeza", caps.hasHead)
        CapabilityRow("Torso", caps.hasTorso)
        CapabilityRow("Actuador Final (Gripper)", caps.hasGripper)
        CapabilityRow("LiDAR (LaserScan/PointCloud)", caps.hasLidar)
        CapabilityRow("IMU (Sensor Inercial)", caps.hasImu)
        CapabilityRow("Odometría", caps.hasOdom)
        CapabilityRow("Navegación (Nav2)", caps.hasNav)
        CapabilityRow("Planificación (MoveIt)", caps.hasMoveit)
        CapabilityRow("Movimientos grabados (PlayMotion)", caps.hasPlayMotion ?: false)
        CapabilityRow("Sensor de Fuerza-Par (F/T)", caps.hasFtSensor ?: false)

        // Resumen de Cámaras
        ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Cámaras Físicas: ${caps.cameras.size}", fontWeight = FontWeight.Bold)
                caps.cameras.forEach { cam ->
                    Text("• ${cam.name}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

// Helper composable para pintar una fila de característica con su tick verde o cruz roja
@Composable
fun CapabilityRow(name: String, available: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name, style = MaterialTheme.typography.bodyLarge)
        Icon(
            imageVector = if (available) Icons.Default.CheckCircle else Icons.Default.Close,
            contentDescription = if (available) "Disponible" else "No Disponible",
            tint = if (available) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
        )
    }
}