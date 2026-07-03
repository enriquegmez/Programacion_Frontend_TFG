package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import com.enrique.tiago_app.logic.ProtocolDirector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.enrique.tiago_app.utils.AppConstants

class SensorViewModel(
    private val director: ProtocolDirector
) : ViewModel() {

    // 1. La "Carta del Menú" (Lista de sensores detectados por ROS 2)
    val availableSensors = director.availableSensors

    // ¡NUEVO! Estado de si ya hemos buscado
    val hasSearched = director.hasScannedSensors

    // 2. El mapa en tiempo real con los datos puros para pintar las gráficas
    val activeSensorData = director.activeSensorData

    // 3. Guardamos qué sensores tienen el "tick" puesto en la UI (Set de topics)
    private val _activeSensorTopics = MutableStateFlow<Set<String>>(emptySet())
    val activeSensorTopics: StateFlow<Set<String>> = _activeSensorTopics.asStateFlow()

    // 4. Recorrido (trayectoria X/Y) de odometría. Vive en el ViewModel para que
    //    persista aunque el usuario salga y vuelva a entrar a la pantalla de
    //    sensores; solo se borra al desconectar (clearTrail).
    private val _odomTrail = MutableStateFlow<List<Pair<Float, Float>>>(emptyList())
    val odomTrail: StateFlow<List<Pair<Float, Float>>> = _odomTrail.asStateFlow()
    private val maxTrailPoints = 1000

    /** Añade un punto al recorrido si el robot se ha movido lo suficiente. */
    fun addTrailPoint(x: Float, y: Float) {
        val last = _odomTrail.value.lastOrNull()
        if (last == null || kotlin.math.hypot((x - last.first).toDouble(), (y - last.second).toDouble()) > 0.01) {
            val updated = _odomTrail.value + (x to y)
            _odomTrail.value = if (updated.size > maxTrailPoints) updated.drop(updated.size - maxTrailPoints) else updated
        }
    }

    /** Borra el recorrido. Se llama al desconectar la sesión. */
    fun clearTrail() {
        _odomTrail.value = emptyList()
    }

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
            director.sendStartStream(AppConstants.Resource.SENSORS, topic)
        } else {
            currentActive.remove(topic)
            // Le pedimos al backend que destruya el suscriptor de este topic
            director.sendStopStream(AppConstants.Resource.SENSORS, topic)
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
            director.sendStopStream(AppConstants.Resource.SENSORS, topic)
        }

        // 2. Vaciamos nuestra lista de interruptores marcados
        _activeSensorTopics.value = emptySet()

        // 3. Limpiamos la memoria del Director para que al volver a entrar la pantalla esté limpia
        director.clearActiveSensorData()
    }
}