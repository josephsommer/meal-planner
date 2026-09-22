package com.compendium.api.controller;

import com.compendium.api.service.ArticleService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

// Separate class from ArticleController, not just a second method on it —
// the caller here is the worker Lambda authenticated by a shared secret
// (see SecurityConfig's internalFilterChain), not a logged-in user, so this
// controller's trust boundary is deliberately visible at a glance rather
// than folded into the user-facing one.
@RestController
@RequestMapping("/api/internal/articles")
public class ArticleInternalController {

    private final ArticleService articleService;

    public ArticleInternalController(ArticleService articleService) {
        this.articleService = articleService;
    }

    record FetchResultRequest(boolean success, String title, String excerpt, String content, String errorMessage) {}

    @PostMapping("/{id}/fetch-result")
    @ResponseStatus(HttpStatus.OK)
    public void reportFetchResult(@PathVariable Long id, @RequestBody FetchResultRequest request) {
        if (request.success()) {
            articleService.recordFetchSuccess(id, request.title(), request.excerpt(), request.content());
        } else {
            articleService.recordFetchFailure(id, request.errorMessage());
        }
    }
}
