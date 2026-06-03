package com.enrique.tiago_app.ui.screens // Ajusta a tu paquete

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Tus ViewModels
import com.enrique.tiago_app.ui.logic.ControlViewModel
import com.enrique.tiago_app.ui.logic.MainViewModel
import com.enrique.tiago_app.ui.logic.StreamViewModel // ¡NUEVO! Importamos el StreamViewModel
import com.enrique.tiago_app.ui.logic.AppScreen // El Enum que añadimos al MainViewModel
import com.enrique.tiago_app.utils.AppConstants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    mainViewModel: MainViewModel,
    controlViewModel: ControlViewModel,
    streamViewModel: StreamViewModel // ¡NUEVO! Añadimos el ViewModel del vídeo
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
                NavigationDrawerItem(
                    label = { Text("Teleoperación") },
                    selected = currentScreen == AppScreen.TELEOP,
                    onClick = {
                        mainViewModel.navigateTo(AppScreen.TELEOP)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Ver Cámara / Sensores") },
                    selected = currentScreen == AppScreen.CAMERA,
                    onClick = {
                        mainViewModel.navigateTo(AppScreen.CAMERA)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
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
                    // ¡NUEVO! Añadimos el interruptor de Pantalla Dividida arriba a la derecha
                    actions = {
                        // Solo mostramos el botón si NO estamos en el Dashboard
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
                                    modifier = Modifier.scale(0.8f) // Lo hacemos un pelín más pequeño
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
                    // MODO PANTALLA DIVIDIDA: Una columna con dos cajas que ocupan el 50% cada una
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Mitad superior: Vídeo
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            StreamView(streamViewModel = streamViewModel, isCompact = true)
                        }

                        // Una línea divisoria bonita en el medio
                        HorizontalDivider(
                            thickness = 3.dp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )

                        // Mitad inferior: Joystick
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            JoystickView(controlViewModel = controlViewModel, isCompact = true)
                        }
                    }
                } else {
                    // MODO PANTALLA COMPLETA (El que teníamos antes)
                    when (currentScreen) {
                        AppScreen.DASHBOARD -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Información del Robot", style = MaterialTheme.typography.headlineSmall)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("(Próximamente...)", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        AppScreen.TELEOP -> {
                            JoystickView(controlViewModel = controlViewModel)
                        }
                        AppScreen.CAMERA -> {
                            StreamView(streamViewModel = streamViewModel)
                        }
                    }
                }
            }
        }
    }
}