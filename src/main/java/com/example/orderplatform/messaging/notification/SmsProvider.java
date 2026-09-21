package com.example.orderplatform.messaging.notification;

import com.azure.communication.sms.SmsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The "SMS provider" stage — sends a text message through Azure Communication Services.
 * <p>
 * When no ACS connection string is configured (local development), the
 * {@code SmsClient} bean is absent and this stage no-ops and simply logs the
 * SMS it would have sent.
 */
@Component
public class SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(SmsProvider.class);

    private final SmsClient smsClient;
    private final String fromPhoneNumber;

    public SmsProvider(ObjectProvider<SmsClient> smsClientProvider,
                       @Value("${azure.communication.from-phone-number:+15550000000}") String fromPhoneNumber) {
        this.smsClient = smsClientProvider.getIfAvailable();
        this.fromPhoneNumber = fromPhoneNumber;
    }

    public void send(String phoneNumber, String messageText) {
        if (smsClient == null) {
            log.info("[sms-provider] (local, no ACS) Would send SMS from {} to {}: {}",
                    fromPhoneNumber, phoneNumber, messageText);
            return;
        }
        smsClient.send(fromPhoneNumber, phoneNumber, messageText);
        log.info("[sms-provider] Sent SMS to {}", phoneNumber);
    }
}
