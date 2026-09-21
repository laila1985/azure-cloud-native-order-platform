package com.example.orderplatform.config;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.EmailClientBuilder;
import com.azure.communication.sms.SmsClient;
import com.azure.communication.sms.SmsClientBuilder;
import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures Azure Communication Services clients (Email + SMS).
 * <p>
 * A connection string is used for local development; in Azure, leave it blank
 * and rely on the {@code DefaultAzureCredential} (Managed Identity / environment).
 * <p>
 * The {@code EmailClient} / {@code SmsClient} beans are only registered when a
 * non-blank connection string is configured; otherwise the email/SMS providers
 * degrade to logging (no-op) via {@code ObjectProvider} injection.
 */
@Configuration
public class CommunicationConfig {

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${azure.communication.connection-string:}')")
    public EmailClient emailClient(
            @Value("${azure.communication.connection-string:}") String connectionString) {
        return new EmailClientBuilder().connectionString(connectionString).buildClient();
    }

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${azure.communication.connection-string:}')")
    public SmsClient smsClient(
            @Value("${azure.communication.connection-string:}") String connectionString) {
        return new SmsClientBuilder().connectionString(connectionString).buildClient();
    }

    /**
     * Shared token credential for Azure services (Managed Identity / DefaultAzureCredential).
     * Used when the connection string is not provided (production).
     */
    @Bean
    public TokenCredential tokenCredential() {
        return new DefaultAzureCredentialBuilder().build();
    }
}

