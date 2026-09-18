package com.compendium.api.service;

import com.compendium.api.domain.Article;
import com.compendium.api.domain.ArticleRepository;
import com.compendium.api.domain.User;
import com.compendium.api.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ArticleService articleService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void saveArticle_savesArticleOwnedByTheAuthenticatedUser() {
        User user = new User("admin", "hash");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(articleRepository.save(any(Article.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Article saved = articleService.saveArticle("https://example.com/recipe", "admin");

        assertThat(saved.getUrl()).isEqualTo("https://example.com/recipe");
        assertThat(saved.getCreatedBy()).isEqualTo(user);

        ArgumentCaptor<Article> captor = ArgumentCaptor.forClass(Article.class);
        verify(articleRepository).save(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo("https://example.com/recipe");
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
}
