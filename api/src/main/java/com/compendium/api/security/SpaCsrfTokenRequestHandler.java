package com.compendium.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import java.util.function.Supplier;

/**
 * A copy of Spring Security's own CsrfConfigurer.spa() request handler —
 * that class is a private nested type, so csrf.spa() can't be combined
 * with CookieCsrfTokenRepository.setCookieCustomizer (needed here to set
 * Secure; see SecurityConfig). This preserves spa()'s exact
 * behavior: BREACH-safe XOR masking on the way out, but still accepting
 * either the raw or XOR-masked value echoed back in the X-XSRF-TOKEN
 * header, since the cookie itself always holds the raw value for
 * JavaScript to read.
 */
public class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestAttributeHandler plain = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestAttributeHandler xor = new XorCsrfTokenRequestAttributeHandler();

    public SpaCsrfTokenRequestHandler() {
        this.xor.setCsrfRequestAttributeName(null);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
        this.xor.handle(request, response, csrfToken);
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
        String headerValue = request.getHeader(csrfToken.getHeaderName());
        return (StringUtils.hasText(headerValue) ? this.plain : this.xor).resolveCsrfTokenValue(request, csrfToken);
    }
}
