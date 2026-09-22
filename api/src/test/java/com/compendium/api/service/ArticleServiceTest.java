package com.compendium.api.service;

import com.compendium.api.domain.Article;
import com.compendium.api.domain.ArticleRepository;
import com.compendium.api.domain.User;
import com.compendium.api.domain.UserRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SqsTemplate sqsTemplate;

    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Not @InjectMocks: the constructor's queue-url String can't be
        // resolved from a mock, and a real value here is more honest than
        // relying on Mockito silently passing null for it.
        articleService = new ArticleService(articleRepository, userRepository, sqsTemplate, "test-queue-url");
    }

    @Test
    void saveArticle_savesArticleOwnedByTheAuthenticatedUser() {
        User user = new User("admin", "hash");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Article saved = articleService.saveArticle("https://example.com/recipe", "admin");

        assertThat(saved.getUrl()).isEqualTo("https://example.com/recipe");
        assertThat(saved.getCreatedBy()).isEqualTo(user);
        assertThat(saved.getLastEnqueuedAt()).isNotNull();

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo("https://example.com/recipe");

        ArgumentCaptor<ArticleService.ArticleFetchMessage> messageCaptor =
                ArgumentCaptor.forClass(ArticleService.ArticleFetchMessage.class);
        verify(sqsTemplate).send(eq("test-queue-url"), messageCaptor.capture());
        assertThat(messageCaptor.getValue().url()).isEqualTo("https://example.com/recipe");
    }

    @Test
    void saveArticle_stillSucceedsWhenSqsPublishFails() {
        User user = new User("admin", "hash");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("SQS unreachable")).when(sqsTemplate).send(eq("test-queue-url"), any());

        Article saved = articleService.saveArticle("https://example.com/recipe", "admin");

        assertThat(saved.getUrl()).isEqualTo("https://example.com/recipe");
    }

    @Test
    void saveArticle_rejectsBlankUrl() {
        assertThatThrownBy(() -> articleService.saveArticle("   ", "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("url must not be blank");
    }

    @Test
    void saveArticle_rejectsUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.saveArticle("https://example.com/recipe", "ghost"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getArticlesForUser_returnsOnlyThatUsersNonDeletedArticles() {
        User user = new User("admin", "hash");
        Article article = new Article("https://example.com/recipe", user);
        when(articleRepository.findByCreatedBy_UsernameAndDeletedFalseOrderByCreatedAtDesc("admin"))
                .thenReturn(List.of(article));

        List<Article> articles = articleService.getArticlesForUser("admin");

        assertThat(articles).containsExactly(article);
    }

    @Test
    void deleteArticle_marksTheOwnedArticleAsDeleted() {
        User user = new User("admin", "hash");
        Article article = new Article("https://example.com/recipe", user);
        when(articleRepository.findByIdAndCreatedBy_UsernameAndDeletedFalse(1L, "admin"))
                .thenReturn(Optional.of(article));

        articleService.deleteArticle(1L, "admin");

        assertThat(article.isDeleted()).isTrue();
        verify(articleRepository).save(article);
    }

    @Test
    void deleteArticle_returns404WhenArticleIsMissingOrNotOwned() {
        when(articleRepository.findByIdAndCreatedBy_UsernameAndDeletedFalse(1L, "admin"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> articleService.deleteArticle(1L, "admin"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);
    }

    @Test
    void recordFetchSuccess_delegatesToTheConditionalRepositoryUpdate() {
        articleService.recordFetchSuccess(1L, "Title", "Excerpt", "Body text");

        verify(articleRepository).recordSuccessIfPending(eq(1L), eq("Title"), eq("Excerpt"), eq("Body text"), any(Instant.class));
    }

    @Test
    void recordFetchFailure_delegatesToTheConditionalRepositoryUpdate() {
        articleService.recordFetchFailure(1L, "404 Not Found");

        verify(articleRepository).recordFailureIfPending(eq(1L), eq("404 Not Found"), any(Instant.class));
    }
}
