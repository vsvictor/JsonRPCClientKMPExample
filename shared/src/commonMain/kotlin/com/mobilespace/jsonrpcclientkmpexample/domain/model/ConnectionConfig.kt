package com.mobilespace.jsonrpcclientkmpexample.domain.model

data class Transport(
    val id: String,
    val supportedOnIos: Boolean,
)

enum class CertificateEncoding { PEM, DER }

sealed interface CaCertificate {
    data class PemText(val pem: String) : CaCertificate

    class Bytes(val bytes: ByteArray, val encoding: CertificateEncoding) : CaCertificate

    data class FilePath(val path: String, val encoding: CertificateEncoding) : CaCertificate

    data class Asset(val name: String, val encoding: CertificateEncoding) : CaCertificate

    data class SharedResource(val name: String, val encoding: CertificateEncoding) : CaCertificate
}

data class BrokerConfig(
    val url: String? = null,
    val route: String? = null,
    val caCertificatePem: String? = null,
    val operationTimeoutMillis: ULong? = null,
    val responseTimeoutMillis: ULong? = null,
)

data class MonitoringSettings(
    val pingFrequencyMillis: ULong,
    val maxMissedPongs: UInt,
)

data class ConnectionConfig(
    val transport: Transport,
    val host: String,
    val port: Int,
    val monitoring: MonitoringSettings,
    val serverName: String? = null,
    val caCertificate: CaCertificate? = null,
    val srtPassphrase: String? = null,
    val udpLiteSendCoverage: UShort? = null,
    val udpLiteReceiveCoverage: UShort? = null,
    val broker: BrokerConfig = BrokerConfig(),
)
