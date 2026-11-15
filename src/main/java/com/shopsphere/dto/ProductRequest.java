package com.shopsphere.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 4000) String description,
    @NotNull
    @DecimalMin("0.01")
    @DecimalMax("9999999999.99")
    @Digits(integer = 10, fraction = 2)
    BigDecimal price,
    @Min(0) int stock,
    @Size(max = 1000)
    @Pattern(regexp = "^$|^https://[^\\s]+$|^/images/[a-zA-Z0-9_./-]+$")
    String imageUrl,
    boolean featured,
    @NotNull @Positive Long categoryId
) {}
