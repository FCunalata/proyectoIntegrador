package com.equipoft4.authservice.dto;

import jakarta.validation.constraints.NotBlank;

public record AsignarRolRequestDTO(

        @NotBlank(message = "El nombre del rol es obligatorio")
        String nombreRol
) {
}
