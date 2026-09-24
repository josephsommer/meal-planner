package com.compendium.api.service;

import com.compendium.api.domain.Article;
import com.compendium.api.domain.ArticleRepository;
import com.compendium.api.domain.User;
import com.compendium.api.domain.UserRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class ArticleService {

    private static final Logger log = LoggerFactory.getLogger(ArticleService.class);

    // {articleId, url} sent to the fetch queue — kept as a small record
    // rather than reusing the Article entity so the wire message shape
    // doesn't accidentally couple to entity field changes.
    public record ArticleFetchMessage(Long articleId, String url) {}

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;
    private final SqsTemplate sqsTemplate;
    private final String fetchQueueUrl;

    public ArticleService(ArticleRepository articleRepository, UserRepository userRepository,
            SqsTemplate sqsTemplate, @Value("${app.article-fetch-queue-url}") String fetchQueueUrl) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
        this.sqsTemplate = sqsTemplate;
        this.fetchQueueUrl = fetchQueueUrl;
    }

    // The worker picks this up asynchronously: extracts readable text via
    // Readability4J and reports back through the internal callback endpoint.
    public Article saveArticle(String url, String username) {
        if (url == null || url.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url must not be blank");
        }

        // UsernameNotFoundException would be the Spring Security-idiomatic
        // choice here, but it's not @ResponseStatus-annotated and there's no
        // @ControllerAdvice in this codebase to map it — left uncaught it
        // becomes a generic 500. This can only happen if the authenticated
        // principal's row was deleted mid-session, which is as much a client
        // problem (their session is stale) as a server one, so it gets the
        // same ResponseStatusException treatment as the validation above
        // rather than a distinct exception type.
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "No user with username " + username));

        Article article = new Article(url, user);
        article.markEnqueued();
        article = articleRepository.save(article);

        enqueueFetch(article.getId(), url);

        return article;
    }

    // A publish failure here is just an earlier instance of the "lost
    // message" case the periodic rescan job already exists to handle — the
    // article still saves as PENDING and gets picked up on the next rescan
    // pass, rather than failing the whole request or adding a second retry
    // path just for this one call site.
    private void enqueueFetch(Long articleId, String url) {
        try {
            sqsTemplate.send(fetchQueueUrl, new ArticleFetchMessage(articleId, url));
        } catch (RuntimeException e) {
            log.warn("Failed to enqueue fetch job for article {}, relying on rescan job to recover", articleId, e);
        }
    }

    // Called by the worker's internal callback (and by the rescan job's DLQ
    // drain) to record a completed extraction. Looked up by id alone — the
    // caller is the worker, not a user, so there's no owning username to
    // scope by. The repository update is conditional on the article still
    // being PENDING (see ArticleRepository) so a stale duplicate SQS
    // delivery can't clobber a result an earlier delivery already recorded;
    // a false return here is a correct no-op, not an error.
    // @Modifying repository queries need an active transaction — unlike a
    // plain read, Spring Data doesn't wrap these automatically when called
    // from outside one.
    @Transactional
    public void recordFetchSuccess(Long articleId, String title, String excerpt, String content) {
        articleRepository.recordSuccessIfPending(articleId, title, excerpt, content, Instant.now());
    }

    @Transactional
    public void recordFetchFailure(Long articleId, String errorMessage) {
        articleRepository.recordFailureIfPending(articleId, errorMessage, Instant.now());
    }

    // Used by the rescan job's republish path (ArticleRescanConfig) to bump
    // an article's staleness clock without a load-then-save of the whole
    // entity — see the repository method's own comment for why that
    // distinction matters here.
    @Transactional
    public void markEnqueuedIfPending(Long articleId) {
        articleRepository.markEnqueuedIfPending(articleId, Instant.now());
    }

    public List<Article> getArticlesForUser(String username) {
        return articleRepository.findByCreatedBy_UsernameAndDeletedFalseOrderByCreatedAtDesc(username);
    }

    // Scoping the lookup to the requesting user (rather than fetching by id
    // alone and checking ownership after) means a wrong-owner id 404s the
    // same way a nonexistent one does, instead of leaking via a 403 that an
    // id belongs to someone else.
    public void deleteArticle(Long id, String username) {
        Article article = articleRepository.findByIdAndCreatedBy_UsernameAndDeletedFalse(id, username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No article with id " + id));

        article.markDeleted();
        articleRepository.save(article);
    }
}
