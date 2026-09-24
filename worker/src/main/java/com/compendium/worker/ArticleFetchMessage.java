package com.compendium.worker;

// Mirrors ArticleService.ArticleFetchMessage on the API side — the wire
// shape both the initial SQS send and the DLQ redelivery share.
public record ArticleFetchMessage(Long articleId, String url) {}
