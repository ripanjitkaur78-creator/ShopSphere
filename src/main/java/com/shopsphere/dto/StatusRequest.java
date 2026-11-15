package com.shopsphere.dto;

import com.shopsphere.model.Status;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull Status status) {}
