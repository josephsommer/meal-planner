package com.compendium.api.config;

import com.compendium.api.domain.Article;
import com.compendium.api.domain.ArticleRepository;
import com.compendium.api.domain.FetchStatus;
import com.compendium.api.domain.User;
import com.compendium.api.service.ArticleService;
import com.compendium.api.service.ArticleService.ArticleFetchMessage;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleRescanConfigTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private ArticleService articleService;

    @Mock
    private SqsTemplate sqsTemplate;

    private ArticleRescanConfig rescanConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        rescanConfig = new ArticleRescanConfig(articleRepository, articleService, sqsTemplate,
                "fetch-queue-url", "dlq-url");
        when(sqsTemplate.receiveMany(eq("dlq-url"), eq(ArticleFetchMessage.class))).thenReturn(List.of());
    }

    @Test
    void rescan_republishesStuckPendingArticlesAndBumpsLastEnqueuedAt() {
        Article stuck = new Article("https://example.com/recipe", new User("admin", "hash"));
        when(articleRepository.findByFetchStatusAndDeletedFalseAndLastEnqueuedAtBefore(eq(FetchStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(stuck));

        rescanConfig.rescan();

        ArgumentCaptor<ArticleFetchMessage> messageCaptor = ArgumentCaptor.forClass(ArticleFetchMessage.class);
        verify(sqsTemplate).send(eq("fetch-queue-url"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().url()).isEqualTo("https://example.com/recipe");
        // Conditional update through the service, not a full entity save —
        // guards against reverting a result the worker's callback may have
        // just recorded concurrently (see ArticleRepository.markEnqueuedIfPending).
        verify(articleService).markEnqueuedIfPending(stuck.getId());
        verify(articleRepository, never()).save(any(Article.class));
    }

    @Test
    void rescan_continuesToTheNextArticleWhenOnePublishFails() {
        Article first = new Article("https://example.com/one", new User("admin", "hash"));
        Article second = new Article("https://example.com/two", new User("admin", "hash"));
        setId(first, 1L);
        setId(second, 2L);
        when(articleRepository.findByFetchStatusAndDeletedFalseAndLastEnqueuedAtBefore(eq(FetchStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of(first, second));
        doThrow(new RuntimeException("SQS unreachable"))
                .when(sqsTemplate).send(eq("fetch-queue-url"), eq(new ArticleFetchMessage(1L, "https://example.com/one")));

        rescanConfig.rescan();

        verify(articleService).markEnqueuedIfPending(2L);
        verify(articleService, never()).markEnqueuedIfPending(1L);
    }

    // Article's id is @GeneratedValue with no public setter — fine for real
    // persistence, but these two articles need distinguishable ids to
    // verify per-article behavior without a real database.
    private static void setId(Article article, Long id) {
        try {
            var field = Article.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(article, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void rescan_marksAnArticleFoundInTheDlqAsFailed() {
        when(articleRepository.findByFetchStatusAndDeletedFalseAndLastEnqueuedAtBefore(eq(FetchStatus.PENDING), any(Instant.class)))
                .thenReturn(List.of());
        Message<ArticleFetchMessage> dlqMessage = new GenericMessage<>(new ArticleFetchMessage(42L, "https://example.com/recipe"));
        when(sqsTemplate.receiveMany(eq("dlq-url"), eq(ArticleFetchMessage.class))).thenReturn(List.of(dlqMessage));

        rescanConfig.rescan();

        verify(articleService).recordFetchFailure(eq(42L), any(String.class));
    }
}
