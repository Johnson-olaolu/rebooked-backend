package com.personal.rebooked.user.role.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRoleDTO(
        @NotBlank()
        String name,
        @NotBlank()
        String description
) {}
