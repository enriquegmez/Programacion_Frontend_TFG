/**
 * @file AnimationScreen.kt
 * @brief Pantalla de bienvenida animada.
 * @details Primera vista de la aplicación que muestra una animación del logo de la app
 *          durante el arranque inicial antes de ceder el control al grafo de navegación.
 * @author Enrique Gómez
 * @date 2026
 */

package com.enrique.tiago_app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.enrique.tiago_app.R

/**
 * @brief Renderiza la vista de inicialización mediante una animación Lottie.
 * @details Se apoya en una corrutina (LaunchedEffect) conectada al estado de progreso
 *          de la animación. Una vez el frame final es alcanzado, dispara el evento
 *          para transicionar a la pantalla de conexión.
 * @param onAnimationFinished Callback que se invoca de forma asíncrona
 *                            cuando la animación vectorial alcanza el 100% de su progreso.
 */
@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {

    // ========================================================================
    // 1. CARGA DE RECURSOS VECTORIALES
    // ========================================================================
    // Se deserializa el archivo JSON alojado en res/raw. Compose "recuerda" esta composición
    // para no recargar y parsear el JSON de nuevo en cada recomposición de la pantalla.
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.logo_animation)
    )

    // ========================================================================
    // 2. MÁQUINA DE ESTADOS DE LA ANIMACIÓN
    // ========================================================================
    // Vincula el avance del tiempo en Compose con el progreso de la animación (0.0f a 1.0f).
    // Se configura para una única iteración (sin bucle infinito) a velocidad normal (1.0f).
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        speed = 1.0f
    )

    // ========================================================================
    // 3. GESTIÓN DE EFECTOS SECUNDARIOS
    // ========================================================================
    // LaunchedEffect reacciona cada vez que el valor de 'progress' cambia.
    LaunchedEffect(progress) {
        if (progress == 1f) { // 1f = 100% (Animación terminada)
            onAnimationFinished() // Disparamos la transición hacia el LoginScreen
        }
    }

    // ========================================================================
    // 4. RENDERIZADO VISUAL
    // ========================================================================
    // Contenedor padre que ocupa toda la superficie disponible y centra su contenido
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Lienzo que pinta los vectores de la composición interpolando según el progreso actual
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(280.dp) // Tamaño relativo escalable
        )
    }
}