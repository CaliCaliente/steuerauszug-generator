package com.steuerauszug.backend.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IbParserTest {

    @Test
    fun `should extract symbol from description with parenthesis`() {
        assertEquals("AAPL", IbParser.extractSymbol("AAPL(US0378331005) Common Stock"))
    }

    @Test
    fun `should extract first word when no parenthesis`() {
        assertEquals("USD", IbParser.extractSymbol("USD Interest"))
    }

    @Test
    fun `should return description as-is when single word and no parenthesis`() {
        assertEquals("AAPL", IbParser.extractSymbol("AAPL"))
    }
}
