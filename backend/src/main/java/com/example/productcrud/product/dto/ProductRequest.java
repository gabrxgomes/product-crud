package com.example.productcrud.product.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Inbound payload for creating/updating a product. Kept separate from the
 * entity so the API contract doesn't change every time the persistence model does.
 */
public record ProductRequest(

        @NotBlank(message = "name is required")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @NotNull(message = "price is required")
        @jakarta.validation.constraints.DecimalMin(value = "0.01", message = "price must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "price must have at most 2 decimal places")
        BigDecimal price,

        @NotNull(message = "quantity is required")
        @PositiveOrZero(message = "quantity cannot be negative")
        Integer quantity
) {
}
