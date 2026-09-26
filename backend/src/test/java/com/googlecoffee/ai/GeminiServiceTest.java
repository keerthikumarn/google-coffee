package com.googlecoffee.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeminiServiceTest {
    @Test
    void stripsMarkdownFences() {
        assertEquals("{\"a\":1}", GeminiService.stripFences("```json\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1}", GeminiService.stripFences("  {\"a\":1} "));
    }
}
