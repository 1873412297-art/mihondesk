package mihon.desktop.platform

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WindowsCredentialStoreTest {

    private class FakeCredentials : WindowsCredentialStore.Credentials {
        val stored = mutableMapOf<String, ByteArray>()

        override fun CredReadW(
            target: WString,
            type: Int,
            flags: Int,
            credential: PointerByReference,
        ): Boolean {
            val bytes = stored[target.toString()] ?: return false
            val mem = Memory(bytes.size.toLong())
            mem.write(0, bytes, 0, bytes.size)

            val cred = WindowsCredentialStore.Credential().apply {
                this.target = target
                this.size = bytes.size
                this.blob = mem
            }
            cred.write()
            credential.value = cred.pointer
            return true
        }

        override fun CredWriteW(credential: WindowsCredentialStore.Credential, flags: Int): Boolean {
            val targetKey = credential.target.toString()
            val bytes = credential.blob?.getByteArray(0, credential.size) ?: ByteArray(0)
            stored[targetKey] = bytes
            return true
        }

        override fun CredDeleteW(target: WString, type: Int, flags: Int): Boolean {
            return stored.remove(target.toString()) != null
        }

        override fun CredFree(pointer: Pointer) {
            // No-op for test memory
        }
    }

    @Test
    fun `writes and reads back credential successfully`() {
        val fake = FakeCredentials()
        val store = WindowsCredentialStore { fake }

        val target = "MihonW:test-token"
        val secret = "super-secret-password-123"

        store.write(target, secret) shouldBe true
        store.read(target) shouldBe secret
    }

    @Test
    fun `read returns null when target does not exist`() {
        val fake = FakeCredentials()
        val store = WindowsCredentialStore { fake }

        store.read("nonexistent-key") shouldBe null
    }

    @Test
    fun `deletes credential successfully`() {
        val fake = FakeCredentials()
        val store = WindowsCredentialStore { fake }

        val target = "MihonW:delete-me"
        store.write(target, "value")
        store.read(target) shouldBe "value"

        store.delete(target) shouldBe true
        store.read(target) shouldBe null
    }

    @Test
    fun `write throws IllegalArgumentException when credential exceeds limit`() {
        val fake = FakeCredentials()
        val store = WindowsCredentialStore { fake }

        val oversized = "a".repeat(2561)
        val exception = shouldThrow<IllegalArgumentException> {
            store.write("large-key", oversized)
        }
        exception.message shouldBe "Credential exceeds Windows generic credential limit"
    }
}
