package com.example.productcrud.product;

import com.example.productcrud.product.dto.ProductRequest;
import com.example.productcrud.product.dto.ProductResponse;
import com.example.productcrud.web.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the full Spring context on a random port and drives the API exactly
 * like a real client would, against the real "productcrud_test" Postgres
 * database (Flyway-migrated). This is the test that proves the whole stack
 * - controller, validation, service, repository, schema - works together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullCrudLifecycle() {
        ProductRequest createRequest = new ProductRequest("Laptop", "14-inch", new BigDecimal("1499.00"), 5);

        ResponseEntity<ProductResponse> createResponse =
                restTemplate.postForEntity("/api/products", createRequest, ProductResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = createResponse.getBody().id();
        assertThat(id).isNotNull();

        ResponseEntity<ProductResponse> getResponse =
                restTemplate.getForEntity("/api/products/{id}", ProductResponse.class, id);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().name()).isEqualTo("Laptop");

        ProductRequest updateRequest = new ProductRequest("Laptop Pro", "16-inch", new BigDecimal("1999.00"), 3);
        ResponseEntity<ProductResponse> updateResponse = restTemplate.exchange(
                "/api/products/{id}",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(updateRequest),
                ProductResponse.class,
                id);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().name()).isEqualTo("Laptop Pro");
        assertThat(updateResponse.getBody().quantity()).isEqualTo(3);

        restTemplate.delete("/api/products/{id}", id);

        ResponseEntity<ApiError> afterDelete =
                restTemplate.getForEntity("/api/products/{id}", ApiError.class, id);
        assertThat(afterDelete.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
