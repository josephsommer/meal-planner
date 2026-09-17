package com.compendium.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    private static final List<PathItem.HttpMethod> MUTATING_METHODS = List.of(
            PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT,
            PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE);

    // Spring Security's csrf.spa() (see SecurityConfig) requires an
    // X-XSRF-TOKEN header on every mutating request, but that's filter-chain
    // config springdoc can't see by introspecting controller methods alone.
    // Excludes /api/auth/login: formLogin is CSRF-exempt since the session
    // doesn't exist yet to have a token tied to it.
    @Bean
    public OpenApiCustomizer csrfHeaderCustomizer() {
        return openApi -> {
            Parameter csrfHeader = new Parameter()
                    .in("header")
                    .name("X-XSRF-TOKEN")
                    .description("Value of the XSRF-TOKEN cookie, required on all mutating requests")
                    .required(true)
                    .schema(new io.swagger.v3.oas.models.media.StringSchema());

            for (Map.Entry<String, PathItem> pathEntry : openApi.getPaths().entrySet()) {
                if (pathEntry.getKey().equals("/api/auth/login")) {
                    continue;
                }
                PathItem pathItem = pathEntry.getValue();
                for (PathItem.HttpMethod method : MUTATING_METHODS) {
                    Operation operation = pathItem.readOperationsMap().get(method);
                    if (operation != null) {
                        operation.addParametersItem(csrfHeader);
                    }
                }
            }
        };
    }
}
