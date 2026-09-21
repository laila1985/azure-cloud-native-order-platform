package com.example.orderplatform.messaging.notification;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderplatform.event.OrderCreatedEvent;
import com.example.orderplatform.model.Customer;
import com.example.orderplatform.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The "Email Function" — a pipeline that turns an order event into a sent email.
 *
 * <pre>
 *   Subscription → Email Function → Validate → Load template → Build email → Email provider → Success
 * </pre>
 *
 * It listens on the "order-email" subscription (fed by the topic), builds a
 * {@link NotificationMessage} for the customer, and runs it through the stages.
 */
@Component
public class EmailNotificationHandler {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationHandler.class);

    private final ServiceBusReceiverClient receiver;
    private final ObjectMapper objectMapper;
    private final CustomerService customerService;
    private final NotificationValidator validator;
    private final TemplateService templateService;
    private final EmailProvider emailProvider;
    private final String currency;

    public EmailNotificationHandler(ServiceBusReceiverClient emailReceiver,
                                    ObjectMapper objectMapper,
                                    CustomerService customerService,
                                    NotificationValidator validator,
                                    TemplateService templateService,
                                    EmailProvider emailProvider,
                                    @Value("${azure.currency:AED}") String currency) {
        this.receiver = emailReceiver;
        this.objectMapper = objectMapper;
        this.customerService = customerService;
        this.validator = validator;
        this.templateService = templateService;
        this.emailProvider = emailProvider;
        this.currency = currency;
    }

    @Scheduled(fixedDelayString = "${azure.servicebus.poll-interval-ms:5000}")
    public void poll() {
        try {
            receiver.receiveMessages(10, Duration.ofSeconds(5)).stream()
                    .forEach(this::process);
        } catch (Exception e) {
            log.warn("[email-function] Failed to receive messages", e);
        }
    }

    private void process(ServiceBusReceivedMessage message) {
        try {
            OrderCreatedEvent event = parseEvent(message.getBody().toString());
            Customer customer = customerService.findById(event.customerId());
            handle(event, customer);
            receiver.complete(message);
        } catch (Exception e) {
            log.error("[email-function] Failed to process message {}: {}", message.getMessageId(), e.getMessage(), e);
            try {
                receiver.abandon(message);
            } catch (Exception abandonEx) {
                log.warn("[email-function] Failed to abandon message {}", message.getMessageId(), abandonEx);
            }
        }
    }

    /** Runs the explicit pipeline stages for an email notification. */
    void handle(OrderCreatedEvent event, Customer customer) {
        // Build the notification message (EMAIL channel).
        NotificationMessage notification = new NotificationMessage(
                "notif-" + UUID.randomUUID(),
                "ORDER_CREATED",
                "EMAIL",
                "ORDER_CREATED",
                dataFor(event, customer),
                customer.getEmail(),
                "Order " + event.orderId() + " confirmed",
                null,
                null,
                Instant.now());

        // Stage 1 — Validate message.
        validator.validate(notification);
        log.info("[email-function] Validated notification {}", notification.notificationId());

        // Stage 2 — Load template.
        String template = templateService.load(notification.template());
        log.info("[email-function] Loaded template {}", notification.template());

        // Stage 3 — Build email.
        String body = templateService.render(template, notification.data());

        // Stage 4 — Email provider.
        emailProvider.send(notification.recipient(), notification.subject(), body);

        // Stage 5 — Success.
        log.info("[email-function] Success: sent {} email to {} (notification {})",
                notification.type(), notification.recipient(), notification.notificationId());
    }

    private Map<String, Object> dataFor(OrderCreatedEvent event, Customer customer) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderId", event.orderId());
        data.put("customerName", customer.getName());
        data.put("totalAmount", event.totalAmount());
        data.put("currency", currency);
        return data;
    }

    private OrderCreatedEvent parseEvent(String body) throws Exception {
        return objectMapper.readValue(body, OrderCreatedEvent.class);
    }
}
