package com.enrique.tiago_app.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Tus ViewModels y utilidades
import com.enrique.tiago_app.ui.logic.ControlViewModel
import com.enrique.tiago_app.ui.logic.MainViewModel
import com.enrique.tiago_app.ui.logic.StreamViewModel
import com.enrique.tiago_app.ui.logic.AppScreen
import com.enrique.tiago_app.ui.logic.PlayMotionViewModel
import com.enrique.tiago_app.ui.logic.InvestigationViewModel
import com.enrique.tiago_app.ui.logic.JointControlViewModel
import com.enrique.tiago_app.ui.logic.SensorViewModel
import com.enrique.tiago_app.utils.AppConstants

// Componentes AXON (Asegúrate de que la ruta del paquete de los componentes sea correcta en tu proyecto)
import com.enrique.tiago_app.ui.components.AxonBottomBar
import com.enrique.tiago_app.ui.components.ScreenHeader
import com.enrique.tiago_app.ui.components.SplitSegmentedControl
import com.enrique.tiago_app.ui.components.SplitSourcePicker

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

    // Lista de pantallas que admiten la división
    val splitAllowedScreens = listOf(AppScreen.TELEOP, AppScreen.PLAY_MOTION, AppScreen.ARTICULACIONES)

    // Estados locales para controlar la pantalla dividida
    var isSplitScreen by remember { mutableStateOf(false) }
    var topScreenSelection by remember { mutableStateOf(AppScreen.CAMERA) }
    // Selector flotante Cámara/Sensores que aparece al pulsar "Dividida".
    var showSourcePicker by remember { mutableStateOf(false) }

    val movState by controlViewModel.movementState.collectAsState()
    val isTeleopActive = (movState == AppConstants.MovementState.ENVIANDO_INFO)

    val robotData by mainViewModel.robotCapabilities.collectAsState()
    val hasCameras = robotData?.capabilities?.cameras?.isNotEmpty() == true
    val hasBase = robotData?.capabilities?.hasBase == true
    val hasPlayMotion = robotData?.capabilities?.hasPlayMotion == true
    // Articulaciones controlables (mismo campo que usabas en el menú lateral antiguo)
    val hasJoints = robotData?.capabilities?.controlableJoints?.isNotEmpty() == true
    // Sensores: se descubren aparte (no vienen en capabilities). Si el ViewModel
    // expone la lista, la usamos; si no, dejamos Sensores siempre habilitado.
    val hasSensors = true // TODO: enlazar con availableSensors cuando se exponga en MainViewModel

    // Rutas habilitadas según el hardware detectado. Las que no estén aquí
    // aparecen en gris y no se puede navegar a ellas (ni en modo dividido).
    val enabledRoutes: Set<String> = buildSet {
        add("dashboard")                 // siempre
        add("invest")                    // análisis: siempre
        if (hasSensors) add("sensors")
        if (hasJoints) add("joints")
        if (hasBase) add("teleop")
        if (hasCameras) add("camera")
        if (hasPlayMotion) add("motion")
    }

    // Estado del velo blanco a pantalla completa cuando se abre un grupo del menú.
    var menuOpen by remember { mutableStateOf(false) }
    var closeMenuSignal by remember { mutableStateOf(0) }

    // Reseteo total al cambiar de pestaña
    LaunchedEffect(currentScreen) {
        isSplitScreen = false
        topScreenSelection = AppScreen.CAMERA

        if (currentScreen != AppScreen.TELEOP && isTeleopActive) {
            controlViewModel.toggleTeleop(false)
        }
    }

    // Texto de cabecera (eyebrow + título) por pantalla. La cabecera es común.
    val (headerEyebrow, headerTitle) = when (currentScreen) {
        AppScreen.DASHBOARD -> "Resumen" to "Dashboard"
        AppScreen.TELEOP -> "Control" to "Teleoperación"
        AppScreen.CAMERA -> "Datos" to "Cámara"
        AppScreen.PLAY_MOTION -> "Control" to "Acciones"
        AppScreen.INVESTIGACION -> "Datos" to "Investigación"
        AppScreen.ARTICULACIONES -> "Control" to "Articulaciones"
        AppScreen.SENSORES -> "Datos" to "Sensores"
    }

    // Adaptamos tu ENUM AppScreen a los String-Rutas del nuevo menú
    val currentRouteString = when (currentScreen) {
        AppScreen.DASHBOARD -> "dashboard"
        AppScreen.TELEOP -> "teleop"
        AppScreen.CAMERA -> "camera"
        AppScreen.PLAY_MOTION -> "motion"
        AppScreen.INVESTIGACION -> "invest"
        AppScreen.ARTICULACIONES -> "joints"
        AppScreen.SENSORES -> "sensors"
    }

    // Función puente para navegar usando tu MainViewModel
    fun navigateFromAxonMenu(route: String) {
        // Defensa extra: si la ruta está deshabilitada por capacidades, ignorar.
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
                // ==========================================
                // CABECERA COMÚN (RESUMEN / Título + batería)
                // ==========================================
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

                    // Controles de pantalla dividida (solo en pantallas que lo permiten)
                    val isSplitAllowed = currentScreen in splitAllowedScreens
                    if (isSplitAllowed) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            SplitSegmentedControl(
                                split = isSplitScreen,
                                onSplitChange = { split ->
                                    if (split) {
                                        // Entrar en dividido: apagamos sensores que pudieran seguir
                                        // activos y abrimos el selector flotante Cámara/Sensores.
                                        sensorViewModel.onScreenDisposed()
                                        isSplitScreen = true
                                        // Si no hay cámara, el panel por defecto es sensores.
                                        if (!hasCameras) topScreenSelection = AppScreen.SENSORES
                                        showSourcePicker = true
                                    } else {
                                        // Volver a pantalla completa: cerramos el selector.
                                        isSplitScreen = false
                                        showSourcePicker = false
                                    }
                                }
                            )
                            // El selector Cámara/Sensores ya no vive aquí fijo: aparece
                            // flotante (más abajo) al pulsar "Dividida".
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .padding(bottom = 96.dp)   // hueco para la barra flotante (que ya no está en el Scaffold)
            ) {

                // ==========================================
                // LÓGICA DE VISUALIZACIÓN DIVIDIDA (Intacta)
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
                                        Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("El robot no tiene cámaras", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else if (topScreenSelection == AppScreen.SENSORES) {
                                SensorScreen(viewModel = sensorViewModel, isCompact = true)
                            }
                        }

                        HorizontalDivider(thickness = 3.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))

                        // MITAD INFERIOR: La herramienta que estábamos usando
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
                    // ==========================================
                    // MODO PANTALLA COMPLETA
                    // ==========================================
                    when (currentScreen) {
                        AppScreen.DASHBOARD -> DashboardView(mainViewModel)
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

        // --- VELO BLANCO a PANTALLA COMPLETA cuando el menú está desplegado ---
        androidx.compose.animation.AnimatedVisibility(
            visible = menuOpen,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.85f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { closeMenuSignal++ }
            )
        }

        // --- BARRA FLOTANTE AXON (siempre por encima del velo del menú) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AxonBottomBar(
                currentRoute = currentRouteString,
                enabledRoutes = enabledRoutes,
                onNavigate = { navigateFromAxonMenu(it) },
                onDisconnect = { mainViewModel.disconnectFromRobot() },
                onOpenChange = { menuOpen = it },
                closeSignal = closeMenuSignal
            )
        }

        // --- SELECTOR FLOTANTE Cámara/Sensores (al pulsar "Dividida") ---
        // Se dibuja el ÚLTIMO para que su velo blanco cubra también la barra
        // inferior: mientras se elige la fuente, sólo se puede tocar el selector
        // (o el propio velo para cerrar), no las opciones del menú.
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