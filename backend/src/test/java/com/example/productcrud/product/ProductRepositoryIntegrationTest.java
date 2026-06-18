package com.example.productcrud.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres "productcrud_test" database (see
 * src/test/resources/application.properties) so Flyway migrations and
 * column constraints are exercised exactly as in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void savesAndReloadsProductWithTimestamps() {
        Product product = new Product();
        product.setName("Webcam");
        product.setDescription("1080p");
        product.setPrice(new BigDecimal("149.99"));
        product.setQuantity(8);

        Product saved = productRepository.save(product);

        assertThat(saved.getId()).isNotNull();

        Product reloaded = productRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("Webcam");
        assertThat(reloaded.getPrice()).isEqualByComparingTo("149.99");
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void deleteRemovesProduct() {
        Product product = new Product();
        product.setName("Headset");
        product.setPrice(new BigDecimal("59.00"));
        product.setQuantity(2);
        Product saved = productRepository.save(product);

        productRepository.delete(saved);

        assertThat(productRepository.findById(saved.getId())).isEmpty();
    }
}
