package com.equipoft4.authservice.service;

import com.equipoft4.authservice.dto.JwtResponseDTO;
import com.equipoft4.authservice.dto.LoginRequestDTO;
import com.equipoft4.authservice.dto.RegistroRequestDTO;
import com.equipoft4.authservice.dto.UsuarioResponseDTO;
import com.equipoft4.authservice.entity.Rol;
import com.equipoft4.authservice.entity.Usuario;
import com.equipoft4.authservice.exception.ApiException;
import com.equipoft4.authservice.repository.RolRepository;
import com.equipoft4.authservice.repository.UsuarioRepository;
import com.equipoft4.authservice.security.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final String ROL_POR_DEFECTO = "USUARIO";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    @Transactional
    public UsuarioResponseDTO registrar(RegistroRequestDTO request) {
        if (usuarioRepository.existsByNombreUsuario(request.nombreUsuario())) {
            throw new ApiException("El nombre de usuario ya está en uso", HttpStatus.CONFLICT);
        }
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new ApiException("El correo electrónico ya está registrado", HttpStatus.CONFLICT);
        }

        Rol rolPorDefecto = rolRepository.findByNombre(ROL_POR_DEFECTO)
                .orElseGet(() -> rolRepository.save(
                        Rol.builder()
                                .nombre(ROL_POR_DEFECTO)
                                .descripcion("Rol asignado por defecto a nuevos usuarios")
                                .build()));

        Set<Rol> roles = new HashSet<>();
        roles.add(rolPorDefecto);

        Usuario usuario = Usuario.builder()
                .nombreUsuario(request.nombreUsuario())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .activo(true)
                .roles(roles)
                .build();

        Usuario guardado = usuarioRepository.save(usuario);
        return aResponseDTO(guardado);
    }

    public UsuarioResponseDTO obtenerPerfil(String nombreUsuario) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));
        return aResponseDTO(usuario);
    }

    public JwtResponseDTO login(LoginRequestDTO request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.nombreUsuario(), request.password()));

        Usuario usuario = usuarioRepository.findByNombreUsuario(request.nombreUsuario())
                .orElseThrow(() -> new ApiException("Usuario no encontrado", HttpStatus.NOT_FOUND));

        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername(usuario.getNombreUsuario())
                .password(usuario.getPassword())
                .authorities(usuario.getRoles().stream()
                        .map(rol -> "ROLE_" + rol.getNombre())
                        .toArray(String[]::new))
                .build();

        String token = jwtService.generarToken(userDetails);
        return new JwtResponseDTO(token, usuario.getNombreUsuario(), jwtService.getExpirationMs());
    }

    private UsuarioResponseDTO aResponseDTO(Usuario usuario) {
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
