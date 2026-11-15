package com.shopsphere.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrationRequest(
    @NotBlank @Size(max = 80) String name,
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Size(min = 10, max = 72) String password
) {}
