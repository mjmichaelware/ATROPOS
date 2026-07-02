package atropos.core.endpoint

interface OperationRegistry {
    fun getAll(): List<OperationEndpoint>
    fun getById(id: String): OperationEndpoint?
    fun getByKind(kind: EndpointKind): List<OperationEndpoint>
}
