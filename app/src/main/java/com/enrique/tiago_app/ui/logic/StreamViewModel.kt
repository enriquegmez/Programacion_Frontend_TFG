package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enrique.tiago_app.utils.AppConstants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.enrique.tiago_app.logic.ProtocolDirector

class StreamViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // ==========================================
    // ESTADOS OBSERVABLES POR LA INTERFAZ
    // ==========================================

    // 1. Exponemos el estado de la cámara (IDLE, RECIBIENDO_STREAM...) para la UI
    val monitorState: StateFlow<String> = director.stateManager.monitorState

    // 2. La variable mágica que guardará la URL del vídeo
    private val _streamUrl = MutableStateFlow<String?>(null)
    val streamUrl: StateFlow<String?> = _streamUrl.asStateFlow()

    // 3. Preferencias del usuario (Con valores por defecto)
    // ¡AÑADIDO! El recurso para elegir qué sensor ver
    private val _currentResource = MutableStateFlow("camera")
    val currentResource: StateFlow<String> = _currentResource.asStateFlow()

    private val _currentTopic = MutableStateFlow("/head_front_camera/rgb/image_raw")
    val currentTopic: StateFlow<String> = _currentTopic.asStateFlow()

    private val _currentQuality = MutableStateFlow(AppConstants.CameraQuality.MEDIUM)
    val currentQuality: StateFlow<String> = _currentQuality.asStateFlow()


    // ==========================================
    // INICIALIZACIÓN
    // ==========================================
    init {
        viewModelScope.launch {
            director.cameraStreamUrl.collect { url ->
                _streamUrl.value = url
            }
        }

        viewModelScope.launch {
            monitorState.collect { state ->
                if (state == AppConstants.MonitorState.IDLE) {
                    _streamUrl.value = null
                }
            }
        }
    }

    // ==========================================
    // ACCIONES QUE PUEDE LLAMAR LA INTERFAZ (BOTONES)
    // ==========================================

    fun toggleStream() { // Renombrado para que sirva para cualquier sensor
        if (monitorState.value == AppConstants.MonitorState.IDLE) {
            director.sendStartStream(
                resource = _currentResource.value,
                topic = _currentTopic.value,
                quality = _currentQuality.value
            )
        } else if (monitorState.value == AppConstants.MonitorState.RECIBIENDO_STREAM) {
            director.sendStopStream(resource = _currentResource.value)
        }
    }

    // ¡AÑADIDO! Función para mostrar errores locales de formulario vacío
    fun showValidationError(message: String) {
        director.stateManager.showSystemAlert(message)
    }

    // ¡AÑADIDO! Para cuando el usuario cambie de sensor en la interfaz
    fun updateResource(newResource: String) {
        _currentResource.value = newResource
    }

    fun updateTopic(newTopic: String) {
        _currentTopic.value = newTopic
    }

    fun updateQuality(newQuality: String) {
        _currentQuality.value = newQuality
    }
}