package com.mobilespace.jsonrpcclientkmpexample

import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.LivenessEvent
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import com.mobilespace.jsonrpcclientkmpexample.domain.repository.JsonRpcRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeJsonRpcRepository : JsonRpcRepository {
    override val availableTransports = listOf(Transport("TCP", true), Transport("TLS", true))
    private val connected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = connected

    var connectedConfig: ConnectionConfig? = null
    var lastRequest: RpcRequest? = null
    var appliedSettings: RuntimeSettings? = null
    var callResult = "result"
    var connectError: Throwable? = null
    var operationError: Throwable? = null
    var livenessError: Throwable? = null
    var connectGate: CompletableDeferred<Unit>? = null
    var connectCalls = 0
    var transportPings = 0
    var notifications = 0
    val pendingLivenessEvents = mutableListOf<LivenessEvent>()
    val livenessBatches = ArrayDeque<List<LivenessEvent>>()

    override suspend fun connect(config: ConnectionConfig) {
        connectCalls++
        connectGate?.await()
        connectError?.let { throw it }
        connectedConfig = config
        connected.value = true
    }

    override suspend fun disconnect() {
        connected.value = false
    }

    override suspend fun call(request: RpcRequest): String {
        operationError?.let { throw it }
        lastRequest = request
        return callResult
    }

    override suspend fun notify(request: RpcRequest) {
        operationError?.let { throw it }
        notifications++
        lastRequest = request
    }

    override suspend fun transportPing() {
        operationError?.let { throw it }
        transportPings++
    }

    override suspend fun applyRuntimeSettings(settings: RuntimeSettings) {
        operationError?.let { throw it }
        appliedSettings = settings
    }

    override suspend fun takeLivenessEvents(): List<LivenessEvent> {
        livenessError?.let { throw it }
        livenessBatches.removeFirstOrNull()?.let { return it }
        return pendingLivenessEvents.toList().also { pendingLivenessEvents.clear() }
    }
}
