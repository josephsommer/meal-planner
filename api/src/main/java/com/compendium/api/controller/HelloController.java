package com.compendium.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api")
public class HelloController {

    record HelloResponse(String message, Instant timestamp) {}

    @GetMapping("/hello")
    public HelloResponse hello() {
        return new HelloResponse("Hello from Compendium API", Instant.now());
    }
}
