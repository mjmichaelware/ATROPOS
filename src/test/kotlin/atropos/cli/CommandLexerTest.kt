package atropos.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CommandLexerTest {
    @Test
    fun apostrophe_inside_plain_word_is_not_an_unterminated_quote() {
        val result = assertIs<LexResult.Success>(CommandLexer.lex("/artifact build today's report"))
        assertEquals(listOf("/artifact", "build", "today's", "report"), result.tokens)
    }

    @Test
    fun quoted_arguments_still_support_single_quotes() {
        val result = assertIs<LexResult.Success>(CommandLexer.lex("/factory run 'notes app'"))
        assertEquals(listOf("/factory", "run", "notes app"), result.tokens)
    }
}
