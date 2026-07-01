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
 * Pantalla de bienvenida (Splash) con animación Lottie.
 * Reproduce la animación del logo una vez y, al terminar, llama a
 * [onAnimationFinished] para que la navegación pase a la pantalla real.
 */
@Composable
fun SplashScreen(onAnimationFinished: () -> Unit) {
    // Cargamos el JSON desde res/raw
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.logo_animation)
    )

    // Controla la reproducción: una sola pasada
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,          // 1 = se reproduce una vez. Usa LottieConstants.IterateForever para bucle
        speed = 1.0f
    )

    // Cuando la animación llega al final (progress == 1f), avisamos
    LaunchedEffect(progress) {
        if (progress == 1f) {
            onAnimationFinished()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(280.dp)
        )
    }
}