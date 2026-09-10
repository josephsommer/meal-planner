package com.compendium.api.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers logging in (V4__CreateAdminUser seeds the account these tests log
// into) and posting an authenticated article. Overrides admin-username/
// admin-password rather than relying on application.yml's local-dev
// fallback so these tests don't silently break if that default ever changes.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "admin-username=testadmin",
        "admin-password=test-password"
})
class AuthenticationIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void login_withCorrectCredentials_succeedsAndEstablishesASession() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();

        MvcResult result = mockMvc.perform(loginRequest("testadmin", "test-password", csrfCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("testadmin"))
                .andReturn();

        // MockMvc's MockHttpSession never shows up as a literal Set-Cookie:
        // JSESSIONID header (that's real servlet-container plumbing this
        // test double doesn't simulate) — checking the session itself got
        // created is the equivalent signal here.
        assertThat(result.getRequest().getSession(false)).isNotNull();
    }

    @Test
    void login_withWrongPassword_returns401() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();

        mockMvc.perform(loginRequest("testadmin", "not-the-password", csrfCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void postArticle_whenAuthenticated_savesTheArticleUnderTheLoggedInUser() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();

        MvcResult loginResult = mockMvc.perform(loginRequest("testadmin", "test-password", csrfCookie))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(post("/api/articles")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType("application/json")
                        .content("{\"url\":\"https://example.com/recipe\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.url").value("https://example.com/recipe"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void postArticle_whenNotAuthenticated_returns401() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();

        mockMvc.perform(post("/api/articles")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType("application/json")
                        .content("{\"url\":\"https://example.com/recipe\"}"))
                .andExpect(status().isUnauthorized());
    }

    private RequestBuilder loginRequest(String username, String password, Cookie csrfCookie) {
        return post("/api/auth/login")
                .param("username", username)
                .param("password", password)
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue());
    }

    // GET /api/auth/csrf is permitAll and, per AuthController#csrf, forces
    // CookieCsrfTokenRepository to actually write the XSRF-TOKEN cookie.
    // Every state-changing request below resends this same cookie plus a
    // matching X-XSRF-TOKEN header, mirroring what the SPA is expected to do.
    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).as("XSRF-TOKEN cookie").isNotNull();
        return cookie;
    }
}
