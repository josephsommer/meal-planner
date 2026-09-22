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
        Instant enqueuedBefore = stuck.getLastEnqueuedAt();

        rescanConfig.rescan();

        ArgumentCaptor<ArticleFetchMessage> messageCaptor = ArgumentCaptor.forClass(ArticleFetchMessage.class);
        verify(sqsTemplate).send(eq("fetch-queue-url"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().url()).isEqualTo("https://example.com/recipe");
        assertThat(stuck.getLastEnqueuedAt()).isNotEqualTo(enqueuedBefore);
        verify(articleRepository).save(stuck);
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
