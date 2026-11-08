package mihon.sync.transport.gdrive

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Pkce {
    private val random = SecureRandom()
    private val encoder = Base64.getUrlEncoder().withoutPadding()

    fun newVerifier(): String = ByteArray(48).also(random::nextBytes).let(encoder::encodeToString)

    fun challenge(verifier: String): String =
        encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun newState(): String = ByteArray(16).also(random::nextBytes).let(encoder::encodeToString)
}
