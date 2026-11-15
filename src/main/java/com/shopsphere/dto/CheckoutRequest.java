package com.shopsphere.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequest(
    @NotBlank @Size(max = 80) String shippingName,
    @NotBlank @Size(min = 10, max = 500) String shippingAddress,
    @NotBlank @Pattern(regexp = "[+0-9 ()-]{7,25}") String phone
) {}
