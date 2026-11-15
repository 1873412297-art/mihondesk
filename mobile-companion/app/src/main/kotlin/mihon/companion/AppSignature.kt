package mihon.companion

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.security.MessageDigest

/**
 * Package name + signing-certificate SHA-1 of the running build: exactly what the Android OAuth
 * client in Google Cloud has to be registered with. Shown on the Sync screen for copying.
 */
data class AppSignature(val packageName: String, val sha1: String) {
    companion object {
        fun of(context: Context): AppSignature {
            val name = context.packageName
            val sha1 = try {
                signatures(context, name).firstOrNull()?.let(::sha1Of) ?: UNKNOWN
            } catch (error: Exception) {
                CompanionLog.warn("AppSignature", "Could not read the signing certificate", error)
                UNKNOWN
            }
            return AppSignature(name, sha1)
        }

        @Suppress("DEPRECATION") // GET_SIGNATURES is the only option below API 28 (minSdk is 26).
        private fun signatures(context: Context, name: String): List<Signature> {
            val pm = context.packageManager
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
                info?.apkContentsSigners?.toList().orEmpty()
            } else {
                pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures?.toList().orEmpty()
            }
        }

        private fun sha1Of(signature: Signature): String =
            MessageDigest.getInstance("SHA-1").digest(signature.toByteArray())
                .joinToString(":") { "%02X".format(it) }

        private const val UNKNOWN = "?"
    }
}
