package com.mobilespace.jsonrpcclientkmpexample.presentation.main

import com.mobilespace.jsonrpcclientkmpexample.domain.model.BrokerConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CaCertificate
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CertificateEncoding
import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.MonitoringSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import kotlin.io.encoding.Base64

/** Parses text input into domain models; throws [IllegalArgumentException] with a user-facing message. */
object MainFormMapper {
    fun toConnectionConfig(form: MainForm, transports: List<Transport>): ConnectionConfig =
        ConnectionConfig(
            transport = transports.firstOrNull { it.id == form.transportId }
                ?: throw IllegalArgumentException("Unknown transport: ${form.transportId}"),
            host = form.host,
            port = form.port.toIntOrNull()
                ?: throw IllegalArgumentException("Port must be a number between 1 and 65535"),
            monitoring = MonitoringSettings(
                pingFrequencyMillis = form.pingFrequency.toRequiredULong("Ping frequency"),
                maxMissedPongs = form.maxMissedPongs.toUIntOrNull()
                    ?: throw IllegalArgumentException("Maximum missed pongs must be an unsigned integer"),
            ),
            serverName = form.serverName.takeIf(String::isNotBlank),
            caCertificate = form.toCaCertificate(),
            srtPassphrase = form.srtPassphrase.takeIf(String::isNotBlank),
            udpLiteSendCoverage = form.udpLiteSendCoverage.toOptionalUShort("UDP-Lite send coverage"),
            udpLiteReceiveCoverage = form.udpLiteReceiveCoverage.toOptionalUShort("UDP-Lite receive coverage"),
            broker = BrokerConfig(
                url = form.brokerUrl.takeIf(String::isNotBlank),
                route = form.brokerRoute.takeIf(String::isNotBlank),
                caCertificatePem = form.brokerCaPem.takeIf(String::isNotBlank),
                operationTimeoutMillis = form.brokerOperationTimeout.toOptionalULong("Broker operation timeout"),
                responseTimeoutMillis = form.brokerResponseTimeout.toOptionalULong("Broker response timeout"),
            ),
        )

    fun toRuntimeSettings(form: MainForm): RuntimeSettings = RuntimeSettings(
        pingFrequencyMillis = form.pingFrequency.toRequiredULong("Ping frequency"),
        maxMissedPongs = form.maxMissedPongs.toUIntOrNull()
            ?: throw IllegalArgumentException("Maximum missed pongs must be an unsigned integer"),
        requestTimeoutMillis = form.requestTimeout.toRequiredULong("Request timeout"),
    )

    fun toRequest(form: MainForm): RpcRequest = RpcRequest(form.method, form.paramsJson)

    private fun MainForm.toCaCertificate(): CaCertificate? = when (certificateSource) {
        CertificateSourceOption.NONE -> null
        CertificateSourceOption.PEM_TEXT -> CaCertificate.PemText(certificateInput)
        CertificateSourceOption.PEM_BYTES ->
            CaCertificate.Bytes(certificateInput.encodeToByteArray(), CertificateEncoding.PEM)
        CertificateSourceOption.DER_BASE64 -> CaCertificate.Bytes(
            Base64.decode(certificateInput.filterNot(Char::isWhitespace)),
            CertificateEncoding.DER,
        )
        CertificateSourceOption.FILE_PATH -> CaCertificate.FilePath(certificateInput, certificateEncoding)
        CertificateSourceOption.APP_ASSET -> CaCertificate.Asset(certificateInput, certificateEncoding)
        CertificateSourceOption.SHARED_RESOURCE ->
            CaCertificate.SharedResource(certificateInput, certificateEncoding)
    }

    private fun String.toRequiredULong(label: String): ULong =
        toULongOrNull() ?: throw IllegalArgumentException("$label must be an unsigned integer")

    private fun String.toOptionalUShort(label: String): UShort? =
        if (isBlank()) null
        else toUShortOrNull() ?: throw IllegalArgumentException("$label must be between 0 and 65535")

    private fun String.toOptionalULong(label: String): ULong? =
        if (isBlank()) null else toRequiredULong(label)
}
