package com.softdesign.votacao.config;

import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi apiV1() {
        return GroupedOpenApi.builder()
                .group("v1")
                .pathsToMatch("/api/v1/**")
                .addOpenApiCustomizer(openApi ->
                        openApi.info(new Info()
                                .title("API para o teste da SoftDesign")
                                .description("Primeira versão da API")
                                .version("v1")))
                .build();
    }

}