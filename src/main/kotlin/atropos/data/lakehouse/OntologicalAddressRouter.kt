package atropos.data.lakehouse
import java.io.File
class OntologicalAddressRouter(val path: String) {
    init { File(path).mkdirs() }
}
