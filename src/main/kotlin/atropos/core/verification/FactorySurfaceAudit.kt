package atropos.core.verification

internal data class FactorySurfaceAudit(
    val territoryValid: Boolean,
    val lineageValid: Boolean,
    val integrityValid: Boolean,
    val auditorAllowed: Boolean,
    val auditorDecision: String,
    val auditorReportSha256: String,
    val directorAllowed: Boolean,
    val directorDecision: String,
    val detail: String
) {
    companion object {
        fun refused(detail: String) = FactorySurfaceAudit(
            territoryValid = false,
            lineageValid = false,
            integrityValid = false,
            auditorAllowed = false,
            auditorDecision = "",
            auditorReportSha256 = "",
            directorAllowed = false,
            directorDecision = "",
            detail = detail
        )
    }
}
