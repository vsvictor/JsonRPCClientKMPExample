package com.mobilespace.jsonrpcclientkmpexample.data.repository

import com.mobilespace.jsonrpcclientkmpexample.data.mapper.toClientBuilder
import com.mobilespace.jsonrpcclientkmpexample.data.mapper.toDomain
import com.mobilespace.jsonrpcclientkmpexample.data.source.CertificateResourceDataSource
import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.LivenessEvent
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import com.mobilespace.jsonrpcclientkmpexample.domain.repository.JsonRpcRepository
import com.vsvictor.jsonrpckmp.JsonRpcClient
import com.vsvictor.jsonrpckmp.RpcTransport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

class JsonRpcRepositoryImpl(
    private val certificateResources: CertificateResourceDataSource,
) : JsonRpcRepository {
    private val lifecycleMutex = Mutex()

    @Volatile
    private var client: JsonRpcClient? = null
    private val connected = MutableStateFlow(false)

    override val availableTransports: List<Transport> = RpcTransport.entries.map { it.toDomain() }
    override val isConnected: StateFlow<Boolean> = connected.asStateFlow()

    override suspend fun connect(config: ConnectionConfig) = lifecycleMutex.withLock {
        check(client == null) { "Already connected" }
        client = config.toClientBuilder(certificateResources).connect()
        connected.value = true
    }

    override suspend fun disconnect() = lifecycleMutex.withLock {
        val current = checkNotNull(client) { "Not connected" }
        try {
            current.disconnect()
        } finally {
            client = null
            connected.value = false
        }
    }

    override suspend fun call(request: RpcRequest): String =
        requireClient().call(request.method, request.paramsJson)

    override suspend fun notify(request: RpcRequest) =
        requireClient().notify(request.method, request.paramsJson)

    override suspend fun transportPing() = requireClient().transportPing()

    override suspend fun applyRuntimeSettings(settings: RuntimeSettings) {
        val current = requireClient()
        current.setMaxMissedPongs(settings.maxMissedPongs)
        current.setRequestTimeout(settings.requestTimeoutMillis)
        current.setPingFrequency(settings.pingFrequencyMillis)
    }

    override suspend fun takeLivenessEvents(): List<LivenessEvent> =
        client?.takeLivenessLog()?.map(::LivenessEvent).orEmpty()

    private fun requireClient(): JsonRpcClient = checkNotNull(client) { "Not connected" }
}
