package com.compendium.api.controller;

import com.compendium.api.domain.Article;
import com.compendium.api.service.ArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articleService;

    public ArticleController(ArticleService articleService) {
        this.articleService = articleService;
    }

    record CreateArticleRequest(String url) {}

    record ArticleResponse(Long id, String url, Instant createdAt) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ArticleResponse create(@RequestBody CreateArticleRequest request, Authentication authentication) {
        Article article = articleService.saveArticle(request.url(), authentication.getName());
        return new ArticleResponse(article.getId(), article.getUrl(), article.getCreatedAt());
    }
}
