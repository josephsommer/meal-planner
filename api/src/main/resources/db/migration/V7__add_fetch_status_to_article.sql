ALTER TABLE article
    ADD COLUMN fetch_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ADD COLUMN title            VARCHAR(512) NULL,
    ADD COLUMN excerpt          VARCHAR(1000) NULL,
    ADD COLUMN content          LONGTEXT     NULL,
    ADD COLUMN last_enqueued_at TIMESTAMP    NULL,
    ADD COLUMN fetched_at       TIMESTAMP    NULL,
    ADD COLUMN last_error       VARCHAR(500) NULL,
    ADD CONSTRAINT chk_article_fetch_status CHECK (fetch_status IN ('PENDING', 'SUCCEEDED', 'FAILED'));
