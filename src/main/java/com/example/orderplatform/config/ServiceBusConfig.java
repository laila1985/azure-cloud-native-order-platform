package com.example.orderplatform.config;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Azure Service Bus clients for the topic and its subscriptions.
 * <p>
 * A single sender (topic) is shared by the publisher; each subscription has its
 * own long-lived {@code ServiceBusReceiverClient} (PEEK_LOCK) used by the
 * {@code @Scheduled} consumers.
 */
@Configuration
public class ServiceBusConfig {

    @Value("${azure.servicebus.connection-string}")
    private String connectionString;

    @Value("${azure.servicebus.topic-name}")
    private String topicName;

    @Value("${azure.servicebus.processor-subscription}")
    private String processorSubscription;

    @Value("${azure.servicebus.lambda-subscription}")
    private String lambdaSubscription;

    @Value("${azure.servicebus.email-subscription}")
    private String emailSubscription;

    @Value("${azure.servicebus.sms-subscription}")
    private String smsSubscription;

    @Value("${azure.servicebus.shipping-subscription}")
    private String shippingSubscription;

    @Bean
    public ServiceBusSenderClient orderTopicSender() {
        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .sender()
                .topicName(topicName)
                .buildClient();
    }

    @Bean
    public ServiceBusReceiverClient processorReceiver() {
        return receiver(processorSubscription);
    }

    @Bean
    public ServiceBusReceiverClient lambdaReceiver() {
        return receiver(lambdaSubscription);
    }

    @Bean
    public ServiceBusReceiverClient emailReceiver() {
        return receiver(emailSubscription);
    }

    @Bean
    public ServiceBusReceiverClient smsReceiver() {
        return receiver(smsSubscription);
    }

    @Bean
    public ServiceBusReceiverClient shippingReceiver() {
        return receiver(shippingSubscription);
    }

    private ServiceBusReceiverClient receiver(String subscriptionName) {
        return new ServiceBusClientBuilder()
                .connectionString(connectionString)
                .receiver()
                .topicName(topicName)
                .subscriptionName(subscriptionName)
                .buildClient();
    }
}
