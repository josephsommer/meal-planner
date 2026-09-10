package com.compendium.api.service;

import com.compendium.api.domain.Article;
import com.compendium.api.domain.ArticleRepository;
import com.compendium.api.domain.User;
import com.compendium.api.domain.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
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

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user with username " + username));

        return articleRepository.save(new Article(url, user));
    }
}
