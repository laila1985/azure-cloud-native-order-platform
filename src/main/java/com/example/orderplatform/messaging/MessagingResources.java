package com.example.orderplatform.messaging;

import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.azure.messaging.servicebus.administration.models.CreateSubscriptionOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Provisions (get-or-create) the Service Bus topic and its five subscriptions.
 * <p>
 * Provisioning is idempotent and best-effort: if the Service Bus backend is not
 * reachable at startup (e.g. the emulator is down), the application still boots
 * and the consumers simply skip polling until resources become available.
 * <p>
 * Flow: {@code Order API → Service Bus topic → (processor, lambda, email, sms,
 * shipping) subscriptions}.
 */
@Component
public class MessagingResources {

    private static final Logger log = LoggerFactory.getLogger(MessagingResources.class);

    private final String connectionString;
    private final String topicName;
    private final String processorSubscription;
    private final String lambdaSubscription;
    private final String emailSubscription;
    private final String smsSubscription;
    private final String shippingSubscription;

    private volatile boolean ready = false;

    public MessagingResources(
            @Value("${azure.servicebus.connection-string}") String connectionString,
            @Value("${azure.servicebus.topic-name}") String topicName,
            @Value("${azure.servicebus.processor-subscription}") String processorSubscription,
            @Value("${azure.servicebus.lambda-subscription}") String lambdaSubscription,
            @Value("${azure.servicebus.email-subscription}") String emailSubscription,
            @Value("${azure.servicebus.sms-subscription}") String smsSubscription,
            @Value("${azure.servicebus.shipping-subscription}") String shippingSubscription) {
        this.connectionString = connectionString;
        this.topicName = topicName;
        this.processorSubscription = processorSubscription;
        this.lambdaSubscription = lambdaSubscription;
        this.emailSubscription = emailSubscription;
        this.smsSubscription = smsSubscription;
        this.shippingSubscription = shippingSubscription;
    }

    @PostConstruct
    void init() {
        // The Service Bus emulator pre-provisions entities from Config.json and
        // does not expose the management API to the Java admin client without a
        // non-TLS HTTP workaround. When `UseDevelopmentEmulator=true` is present
        // in the connection string, skip provisioning and assume the topic and
        // subscriptions already exist.
        if (connectionString != null && connectionString.contains("UseDevelopmentEmulator=true")) {
            ready = true;
            log.info("Using the Service Bus emulator; topic '{}' and its subscriptions "
                    + "are expected to be pre-provisioned in Config.json.", topicName);
            return;
        }

        try {
            ServiceBusAdministrationClient admin = new ServiceBusAdministrationClientBuilder()
                    .connectionString(connectionString)
                    .buildClient();

            if (!admin.getTopicExists(topicName)) {
                admin.createTopic(topicName);
                log.info("Created Service Bus topic '{}'", topicName);
            }

            ensureSubscription(admin, processorSubscription);
            ensureSubscription(admin, lambdaSubscription);
            ensureSubscription(admin, emailSubscription);
            ensureSubscription(admin, smsSubscription);
            ensureSubscription(admin, shippingSubscription);

            log.info("Messaging resources ready. topic={}", topicName);
            ready = true;
        } catch (Exception e) {
            ready = false;
            log.warn("Could not provision messaging resources (is the Service Bus "
                    + "emulator running?); messaging will be inactive until restart.", e);
        }
    }

    /**
     * Whether the Service Bus topic and subscriptions were provisioned
     * successfully. Consumers skip polling while this is {@code false}.
     */
    public boolean isReady() {
        return ready;
    }

    private void ensureSubscription(ServiceBusAdministrationClient admin, String subscriptionName) {
        if (!admin.getSubscriptionExists(topicName, subscriptionName)) {
            CreateSubscriptionOptions options = new CreateSubscriptionOptions()
                    .setMaxDeliveryCount(10)
                    .setLockDuration(Duration.ofMinutes(1));
            admin.createSubscription(topicName, subscriptionName, options);
            log.info("Created subscription '{}' on topic '{}'", subscriptionName, topicName);
        }
    }
}
