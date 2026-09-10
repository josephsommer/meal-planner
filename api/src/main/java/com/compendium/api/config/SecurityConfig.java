package com.compendium.api.config;

import com.compendium.api.security.JsonAccessDeniedHandler;
import com.compendium.api.security.JsonAuthenticationFailureHandler;
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
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
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
            // stays on rather than being disabled for convenience.
            // CookieCsrfTokenRepository is the standard fit for a JSON SPA
            // that can't render a hidden form field: the token lives in a
            // JS-readable cookie (withHttpOnlyFalse) and the SPA echoes it
            // back as a header. See AuthController#csrf for how the SPA gets
            // that cookie in the first place.
            //
            // The request handler must be the plain CsrfTokenRequestAttributeHandler,
            // not the XorCsrfTokenRequestAttributeHandler Spring Security 6
            // defaults to: Xor BREACH-masks the token it hands out, so it
            // expects whatever comes back in the header to be masked too.
            // That's fine for a server-rendered form (which reads the masked
            // value and resubmits it verbatim) but not here, where the SPA
            // just reads the raw cookie value and echoes that back as-is.
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            // formLogin()'s default AuthenticationEntryPoint redirects an
            // unauthenticated request to a login *page* (302), which makes
            // sense for a server-rendered app but not for a JSON client —
            // this makes an unauthenticated request to a protected endpoint
            // a plain 401 instead. accessDeniedHandler covers the sibling
            // case: an authenticated session whose CSRF token is stale or
            // missing, which otherwise falls through to Spring's default
            // (non-JSON) 403 handling.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                .accessDeniedHandler(new JsonAccessDeniedHandler(objectMapper)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/hello").permitAll()
                .requestMatchers("/api/auth/csrf").permitAll()
                .requestMatchers("/api/auth/login").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")
                .successHandler(new JsonAuthenticationSuccessHandler(objectMapper))
                .failureHandler(new JsonAuthenticationFailureHandler(objectMapper)))
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
