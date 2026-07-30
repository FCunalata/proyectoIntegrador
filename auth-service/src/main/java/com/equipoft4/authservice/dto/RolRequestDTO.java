package com.equipoft4.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RolRequestDTO(

        @NotBlank(message = "El nombre del rol es obligatorio")
        @Size(max = 50, message = "El nombre del rol no puede superar los 50 caracteres")
        @Pattern(
                regexp = "^[A-Za-zÀ-ÿ0-9 _.-]+$",
                message = "El nombre del rol solo puede contener letras, números, espacios, guiones, puntos y guiones bajos"
        )
        String nombre,

        @Size(max = 255, message = "La descripción no puede superar los 255 caracteres")
        @Pattern(regexp = "^[^<>]*$", message = "La descripción no puede contener los caracteres < o >")
        String descripcion
) {
}
