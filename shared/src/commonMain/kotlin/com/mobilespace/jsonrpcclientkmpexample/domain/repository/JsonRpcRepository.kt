package com.mobilespace.jsonrpcclientkmpexample.domain.repository

import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.LivenessEvent
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import kotlinx.coroutines.flow.StateFlow

interface JsonRpcRepository {
    val availableTransports: List<Transport>
    val isConnected: StateFlow<Boolean>

    suspend fun connect(config: ConnectionConfig)
    suspend fun disconnect()
    suspend fun call(request: RpcRequest): String
    suspend fun notify(request: RpcRequest)
    suspend fun transportPing()
    suspend fun applyRuntimeSettings(settings: RuntimeSettings)

    /** Drains native liveness events buffered since the previous call; empty when disconnected. */
    suspend fun takeLivenessEvents(): List<LivenessEvent>
}
