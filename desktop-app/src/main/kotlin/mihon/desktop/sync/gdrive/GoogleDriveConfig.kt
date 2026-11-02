package mihon.desktop.sync.gdrive

import java.util.Properties

/**
 * OAuth client for Google Drive sync. The client secret is never committed: it is injected at
 * build time (gradle property / local.properties `gdrive.clientSecret`, or env `GDRIVE_CLIENT_SECRET`)
 * into the generated resource [RESOURCE], or supplied at runtime via `-Dmihon.gdrive.clientSecret`
 * or the same env var.
 */
data class GoogleDriveConfig(
    val clientId: String,
    val clientSecret: String?,
) {
    val isConfigured: Boolean get() = clientId.isNotBlank() && !clientSecret.isNullOrBlank()

    companion object {
        const val RESOURCE = "/mihon-gdrive.properties"
        const val DEFAULT_CLIENT_ID = "467952807351-4p4c1j9844fo9snoid72ceilvg8j1d4m.apps.googleusercontent.com"
        const val MISSING_SECRET_HINT =
            "Google Drive client secret missing: set gdrive.clientSecret in local.properties " +
                "(or env GDRIVE_CLIENT_SECRET) and rebuild."

        fun resolve(
            resource: Properties? = loadResource(),
            systemProperty: (String) -> String? = System::getProperty,
            env: (String) -> String? = System::getenv,
        ): GoogleDriveConfig {
            fun pick(vararg values: String?) = values.firstOrNull { !it.isNullOrBlank() }?.trim()
            return GoogleDriveConfig(
                clientId = pick(
                    systemProperty("mihon.gdrive.clientId"),
                    env("GDRIVE_CLIENT_ID"),
                    resource?.getProperty("clientId"),
                ) ?: DEFAULT_CLIENT_ID,
                clientSecret = pick(
                    systemProperty("mihon.gdrive.clientSecret"),
                    env("GDRIVE_CLIENT_SECRET"),
                    resource?.getProperty("clientSecret"),
                ),
            )
        }

        private fun loadResource(): Properties? =
            GoogleDriveConfig::class.java.getResourceAsStream(RESOURCE)?.use { stream ->
                Properties().apply { load(stream) }
            }
    }
}
