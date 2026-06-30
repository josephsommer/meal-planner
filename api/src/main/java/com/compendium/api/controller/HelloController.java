package com.compendium.api.controller;

import com.compendium.api.service.GreetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api")
public class HelloController {

    private final GreetingService greetingService;

    public HelloController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    record HelloResponse(String message, Instant timestamp) {}

    @GetMapping("/hello")
    public HelloResponse hello() {
        return new HelloResponse(greetingService.getGreeting(), Instant.now());
    }
}
