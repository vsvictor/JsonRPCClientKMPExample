package com.mobilespace.jsonrpcclientkmpexample.presentation.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ApplyRuntimeSettingsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.CallMethodUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ConnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.DisconnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.GetAvailableTransportsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveConnectionStateUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveLivenessEventsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.SendNotificationUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.TransportPingUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(
    getAvailableTransports: GetAvailableTransportsUseCase,
    private val observeConnectionState: ObserveConnectionStateUseCase,
    private val observeLivenessEvents: ObserveLivenessEventsUseCase,
    private val connect: ConnectUseCase,
    private val disconnect: DisconnectUseCase,
    private val callMethod: CallMethodUseCase,
    private val sendNotification: SendNotificationUseCase,
    private val transportPing: TransportPingUseCase,
    private val applyRuntimeSettings: ApplyRuntimeSettingsUseCase,
    private val logSink: (String) -> Unit = { println("[JsonRPCClient] $it") },
) : ViewModel() {
    private val state = MutableStateFlow(MainUiState(transports = getAvailableTransports()))
    val uiState: StateFlow<MainUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            observeConnectionState().collectLatest { connected ->
                state.update { it.copy(isConnected = connected) }
                if (connected) collectLivenessEvents()
            }
        }
    }

    fun onAction(action: MainAction) {
        when (action) {
            is MainAction.FormChanged -> state.update { it.copy(form = action.form) }
            is MainAction.LogPingPongChanged -> state.update { it.copy(logPingPong = action.enabled) }
            MainAction.ClearLog -> state.update { it.copy(logEntries = emptyList()) }
            MainAction.Connect -> onConnect()
            MainAction.Disconnect -> runOperation("Disconnect") {
                disconnect()
                "Connection closed"
            }
            MainAction.Call -> runOperation("Call") {
                callMethod(MainFormMapper.toRequest(state.value.form))
            }
            MainAction.Notify -> runOperation("Notify") {
                val request = MainFormMapper.toRequest(state.value.form)
                sendNotification(request)
                "Notification sent for ${request.method}"
            }
            MainAction.TransportPing -> runOperation("Ping") {
                transportPing()
                "Transport ping completed"
            }
            MainAction.ApplyRuntimeSettings -> runOperation("Apply runtime settings") {
                applyRuntimeSettings(MainFormMapper.toRuntimeSettings(state.value.form))
                "Ping and request settings applied"
            }
        }
    }

    private fun onConnect() = runOperation("Connect") {
        val current = state.value
        val config = MainFormMapper.toConnectionConfig(current.form, current.transports)
        log("Preparing ${config.transport.id} connection to ${config.host}:${config.port}")
        if (config.caCertificate != null) {
            log(
                "Loading CA certificate (${current.form.certificateSource.label}, " +
                    "${current.form.certificateEncoding.name})",
            )
        }
        log("Configuration ready; starting native connection")
        connect(config)
        log("Native connection established")
        log(
            "Native ping frequency: ${config.monitoring.pingFrequencyMillis}ms; recovery after " +
                "${config.monitoring.maxMissedPongs} consecutive missed pongs",
        )
        "Connected to ${config.host}:${config.port} via ${config.transport.id}"
    }

    private suspend fun collectLivenessEvents() {
        try {
            observeLivenessEvents().collect { event ->
                if (state.value.logPingPong || !event.isPingPong) log(event.message)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log("Unable to read liveness log: ${error.stackTraceToString()}")
        }
    }

    private fun runOperation(label: String, operation: suspend () -> String) {
        if (state.value.isBusy) return
        state.update { it.copy(isBusy = true, status = "$label…") }
        viewModelScope.launch {
            log("$label started")
            try {
                val output = operation()
                state.update { it.copy(result = output, status = "$label succeeded") }
                log("$label succeeded")
            } catch (error: CancellationException) {
                log("$label cancelled: ${error.stackTraceToString()}")
                throw error
            } catch (error: Throwable) {
                state.update {
                    it.copy(status = "$label failed: ${error.message ?: error::class.simpleName}")
                }
                log("$label failed: ${error.stackTraceToString()}")
            } finally {
                state.update { it.copy(isBusy = false) }
            }
        }
    }

    private fun log(message: String) {
        state.update { it.copy(logEntries = (it.logEntries + message).takeLast(MAX_LOG_ENTRIES)) }
        logSink(message)
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 500
    }
}
