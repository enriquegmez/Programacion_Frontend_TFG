package com.enrique.tiago_app.ui.screens

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
import com.enrique.tiago_app.ui.logic.JointControlViewModel
import com.enrique.tiago_app.ui.logic.SensorViewModel
import com.enrique.tiago_app.ui.screens.JointControlScreen
import com.enrique.tiago_app.ui.screens.InvestigationScreen
import com.enrique.tiago_app.utils.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    controlViewModel: ControlViewModel,
    streamViewModel: StreamViewModel,
    playMotionViewModel: PlayMotionViewModel,
    investigationViewModel: InvestigationViewModel,
    jointControlViewModel: JointControlViewModel,
    sensorViewModel: SensorViewModel
) {
    // Observamos en qué pantalla estamos
    val currentScreen by mainViewModel.currentScreen.collectAsState()

    // ¡NUEVO! Lista de pantallas que admiten la división
    val splitAllowedScreens = listOf(AppScreen.TELEOP, AppScreen.PLAY_MOTION, AppScreen.ARTICULACIONES)

    // Estados locales para controlar la pantalla dividida
    var isSplitScreen by remember { mutableStateOf(false) }
    // ¡NUEVO! Estado para saber qué ver en la mitad de arriba (Por defecto Cámara)
    var topScreenSelection by remember { mutableStateOf(AppScreen.CAMERA) }

    // Herramientas para el menú lateral
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val movState by controlViewModel.movementState.collectAsState()
    val isTeleopActive = (movState == AppConstants.MovementState.ENVIANDO_INFO)

    val robotData by mainViewModel.robotCapabilities.collectAsState()
    val hasBase = robotData?.capabilities?.hasBase == true
    val hasCameras = robotData?.capabilities?.cameras?.isNotEmpty() == true
    val hasPlayMotion = robotData?.capabilities?.hasPlayMotion == true
    val hasJoints = robotData?.capabilities?.controlableJoints?.isNotEmpty() == true

    // ¡CAMBIO! Reseteo total al cambiar de pestaña
    LaunchedEffect(currentScreen) {
        // Se resetea la division y la selección superior al cambiar de pantalla
        isSplitScreen = false
        topScreenSelection = AppScreen.CAMERA

        // Si el robot estaba en movimiento en TELEOP, mandamos la señal de STOP al salir
        if (currentScreen != AppScreen.TELEOP && isTeleopActive) {
            controlViewModel.toggleTeleop(false)
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

                NavigationDrawerItem(
                    label = { Text("Dashboard (Inicio)") },
                    selected = currentScreen == AppScreen.DASHBOARD,
                    onClick = {
                        mainViewModel.navigateTo(AppScreen.DASHBOARD)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )

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

                NavigationDrawerItem(
                    label = { Text("Investigación (ROS 2)") },
                    selected = currentScreen == AppScreen.INVESTIGACION,
                    onClick = {
                        mainViewModel.navigateTo(AppScreen.INVESTIGACION)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                )

                NavigationDrawerItem(
                    label = { Text(if (hasJoints) "Mover Articulaciones" else "Articulaciones (No Disp.)") },
                    selected = currentScreen == AppScreen.ARTICULACIONES,
                    onClick = {
                        if (hasJoints) {
                            mainViewModel.navigateTo(AppScreen.ARTICULACIONES)
                            scope.launch { drawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    badge = { if (!hasJoints) Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )

                NavigationDrawerItem(
                    label = { Text("Telemetría (Sensores)") },
                    selected = currentScreen == AppScreen.SENSORES,
                    onClick = {
                        mainViewModel.navigateTo(AppScreen.SENSORES)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    icon = { Icon(Icons.Default.Info, contentDescription = null) }
                )

                Spacer(Modifier.weight(1f))

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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { // ¡CAMBIO! Solo mostramos el título si NO estamos en pantalla dividida
                        if (!isSplitScreen) {
                            Text(text = "Tiago App")
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Abrir Menú")
                        }
                    },
                    actions = {
                        val batteryPct = robotData?.status?.batteryPct
                        if (batteryPct != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val batteryColor = when {
                                    batteryPct > 50 -> Color(0xFF4CAF50)
                                    batteryPct > 25 -> Color(0xFFFFA000)
                                    else -> MaterialTheme.colorScheme.error
                                }

                                val isCharging = robotData?.status?.isCharging == true

                                Icon(
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
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }

                        // ¡NUEVO! EL INTERRUPTOR DE PANTALLA DIVIDIDA Y SELECTOR
                        val isSplitAllowed = currentScreen in splitAllowedScreens
                        if (isSplitAllowed) {

                            // Botón dinámico que solo aparece si la pantalla está dividida
                            if (isSplitScreen) {
                                TextButton(
                                    onClick = {
                                        topScreenSelection = if (topScreenSelection == AppScreen.CAMERA) AppScreen.SENSORES else AppScreen.CAMERA
                                    }
                                ) {
                                    Text(
                                        text = if (topScreenSelection == AppScreen.CAMERA) "Ver Sensores" else "Ver Cámara",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

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
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {

                // ==========================================
                // LÓGICA DE VISUALIZACIÓN DIVIDIDA
                // ==========================================
                if (isSplitScreen && currentScreen in splitAllowedScreens) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // MITAD SUPERIOR: Cámara o Sensores
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (topScreenSelection == AppScreen.CAMERA) {
                                if (hasCameras) {
                                    StreamView(
                                        streamViewModel = streamViewModel,
                                        cameraTopics = robotData?.capabilities?.cameraTopics ?: emptyList(),
                                        isCompact = true
                                    )
                                } else {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = "Sin Cámara",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("El robot no tiene cámaras disponibles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else if (topScreenSelection == AppScreen.SENSORES) {
                                // La pantalla de sensores se adapta automáticamente a la mitad del espacio
                                SensorScreen(viewModel = sensorViewModel)
                            }
                        }

                        HorizontalDivider(
                            thickness = 3.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )

                        // MITAD INFERIOR: La herramienta que estábamos usando
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (currentScreen) {
                                AppScreen.TELEOP -> JoystickView(controlViewModel = controlViewModel, teleopTopics = robotData?.capabilities?.teleopTopics ?: emptyList(), isCompact = true)
                                AppScreen.PLAY_MOTION -> PlayMotionScreen(viewModel = playMotionViewModel, isCompact = true)
                                AppScreen.ARTICULACIONES -> JointControlScreen(viewModel = jointControlViewModel)
                                else -> {}
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // MODO PANTALLA COMPLETA
                    // ==========================================
                    when (currentScreen) {
                        AppScreen.DASHBOARD -> DashboardView(mainViewModel)
                        AppScreen.TELEOP -> JoystickView(controlViewModel = controlViewModel, teleopTopics = robotData?.capabilities?.teleopTopics ?: emptyList())
                        AppScreen.CAMERA -> StreamView(streamViewModel = streamViewModel, cameraTopics = robotData?.capabilities?.cameraTopics ?: emptyList())
                        // ¡CAMBIO! Añadimos isCompact = true
                        AppScreen.PLAY_MOTION -> PlayMotionScreen(viewModel = playMotionViewModel)
                        AppScreen.INVESTIGACION -> InvestigationScreen(viewModel = investigationViewModel)
                        AppScreen.ARTICULACIONES -> JointControlScreen(viewModel = jointControlViewModel)
                        AppScreen.SENSORES -> SensorScreen(viewModel = sensorViewModel)
                    }
                }
            }
        }
    }
}

// ... [Aquí sigues dejando tu función DashboardView y CapabilityRow tal y como las tienes] ...

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