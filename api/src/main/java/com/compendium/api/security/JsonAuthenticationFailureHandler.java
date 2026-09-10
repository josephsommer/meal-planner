package com.compendium.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/**
 * formLogin()'s default on failure is also a redirect (back to a
 * ?error login page). Same reasoning as JsonAuthenticationSuccessHandler:
 * a JSON SPA needs a status code and a body it can parse, not a redirect,
 * and the ObjectMapper is injected rather than constructed for the same
 * reason — stay consistent with the rest of the API's Jackson settings.
 */
public class JsonAuthenticationFailureHandler implements AuthenticationFailureHandler {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationFailureHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), Map.of("error", "Invalid username or password"));
    }
}
