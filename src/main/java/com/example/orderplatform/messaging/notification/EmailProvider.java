package com.example.orderplatform.messaging.notification;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The "email provider" stage — sends an email through Azure Communication Services.
 * <p>
 * When no ACS connection string is configured (local development), the
 * {@code EmailClient} bean is absent and this stage no-ops and simply logs the
 * email it would have sent.
 */
@Component
public class EmailProvider {

    private static final Logger log = LoggerFactory.getLogger(EmailProvider.class);

    private final EmailClient emailClient;
    private final String senderEmail;

    public EmailProvider(ObjectProvider<EmailClient> emailClientProvider,
                         @Value("${azure.communication.sender-email:no-reply@example.com}") String senderEmail) {
        this.emailClient = emailClientProvider.getIfAvailable();
        this.senderEmail = senderEmail;
    }

    public void send(String recipient, String subject, String bodyText) {
        if (emailClient == null) {
            log.info("[email-provider] (local, no ACS) Would send email from {} to {} subject '{}':\n{}",
                    senderEmail, recipient, subject, bodyText);
            return;
        }
        EmailMessage message = new EmailMessage()
                .setSenderAddress(senderEmail)
                .setToRecipients(new String[]{recipient})
                .setSubject(subject)
                .setBodyPlainText(bodyText);
        emailClient.beginSend(message).waitForCompletion();
        log.info("[email-provider] Sent email to {}", recipient);
    }
}
