package atropos.core.provider.adapter

import java.net.HttpURLConnection
import java.net.URI

/** Opens provider connections without allowing credentials to cross an HTTP redirect boundary. */
internal object CredentialSafeHttpTransport {
    fun open(endpoint: URI): HttpURLConnection {
        require(endpoint.scheme.equals("https", ignoreCase = true)) { "provider endpoint must use HTTPS" }
        require(endpoint.rawUserInfo == null) { "provider endpoint must not contain URI credentials" }

        return (endpoint.toURL().openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            useCaches = false
        }
    }
}
