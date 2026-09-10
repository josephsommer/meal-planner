package com.compendium.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/**
 * Handles the case JsonAuthenticationFailureHandler / HttpStatusEntryPoint
 * don't cover: an already-authenticated session whose request still gets
 * denied — in practice, a CSRF token that's stale or missing on a
 * state-changing request. Without this, that case falls through to Spring
 * Security's default (non-JSON) 403 handling, which the SPA can't parse.
 */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JsonAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException exception) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), Map.of("error", "Access denied"));
    }
}
