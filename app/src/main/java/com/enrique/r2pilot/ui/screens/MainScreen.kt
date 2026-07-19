/**
 * @file MainScreen.kt
 * @brief Orquestador principal de la interfaz gráfica.
 * @details Gestiona el enrutamiento entre los distintos módulos de la aplicación,
 *          la adaptación condicional de la interfaz según el hardware detectado,
 *          y el sistema multitarea de pantalla dividida para operaciones simultáneas.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.r2pilot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// --- IMPORTS DE LA LÓGICA DE NEGOCIO ---
import com.enrique.r2pilot.ui.logic.ControlViewModel
import com.enrique.r2pilot.ui.logic.MainViewModel
import com.enrique.r2pilot.ui.logic.StreamViewModel
import com.enrique.r2pilot.ui.logic.AppScreen
import com.enrique.r2pilot.ui.logic.PlayMotionViewModel
import com.enrique.r2pilot.ui.logic.InvestigationViewModel
import com.enrique.r2pilot.ui.logic.JointControlViewModel
import com.enrique.r2pilot.ui.logic.SensorViewModel
import com.enrique.r2pilot.utils.AppConstants

// --- IMPORTS DE COMPONENTES VISUALES ---
import com.enrique.r2pilot.ui.components.BottomBar
import com.enrique.r2pilot.ui.components.ScreenHeader
import com.enrique.r2pilot.ui.components.SplitSegmentedControl
import com.enrique.r2pilot.ui.components.SplitSourcePicker

/**
 * @brief Componente raíz que aloja el marco estructural de la app tras la conexión.
 * @details Recibe todos los ViewModels del sistema y los inyecta en sus respectivas vistas
 *          hijas. Implementa el patrón "State Hoisting" centralizando el estado de navegación
 *          y la distribución de capas (Z-Index) para menús flotantes.
 *
 * @param mainViewModel Gestión global de la app, estado de red y capacidades de hardware del robot.
 * @param controlViewModel Lógica de teleoperación (joystick y envío de comandos de velocidad).
 * @param streamViewModel Gestión de la recepción, decodificación y renderizado de vídeo ROS 2.
 * @param playMotionViewModel Lógica para la ejecución de macros y movimientos pregrabados.
 * @param investigationViewModel Herramientas de análisis de datos y telemetría avanzada.
 * @param jointControlViewModel Control cinemático directo de articulaciones individuales.
 * @param sensorViewModel Lectura y visualización de telemetría de sensores en tiempo real.
 */
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
    // ========================================================================
    // 1. OBSERVADORES DE NAVEGACIÓN Y CAPACIDADES
    // ========================================================================
    val currentScreen by mainViewModel.currentScreen.collectAsState()

    // Rutas operativas que soportan el modo multitarea (Split-Screen)
    val splitAllowedScreens = listOf(AppScreen.TELEOP, AppScreen.PLAY_MOTION, AppScreen.ARTICULACIONES)

    // Estados de control de la pantalla dividida
    var isSplitScreen by remember { mutableStateOf(false) }
    var topScreenSelection by remember { mutableStateOf(AppScreen.CAMERA) }
    var showSourcePicker by remember { mutableStateOf(false) }

    // Estado del hardware para adaptar la UI
    val movState by controlViewModel.movementState.collectAsState()
    val isTeleopActive = (movState == AppConstants.MovementState.ENVIANDO_INFO)
    val robotData by mainViewModel.robotCapabilities.collectAsState()

    val hasCameras = robotData?.capabilities?.cameras?.isNotEmpty() == true
    val hasBase = robotData?.capabilities?.hasBase == true
    val hasPlayMotion = robotData?.capabilities?.hasPlayMotion == true
    val hasJoints = robotData?.capabilities?.controlableJoints?.isNotEmpty() == true
    val hasSensors = true // TODO: enlazar con availableSensors cuando se exponga en MainViewModel

    // ========================================================================
    // 2. ENRUTAMIENTO CONDICIONAL (Hardware-Agnostic UI)
    // ========================================================================
    // Construcción dinámica de accesos: Si el robot no reporta una capacidad,
    // la ruta queda inhabilitada por defecto para prevenir pantallas vacías o errores.
    val enabledRoutes: Set<String> = buildSet {
        add("dashboard")
        add("invest")
        if (hasSensors) add("sensors")
        if (hasJoints) add("joints")
        if (hasBase) add("teleop")
        if (hasCameras) add("camera")
        if (hasPlayMotion) add("motion")
    }

    var menuOpen by remember { mutableStateOf(false) }
    var closeMenuSignal by remember { mutableIntStateOf(0) }

    // ========================================================================
    // 3. LIMPIEZA DE ESTADO DE NAVEGACIÓN
    // ========================================================================
    // Resetea el estado de layout y por seguridad desactiva los motores si el
    // usuario abandona la pantalla de teleoperación.
    LaunchedEffect(currentScreen) {
        isSplitScreen = false
        topScreenSelection = AppScreen.CAMERA

        if (currentScreen != AppScreen.TELEOP && isTeleopActive) {
            controlViewModel.toggleTeleop(false)
        }
    }

    // Traducción semántica de la ruta actual a etiquetas legibles
    val (headerEyebrow, headerTitle) = when (currentScreen) {
        AppScreen.DASHBOARD -> "Resumen" to "Dashboard"
        AppScreen.TELEOP -> "Control" to "Teleoperación"
        AppScreen.CAMERA -> "Datos" to "Cámara"
        AppScreen.PLAY_MOTION -> "Control" to "Acciones"
        AppScreen.INVESTIGACION -> "Datos" to "Investigación"
        AppScreen.ARTICULACIONES -> "Control" to "Articulaciones"
        AppScreen.SENSORES -> "Datos" to "Sensores"
    }

    val currentRouteString = when (currentScreen) {
        AppScreen.DASHBOARD -> "dashboard"
        AppScreen.TELEOP -> "teleop"
        AppScreen.CAMERA -> "camera"
        AppScreen.PLAY_MOTION -> "motion"
        AppScreen.INVESTIGACION -> "invest"
        AppScreen.ARTICULACIONES -> "joints"
        AppScreen.SENSORES -> "sensors"
    }

    /**
     * @brief Traduce y enruta las peticiones de navegación desde el menú base.
     * @details Actúa como pasarela de seguridad antes de cambiar
     *          el estado global de la vista. Verifica que la ruta solicitada esté soportada
     *          por el hardware detectado (lista [enabledRoutes]). Si no lo está, la ignora.
     * @param route Cadena de texto representativa del destino elegido en la UI (ej. "teleop").
     */
    fun navigateFromMenu(route: String) {
        // Lógica defensiva: Bloquea intentos de navegación a hardware inexistente
        if (route !in enabledRoutes) return

        val nextScreen = when (route) {
            "dashboard" -> AppScreen.DASHBOARD
            "teleop" -> AppScreen.TELEOP
            "camera" -> AppScreen.CAMERA
            "motion" -> AppScreen.PLAY_MOTION
            "invest" -> AppScreen.INVESTIGACION
            "joints" -> AppScreen.ARTICULACIONES
            "sensors" -> AppScreen.SENSORES
            else -> AppScreen.DASHBOARD
        }
        mainViewModel.navigateTo(nextScreen)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // ========================================================================
                // 4. CABECERA GLOBAL Y GESTIÓN DE BATERÍA
                // ========================================================================
                val batteryPct = robotData?.status?.batteryPct
                val isCharging = robotData?.status?.isCharging == true

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                ) {
                    ScreenHeader(
                        eyebrow = headerEyebrow,
                        title = headerTitle,
                        batteryPct = batteryPct,
                        isCharging = isCharging
                    )

                    // Despliegue de controles de Split-Screen solo en rutas permitidas
                    val isSplitAllowed = currentScreen in splitAllowedScreens
                    if (isSplitAllowed) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            SplitSegmentedControl(
                                split = isSplitScreen,
                                onSplitChange = { split ->
                                    if (split) {
                                        sensorViewModel.onScreenDisposed()
                                        isSplitScreen = true
                                        // Ajuste inteligente: si no hay cámara, salta directo a sensores
                                        if (!hasCameras) topScreenSelection = AppScreen.SENSORES
                                        showSourcePicker = true
                                    } else {
                                        isSplitScreen = false
                                        showSourcePicker = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(bottom = 96.dp)
            ) {
                // ========================================================================
                // 5. MOTOR DE RENDERIZADO BIFURCADO (Split vs Full Screen)
                // ========================================================================
                if (isSplitScreen && currentScreen in splitAllowedScreens) {
                    Column(modifier = Modifier.fillMaxSize()) {

                        // --- MITAD SUPERIOR: Conciencia Situacional (Visual o Sensorial) ---
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            if (topScreenSelection == AppScreen.CAMERA) {
                                if (hasCameras) {
                                    StreamView(
                                        streamViewModel = streamViewModel,
                                        cameraTopics = robotData?.capabilities?.cameraTopics ?: emptyList(),
                                        isCompact = true
                                    )
                                } else {
                                    // Empty State: Fallback visual si falla la detección hardware
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("El robot no tiene cámaras", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else if (topScreenSelection == AppScreen.SENSORES) {
                                SensorScreen(viewModel = sensorViewModel, isCompact = true)
                            }
                        }

                        // Divisor semántico entre Contexto y Acción
                        HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))

                        // --- MITAD INFERIOR: Capa de Actuación ---
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (currentScreen) {
                                AppScreen.TELEOP -> JoystickView(controlViewModel = controlViewModel, teleopTopics = robotData?.capabilities?.teleopTopics ?: emptyList(), isCompact = true)
                                AppScreen.PLAY_MOTION -> PlayMotionScreen(viewModel = playMotionViewModel, isCompact = true)
                                AppScreen.ARTICULACIONES -> JointControlScreen(viewModel = jointControlViewModel, isCompact = true)
                                else -> {}
                            }
                        }
                    }
                } else {
                    // --- MODO PANTALLA COMPLETA ---
                    when (currentScreen) {
                        AppScreen.DASHBOARD -> DashboardScreen(mainViewModel)
                        AppScreen.TELEOP -> JoystickView(controlViewModel = controlViewModel, teleopTopics = robotData?.capabilities?.teleopTopics ?: emptyList())
                        AppScreen.CAMERA -> StreamView(streamViewModel = streamViewModel, cameraTopics = robotData?.capabilities?.cameraTopics ?: emptyList())
                        AppScreen.PLAY_MOTION -> PlayMotionScreen(viewModel = playMotionViewModel)
                        AppScreen.INVESTIGACION -> InvestigationScreen(viewModel = investigationViewModel)
                        AppScreen.ARTICULACIONES -> JointControlScreen(viewModel = jointControlViewModel)
                        AppScreen.SENSORES -> SensorScreen(viewModel = sensorViewModel)
                    }
                }
            }
        }

        // ========================================================================
        // 6. CAPAS DE SEGURIDAD (Prevención de Toques Accidentales)
        // ========================================================================

        // Velo Blanco del Menú de Navegación
        androidx.compose.animation.AnimatedVisibility(
            visible = menuOpen,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
                    // Atrapa los clics fuera del menú para cerrarlo y evitar clics "fantasma" en el fondo
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { closeMenuSignal++ }
            )
        }

        // Componente Menú Inferior Flotante
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            BottomBar(
                currentRoute = currentRouteString,
                enabledRoutes = enabledRoutes,
                onNavigate = { navigateFromMenu(it) },
                onDisconnect = { sensorViewModel.clearTrail(); mainViewModel.disconnectFromRobot() },
                onOpenChange = { menuOpen = it },
                closeSignal = closeMenuSignal
            )
        }

        // Velo Selector de Fuente (Split-Screen)
        // Se dibuja el último en el árbol para asegurar la mayor jerarquía visual
        SplitSourcePicker(
            visible = showSourcePicker,
            cameraEnabled = hasCameras,
            onSelectCamera = {
                sensorViewModel.onScreenDisposed()
                topScreenSelection = AppScreen.CAMERA
                showSourcePicker = false
            },
            onSelectSensors = {
                topScreenSelection = AppScreen.SENSORES
                showSourcePicker = false
            },
            onDismiss = { showSourcePicker = false }
        )
    }
}