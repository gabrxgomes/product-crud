package com.example.productcrud.product.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer quantity,
        Instant createdAt,
        Instant updatedAt
) {
}
