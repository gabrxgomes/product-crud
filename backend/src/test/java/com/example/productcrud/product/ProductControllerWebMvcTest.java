package com.example.productcrud.product;

import com.example.productcrud.product.dto.ProductRequest;
import com.example.productcrud.product.dto.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductController.class)
class ProductControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @Test
    void findAllReturnsPageOfProducts() throws Exception {
        ProductResponse response = new ProductResponse(1L, "Mouse", "Wireless", new BigDecimal("99.90"), 3,
                Instant.now(), Instant.now());
        when(productService.findAll(any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mouse"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void findByIdReturns404WhenMissing() throws Exception {
        when(productService.findById(99L)).thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/products/{id}", 99L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found with id 99"));
    }

    @Test
    void createReturns201WithLocationHeader() throws Exception {
        ProductRequest request = new ProductRequest("Monitor", "27 inch", new BigDecimal("899.00"), 4);
        ProductResponse created = new ProductResponse(5L, "Monitor", "27 inch", new BigDecimal("899.00"), 4,
                Instant.now(), Instant.now());
        when(productService.create(eq(request))).thenReturn(created);

        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/products/5"))
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void createReturns400WhenNameBlank() throws Exception {
        ProductRequest invalid = new ProductRequest("", "desc", new BigDecimal("10.00"), 1);

        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void createReturns400WhenPriceIsZero() throws Exception {
        ProductRequest invalid = new ProductRequest("Cable", "desc", new BigDecimal("0.00"), 1);

        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/products/{id}", 1L))
                .andExpect(status().isNoContent());
    }
}
