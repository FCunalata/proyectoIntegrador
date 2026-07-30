package com.equipoft4.authservice.dto;

public record JwtResponseDTO(
        String token,
        String tipo,
        String nombreUsuario,
        long expiraEnMs
) {
    public JwtResponseDTO(String token, String nombreUsuario, long expiraEnMs) {
        this(token, "Bearer", nombreUsuario, expiraEnMs);
    }
}
