package atropos.core.shared

/**
 * Small multiplatform boundary shared by engine surfaces.
 *
 * The JVM engine owns policy and platform adapters; this module owns only the
 * value contract that lets another target describe its reachable surfaces
 * without importing JVM classes.
 */
data class PortableSurface(
    val id: String,
    val capabilities: Set<String>
)

interface PortableSurfaceContract {
    fun surfaces(): List<PortableSurface>
}
