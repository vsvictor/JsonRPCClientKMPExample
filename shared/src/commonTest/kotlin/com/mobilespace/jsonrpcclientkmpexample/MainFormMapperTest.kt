package com.mobilespace.jsonrpcclientkmpexample

import com.mobilespace.jsonrpcclientkmpexample.domain.model.BrokerConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CaCertificate
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CertificateEncoding
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.CertificateSourceOption
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainForm
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainFormMapper
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainUiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainFormMapperTest {
    private val transports = listOf(Transport("TCP", true), Transport("SRT", true))

    private fun map(form: MainForm) = MainFormMapper.toConnectionConfig(form, transports)

    @Test
    fun defaultFormProducesPlainTcpConfig() {
        val config = map(MainForm())
        assertEquals("TCP", config.transport.id)
        assertEquals("127.0.0.1", config.host)
        assertEquals(12345, config.port)
        assertNull(config.caCertificate)
        assertNull(config.serverName)
        assertNull(config.srtPassphrase)
        assertNull(config.udpLiteSendCoverage)
        assertNull(config.udpLiteReceiveCoverage)
        assertEquals(BrokerConfig(), config.broker)
        assertEquals(1000uL, config.monitoring.pingFrequencyMillis)
    }

    @Test
    fun unknownTransportIsReported() {
        val error = assertFailsWith<IllegalArgumentException> { map(MainForm(transportId = "XYZ")) }
        assertEquals("Unknown transport: XYZ", error.message)
    }

    @Test
    fun optionalTextFieldsAreTrimmedToNull() {
        val config = map(MainForm(serverName = " ", srtPassphrase = "", brokerUrl = "  ", brokerCaPem = ""))
        assertNull(config.serverName)
        assertNull(config.srtPassphrase)
        assertNull(config.broker.url)
        assertNull(config.broker.caCertificatePem)
    }

    @Test
    fun allOptionalFieldsAreMapped() {
        val config = map(
            MainForm(
                transportId = "SRT",
                serverName = "localhost",
                srtPassphrase = "secret-pass",
                udpLiteSendCoverage = "0",
                udpLiteReceiveCoverage = "65535",
                brokerUrl = "mqtt://broker",
                brokerRoute = "rpc",
                brokerCaPem = "CA",
                brokerOperationTimeout = "100",
                brokerResponseTimeout = "200",
            ),
        )
        assertEquals("localhost", config.serverName)
        assertEquals("secret-pass", config.srtPassphrase)
        assertEquals(0.toUShort(), config.udpLiteSendCoverage)
        assertEquals(UShort.MAX_VALUE, config.udpLiteReceiveCoverage)
        assertEquals(BrokerConfig("mqtt://broker", "rpc", "CA", 100uL, 200uL), config.broker)
    }

    @Test
    fun numericValidationMessages() {
        val cases = mapOf(
            MainForm(pingFrequency = "-5") to "Ping frequency must be an unsigned integer",
            MainForm(maxMissedPongs = "x") to "Maximum missed pongs must be an unsigned integer",
            MainForm(udpLiteSendCoverage = "70000") to "UDP-Lite send coverage must be between 0 and 65535",
            MainForm(udpLiteReceiveCoverage = "-1") to "UDP-Lite receive coverage must be between 0 and 65535",
            MainForm(brokerOperationTimeout = "1.5") to "Broker operation timeout must be an unsigned integer",
            MainForm(brokerResponseTimeout = "abc") to "Broker response timeout must be an unsigned integer",
            MainForm(port = "") to "Port must be a number between 1 and 65535",
        )
        cases.forEach { (form, message) ->
            assertEquals(message, assertFailsWith<IllegalArgumentException> { map(form) }.message)
        }
    }

    @Test
    fun everyCertificateSourceIsMapped() {
        fun certificate(source: CertificateSourceOption, input: String = "ca.pem") = map(
            MainForm(certificateSource = source, certificateInput = input, certificateEncoding = CertificateEncoding.DER),
        ).caCertificate

        assertNull(certificate(CertificateSourceOption.NONE))
        assertEquals(CaCertificate.PemText("PEM"), certificate(CertificateSourceOption.PEM_TEXT, "PEM"))
        val bytes = assertIs<CaCertificate.Bytes>(certificate(CertificateSourceOption.PEM_BYTES, "PEM"))
        assertEquals(CertificateEncoding.PEM, bytes.encoding)
        assertEquals("PEM", bytes.bytes.decodeToString())
        assertEquals(CaCertificate.FilePath("ca.pem", CertificateEncoding.DER), certificate(CertificateSourceOption.FILE_PATH))
        assertEquals(CaCertificate.Asset("ca.pem", CertificateEncoding.DER), certificate(CertificateSourceOption.APP_ASSET))
        assertEquals(
            CaCertificate.SharedResource("ca.pem", CertificateEncoding.DER),
            certificate(CertificateSourceOption.SHARED_RESOURCE),
        )
    }

    @Test
    fun invalidDerBase64IsRejected() {
        assertFailsWith<IllegalArgumentException> {
            map(MainForm(certificateSource = CertificateSourceOption.DER_BASE64, certificateInput = "%%%"))
        }
    }

    @Test
    fun runtimeSettingsAndRequestAreMapped() {
        assertEquals(
            RuntimeSettings(250uL, 5u, 3000uL),
            MainFormMapper.toRuntimeSettings(MainForm(pingFrequency = "250", maxMissedPongs = "5", requestTimeout = "3000")),
        )
        assertEquals(
            RpcRequest("sum", "[1,2]"),
            MainFormMapper.toRequest(MainForm(method = "sum", paramsJson = "[1,2]")),
        )
    }

    @Test
    fun uiStateFlagsFollowBusyAndConnection() {
        assertTrue(MainUiState().canConnect)
        assertFalse(MainUiState().canUseConnection)
        assertFalse(MainUiState(isConnected = true).canConnect)
        assertTrue(MainUiState(isConnected = true).canUseConnection)
        assertFalse(MainUiState(isConnected = true, isBusy = true).canUseConnection)
        assertFalse(MainUiState(isBusy = true).canConnect)
    }

    @Test
    fun certificateOptionsDescribeTheirInput() {
        assertEquals("", CertificateSourceOption.NONE.inputLabel)
        CertificateSourceOption.entries.filter { it != CertificateSourceOption.NONE }.forEach {
            assertTrue(it.inputLabel.isNotBlank(), it.name)
            assertTrue(it.inputMinLines >= 1, it.name)
        }
        assertEquals(CertificateSourceOption.entries.size, CertificateSourceOption.entries.map { it.label }.toSet().size)
    }
}
