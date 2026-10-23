package com.mobilespace.jsonrpcclientkmpexample.domain.model

data class RpcRequest(
    val method: String,
    val paramsJson: String?,
)

data class RuntimeSettings(
    val pingFrequencyMillis: ULong,
    val maxMissedPongs: UInt,
    val requestTimeoutMillis: ULong,
)

data class LivenessEvent(val message: String) {
    val isPingPong: Boolean
        get() = message.startsWith("PING:") || message.startsWith("PONG:")
}
