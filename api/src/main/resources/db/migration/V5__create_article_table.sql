CREATE TABLE article (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    url        VARCHAR(2048) NOT NULL,
    created_by BIGINT        NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_article_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
