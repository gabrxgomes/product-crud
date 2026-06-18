package com.example.productcrud.product;

import com.example.productcrud.product.dto.ProductRequest;
import com.example.productcrud.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void createPersistsAndReturnsMappedResponse() {
        ProductRequest request = new ProductRequest("Keyboard", "Mechanical", new BigDecimal("199.90"), 10);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ProductResponse response = productService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Keyboard");
        assertThat(response.price()).isEqualTo(new BigDecimal("199.90"));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualTo(10);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(42L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void updateAppliesChangesToExistingProduct() {
        Product existing = new Product();
        existing.setId(7L);
        existing.setName("Old name");
        existing.setPrice(new BigDecimal("10.00"));
        existing.setQuantity(1);
        when(productRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest request = new ProductRequest("New name", "desc", new BigDecimal("25.50"), 5);
        ProductResponse response = productService.update(7L, request);

        assertThat(response.name()).isEqualTo("New name");
        assertThat(response.price()).isEqualTo(new BigDecimal("25.50"));
        assertThat(response.quantity()).isEqualTo(5);
    }

    @Test
    void deleteThrowsWhenProductMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productRepository, never()).delete(any());
    }
}
