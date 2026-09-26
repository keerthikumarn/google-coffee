package com.googlecoffee.ai;

import com.googlecoffee.model.MenuItem;

public record Suggestion(MenuItem item, String reason) {}
