package com.mobilespace.jsonrpcclientkmpexample

import com.mobilespace.jsonrpcclientkmpexample.data.mapper.toBrokerOptions
import com.mobilespace.jsonrpcclientkmpexample.data.mapper.toCertificateFormat
import com.mobilespace.jsonrpcclientkmpexample.data.mapper.toClientBuilder
import com.mobilespace.jsonrpcclientkmpexample.data.mapper.toDomain
import com.mobilespace.jsonrpcclientkmpexample.data.mapper.toRpcTransport
import com.mobilespace.jsonrpcclientkmpexample.data.repository.JsonRpcRepositoryImpl
import com.mobilespace.jsonrpcclientkmpexample.data.source.CertificateResourceDataSource
import com.mobilespace.jsonrpcclientkmpexample.domain.model.BrokerConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CaCertificate
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CertificateEncoding
import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.MonitoringSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import com.vsvictor.jsonrpckmp.BrokerOptions
import com.vsvictor.jsonrpckmp.CertificateFormat
import com.vsvictor.jsonrpckmp.RpcTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataLayerTest {
    private val noResources = CertificateResourceDataSource { error("unexpected resource read: $it") }

    private fun config(
        certificate: CaCertificate? = null,
        transport: Transport = Transport("TLS", true),
        host: String = "localhost",
    ) = ConnectionConfig(transport, host, 8445, MonitoringSettings(1000uL, 3u), caCertificate = certificate)

    @Test
    fun everyLibraryTransportRoundTrips() {
        RpcTransport.entries.forEach { transport ->
            val domain = transport.toDomain()
            assertEquals(transport.name, domain.id)
            assertEquals(transport.supportedOnIos, domain.supportedOnIos)
            assertEquals(transport, domain.toRpcTransport())
        }
    }

    @Test
    fun unknownTransportIsRejected() {
        val error = assertFailsWith<IllegalArgumentException> { Transport("CARRIER_PIGEON", true).toRpcTransport() }
        assertEquals("Unknown transport: CARRIER_PIGEON", error.message)
    }

    @Test
    fun encodingsAndBrokerAreMapped() {
        assertEquals(CertificateFormat.PEM, CertificateEncoding.PEM.toCertificateFormat())
        assertEquals(CertificateFormat.DER, CertificateEncoding.DER.toCertificateFormat())
        assertEquals(
            BrokerOptions("mqtt://b", "route", "CA", 1uL, 2uL),
            BrokerConfig("mqtt://b", "route", "CA", 1uL, 2uL).toBrokerOptions(),
        )
    }

    @Test
    fun builderAcceptsEveryInMemoryCertificateSource() = runTest {
        config(CaCertificate.PemText(PEM)).toClientBuilder(noResources)
        config(CaCertificate.Bytes(PEM.encodeToByteArray(), CertificateEncoding.PEM)).toClientBuilder(noResources)
        config(CaCertificate.Bytes(byteArrayOf(1, 2, 3), CertificateEncoding.DER)).toClientBuilder(noResources)
    }

    @Test
    fun invalidCertificatesFailWhileBuilding() = runTest {
        assertFailsWith<IllegalArgumentException> {
            config(CaCertificate.PemText("garbage")).toClientBuilder(noResources)
        }
        assertFailsWith<IllegalArgumentException> {
            config(CaCertificate.Bytes(ByteArray(0), CertificateEncoding.DER)).toClientBuilder(noResources)
        }
        assertFailsWith<Throwable> {
            config(CaCertificate.FilePath("/missing/ca.pem", CertificateEncoding.PEM)).toClientBuilder(noResources)
        }
    }

    @Test
    fun sharedResourceCertificateIsReadByName() = runTest {
        val requested = mutableListOf<String>()
        val resources = CertificateResourceDataSource { name ->
            requested += name
            PEM.encodeToByteArray()
        }
        config(CaCertificate.SharedResource("cert.pem", CertificateEncoding.PEM)).toClientBuilder(resources)
        assertEquals(listOf("cert.pem"), requested)
    }

    @Test
    fun builderRejectsZeroMissedPongs() = runTest {
        assertFailsWith<IllegalArgumentException> {
            config().copy(monitoring = MonitoringSettings(1000uL, 0u)).toClientBuilder(noResources)
        }
    }

    @Test
    fun repositoryExposesAllTransportsAndStartsDisconnected() = runTest {
        val repository = JsonRpcRepositoryImpl(noResources)
        assertEquals(RpcTransport.entries.size, repository.availableTransports.size)
        assertEquals(6, repository.availableTransports.count { !it.supportedOnIos })
        assertFalse(repository.isConnected.value)
        assertTrue(repository.takeLivenessEvents().isEmpty())
    }

    @Test
    fun repositoryOperationsRequireConnection() = runTest {
        val repository = JsonRpcRepositoryImpl(noResources)
        val operations: List<suspend () -> Unit> = listOf(
            { repository.call(RpcRequest("echo", null)) },
            { repository.notify(RpcRequest("echo", null)) },
            { repository.transportPing() },
            { repository.applyRuntimeSettings(RuntimeSettings(1uL, 1u, 1uL)) },
            { repository.disconnect() },
        )
        operations.forEach { operation ->
            assertEquals("Not connected", assertFailsWith<IllegalStateException> { operation() }.message)
        }
    }

    @Test
    fun failedConnectLeavesRepositoryDisconnected() = runTest {
        val repository = JsonRpcRepositoryImpl(noResources)
        assertFailsWith<IllegalArgumentException> {
            repository.connect(config(transport = Transport("TCP", true), host = " "))
        }
        assertFalse(repository.isConnected.value)
        assertFailsWith<IllegalStateException> { repository.transportPing() }
    }

    private companion object {
        const val PEM = "-----BEGIN CERTIFICATE-----\nAQID\n-----END CERTIFICATE-----\n"
    }
}
