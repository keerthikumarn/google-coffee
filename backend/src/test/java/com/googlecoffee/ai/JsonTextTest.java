package com.googlecoffee.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Models often wrap JSON in markdown fences; this is what turns their output back into parseable text. */
class JsonTextTest {

    @Test
    void plainJsonIsOnlyTrimmed() {
        assertEquals("{\"a\":1}", JsonText.stripFences("  {\"a\":1} \n"));
    }

    @Test
    void removesAFenceWithALanguageTag() {
        assertEquals("{\"a\":1}", JsonText.stripFences("```json\n{\"a\":1}\n```"));
    }

    @Test
    void removesAFenceWithoutALanguageTag() {
        assertEquals("{\"a\":1}", JsonText.stripFences("```\n{\"a\":1}\n```"));
    }

    @Test
    void keepsMultiLineJsonInsideTheFenceIntact() {
        assertEquals("{\n  \"a\": 1,\n  \"b\": [2]\n}", JsonText.stripFences("```json\n{\n  \"a\": 1,\n  \"b\": [2]\n}\n```"));
    }

    @Test
    void toleratesAMissingClosingFence() {
        assertEquals("{\"a\":1}", JsonText.stripFences("```json\n{\"a\":1}"));
    }

    @Test
    void toleratesWhitespaceAroundTheFences() {
        assertEquals("{\"a\":1}", JsonText.stripFences("\n  ```json\n{\"a\":1}\n```  \n"));
    }

    @Test
    void handlesAFenceOnASingleLine() {
        assertEquals("{\"a\":1}", JsonText.stripFences("```{\"a\":1}```"));
    }

    @Test
    void leavesBackticksInsideTheJsonAlone() {
        assertEquals("{\"code\":\"use `x`\"}", JsonText.stripFences("```json\n{\"code\":\"use `x`\"}\n```"));
    }

    @Test
    void emptyAndFenceOnlyInputsBecomeEmpty() {
        assertEquals("", JsonText.stripFences(""));
        assertEquals("", JsonText.stripFences("   "));
        assertEquals("", JsonText.stripFences("```"));
        assertEquals("", JsonText.stripFences("```json\n```"));
    }
}
