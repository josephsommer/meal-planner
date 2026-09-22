package com.compendium.api.controller;

import com.compendium.api.domain.Article;
import com.compendium.api.domain.FetchStatus;
import com.compendium.api.service.ArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    record CreateArticleRequest(String url) {}

    // No last_error field here on purpose — the raw failure reason (which
    // could contain exception text or fragments of the target site's
    // response) stays server-side; the client only ever needs to know the
    // article failed, not why.
    record ArticleResponse(Long id, String url, Instant createdAt, FetchStatus fetchStatus,
            String title, String excerpt, String content) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleResponse create(@RequestBody CreateArticleRequest request, Authentication authentication) {
        Article article = articleService.saveArticle(request.url(), authentication.getName());
        return toResponse(article);
    }

    @GetMapping
    public List<ArticleResponse> list(Authentication authentication) {
        return articleService.getArticlesForUser(authentication.getName()).stream()
                .map(ArticleController::toResponse)
                .toList();
    }

    private static ArticleResponse toResponse(Article article) {
        return new ArticleResponse(article.getId(), article.getUrl(), article.getCreatedAt(),
                article.getFetchStatus(), article.getTitle(), article.getExcerpt(), article.getContent());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public void delete(@PathVariable Long id, Authentication authentication) {
        articleService.deleteArticle(id, authentication.getName());
    }
}
