package com.example.orderplatform.messaging.consumer;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.orderplatform.messaging.MessagingResources;
import com.example.orderplatform.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingProcessorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ServiceBusReceiverClient receiver;

    @Mock
    private OrderService orderService;

    @Mock
    private MessagingResources messagingResources;

    private ShippingProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ShippingProcessor(receiver, OBJECT_MAPPER, orderService, messagingResources);
        when(messagingResources.isReady()).thenReturn(true);
    }

    @Test
    @DisplayName("poll() marks an order SHIPPING and completes the message")
    void poll_marksOrderShippingAndCompletesMessage() throws Exception {
        String body = "{\"orderId\":\"id-1\",\"customerId\":\"cust-1\",\"status\":\"CREATED\"}";
        ServiceBusReceivedMessage message = org.mockito.Mockito.mock(ServiceBusReceivedMessage.class);
        when(message.getBody()).thenReturn(com.azure.core.util.BinaryData.fromString(body));
        com.azure.core.util.IterableStream<ServiceBusReceivedMessage> stream =
                new com.azure.core.util.IterableStream<>(List.of(message));
        when(receiver.receiveMessages(eq(10), any(Duration.class))).thenReturn(stream);

        processor.poll();

        verify(orderService).updateStatus("id-1", "SHIPPING");
        verify(receiver).complete(message);
    }
}
