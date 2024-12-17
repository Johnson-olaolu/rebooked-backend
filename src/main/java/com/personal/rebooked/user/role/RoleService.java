package com.personal.rebooked.user.role;

import com.personal.rebooked.user.role.dto.CreateRoleDTO;
import com.personal.rebooked.user.role.dto.UpdateRoleDTO;
import com.personal.rebooked.user.role.models.Role;
import com.personal.rebooked.user.role.repositories.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RequiredArgsConstructor()
@Service
public class RoleService {

    private final RoleRepository roleRepository;

    public Role getRoleById(String id) {
        return roleRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public Role getRoleByName(String name) {
        return roleRepository.findByName(name).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Role createRole(CreateRoleDTO createRoleDTO) {
        Role role = new Role();
        role.setName(createRoleDTO.name());
        role.setDescription(createRoleDTO.description());
        return roleRepository.save(role);
    }

    public Role updateRole(String id, UpdateRoleDTO updateRoleDTO) {
        Role role = getRoleById(id);
        if(updateRoleDTO.name() != null) {
            role.setName(updateRoleDTO.name());
        }
        if(updateRoleDTO.description() != null) {
            role.setDescription(updateRoleDTO.description());
        }
        return roleRepository.save(role);
    }

    public void deleteRole(String id) {
        roleRepository.deleteById(id);
    }
}
