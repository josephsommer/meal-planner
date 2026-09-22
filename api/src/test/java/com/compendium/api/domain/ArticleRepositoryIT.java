package com.compendium.api.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// @SpringBootTest + real MySQL, matching AuthenticationIT/ArticleIT's
// convention — this repo has no embedded test database on the classpath
// (MySQL via Testcontainers is used everywhere), so @DataJpaTest's default
// embedded-database auto-replacement would fail here.
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
        "admin-username=testadmin",
        "admin-password=test-password",
        "internal.worker-secret=test-internal-secret"
})
class ArticleRepositoryIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findsOnlyStuckPendingArticlesExcludingDeletedAndRecent() {
        User user = userRepository.save(new User("admin", "hash"));
        Instant cutoff = Instant.now().minus(15, ChronoUnit.MINUTES);

        Article stuckPending = new Article("https://example.com/stuck", user);
        setLastEnqueuedAt(stuckPending, cutoff.minus(1, ChronoUnit.MINUTES));
        articleRepository.save(stuckPending);

        Article recentlyEnqueued = new Article("https://example.com/recent", user);
        setLastEnqueuedAt(recentlyEnqueued, Instant.now());
        articleRepository.save(recentlyEnqueued);

        Article stuckButDeleted = new Article("https://example.com/deleted", user);
        setLastEnqueuedAt(stuckButDeleted, cutoff.minus(1, ChronoUnit.MINUTES));
        stuckButDeleted.markDeleted();
        articleRepository.save(stuckButDeleted);

        List<Article> stuck = articleRepository
                .findByFetchStatusAndDeletedFalseAndLastEnqueuedAtBefore(FetchStatus.PENDING, cutoff);

        assertThat(stuck).extracting(Article::getUrl).containsExactly("https://example.com/stuck");
    }

    // @Modifying repository queries need an active transaction, normally
    // provided by a @Transactional service method (ArticleService.
    // recordFetchSuccess/recordFetchFailure) — this test calls the
    // repository directly, so it opens one itself.
    @Test
    @Transactional
    void recordSuccessIfPending_isANoOpWhenArticleIsAlreadyTerminal() {
        User user = userRepository.save(new User("admin2", "hash"));
        Article article = new Article("https://example.com/recipe", user);
        article = articleRepository.save(article);

        int firstUpdate = articleRepository.recordSuccessIfPending(
                article.getId(), "Title", "Excerpt", "Content", Instant.now());
        int secondUpdate = articleRepository.recordFailureIfPending(
                article.getId(), "should not apply", Instant.now());

        assertThat(firstUpdate).isEqualTo(1);
        assertThat(secondUpdate).isEqualTo(0);
        Article reloaded = articleRepository.findById(article.getId()).orElseThrow();
        assertThat(reloaded.getFetchStatus()).isEqualTo(FetchStatus.SUCCEEDED);
        assertThat(reloaded.getTitle()).isEqualTo("Title");
    }

    private static void setLastEnqueuedAt(Article article, Instant value) {
        try {
            var field = Article.class.getDeclaredField("lastEnqueuedAt");
            field.setAccessible(true);
            field.set(article, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
