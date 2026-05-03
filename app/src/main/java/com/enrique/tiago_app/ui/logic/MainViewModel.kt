package com.enrique.tiago_app.ui.logic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.enrique.tiago_app.logic.RobotRepository

class MainViewModel(private val repository: RobotRepository) : ViewModel() {

    // Exponemos las dos variables "StateFlow" del Repositorio
    // La pantalla se quedará "observando" estas variables y cambiará los textos sola.
    val connectionStatus = repository.connectionStatus
    val lastLogs = repository.lastLogs

    fun connect() {
        repository.connect()
    }

    fun disconnect() {
        repository.disconnect()
    }
}

// Esto es una herramienta necesaria porque nuestro ViewModel necesita recibir
// el Repositorio por parámetro al nacer.
class MainViewModelFactory(private val repository: RobotRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Clase ViewModel desconocida")
    }
}