package com.compendium.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HelloControllerIT {

    // Static so the container is shared across all tests in this class,
    // avoiding the cost of starting a new MySQL instance per test.
    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void hello_returnsGreetingFromDatabase() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello from the database!"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    // web/src/App.tsx fetches this unauthenticated on every page load — it's
    // the homepage greeting, not something behind a login wall. The test
    // above alone wouldn't catch a regression here: @WithMockUser makes it
    // pass regardless of whether SecurityConfig actually permitAll's this
    // path, which is exactly what broke once session auth was added.
    @Test
    void hello_isAccessibleWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk());
    }
}
