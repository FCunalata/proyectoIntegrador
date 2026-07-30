package com.equipoft4.authservice.service;

import com.equipoft4.authservice.dto.RolRequestDTO;
import com.equipoft4.authservice.dto.RolResponseDTO;
import com.equipoft4.authservice.dto.UsuarioResponseDTO;
import com.equipoft4.authservice.entity.Rol;
import com.equipoft4.authservice.entity.Usuario;
import com.equipoft4.authservice.exception.ApiException;
import com.equipoft4.authservice.repository.RolRepository;
import com.equipoft4.authservice.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RolService {

    private final RolRepository rolRepository;
    private final UsuarioRepository usuarioRepository;

    public RolService(RolRepository rolRepository, UsuarioRepository usuarioRepository) {
        this.rolRepository = rolRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public List<RolResponseDTO> listarRoles() {
        return rolRepository.findAll().stream()
                .map(this::aResponseDTO)
                .toList();
    }

    public List<UsuarioResponseDTO> listarUsuarios() {
        return usuarioRepository.findAll().stream()
                .map(this::aUsuarioResponseDTO)
                .toList();
    }

    @Transactional
    public RolResponseDTO crearRol(RolRequestDTO request) {
        if (rolRepository.findByNombre(request.nombre()).isPresent()) {
            throw new ApiException("Ya existe un rol con ese nombre", HttpStatus.CONFLICT);
        }

        Rol rol = Rol.builder()
                .nombre(request.nombre())
                .descripcion(request.descripcion())
                .build();

        return aResponseDTO(rolRepository.save(rol));
    }

    @Transactional
    public UsuarioResponseDTO asignarRol(Long usuarioId, String nombreRol) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        Rol rol = rolRepository.findByNombre(nombreRol)
                .orElseThrow(() -> new ApiException("Rol no encontrado", HttpStatus.NOT_FOUND));

        usuario.getRoles().add(rol);
        Usuario guardado = usuarioRepository.save(usuario);
        return aUsuarioResponseDTO(guardado);
    }

    @Transactional
    public UsuarioResponseDTO quitarRol(Long usuarioId, String nombreRol) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        Rol rol = rolRepository.findByNombre(nombreRol)
                .orElseThrow(() -> new ApiException("Rol no encontrado", HttpStatus.NOT_FOUND));

        usuario.getRoles().remove(rol);
        Usuario guardado = usuarioRepository.save(usuario);
        return aUsuarioResponseDTO(guardado);
    }

    private RolResponseDTO aResponseDTO(Rol rol) {
        return new RolResponseDTO(rol.getId(), rol.getNombre(), rol.getDescripcion());
    }

    private UsuarioResponseDTO aUsuarioResponseDTO(Usuario usuario) {
        Set<String> nombresRoles = usuario.getRoles().stream()
                .map(Rol::getNombre)
                .collect(Collectors.toSet());

        return new UsuarioResponseDTO(
                usuario.getId(),
                usuario.getNombreUsuario(),
                usuario.getEmail(),
                usuario.isActivo(),
                usuario.getFechaCreacion(),
                nombresRoles
        );
    }
}
