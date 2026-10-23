package com.mobilespace.jsonrpcclientkmpexample.data.mapper

import com.mobilespace.jsonrpcclientkmpexample.data.source.CertificateResourceDataSource
import com.mobilespace.jsonrpcclientkmpexample.domain.model.BrokerConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CaCertificate
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CertificateEncoding
import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import com.vsvictor.jsonrpckmp.BrokerOptions
import com.vsvictor.jsonrpckmp.CertificateFormat
import com.vsvictor.jsonrpckmp.JsonRpcClientBuilder
import com.vsvictor.jsonrpckmp.RpcTransport

internal fun RpcTransport.toDomain() = Transport(id = name, supportedOnIos = supportedOnIos)

internal fun Transport.toRpcTransport(): RpcTransport =
    RpcTransport.entries.firstOrNull { it.name == id }
        ?: throw IllegalArgumentException("Unknown transport: $id")

internal fun CertificateEncoding.toCertificateFormat(): CertificateFormat = when (this) {
    CertificateEncoding.PEM -> CertificateFormat.PEM
    CertificateEncoding.DER -> CertificateFormat.DER
}

internal fun BrokerConfig.toBrokerOptions() = BrokerOptions(
    url = url,
    route = route,
    caCertificatePem = caCertificatePem,
    operationTimeoutMillis = operationTimeoutMillis,
    responseTimeoutMillis = responseTimeoutMillis,
)

internal suspend fun ConnectionConfig.toClientBuilder(
    resources: CertificateResourceDataSource,
): JsonRpcClientBuilder {
    val builder = JsonRpcClientBuilder.builder(transport.toRpcTransport(), host, port)
        .pingFrequency(monitoring.pingFrequencyMillis)
        .maxMissedPongs(monitoring.maxMissedPongs)
    when (val certificate = caCertificate) {
        null -> Unit
        is CaCertificate.PemText -> builder.caCertificatePem(certificate.pem)
        is CaCertificate.Bytes ->
            builder.caCertificate(certificate.bytes, certificate.encoding.toCertificateFormat())
        is CaCertificate.FilePath ->
            builder.caCertificateFile(certificate.path, certificate.encoding.toCertificateFormat())
        is CaCertificate.Asset ->
            builder.caCertificateAsset(certificate.name, certificate.encoding.toCertificateFormat())
        is CaCertificate.SharedResource -> builder.caCertificateResource(
            readBytes = { resources.read(certificate.name) },
            format = certificate.encoding.toCertificateFormat(),
        )
    }
    serverName?.let(builder::serverName)
    srtPassphrase?.let(builder::srtPassphrase)
    return builder
        .udpLiteChecksumCoverage(send = udpLiteSendCoverage, receive = udpLiteReceiveCoverage)
        .broker(broker.toBrokerOptions())
}
