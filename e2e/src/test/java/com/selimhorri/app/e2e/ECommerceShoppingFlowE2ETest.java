package com.selimhorri.app.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selimhorri.app.e2e.util.JwtTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * E2E Test: Complete eCommerce Shopping Flow
 * Tests end-to-end shopping experience: browse products → add to cart →
 * checkout → order history
 * 
 * NOTE: These tests require inter-service communication through Eureka discovery.
 * If Eureka discovery is not fully operational, these tests may fail.
 */
@Disabled("Requires inter-service communication through Eureka discovery - enable when discovery is fully operational")
@DisplayName("eCommerce Shopping Flow E2E Tests")
public class ECommerceShoppingFlowE2ETest {

        private RestTemplate restTemplate;
        private ObjectMapper objectMapper;
        private String apiGatewayUrl;
        private String uniqueId;

        private Map<String, Object> testUser;
        private Map<String, Object> testProduct;
        private String userId;
        private String productId;
        private String cartId;
        private String orderId;

        @BeforeEach
        void setUp() {
                restTemplate = new RestTemplateBuilder()
                                .setConnectTimeout(java.time.Duration.ofSeconds(10))
                                .setReadTimeout(java.time.Duration.ofSeconds(30))
                                .build();

                objectMapper = new ObjectMapper();
                apiGatewayUrl = System.getProperty("api.gateway.url", "http://localhost:9090");
                uniqueId = UUID.randomUUID().toString().substring(0, 8);

                // Setup test user with nested credential structure
                testUser = new HashMap<>();
                testUser.put("firstName", "Shopper" + uniqueId);
                testUser.put("lastName", "Customer");
                testUser.put("imageUrl", "https://example.com/shopper.jpg");
                testUser.put("email", "shopper" + uniqueId + "@example.com");
                testUser.put("phone", "+1555" + uniqueId.substring(0, 7));

                // Nested credential object
                Map<String, Object> credential = new HashMap<>();
                credential.put("username", "shopper" + uniqueId);
                credential.put("password", "ShopSecure123!");
                credential.put("roleBasedAuthority", "ROLE_USER");
                credential.put("isEnabled", true);
                credential.put("isAccountNonExpired", true);
                credential.put("isAccountNonLocked", true);
                credential.put("isCredentialsNonExpired", true);
                testUser.put("credential", credential);

                // Setup test product
                testProduct = new HashMap<>();
                testProduct.put("productTitle", "E2ETestProduct" + uniqueId);
                testProduct.put("imageUrl", "https://example.com/product.jpg");
                testProduct.put("sku", "E2E" + uniqueId);
                testProduct.put("priceUnit", 29.99);
                testProduct.put("quantity", 100);

                // Nested category object
                Map<String, Object> category = new HashMap<>();
                category.put("categoryId", 1);
                testProduct.put("category", category);
        }

        private HttpHeaders createHeadersWithJwt() {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String jwtToken = JwtTestHelper.generateToken("testuser");
                headers.set("Authorization", "Bearer " + jwtToken);
                return headers;
        }

