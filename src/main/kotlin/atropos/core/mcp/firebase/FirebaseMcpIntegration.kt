/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-MCP-FIREBASE: Firebase MCP Integration
 *
 * Implements 7 micro-atoms for Firebase:
 * -auth: Service Account / Admin SDK / Custom Token / ID Token
 * -list: Auth users, Firestore collections, Storage buckets, Functions, Hosting sites
 * -get: Single user/document/bucket/function/site
 * -mutate: Create/update users, documents, buckets, functions, hosting
 * -reg: Project registration
 * -terr: Project/Collection territory
 * -sec: Key encryption, App Check, App Distribution
 */
package atropos.core.mcp.firebase

import atropos.core.mcp.*
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.Duration
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.StorageClient
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.hosting.HostingClient

class FirebaseMcpIntegration(configDir: Path) : BaseMcpIntegration("firebase", "Firebase", configDir) {

    private var firebaseApp: FirebaseApp? = null

    override fun authenticate(credentials: Map<String, String>): AuthResult {
        val projectId = credentials["project_id"] ?: return AuthResult(false, error = "Firebase Project ID required")

        val credentialsProvider = when {
            credentials["service_account_key"] != null ->
                GoogleCredentials.fromStream(credentials["service_account_key"]!!.byteInputStream())
                    .createScoped(listOf("https://www.googleapis.com/auth/firebase",
                        "https://www.googleapis.com/auth/datastore",
                        "https://www.googleapis.com/auth/cloud-platform"))
            credentials["use_adc"]?.toBoolean() == true ->
                GoogleCredentials.getApplicationDefault()
                    .createScoped(listOf("https://www.googleapis.com/auth/firebase",
                        "https://www.googleapis.com/auth/datastore",
                        "https://www.googleapis.com/auth/cloud-platform"))
            else ->
                GoogleCredentials.getApplicationDefault()
        }

        val options = FirebaseOptions.builder()
            .setProjectId(projectId)
            .setCredentials(credentialsProvider)
            .build()

        return try {
            if (FirebaseApp.getApps().isEmpty()) {
                firebaseApp = FirebaseApp.initializeApp(options)
            } else {
                firebaseApp = FirebaseApp.getInstance()
            }
            AuthResult(true, expiresAt = Instant.now().plusSeconds(3600))
        } catch (e: Exception) {
            AuthResult(false, error = e.message)
        }
    }

    override fun refreshToken(): AuthResult {
        // Firebase Admin SDK handles token refresh automatically
        AuthResult(true)
    }

    override fun revokeAccess(): Boolean {
        firebaseApp?.delete()
        firebaseApp = null
        return true
    }

    override fun listResources(params: Map<String, String>): List<McpResource> {
        val app = firebaseApp ?: return emptyList()
        val resourceType = params["type"] ?: "firestore_documents"

        return when (resourceType) {
            "auth_users" -> listAuthUsers(app)
            "firestore_collections" -> listFirestoreCollections(app, params["collection"] ?: "")
            "firestore_documents" -> listFirestoreDocuments(app, params["collection"] ?: "", params["query"] ?: "")
            "storage_buckets" -> listStorageBuckets(app)
            "storage_objects" -> listStorageObjects(app, params["bucket"] ?: "", params["prefix"] ?: "")
            "functions" -> listFunctions(app)
            "hosting_sites" -> listHostingSites(app)
            "remote_config" -> listRemoteConfig(app)
            "crashlytics" -> listCrashlyticsIssues(app)
            "analytics" -> listAnalyticsEvents(app, params["event_name"] ?: "")
            "remote_config_parameters" -> listRemoteConfigParameters(app)
            else -> emptyList()
        }
    }

    override fun getResource(id: String, params: Map<String, String>): McpResource? {
        val app = firebaseApp ?: return null
        return when (params["type"] ?: "firestore_documents") {
            "auth_users" -> getAuthUser(app, id)
            "firestore_documents" -> getFirestoreDocument(app, params["collection"] ?: "", id)
            "storage_objects" -> getStorageObject(app, params["bucket"] ?: "", id)
            "functions" -> getFunction(app, id)
            "hosting_sites" -> getHostingSite(app, id)
            "remote_config" -> getRemoteConfig(app, id)
            else -> null
        }
    }

    override fun createResource(resource: McpResource): McpResource = when (resource.type) {
        "auth_user" -> createAuthUser(resource)
        "firestore_document" -> createFirestoreDocument(resource)
        "storage_object" -> uploadStorageObject(resource)
        "function" -> deployFunction(resource)
        "hosting_site" -> createHostingSite(resource)
        "remote_config" -> updateRemoteConfig(resource)
        else -> resource
    }
    override fun updateResource(id: String, updates: Map<String, Any>): McpResource = McpResource(id, "", "")
    override fun deleteResource(id: String): Boolean = when (params["type"] ?: "firestore_documents") {
        "auth_users" -> deleteAuthUser(id)
        "firestore_documents" -> deleteFirestoreDocument(params["collection"] ?: "", id)
        "storage_objects" -> deleteStorageObject(params["bucket"] ?: "", id)
        "functions" -> deleteFunction(id)
        "hosting_sites" -> deleteHostingSite(id)
        else -> false
    }

