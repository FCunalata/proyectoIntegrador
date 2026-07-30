package com.equipoft4.authservice.dto;

import java.time.LocalDateTime;
import java.util.Set;

public record UsuarioResponseDTO(
        Long id,
        String nombreUsuario,
        String email,
        boolean activo,
        LocalDateTime fechaCreacion,
        Set<String> roles
) {
}
