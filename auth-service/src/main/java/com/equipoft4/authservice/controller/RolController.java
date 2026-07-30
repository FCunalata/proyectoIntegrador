package com.equipoft4.authservice.controller;

import com.equipoft4.authservice.dto.AsignarRolRequestDTO;
import com.equipoft4.authservice.dto.RolRequestDTO;
import com.equipoft4.authservice.dto.RolResponseDTO;
import com.equipoft4.authservice.dto.UsuarioResponseDTO;
import com.equipoft4.authservice.service.RolService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class RolController {

    private final RolService rolService;

    public RolController(RolService rolService) {
        this.rolService = rolService;
    }

    @GetMapping("/roles")
    public ResponseEntity<List<RolResponseDTO>> listarRoles() {
        return ResponseEntity.ok(rolService.listarRoles());
    }

    @GetMapping("/usuarios")
    public ResponseEntity<List<UsuarioResponseDTO>> listarUsuarios() {
        return ResponseEntity.ok(rolService.listarUsuarios());
    }

    @PostMapping("/roles")
    public ResponseEntity<RolResponseDTO> crearRol(@Valid @RequestBody RolRequestDTO request) {
        RolResponseDTO rol = rolService.crearRol(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(rol);
    }

    @PostMapping("/usuarios/{usuarioId}/roles")
    public ResponseEntity<UsuarioResponseDTO> asignarRol(
            @PathVariable("usuarioId") Long usuarioId,
            @Valid @RequestBody AsignarRolRequestDTO request) {
        return ResponseEntity.ok(rolService.asignarRol(usuarioId, request.nombreRol()));
    }

    @DeleteMapping("/usuarios/{usuarioId}/roles/{nombreRol}")
    public ResponseEntity<UsuarioResponseDTO> quitarRol(
            @PathVariable("usuarioId") Long usuarioId,
            @PathVariable("nombreRol") String nombreRol) {
        return ResponseEntity.ok(rolService.quitarRol(usuarioId, nombreRol));
    }
}
