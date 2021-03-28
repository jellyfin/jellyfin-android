import org.gradle.api.Project
import java.io.File
import java.util.*

object SigningHelper {

    fun loadSigningConfig(project: Project): Config? {
        val serializedKeystore = System.getenv("KEYSTORE") ?: return null
        val storeFile = try {
            project.file("/tmp/keystore.jks").apply {
                writeBytes(Base64.getDecoder().decode(serializedKeystore))
            }
        } catch (e: RuntimeException) {
            return null
        }
        val storePassword = System.getenv("KEYSTORE_PASSWORD") ?: return null
        val keyAlias = System.getenv("KEY_ALIAS") ?: return null
        val keyPassword = System.getenv("KEY_PASSWORD") ?: return null

        return Config(
            storeFile,
            storePassword,
            keyAlias,
            keyPassword
        )
    }

    data class Config(
        /**
         * Store file used when signing.
         */
        val storeFile: File,

        /**
         * Store password used when signing.
         */
        val storePassword: String,

        /**
         * Key alias used when signing.
         */
        val keyAlias: String,

        /**
         * Key password used when signing.
         */
        val keyPassword: String
    )
}
