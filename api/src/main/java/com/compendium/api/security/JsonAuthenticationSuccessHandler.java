package com.compendium.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/**
 * formLogin()'s default on success is a 302 redirect, built for a
 * server-rendered login page. The frontend here is a JSON SPA making an
 * XHR/fetch call, so this swaps that for a 200 + JSON body instead.
 *
 * Takes the app's auto-configured ObjectMapper (injected from SecurityConfig)
 * rather than constructing its own, so this stays consistent with whatever
 * Jackson settings the rest of the API uses.
 */
public class JsonAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationSuccessHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), Map.of("username", authentication.getName()));
    }
}
