package com.equipoft4.authservice.config;

import com.equipoft4.authservice.entity.Permiso;
import com.equipoft4.authservice.entity.Rol;
import com.equipoft4.authservice.repository.PermisoRepository;
import com.equipoft4.authservice.repository.RolRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
public class DataSeeder implements CommandLineRunner {

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;

    public DataSeeder(RolRepository rolRepository, PermisoRepository permisoRepository) {
        this.rolRepository = rolRepository;
        this.permisoRepository = permisoRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Permiso leerUsuarios = obtenerOCrearPermiso("LEER_USUARIOS", "Permite consultar usuarios");
        Permiso gestionarUsuarios = obtenerOCrearPermiso("GESTIONAR_USUARIOS", "Permite crear, editar y eliminar usuarios");
        Permiso gestionarRoles = obtenerOCrearPermiso("GESTIONAR_ROLES", "Permite crear y asignar roles y permisos");

        obtenerOCrearRol("USUARIO", "Rol asignado por defecto a nuevos usuarios", Set.of(leerUsuarios));
        obtenerOCrearRol("ADMIN", "Rol con acceso administrativo total", Set.of(leerUsuarios, gestionarUsuarios, gestionarRoles));
    }

    private Permiso obtenerOCrearPermiso(String nombre, String descripcion) {
        return permisoRepository.findByNombre(nombre)
                .orElseGet(() -> permisoRepository.save(
                        Permiso.builder()
                                .nombre(nombre)
                                .descripcion(descripcion)
                                .build()));
    }

    private void obtenerOCrearRol(String nombre, String descripcion, Set<Permiso> permisos) {
        Rol rol = rolRepository.findByNombre(nombre)
                .orElseGet(() -> Rol.builder()
                        .nombre(nombre)
                        .descripcion(descripcion)
                        .build());

        rol.getPermisos().addAll(permisos);
        rolRepository.save(rol);
    }
}
