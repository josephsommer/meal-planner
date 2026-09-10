package com.compendium.api.service;

import com.compendium.api.domain.Article;
import com.compendium.api.domain.ArticleRepository;
import com.compendium.api.domain.User;
import com.compendium.api.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final UserRepository userRepository;

    public ArticleService(ArticleRepository articleRepository, UserRepository userRepository) {
        this.articleRepository = articleRepository;
        this.userRepository = userRepository;
    }

    // Scraping the URL for ingredients/steps is out of scope here — this
    // just records the submission. The worker picks it up from there.
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

        return articleRepository.save(new Article(url, user));
    }
}
