package com.mobilespace.jsonrpcclientkmpexample.domain.usecase

import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.repository.JsonRpcRepository

class CallMethodUseCase(private val repository: JsonRpcRepository) {
    suspend operator fun invoke(request: RpcRequest): String {
        request.requireValidMethod()
        return repository.call(request)
    }
}

class SendNotificationUseCase(private val repository: JsonRpcRepository) {
    suspend operator fun invoke(request: RpcRequest) {
        request.requireValidMethod()
        repository.notify(request)
    }
}

class TransportPingUseCase(private val repository: JsonRpcRepository) {
    suspend operator fun invoke() = repository.transportPing()
}

private fun RpcRequest.requireValidMethod() {
    require(method.isNotBlank()) { "Method must not be blank" }
}
