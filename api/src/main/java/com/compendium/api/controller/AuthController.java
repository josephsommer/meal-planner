package com.compendium.api.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // POST /api/auth/login and POST /api/auth/logout are handled entirely
    // by Spring Security's filter chain (see SecurityConfig) — they never
    // reach a @Controller.
    //
    // This endpoint exists only to hand the SPA a CSRF cookie before its
    // first state-changing request. CookieCsrfTokenRepository defers writing
    // the XSRF-TOKEN cookie until the token is actually read — injecting
    // CsrfToken alone isn't enough, since the injected value is itself a
    // lazy wrapper; calling getToken() is what forces the read that makes
    // Spring Security save it (and thus write the cookie) on this response.
    // The frontend calls this once, reads the cookie, and echoes its value
    // back as an X-XSRF-TOKEN header on every POST/PUT/DELETE from then on
    // (login included).
    @GetMapping("/csrf")
    public void csrf(CsrfToken token) {
        token.getToken();
    }
}
