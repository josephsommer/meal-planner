package com.compendium.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

// Gates /api/internal/** (see SecurityConfig's internalFilterChain, which
// constructs this directly rather than injecting it as a bean — a Filter
// that's also a Spring-managed bean gets auto-registered by Spring Boot as a
// *global* servlet filter applied to every request, regardless of how it's
// separately wired into one specific SecurityFilterChain. That would gate
// every endpoint in the app behind the internal secret, not just this one).
// There is exactly one caller — the worker Lambda — so a bare shared-secret
// header check is the simplest thing that works; no Authentication object or
// entry point is needed for a single machine-to-machine caller.
public class SharedSecretAuthFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Secret";

    private final byte[] expectedSecret;

    public SharedSecretAuthFilter(String expectedSecret) {
        this.expectedSecret = expectedSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER_NAME);

        // MessageDigest.isEqual is constant-time regardless of where the
        // inputs first differ, unlike String.equals — cheap insurance
        // against a timing side-channel on a secret this endpoint's entire
        // trust boundary depends on.
        if (provided == null || !MessageDigest.isEqual(provided.getBytes(StandardCharsets.UTF_8), expectedSecret)) {
            response.sendError(HttpStatus.FORBIDDEN.value());
            return;
        }

        filterChain.doFilter(request, response);
    }
}
