package com.wallet.txhistory.config;

import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public GlobalOpenApiCustomizer globalHeaderCustomizer() {
        return openApi -> openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {
                    operation.addParametersItem(new HeaderParameter()
                            .name("X-Auth-WalletAccess")
                            .description("Authentication gate — must be set to \"allow\"")
                            .required(true)
                            .schema(new StringSchema()));

                    operation.addParametersItem(new HeaderParameter()
                            .name("X-Role")
                            .description("Role-based access control")
                            .required(false)
                            .schema(new StringSchema()
                                    ._enum(List.of("admin", "user"))
                                    ._default("user")));

                    operation.addParametersItem(new HeaderParameter()
                            .name("X-Request-Id")
                            .description("UUID for request tracing — auto-generated if absent")
                            .required(false)
                            .schema(new StringSchema().format("uuid")));
                }));
    }
}
