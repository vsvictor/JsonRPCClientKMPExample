package com.mobilespace.jsonrpcclientkmpexample

import com.mobilespace.jsonrpcclientkmpexample.data.repository.JsonRpcRepositoryImpl
import com.mobilespace.jsonrpcclientkmpexample.data.source.ComposeCertificateResourceDataSource
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CaCertificate
import com.mobilespace.jsonrpcclientkmpexample.domain.model.CertificateEncoding
import com.mobilespace.jsonrpcclientkmpexample.domain.model.ConnectionConfig
import com.mobilespace.jsonrpcclientkmpexample.domain.model.MonitoringSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RpcRequest
import com.mobilespace.jsonrpcclientkmpexample.domain.model.RuntimeSettings
import com.mobilespace.jsonrpcclientkmpexample.domain.model.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/** Host of a running json-rpc-2 test server, or null when the platform cannot load the native client. */
internal expect fun appIntegrationServerHost(): String?

class RepositoryIntegrationTest {
    private val repository = JsonRpcRepositoryImpl(ComposeCertificateResourceDataSource())

    private fun tcp(host: String) =
        ConnectionConfig(Transport("TCP", true), host, 8000, MonitoringSettings(200uL, 3u))

    private suspend fun reachableHost(): String? {
        val host = appIntegrationServerHost() ?: return null.also { println("SKIPPED: no integration host") }
        return try {
            repository.connect(tcp(host))
            repository.disconnect()
            host
        } catch (error: Throwable) {
            println("SKIPPED: json-rpc-2 server unreachable at $host:8000 ($error)")
            null
        }
    }

    @Test
    fun fullTcpLifecycle() = runTest(timeout = 60.seconds) {
        val host = reachableHost() ?: return@runTest
        repository.connect(tcp(host))
        try {
            assertTrue(repository.isConnected.value)
            assertFailsWith<IllegalStateException> { repository.connect(tcp(host)) }
            assertEquals("""{"message":"hello"}""", repository.call(RpcRequest("echo", """{"message":"hello"}""")))
            repository.notify(RpcRequest("echo", "[]"))
            repository.transportPing()
            repository.applyRuntimeSettings(RuntimeSettings(150uL, 3u, 5_000uL))
            withContext(Dispatchers.Default) { delay(1_200) }
            val events = repository.takeLivenessEvents()
            assertTrue(events.any { it.isPingPong }, "Expected ping/pong events, got $events")
        } finally {
            repository.disconnect()
        }
        assertFalse(repository.isConnected.value)
        assertTrue(repository.takeLivenessEvents().isEmpty())
    }

    @Test
    fun tlsWithBundledComposeResourceCertificate() = runTest(timeout = 60.seconds) {
        val host = reachableHost() ?: return@runTest
        repository.connect(
            ConnectionConfig(
                transport = Transport("TLS", true),
                host = host,
                port = 8445,
                monitoring = MonitoringSettings(1000uL, 3u),
                serverName = "localhost",
                caCertificate = CaCertificate.SharedResource("cert.pem", CertificateEncoding.PEM),
            ),
        )
        try {
            assertEquals("[42]", repository.call(RpcRequest("echo", "[42]")))
        } finally {
            repository.disconnect()
        }
    }

    @Test
    fun connectFailureKeepsRepositoryReusable() = runTest(timeout = 60.seconds) {
        val host = reachableHost() ?: return@runTest
        assertFailsWith<Throwable> { repository.connect(tcp(host).copy(port = 8999)) }
        assertFalse(repository.isConnected.value)
        repository.connect(tcp(host))
        repository.disconnect()
    }
}
