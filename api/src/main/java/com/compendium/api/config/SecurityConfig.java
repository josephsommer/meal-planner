package com.compendium.api.config;

import com.compendium.api.security.JsonAuthenticationSuccessHandler;
import com.compendium.api.security.JsonLogoutSuccessHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
            .csrf(csrf -> csrf.spa())
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

    // Moved off the standalone CorsFilter bean CorsConfig used to declare:
    // Spring Security's filter chain runs its own authorization checks
    // before a plain servlet Filter registered outside it gets a say, so a
    // separate CorsFilter can't reliably answer a preflight OPTIONS request
    // before Security rejects it as unauthenticated. Wiring CORS through
    // HttpSecurity.cors(...) instead makes Security itself preflight-aware.
    // Registration stays scoped to /api/**, same as the filter it replaced —
    // there's no reason to send credentialed CORS headers on /actuator/** or
    // any other non-API path.
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(allowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
