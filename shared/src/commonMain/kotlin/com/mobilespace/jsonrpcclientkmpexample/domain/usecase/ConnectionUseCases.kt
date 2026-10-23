package com.mobilespace.jsonrpcclientkmpexample.domain.usecase

import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.LivenessEvent
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import com.mobilespace.jsonrpcclientkmpexample.domain.repository.JsonRpcRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class GetAvailableTransportsUseCase(private val repository: JsonRpcRepository) {
    operator fun invoke(): List<Transport> = repository.availableTransports
}

class ObserveConnectionStateUseCase(private val repository: JsonRpcRepository) {
    operator fun invoke(): StateFlow<Boolean> = repository.isConnected
}

class ConnectUseCase(private val repository: JsonRpcRepository) {
    suspend operator fun invoke(config: ConnectionConfig) {
        require(config.host.isNotBlank()) { "Host must not be blank" }
        require(config.port in 1..65535) { "Port must be a number between 1 and 65535" }
        require(config.monitoring.maxMissedPongs > 0u) {
            "Maximum missed pongs must be greater than zero"
        }
        repository.connect(config)
    }
}

class DisconnectUseCase(private val repository: JsonRpcRepository) {
    suspend operator fun invoke() = repository.disconnect()
}

class ApplyRuntimeSettingsUseCase(private val repository: JsonRpcRepository) {
    suspend operator fun invoke(settings: RuntimeSettings) {
        require(settings.maxMissedPongs > 0u) { "Maximum missed pongs must be greater than zero" }
        require(settings.requestTimeoutMillis > 0uL) { "Request timeout must be greater than zero" }
        repository.applyRuntimeSettings(settings)
    }
}

class ObserveLivenessEventsUseCase(
    private val repository: JsonRpcRepository,
    private val pollInterval: Duration = 100.milliseconds,
) {
    operator fun invoke(): Flow<LivenessEvent> = flow {
        while (currentCoroutineContext().isActive) {
            repository.takeLivenessEvents().forEach { emit(it) }
            delay(pollInterval)
        }
    }
}
