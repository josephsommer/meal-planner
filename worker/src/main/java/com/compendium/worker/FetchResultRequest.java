package com.compendium.worker;

// Mirrors ArticleInternalController.FetchResultRequest on the API side.
public record FetchResultRequest(boolean success, String title, String excerpt, String content, String errorMessage) {}
