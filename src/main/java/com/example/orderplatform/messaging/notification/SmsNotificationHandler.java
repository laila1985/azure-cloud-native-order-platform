package com.example.orderplatform.messaging.notification;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderplatform.event.OrderCreatedEvent;
import com.example.orderplatform.model.Customer;
import com.example.orderplatform.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The "SMS Function" — a pipeline that turns an order event into a sent SMS.
 *
 * <pre>
 *   Subscription → SMS Function → Validate → Build SMS → SMS provider → Success
 * </pre>
 *
 * It listens on the "order-sms" subscription (fed by the topic), builds a
 * {@link NotificationMessage} for the customer, and runs it through the stages.
 */
@Component
public class SmsNotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationHandler.class);

    private final ServiceBusReceiverClient receiver;
    private final ObjectMapper objectMapper;
    private final CustomerService customerService;
    private final NotificationValidator validator;
    private final SmsProvider smsProvider;

    public SmsNotificationHandler(ServiceBusReceiverClient smsReceiver,
                                  ObjectMapper objectMapper,
                                  CustomerService customerService,
                                  NotificationValidator validator,
                                  SmsProvider smsProvider) {
        this.receiver = smsReceiver;
        this.objectMapper = objectMapper;
        this.customerService = customerService;
        this.validator = validator;
        this.smsProvider = smsProvider;
    }

    @Scheduled(fixedDelayString = "${azure.servicebus.poll-interval-ms:5000}")
    public void poll() {
        try {
            receiver.receiveMessages(10, Duration.ofSeconds(5)).stream()
                    .forEach(this::process);
        } catch (Exception e) {
            log.warn("[sms-function] Failed to receive messages", e);
        }
    }

    private void process(ServiceBusReceivedMessage message) {
        try {
            OrderCreatedEvent event = parseEvent(message.getBody().toString());
            Customer customer = customerService.findById(event.customerId());
            handle(event, customer);
            receiver.complete(message);
        } catch (Exception e) {
            log.error("[sms-function] Failed to process message {}: {}", message.getMessageId(), e.getMessage(), e);
            try {
                receiver.abandon(message);
            } catch (Exception abandonEx) {
                log.warn("[sms-function] Failed to abandon message {}", message.getMessageId(), abandonEx);
            }
        }
    }

    /** Runs the explicit pipeline stages for an SMS notification. */
    void handle(OrderCreatedEvent event, Customer customer) {
        // Build the notification message (SMS channel).
        String text = "Your order " + event.orderId() + " has been confirmed.";
        NotificationMessage notification = new NotificationMessage(
                "notif-" + UUID.randomUUID(),
                "ORDER_CREATED",
                "SMS",
                "ORDER_CREATED",
                dataFor(event, customer),
                null,
                null,
                customer.getPhoneNumber(),
                text,
                Instant.now());

        // Stage 1 — Validate message.
        validator.validate(notification);
        log.info("[sms-function] Validated notification {}", notification.notificationId());

        // Stage 2 — Build SMS (message is pre-built above).
        log.info("[sms-function] Built SMS for {}", notification.phoneNumber());

        // Stage 3 — SMS provider.
        smsProvider.send(notification.phoneNumber(), notification.message());

        // Stage 4 — Success.
        log.info("[sms-function] Success: sent {} SMS to {} (notification {})",
                notification.type(), notification.phoneNumber(), notification.notificationId());
    }

    private Map<String, Object> dataFor(OrderCreatedEvent event, Customer customer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", event.orderId());
        data.put("customerName", customer.getName());
        return data;
    }

    private OrderCreatedEvent parseEvent(String body) throws Exception {
        return objectMapper.readValue(body, OrderCreatedEvent.class);
    }
}
