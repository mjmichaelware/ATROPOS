package atropos.core.hierarchy

import java.time.Instant

enum class HierarchyTaskState {
    DISPATCHED,
    RUNNING,
    COMPLETED,
    FAILED,
    BLOCKED,
    EXPIRED
}

data class HierarchyTaskRecord(
    val contract: HierarchyDispatchContract,
    val state: HierarchyTaskState = HierarchyTaskState.DISPATCHED,
    val result: String? = null,
    val updatedAt: Instant = contract.createdAt
)

data class HierarchyResultAggregation(
    val parentAuthorityId: String,
    val total: Int,
    val completed: Int,
    val failed: Int,
    val blocked: Int,
    val expired: Int,
    val pending: Int,
    val results: List<String>
) {
    val allTerminal: Boolean
        get() = pending == 0
}
