package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import com.enrique.tiago_app.logic.ProtocolDirector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SensorViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // 1. La "Carta del Menú" (Lista de sensores detectados por ROS 2)
    val availableSensors = director.availableSensors

    // 2. El mapa en tiempo real con los datos puros para pintar las gráficas
    val activeSensorData = director.activeSensorData

    // 3. Guardamos qué sensores tienen el "tick" puesto en la UI (Set de topics)
    private val _activeSensorTopics = MutableStateFlow<Set<String>>(emptySet())
    val activeSensorTopics: StateFlow<Set<String>> = _activeSensorTopics.asStateFlow()

    /**
     * Se llama cuando el usuario pulsa el botón "Ver Sensores"
     */
    fun fetchSensors() {
        director.sendQuerySensorsReq()
    }

    /**
     * Se llama cuando el usuario enciende o apaga el interruptor de un sensor
     */
    fun toggleSensor(topic: String, isChecked: Boolean) {
        val currentActive = _activeSensorTopics.value.toMutableSet()

        if (isChecked) {
            currentActive.add(topic)
            // Le pedimos al backend que abra el grifo de datos para este topic
            director.sendStartSensorStream(topic)
        } else {
            currentActive.remove(topic)
            // Le pedimos al backend que destruya el suscriptor de este topic
            director.sendStopSensorStream(topic)
        }
        _activeSensorTopics.value = currentActive
    }

    /**
     * Se llamará cuando el usuario abandone la pestaña "Sensores"
     * para no saturar la red en segundo plano.
     */
    fun onScreenDisposed() {
        // 1. Mandamos apagar todos los sensores que el usuario haya dejado encendidos
        _activeSensorTopics.value.forEach { topic ->
            director.sendStopSensorStream(topic)
        }

        // 2. Vaciamos nuestra lista de interruptores marcados
        _activeSensorTopics.value = emptySet()

        // 3. Limpiamos la memoria del Director para que al volver a entrar la pantalla esté limpia
        director.clearSensorData()
    }
}