        @Test
        @DisplayName("Complete Shopping Journey")
        void testCompleteShoppingJourney() {
                System.out.println("🛒 Starting Complete Shopping Journey Test");

                HttpHeaders headers = createHeadersWithJwt();

                // STEP 1: User Registration
                System.out.println("📝 Step 1: User Registration");

                HttpEntity<Map<String, Object>> userRequest = new HttpEntity<>(testUser, headers);
                ResponseEntity<String> userResponse = restTemplate.postForEntity(
                                apiGatewayUrl + "/user-service/api/users",
                                userRequest,
                                String.class);

                assertThat(userResponse.getStatusCode())
                                .as("User registration should succeed")
                                .isIn(HttpStatus.OK, HttpStatus.CREATED);

                JsonNode userData = parseJsonResponse(userResponse.getBody());
                userId = userData.get("userId").asText();
                assertThat(userId).as("User ID should be generated").isNotBlank();

                System.out.println("✅ User registered: " + userId);

                // STEP 2: Product Catalog - Create and Browse Product
                System.out.println("📦 Step 2: Product Catalog Setup");

                HttpEntity<Map<String, Object>> productRequest = new HttpEntity<>(testProduct, headers);
                ResponseEntity<String> productResponse = restTemplate.postForEntity(
                                apiGatewayUrl + "/product-service/api/products",
                                productRequest,
                                String.class);

                assertThat(productResponse.getStatusCode())
                                .as("Product creation should succeed")
                                .isIn(HttpStatus.OK, HttpStatus.CREATED);

                JsonNode productData = parseJsonResponse(productResponse.getBody());
                productId = productData.get("productId").asText();
                assertThat(productId).as("Product ID should be generated").isNotBlank();

                System.out.println("✅ Product created: " + productId);

                // Verify product can be retrieved (browse functionality)
                HttpEntity<Void> getRequest = new HttpEntity<>(headers);
                ResponseEntity<String> getProductResponse = restTemplate.exchange(
                                apiGatewayUrl + "/product-service/api/products/" + productId,
                                HttpMethod.GET,
                                getRequest,
                                String.class);

                assertThat(getProductResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode retrievedProduct = parseJsonResponse(getProductResponse.getBody());
                assertThat(retrievedProduct.get("productTitle").asText())
                                .as("Product title should match")
                                .isEqualTo(testProduct.get("productTitle"));

                System.out.println("✅ Product browsing verified");

                // STEP 3: Shopping Cart Management
                System.out.println("🛒 Step 3: Shopping Cart Creation");

                Map<String, Object> cartData = new HashMap<>();
                cartData.put("userId", userId);

                HttpEntity<Map<String, Object>> cartRequest = new HttpEntity<>(cartData, headers);
                ResponseEntity<String> cartResponse = restTemplate.postForEntity(
                                apiGatewayUrl + "/order-service/api/carts",
                                cartRequest,
                                String.class);

                assertThat(cartResponse.getStatusCode())
                                .as("Cart creation should succeed")
                                .isIn(HttpStatus.OK, HttpStatus.CREATED);

                JsonNode cartResult = parseJsonResponse(cartResponse.getBody());
                cartId = cartResult.get("cartId").asText();
                assertThat(cartId).as("Cart ID should be generated").isNotBlank();

                System.out.println("✅ Shopping cart created: " + cartId);

                // Verify cart can be retrieved
                HttpEntity<Void> getCartRequest = new HttpEntity<>(headers);
                ResponseEntity<String> getCartResponse = restTemplate.exchange(
                                apiGatewayUrl + "/order-service/api/carts/" + cartId,
                                HttpMethod.GET,
                                getCartRequest,
                                String.class);

                assertThat(getCartResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
                System.out.println("✅ Cart retrieval verified");

                // STEP 4: Order Creation (Checkout Process)
                System.out.println("💳 Step 4: Order Creation (Checkout)");

                Map<String, Object> orderData = new HashMap<>();
                orderData.put("orderDate",
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy__HH:mm:ss:SSSSSS")));
                orderData.put("orderDesc", "E2E test order for shopping flow " + uniqueId);
                orderData.put("orderFee", 34.99); // Product price + shipping

                // Nested cart object
                Map<String, Object> cart = new HashMap<>();
                cart.put("cartId", cartId);
                orderData.put("cart", cart);

                HttpEntity<Map<String, Object>> orderRequest = new HttpEntity<>(orderData, headers);
                ResponseEntity<String> orderResponse = restTemplate.postForEntity(
                                apiGatewayUrl + "/order-service/api/orders",
                                orderRequest,
                                String.class);

                assertThat(orderResponse.getStatusCode())
                                .as("Order creation should succeed")
                                .isIn(HttpStatus.OK, HttpStatus.CREATED);

                JsonNode orderResult = parseJsonResponse(orderResponse.getBody());
                orderId = orderResult.get("orderId").asText();
                assertThat(orderId).as("Order ID should be generated").isNotBlank();

                System.out.println("✅ Order created: " + orderId);

                // STEP 5: Order Verification
                System.out.println("🔍 Step 5: Order Details Verification");

                HttpEntity<Void> getOrderRequest = new HttpEntity<>(headers);
                ResponseEntity<String> getOrderResponse = restTemplate.exchange(
                                apiGatewayUrl + "/order-service/api/orders/" + orderId,
                                HttpMethod.GET,
                                getOrderRequest,
                                String.class);

                assertThat(getOrderResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode orderDetails = parseJsonResponse(getOrderResponse.getBody());
                assertThat(orderDetails.get("orderId").asText())
                                .as("Order ID should match")
                                .isEqualTo(orderId);
                assertThat(orderDetails.get("cart").get("cartId").asText())
                                .as("Cart ID should match")
                                .isEqualTo(cartId);
                assertThat(orderDetails.get("orderDesc").asText())
                                .as("Order description should match")
                                .isEqualTo(orderData.get("orderDesc"));
                assertThat(orderDetails.get("orderFee").asDouble())
                                .as("Order fee should match")
                                .isEqualTo(orderData.get("orderFee"));

                System.out.println("✅ Order details verified");

                // STEP 6: Order History Verification
                System.out.println("📋 Step 6: Order History Check");

                HttpEntity<Void> historyRequest = new HttpEntity<>(headers);
                ResponseEntity<String> orderHistoryResponse = restTemplate.exchange(
                                apiGatewayUrl + "/order-service/api/orders",
                                HttpMethod.GET,
                                historyRequest,
                                String.class);

                assertThat(orderHistoryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode allOrders = parseJsonResponse(orderHistoryResponse.getBody());
                assertThat(allOrders.isArray()).as("Order history should be an array").isTrue();

                // Find our order in the history
                boolean orderFoundInHistory = false;
                for (JsonNode order : allOrders) {
                        if (order.get("orderId").asText().equals(orderId)) {
                                orderFoundInHistory = true;
                                break;
                        }
                }

                assertThat(orderFoundInHistory)
                                .as("Order should be found in order history")
                                .isTrue();

                System.out.println("✅ Order found in order history");

                System.out.println("\n🎉 COMPLETE SHOPPING JOURNEY SUCCESSFUL!");
                System.out.println("   User ID: " + userId);
                System.out.println("   Product ID: " + productId);
                System.out.println("   Cart ID: " + cartId);
                System.out.println("   Order ID: " + orderId);
        }

        @Test
        @DisplayName("Product Inventory Management")
        void testProductInventoryManagement() {
                System.out.println("📊 Testing Product Inventory Management");

                HttpHeaders headers = createHeadersWithJwt();

                // Create product with limited inventory
                Map<String, Object> limitedProduct = new HashMap<>();
                limitedProduct.put("productTitle", "LimitedProduct" + uniqueId);
                limitedProduct.put("imageUrl", "https://example.com/limited.jpg");
                limitedProduct.put("sku", "LIMITED" + uniqueId);
                limitedProduct.put("priceUnit", 99.99);
                limitedProduct.put("quantity", 5); // Limited stock

                // Nested category
                Map<String, Object> category = new HashMap<>();
                category.put("categoryId", 1);
                limitedProduct.put("category", category);

                HttpEntity<Map<String, Object>> productRequest = new HttpEntity<>(limitedProduct, headers);
                ResponseEntity<String> productResponse = restTemplate.postForEntity(
                                apiGatewayUrl + "/product-service/api/products",
                                productRequest,
                                String.class);

                assertThat(productResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

                JsonNode productData = parseJsonResponse(productResponse.getBody());
                String productId = productData.get("productId").asText();

                // Verify initial inventory
                HttpEntity<Void> getProductRequest = new HttpEntity<>(headers);
                ResponseEntity<String> getProductResponse = restTemplate.exchange(
                                apiGatewayUrl + "/product-service/api/products/" + productId,
                                HttpMethod.GET,
                                getProductRequest,
                                String.class);

                assertThat(getProductResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode initialProduct = parseJsonResponse(getProductResponse.getBody());
                assertThat(initialProduct.get("quantity").asInt())
                                .as("Initial quantity should be 5")
                                .isEqualTo(5);

                System.out.println("✅ Initial inventory verified: " + initialProduct.get("quantity").asInt());

                // Update inventory (simulate purchase)
                Map<String, Object> updatedProduct = new HashMap<>();
                updatedProduct.put("productId", productId);
                updatedProduct.put("productTitle", limitedProduct.get("productTitle"));
                updatedProduct.put("imageUrl", limitedProduct.get("imageUrl"));
                updatedProduct.put("sku", limitedProduct.get("sku"));
                updatedProduct.put("priceUnit", limitedProduct.get("priceUnit"));
                updatedProduct.put("quantity", 3); // Reduced inventory
                updatedProduct.put("category", category);

                HttpEntity<Map<String, Object>> updateRequest = new HttpEntity<>(updatedProduct, headers);
                ResponseEntity<String> updateResponse = restTemplate.exchange(
                                apiGatewayUrl + "/product-service/api/products",
                                HttpMethod.PUT,
                                updateRequest,
                                String.class);

                assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                // Verify inventory was updated
                HttpEntity<Void> finalCheckRequest = new HttpEntity<>(headers);
                ResponseEntity<String> finalCheckResponse = restTemplate.exchange(
                                apiGatewayUrl + "/product-service/api/products/" + productId,
                                HttpMethod.GET,
                                finalCheckRequest,
                                String.class);

                assertThat(finalCheckResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode finalProduct = parseJsonResponse(finalCheckResponse.getBody());
                assertThat(finalProduct.get("quantity").asInt())
                                .as("Updated quantity should be 3")
                                .isEqualTo(3);

                System.out.println(
                                "✅ Inventory successfully updated from 5 to " + finalProduct.get("quantity").asInt());
                System.out.println("🎉 Product Inventory Management Test PASSED!");
        }

        @Test
        @DisplayName("Order Update Workflow")
        void testOrderUpdateWorkflow() {
                System.out.println("📝 Testing Order Update Workflow");

                HttpHeaders headers = createHeadersWithJwt();

                // Create minimal setup for order
                HttpEntity<Map<String, Object>> userRequest = new HttpEntity<>(testUser, headers);
                ResponseEntity<String> userResponse = restTemplate.postForEntity(
                                apiGatewayUrl + "/user-service/api/users",
                                userRequest,
                                String.class);
                assertThat(userResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

                String userId = parseJsonResponse(userResponse.getBody()).get("userId").asText();

                Map<String, Object> cartData = new HashMap<>();
                cartData.put("userId", userId);
                HttpEntity<Map<String, Object>> cartRequest = new HttpEntity<>(cartData, headers);
                ResponseEntity<String> cartResponse = restTemplate.postForEntity(
                                apiGatewayUrl + "/order-service/api/carts",
                                cartRequest,
                                String.class);
                assertThat(cartResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

                String cartId = parseJsonResponse(cartResponse.getBody()).get("cartId").asText();

                // Create order
                Map<String, Object> orderData = new HashMap<>();
                orderData.put("orderDate",
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy__HH:mm:ss:SSSSSS")));
                orderData.put("orderDesc", "Original order description");
                orderData.put("orderFee", 50.00);

                // Nested cart
                Map<String, Object> cart = new HashMap<>();
                cart.put("cartId", cartId);
                orderData.put("cart", cart);

                HttpEntity<Map<String, Object>> orderRequest = new HttpEntity<>(orderData, headers);
                ResponseEntity<String> orderResponse = restTemplate.postForEntity(
                                apiGatewayUrl + "/order-service/api/orders",
                                orderRequest,
                                String.class);
                assertThat(orderResponse.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.CREATED);

                String orderId = parseJsonResponse(orderResponse.getBody()).get("orderId").asText();

                // Update order
                Map<String, Object> updatedOrder = new HashMap<>();
                updatedOrder.put("orderId", orderId);
                updatedOrder.put("orderDate", orderData.get("orderDate"));
                updatedOrder.put("orderDesc", "Updated order description - expedited shipping");
                updatedOrder.put("orderFee", 65.00); // Additional shipping cost
                updatedOrder.put("cart", cart);

                HttpEntity<Map<String, Object>> updateRequest = new HttpEntity<>(updatedOrder, headers);
                ResponseEntity<String> updateResponse = restTemplate.exchange(
                                apiGatewayUrl + "/order-service/api/orders",
                                HttpMethod.PUT,
                                updateRequest,
                                String.class);

                assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                // Verify update
                HttpEntity<Void> finalOrderRequest = new HttpEntity<>(headers);
                ResponseEntity<String> finalOrderResponse = restTemplate.exchange(
                                apiGatewayUrl + "/order-service/api/orders/" + orderId,
                                HttpMethod.GET,
                                finalOrderRequest,
                                String.class);
                assertThat(finalOrderResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

                JsonNode finalOrder = parseJsonResponse(finalOrderResponse.getBody());
                assertThat(finalOrder.get("orderDesc").asText())
                                .as("Order description should be updated")
                                .isEqualTo(updatedOrder.get("orderDesc"));
                assertThat(finalOrder.get("orderFee").asDouble())
                                .as("Order fee should be updated")
                                .isEqualTo(updatedOrder.get("orderFee"));

                System.out.println("✅ Order update workflow completed successfully");
                System.out.println("🎉 Order Update Workflow Test PASSED!");
        }

        private JsonNode parseJsonResponse(String responseBody) {
                try {
                        return objectMapper.readTree(responseBody);
                } catch (Exception e) {
                        fail("Failed to parse JSON response: " + responseBody, e);
                        return null;
                }
        }
}