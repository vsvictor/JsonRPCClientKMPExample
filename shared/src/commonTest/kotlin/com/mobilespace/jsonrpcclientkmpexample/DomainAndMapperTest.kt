package com.mobilespace.jsonrpcclientkmpexample

import com.mobilespace.jsonrpcclientkmpexample.domain.model.CaCertificate
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CertificateEncoding
import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.MonitoringSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ApplyRuntimeSettingsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.CallMethodUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ConnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.CertificateSourceOption
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainForm
import com.mobilespace.jsonrpcclientkmpexample.presentation.main.MainFormMapper
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class DomainAndMapperTest {
    private val transports = listOf(Transport("TCP", true))

    @Test
    fun mapsFormToConnectionConfig() {
        val config = MainFormMapper.toConnectionConfig(
            MainForm(
                host = "10.1.0.196",
                port = "8000",
                pingFrequency = "500",
                maxMissedPongs = "4",
                brokerRoute = "rpc",
                udpLiteSendCoverage = "8",
                certificateSource = CertificateSourceOption.FILE_PATH,
                certificateEncoding = CertificateEncoding.DER,
                certificateInput = "/tmp/ca.der",
            ),
            transports,
        )
        assertEquals("10.1.0.196", config.host)
        assertEquals(8000, config.port)
        assertEquals(MonitoringSettings(500uL, 4u), config.monitoring)
        assertEquals("rpc", config.broker.route)
        assertNull(config.broker.url)
        assertEquals(8.toUShort(), config.udpLiteSendCoverage)
        assertEquals(CaCertificate.FilePath("/tmp/ca.der", CertificateEncoding.DER), config.caCertificate)
    }

    @Test
    fun rejectsInvalidFormNumbers() {
        val error = assertFailsWith<IllegalArgumentException> {
            MainFormMapper.toConnectionConfig(MainForm(port = "abc"), transports)
        }
        assertEquals("Port must be a number between 1 and 65535", error.message)
        assertFailsWith<IllegalArgumentException> {
            MainFormMapper.toRuntimeSettings(MainForm(requestTimeout = "-1"))
        }
    }

    @Test
    fun decodesDerBase64Certificate() {
        val config = MainFormMapper.toConnectionConfig(
            MainForm(certificateSource = CertificateSourceOption.DER_BASE64, certificateInput = "AQI D"),
            transports,
        )
        val certificate = assertIs<CaCertificate.Bytes>(config.caCertificate)
        assertEquals(CertificateEncoding.DER, certificate.encoding)
        assertEquals(listOf<Byte>(1, 2, 3), certificate.bytes.toList())
    }

    @Test
    fun useCasesValidateBeforeReachingRepository() = runTest {
        val repository = FakeJsonRpcRepository()
        val config = ConnectionConfig(Transport("TCP", true), "host", 70000, MonitoringSettings(1uL, 3u))

        assertFailsWith<IllegalArgumentException> { ConnectUseCase(repository)(config) }
        assertFailsWith<IllegalArgumentException> { CallMethodUseCase(repository)(RpcRequest(" ", null)) }
        assertFailsWith<IllegalArgumentException> {
            ApplyRuntimeSettingsUseCase(repository)(RuntimeSettings(1uL, 0u, 1uL))
        }
        assertNull(repository.connectedConfig)
        assertNull(repository.lastRequest)
        assertNull(repository.appliedSettings)
    }
}
