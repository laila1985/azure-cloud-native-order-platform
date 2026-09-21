package com.example.orderplatform.integration;

import com.azure.communication.email.EmailClient;
import com.azure.communication.sms.SmsClient;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack integration test: Spring context + REST controller + service +
 * repository against an in-memory H2 database (MSSQL compatibility mode).
 * <p>
 * Azure Service Bus and Communication Services clients are mocked so the test
 * runs without Docker or any Azure resources. Redis caching is disabled.
 */
@SpringBootTest(properties = {
        "app.cache.redis.enabled=false",
        "azure.servicebus.connection-string=Endpoint=sb://localhost;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;UseDevelopmentEmulator=true;"
})
@AutoConfigureMockMvc
class OrderApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ServiceBusSenderClient orderTopicSender;

    @MockBean(name = "processorReceiver")
    private ServiceBusReceiverClient processorReceiver;

    @MockBean(name = "lambdaReceiver")
    private ServiceBusReceiverClient lambdaReceiver;

    @MockBean(name = "emailReceiver")
    private ServiceBusReceiverClient emailReceiver;

    @MockBean(name = "smsReceiver")
    private ServiceBusReceiverClient smsReceiver;

    @MockBean(name = "shippingReceiver")
    private ServiceBusReceiverClient shippingReceiver;

    @MockBean
    private EmailClient emailClient;

    @MockBean
    private SmsClient smsClient;

    @Test
    @DisplayName("full CRUD flow: create -> get -> update -> list -> delete")
    void fullCrudFlow() throws Exception {
        // An order requires an existing customer, so create one first.
        mockMvc.perform(post("/api/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "cust-1",
                                  "name": "John",
                                  "email": "john@example.com",
                                  "phoneNumber": "+971501234567"
                                }
                                """))
                .andExpect(status().isCreated());

        String createBody = """
                {
                  "customerId": "cust-1",
                  "items": [
                    { "productId": "p-1", "productName": "Laptop", "quantity": 2, "unitPrice": 10.50 }
                  ]
                }
                """;

        // Create
        String createResponse = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.totalAmount").value(21.00))
                .andReturn().getResponse().getContentAsString();

        String orderId = com.jayway.jsonpath.JsonPath.read(createResponse, "$.orderId");

        // Get by id
        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.customerId").value("cust-1"));

        // Update
        mockMvc.perform(put("/api/orders/" + orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SHIPPED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        // List
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(orderId));

        // Delete
        mockMvc.perform(delete("/api/orders/" + orderId))
                .andExpect(status().isNoContent());

        // Get after delete -> 404
        mockMvc.perform(get("/api/orders/" + orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET unknown order returns 404 with error body")
    void getUnknownOrderReturns404() throws Exception {
        mockMvc.perform(get("/api/orders/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found: unknown"));
    }

    @Test
    @DisplayName("POST order with unknown customer returns 404")
    void createOrderWithUnknownCustomerReturns404() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":\"ghost\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Customer not found: ghost"));
    }
}
