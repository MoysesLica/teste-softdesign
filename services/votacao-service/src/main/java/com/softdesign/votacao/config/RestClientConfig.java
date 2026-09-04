package com.softdesign.votacao.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient cpfValidationRestClient(RestClient.Builder builder, @Value("${integrations.validacao-cpf.base-url}") String baseUrl) {
        return builder
                .baseUrl(baseUrl)
                .build();
    }

}
