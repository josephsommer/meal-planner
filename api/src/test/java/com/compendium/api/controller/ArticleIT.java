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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Covers the list/delete endpoints added alongside the soft-delete `deleted`
// column. Kept separate from AuthenticationIT, which owns login/logout and
// article creation, so this file stays scoped to what happens to an article
// once it already exists.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "admin-username=testadmin",
        "admin-password=test-password",
        "internal.worker-secret=test-internal-secret"
})
class ArticleIT {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listThenDelete_removesTheArticleFromSubsequentListings() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        MockHttpSession session = login(csrfCookie);

        MvcResult createResult = mockMvc.perform(post("/api/articles")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType("application/json")
                        .content("{\"url\":\"https://example.com/recipe\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long articleId = extractId(createResult);

        mockMvc.perform(get("/api/articles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + articleId + ")]").exists());

        mockMvc.perform(delete("/api/articles/" + articleId)
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/articles").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + articleId + ")]").doesNotExist());
    }

    @Test
    void delete_returns404ForAnIdThatDoesNotExist() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        MockHttpSession session = login(csrfCookie);

        mockMvc.perform(delete("/api/articles/999999")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_whenNotAuthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/articles"))
                .andExpect(status().isUnauthorized());
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
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        return cookie;
    }

    private long extractId(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
