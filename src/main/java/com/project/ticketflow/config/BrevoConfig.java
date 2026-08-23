package com.project.ticketflow.config;

import brevo.ApiClient;
import brevo.auth.ApiKeyAuth;
import brevoApi.TransactionalEmailsApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BrevoConfig {
    @Value("${brevo.api-key}")
    private String brevoApiKey;

    @Bean
    public ApiClient brevoApiClient() {
        ApiClient defaultClient = brevo.Configuration.getDefaultApiClient();
        ApiKeyAuth apiKey = (ApiKeyAuth) defaultClient.getAuthentication("api-key");
        apiKey.setApiKey(brevoApiKey);
        return defaultClient;
    }

    @Bean
    public TransactionalEmailsApi transactionalEmailsApi() {
        return new TransactionalEmailsApi(brevoApiClient());
    }
}
