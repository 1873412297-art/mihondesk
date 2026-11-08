package mihon.desktop.extension

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mihon.extension.ipc.IpcException
import mihon.extension.ipc.IpcResponse
import mihon.extension.ipc.IpcSession
import org.junit.jupiter.api.Test
import java.io.PipedInputStream
import java.io.PipedOutputStream

class ExtensionRequestTransportTest {
    @Test fun `source request times out and remote operation is cancelled`(): Unit = runBlocking {
        val mainOut = PipedOutputStream()
        val hostIn = PipedInputStream(mainOut)
        val hostOut = PipedOutputStream()
        val mainIn = PipedInputStream(hostOut)
        val host = IpcSession(hostIn, hostOut, onRequest = { request ->
            delay(1_000)
            IpcResponse(request.requestId, success = true)
        })
        val main = IpcSession(mainIn, mainOut)
        val transport = ExtensionRequestTransport(defaultTimeoutMillis = 50)
        try {
            val failure = shouldThrow<IpcException> {
                transport.request("get_popular", session = { main })
            }
            failure.isTimeout shouldBe true
            failure.message shouldBe "IPC request 'get_popular' timed out"
        } finally {
            main.close()
            host.close()
        }
    }

    @Test fun `normal requests use the configured timeout and image requests can override it`(): Unit = runBlocking {
        val mainOut = PipedOutputStream()
        val hostIn = PipedInputStream(mainOut)
        val hostOut = PipedOutputStream()
        val mainIn = PipedInputStream(hostOut)
        val host = IpcSession(hostIn, hostOut, onRequest = { request ->
            IpcResponse(request.requestId, success = true, payloadJson = "pong")
        })
        val main = IpcSession(mainIn, mainOut)
        try {
            ExtensionRequestTransport().request("ping", session = { main }) shouldBe "pong"
            ExtensionRequestTransport().request(
                "get_image",
                session = { main },
                timeoutMillis = 120_000,
            ) shouldBe "pong"
        } finally {
            main.close()
            host.close()
        }
    }
}
