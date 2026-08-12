package atropos.core.factory

class FactoryClarificationRequired(
    val request: FactoryClarificationRequest
) : IllegalArgumentException(request.questions.joinToString(" ") { "YES/NO: $it" }) {
    val questions: List<String> get() = request.questions
}
