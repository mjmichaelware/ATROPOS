package atropos.core.multimodal

class LivePreviewEvidenceService(
    private val browserActuator: BrowserActuator = BrowserActuator()
) {
    fun captureUrl(url: String, expectedText: String? = null): BrowserEvidenceResult =
        browserActuator.capture(BrowserEvidenceRequest(url = url, expectedText = expectedText))

    fun captureStaticHtml(label: String, html: String, expectedText: String? = null): BrowserEvidenceResult =
        browserActuator.captureStaticHtml(label, html, expectedText)
}
