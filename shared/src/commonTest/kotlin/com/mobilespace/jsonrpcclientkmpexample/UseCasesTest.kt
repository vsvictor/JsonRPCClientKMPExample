package com.mobilespace.jsonrpcclientkmpexample

import com.mobilespace.jsonrpcclientkmpexample.domain.model.BrokerConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CaCertificate
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CertificateEncoding
import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.LivenessEvent
import com.mobilespace.jsonrpcclientkmpexample.domain.model.MonitoringSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ApplyRuntimeSettingsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ConnectUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.GetAvailableTransportsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveConnectionStateUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.ObserveLivenessEventsUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.SendNotificationUseCase
import com.mobilespace.jsonrpcclientkmpexample.domain.usecase.TransportPingUseCase
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class UseCasesTest {
    private val repository = FakeJsonRpcRepository()
    private val config = ConnectionConfig(Transport("TCP", true), "host", 8000, MonitoringSettings(1000uL, 3u))

    @Test
    fun connectValidatesHostPortAndMissedPongs() = runTest {
        val connect = ConnectUseCase(repository)
        assertEquals("Host must not be blank", assertFailsWith<IllegalArgumentException> {
            connect(config.copy(host = " "))
        }.message)
        listOf(0, 65536).forEach { port ->
            assertFailsWith<IllegalArgumentException> { connect(config.copy(port = port)) }
        }
        assertFailsWith<IllegalArgumentException> {
            connect(config.copy(monitoring = MonitoringSettings(1000uL, 0u)))
        }
        assertNull(repository.connectedConfig)

        connect(config)
        assertEquals(config, repository.connectedConfig)
        assertTrue(ObserveConnectionStateUseCase(repository)().value)
    }

    @Test
    fun boundaryPortsAreAccepted() = runTest {
        listOf(1, 65535).forEach { port ->
            ConnectUseCase(repository)(config.copy(port = port))
            assertEquals(port, repository.connectedConfig?.port)
        }
    }

    @Test
    fun notificationRequiresMethod() = runTest {
        assertFailsWith<IllegalArgumentException> { SendNotificationUseCase(repository)(RpcRequest("", null)) }
        SendNotificationUseCase(repository)(RpcRequest("log", null))
        assertEquals(RpcRequest("log", null), repository.lastRequest)
    }

    @Test
    fun runtimeSettingsRequirePositiveTimeout() = runTest {
        val apply = ApplyRuntimeSettingsUseCase(repository)
        assertFailsWith<IllegalArgumentException> { apply(RuntimeSettings(100uL, 3u, 0uL)) }
        assertNull(repository.appliedSettings)
        apply(RuntimeSettings(0uL, 1u, 1uL))
        assertEquals(RuntimeSettings(0uL, 1u, 1uL), repository.appliedSettings)
    }

    @Test
    fun transportPingAndTransportsDelegateToRepository() = runTest {
        TransportPingUseCase(repository)()
        assertEquals(1, repository.transportPings)
        assertSame(repository.availableTransports, GetAvailableTransportsUseCase(repository)())
    }

    @Test
    fun livenessEventsArePolledInOrder() = runTest {
        repository.pendingLivenessEvents += listOf(LivenessEvent("PING: 1"), LivenessEvent("PONG: 1"))
        val events = ObserveLivenessEventsUseCase(repository, 10.milliseconds)().take(2).toList()
        assertEquals(listOf("PING: 1", "PONG: 1"), events.map { it.message })
    }

    @Test
    fun livenessEventsArePolledRepeatedly() = runTest {
        val flow = ObserveLivenessEventsUseCase(repository, 10.milliseconds)()
        repository.livenessBatches += listOf(listOf(LivenessEvent("A")), emptyList(), listOf(LivenessEvent("B")))
        assertEquals(listOf("A", "B"), flow.take(2).toList().map { it.message })
    }

    @Test
    fun pingPongClassification() {
        assertTrue(LivenessEvent("PING: sent").isPingPong)
        assertTrue(LivenessEvent("PONG: received").isPingPong)
        listOf("RESTORE: 1", "RECONNECT: x", "ERROR: x", "ping", " PING: x").forEach {
            assertFalse(LivenessEvent(it).isPingPong, it)
        }
    }

    @Test
    fun brokerConfigDefaultsAreEmpty() {
        assertEquals(BrokerConfig(null, null, null, null, null), BrokerConfig())
        assertNull(config.caCertificate)
        assertEquals(CaCertificate.PemText("x"), CaCertificate.PemText("x"))
        assertEquals(CertificateEncoding.entries.toSet(), setOf(CertificateEncoding.PEM, CertificateEncoding.DER))
    }
}
