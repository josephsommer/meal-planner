package com.compendium.api.service;

import com.compendium.api.domain.Greeting;
import com.compendium.api.domain.GreetingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class GreetingServiceTest {

    @Mock
    private GreetingRepository greetingRepository;

    @InjectMocks
    private GreetingService greetingService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getGreeting_returnsMessageFromFirstRow() {
        when(greetingRepository.findFirstByOrderByIdAsc())
                .thenReturn(Optional.of(new Greeting(1L, "Hello from the database!")));

        assertThat(greetingService.getGreeting()).isEqualTo("Hello from the database!");
    }

    @Test
    void getGreeting_returnsFallbackWhenTableIsEmpty() {
        when(greetingRepository.findFirstByOrderByIdAsc()).thenReturn(Optional.empty());

        assertThat(greetingService.getGreeting()).isEqualTo("No greeting found");
    }
}
