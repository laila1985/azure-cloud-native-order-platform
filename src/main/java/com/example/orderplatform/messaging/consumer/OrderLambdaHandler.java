package com.example.orderplatform.messaging.consumer;

import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderplatform.messaging.MessagingResources;
import com.example.orderplatform.service.OrderService;
import org.springframework.stereotype.Component;

/**
 * Stands in for a real Azure Function that is subscribed to the Service Bus
 * topic. Locally we emulate it as a subscription ("order-lambda") consumed by
 * this poller, which marks the order {@code NOTIFIED}.
 * <p>
 * In Azure, replace this with an Azure Function triggered by the Service Bus
 * topic — this poller then becomes unnecessary.
 */
@Component
public class OrderLambdaHandler extends AbstractOrderConsumer {

    private final ServiceBusReceiverClient receiver;

    public OrderLambdaHandler(ServiceBusReceiverClient lambdaReceiver,
                              ObjectMapper objectMapper,
                              OrderService orderService,
                              MessagingResources messagingResources) {
        super(objectMapper, orderService, messagingResources);
        this.receiver = lambdaReceiver;
    }

    @Override
    protected String consumerName() {
        return "order-lambda";
    }

    @Override
    protected String targetStatus() {
        return "NOTIFIED";
    }

    @Override
    protected ServiceBusReceiverClient receiver() {
        return receiver;
    }
}
