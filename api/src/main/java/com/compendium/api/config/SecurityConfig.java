package com.compendium.api.config;

import com.compendium.api.security.JsonAuthenticationSuccessHandler;
import com.compendium.api.security.JsonLogoutSuccessHandler;
import com.compendium.api.security.SharedSecretAuthFilter;
import com.compendium.api.security.SpaCsrfTokenRequestHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Secure needs to be true in prod (CloudFront's viewer connection is
    // HTTPS) but false locally (plain http through Vite's dev proxy, which
    // also makes the SPA and API same-origin there already). SameSite stays
    // Lax everywhere — the SPA and API share one CloudFront origin in prod
    // now, so there's no cross-site request for a cookie to need sending on.
    // Overridden via application-aws.yml's cookie.secure.
    @Value("${cookie.same-site:Lax}")
    private String cookieSameSite;

    @Value("${cookie.secure:false}")
    private boolean cookieSecure;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SharedSecretAuthFilter sharedSecretAuthFilter(@Value("${internal.worker-secret}") String workerSecret) {
        return new SharedSecretAuthFilter(workerSecret);
    }

    // Spring Boot auto-registers any Filter bean as a *global* servlet
    // filter applied to every request, regardless of how it's separately
    // wired into one specific SecurityFilterChain via addFilterBefore below.
    // Without disabling that here, this filter would gate every endpoint in
    // the app behind the internal secret, not just /api/internal/**.
    @Bean
    public FilterRegistrationBean<SharedSecretAuthFilter> sharedSecretAuthFilterRegistration(
            SharedSecretAuthFilter filter) {
        FilterRegistrationBean<SharedSecretAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    // Handles the worker Lambda's callback (/api/internal/**) — a separate,
    // higher-precedence chain rather than adding an exception to the main
    // chain's permitAll list, since the auth mechanism (a shared secret
    // header) and trust boundary (a server, not a browser session) are
    // entirely different from everything else this app serves.
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http, SharedSecretAuthFilter filter) throws Exception {
        http
            // Without this, a chain's default matcher is "any request" — and
            // since this chain is ordered first, it would intercept and gate
            // EVERY request in the app, including /api/hello and login.
            .securityMatcher("/api/internal/**")
            // The default HttpSecurity has CSRF enabled even on a new chain.
            // The Lambda has no browser session/XSRF-TOKEN cookie to echo
            // back, so this must be off or every callback 403s on arrival.
            .csrf(csrf -> csrf.disable())
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
            // The filter itself does the real check (403s before reaching
            // here on a bad/missing secret); nothing left for Spring
            // Security's own authorization layer to gate on this path.
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
            // Session-cookie auth is CSRF-exposed by default (a POST rides
            // along with whatever cookies the browser already has), so this
            // stays on rather than being disabled for convenience. spa() is
            // Spring Security's built-in configuration for a JSON SPA: it
            // stores the token in a JS-readable XSRF-TOKEN cookie, applies
            // BREACH-safe masking on the way out while still accepting the
            // raw cookie value echoed back in the header, and writes the
            // cookie on every response — so no dedicated endpoint is needed
            // to hand it out up front. See
            // https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html#csrf-integration-javascript-spa
            //
            // Not calling csrf.spa() directly: its CookieCsrfTokenRepository
            // has no hook to set SameSite/Secure, and its request handler
            // (SpaCsrfTokenRequestHandler) is a private nested class, so both
            // pieces are reconstructed manually here — see
            // SpaCsrfTokenRequestHandler in this app's security package for
            // the handler, an exact copy of Spring Security's own.
            .csrf(csrf -> {
                CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
                csrfTokenRepository.setCookieCustomizer(cookie -> cookie
                        .sameSite(cookieSameSite)
                        .secure(cookieSecure));
                csrf.csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler());
            })
            // formLogin()'s default AuthenticationEntryPoint/failureHandler
            // both redirect (a login *page* for an unauthenticated request,
            // a ?error page on bad credentials), which makes sense for a
            // server-rendered app but not for a JSON client. Both get
            // swapped for a plain 401 status here — the SPA only needs the
            // status code, not a body, so this reuses built-in Spring
            // Security classes rather than a custom JSON-writing handler.
            // accessDeniedHandler (the sibling case: an authenticated
            // session with a stale/missing CSRF token) is left at Spring's
            // default, which already returns a plain 403 with no redirect.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/hello").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler(new JsonAuthenticationSuccessHandler(objectMapper))
                .failureHandler(new AuthenticationEntryPointFailureHandler(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))))
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler(new JsonLogoutSuccessHandler()));
        return http.build();
    }
}
