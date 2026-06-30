package com.compendium.api.service;

import com.compendium.api.domain.GreetingRepository;
import org.springframework.stereotype.Service;

@Service
public class GreetingService {

    private final GreetingRepository greetingRepository;

    public GreetingService(GreetingRepository greetingRepository) {
        this.greetingRepository = greetingRepository;
    }

    public String getGreeting() {
        return greetingRepository.findFirstByOrderByIdAsc()
                .map(g -> g.getMessage())
                .orElse("No greeting found");
    }
}
