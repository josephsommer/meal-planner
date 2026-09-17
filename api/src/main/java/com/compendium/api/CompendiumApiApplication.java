package com.compendium.api;

import com.compendium.api.bootstrap.AwsFlywayBootstrapInitializer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CompendiumApiApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CompendiumApiApplication.class);
        // No-ops outside the aws profile; see AwsFlywayBootstrapInitializer.
        app.addInitializers(new AwsFlywayBootstrapInitializer());
        app.run(args);
    }
}
