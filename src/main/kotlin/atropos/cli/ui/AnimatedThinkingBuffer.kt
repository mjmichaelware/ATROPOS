package atropos.cli.ui

/** Pure frame sequence for thinking output; scheduling remains SpinnerEngine's concern. */
class AnimatedThinkingBuffer(
    private val frames: List<String> = DEFAULT_FRAMES
) {
    init {
        require(frames.isNotEmpty() && frames.all(String::isNotBlank)) { "thinking frames are required" }
    }

    fun frame(index: Int): String = frames[index.floorMod(frames.size)]

    fun render(index: Int, message: String): String {
        require(message.isNotBlank()) { "thinking message is required" }
        return "${frame(index)} $message"
    }

    fun sequence(count: Int, message: String): List<String> {
        require(count >= 0) { "thinking frame count cannot be negative" }
        return (0 until count).map { render(it, message) }
    }

    private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

    private companion object {
        val DEFAULT_FRAMES = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    }
}
