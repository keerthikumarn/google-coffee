package com.googlecoffee.ai;

/** Cleans model output so it can be parsed as JSON (models sometimes wrap it in markdown fences). */
public final class JsonText {
    private JsonText() {}

    public static String stripFences(String text) {
        String finalText = text.trim();
        if (finalText.startsWith("```")) {
            int firstNewline = finalText.indexOf('\n');
            finalText = firstNewline >= 0 ? finalText.substring(firstNewline + 1) : finalText.substring(3);
            if (finalText.endsWith("```")) finalText = finalText.substring(0, finalText.length() - 3);
        }
        return finalText.trim();
    }
}
