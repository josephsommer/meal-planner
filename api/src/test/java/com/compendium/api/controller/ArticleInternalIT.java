package com.compendium.api.controller;

import com.jayway.jsonpath.JsonPath;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers /api/internal/** — the worker's callback path. Deliberately checks
// /api/hello alongside the secret-header cases: a prior version of
// internalFilterChain registered SharedSecretAuthFilter as a Spring bean,
// which Spring Boot auto-registers as a *global* servlet filter regardless
// of how it's wired into one specific SecurityFilterChain, silently gating
// every endpoint in the app behind the internal secret. This test is the
// direct regression check for that.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "admin-username=testadmin",
        "admin-password=test-password",
        "internal.worker-secret=test-internal-secret"
})
class ArticleInternalIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fetchResult_withCorrectSecret_marksThePendingArticleSucceeded() throws Exception {
        long articleId = createArticle();

        mockMvc.perform(post("/api/internal/articles/" + articleId + "/fetch-result")
                        .header("X-Internal-Secret", "test-internal-secret")
                        .contentType("application/json")
                        .content("""
                                {"success": true, "title": "A Title", "excerpt": "An excerpt", "content": "Body text"}
                                """))
                .andExpect(status().isOk());

        Cookie csrfCookie = fetchCsrfCookie();
        MockHttpSession session = login(csrfCookie);
        mockMvc.perform(get("/api/articles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + articleId + ")].fetchStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$[?(@.id == " + articleId + ")].title").value("A Title"));
    }

    @Test
    void fetchResult_withoutSecret_returns403() throws Exception {
        long articleId = createArticle();

        mockMvc.perform(post("/api/internal/articles/" + articleId + "/fetch-result")
                        .contentType("application/json")
                        .content("""
                                {"success": false, "errorMessage": "boom"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void fetchResult_withWrongSecret_returns403() throws Exception {
        long articleId = createArticle();

        mockMvc.perform(post("/api/internal/articles/" + articleId + "/fetch-result")
                        .header("X-Internal-Secret", "not-the-secret")
                        .contentType("application/json")
                        .content("""
                                {"success": false, "errorMessage": "boom"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void helloEndpoint_staysPermitAllRegardlessOfTheInternalSecret() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk());
    }

    private long createArticle() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        MockHttpSession session = login(csrfCookie);

        MvcResult result = mockMvc.perform(post("/api/articles")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType("application/json")
                        .content("{\"url\":\"https://example.com/recipe\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private MockHttpSession login(Cookie csrfCookie) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .param("username", "testadmin")
                        .param("password", "test-password")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private Cookie fetchCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getCookie("XSRF-TOKEN");
    }
}