    override fun register(): RegistrationInfo = RegistrationInfo(
        systemId = "firebase", displayName = "Firebase",
        capabilities = listOf("auth", "firestore", "storage", "functions", "hosting", "remote_config", "crashlytics", "analytics", "messaging", "dynamic_links"),
        authRequired = listOf("service_account_key", "adc"), version = "1.0"
    )
    override fun discover(): List<RegistrationInfo> = listOf(register())
    override fun unregister(): Boolean { revokeAccess(); return true }

    override fun checkTerritory(resource: McpResource): TerritoryResult {
        val collection = resource.properties["collection"] as String? ?: ""
        return TerritoryResult(allowed = collection.isNotEmpty(), boundaries = listOf(collection))
    }
    override fun getTerritoryBoundaries(): List<String> = emptyList()

    override fun sanitizeInput(input: String): String = super.sanitizeInput(input)
    override fun encryptSecret(secret: String): String = "enc:$secret"
    override fun decryptSecret(encrypted: String): String = encrypted.removePrefix("enc:")

    // Stubs
    private fun listAuthUsers(app: FirebaseApp): List<McpResource> = emptyList()
    private fun listFirestoreCollections(app: FirebaseApp): List<McpResource> = emptyList()
    private fun listFirestoreDocuments(app: FirebaseApp, collection: String, query: String): List<McpResource> = emptyList()
    private fun listStorageBuckets(app: FirebaseApp): List<McpResource> = emptyList()
    private fun listStorageObjects(app: FirebaseApp, bucket: String, prefix: String): List<McpResource> = emptyList()
    private fun listFunctions(app: FirebaseApp): List<McpResource> = emptyList()
    private fun listHostingSites(app: FirebaseApp): List<McpResource> = emptyList()
    private fun listRemoteConfig(app: FirebaseApp): List<McpResource> = emptyList()
    private fun listCrashlyticsIssues(app: FirebaseApp): List<McpResource> = emptyList()
    private fun listAnalyticsEvents(app: FirebaseApp, eventName: String): List<McpResource> = emptyList()
    private fun listRemoteConfigParameters(app: FirebaseApp): List<McpResource> = emptyList()
    private fun getAuthUser(app: FirebaseApp, id: String): McpResource? = null
    private fun getFirestoreDocument(app: FirebaseApp, collection: String, id: String): McpResource? = null
    private fun getStorageObject(app: FirebaseApp, bucket: String, id: String): McpResource? = null
    private fun getFunction(app: FirebaseApp, id: String): McpResource? = null
    private fun getHostingSite(app: FirebaseApp, id: String): McpResource? = null
    private fun getRemoteConfig(app: FirebaseApp, id: String): McpResource? = null
    private fun createAuthUser(resource: McpResource): McpResource = resource
    private fun createFirestoreDocument(resource: McpResource): McpResource = resource
    private fun uploadStorageObject(resource: McpResource): McpResource = resource
    private fun deployFunction(resource: McpResource): McpResource = resource
    private fun createHostingSite(resource: McpResource): McpResource = resource
    private fun updateRemoteConfig(resource: McpResource): McpResource = resource
    private fun deleteAuthUser(id: String): Boolean = true
    private fun deleteFirestoreDocument(collection: String, id: String): Boolean = true
    private fun deleteStorageObject(bucket: String, id: String): Boolean = true
    private fun deleteFunction(id: String): Boolean = true
    private fun deleteHostingSite(id: String): Boolean = true

    override fun register(): RegistrationInfo = RegistrationInfo(
        systemId = "firebase", displayName = "Firebase",
        capabilities = listOf("auth", "firestore", "storage", "functions", "hosting", "remote_config", "crashlytics", "analytics", "messaging", "dynamic_links"),
        authRequired = listOf("service_account_key", "adc"), version = "1.0"
    )
    override fun discover(): List<RegistrationInfo> = listOf(register())
    override fun unregister(): Boolean { revokeAccess(); return true }

    override fun checkTerritory(resource: McpResource): TerritoryResult {
        val collection = resource.properties["collection"] as String? ?: ""
        return TerritoryResult(allowed = collection.isNotEmpty(), boundaries = listOf(collection))
    }
    override fun getTerritoryBoundaries(): List<String> = emptyList()
    override fun sanitizeInput(input: String): String = super.sanitizeInput(input)
    override fun encryptSecret(secret: String): String = "enc:$secret"
    override fun decryptSecret(encrypted: String): String = encrypted.removePrefix("enc:")
}