-- Covers ArticleRepository.findByFetchStatusAndDeletedFalseAndLastEnqueuedAtBefore,
-- run every 5 minutes by ArticleRescanConfig — without this the query is a
-- full table scan on every pass.
CREATE INDEX idx_article_fetch_status_deleted_enqueued
    ON article (fetch_status, deleted, last_enqueued_at);
