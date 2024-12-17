package com.personal.rebooked.user.role;

import com.personal.rebooked.user.role.dto.CreateRoleDTO;
import com.personal.rebooked.user.role.dto.UpdateRoleDTO;
import com.personal.rebooked.user.role.models.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RequiredArgsConstructor
@RequestMapping("/role")
@RestController
public class RoleController {

    private final RoleService roleService;

    @PostMapping("")
    public ResponseEntity<Role> createRole(@RequestBody @Valid CreateRoleDTO createRoleDTO) {
        Role role = roleService.createRole(createRoleDTO);
        return ResponseEntity.ok(role);
    }

    @GetMapping("")
    public ResponseEntity<List<Role>> getAllRoles() {
        List<Role> roles = roleService.getAllRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/name")
    public ResponseEntity<Role> getRoleByName(@RequestParam("name") String name) {
        Role role = roleService.getRoleByName(name);
        return ResponseEntity.ok(role);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Role> getRoleById(@PathVariable String id) {
        Role role = roleService.getRoleById(id);
        return ResponseEntity.ok(role);
    }


    @PatchMapping("/{id}")
    public ResponseEntity<Role> updateRole(@PathVariable String id,  @RequestBody @Valid UpdateRoleDTO updateRoleDTO) {
        Role role = roleService.updateRole(id, updateRoleDTO);
        return ResponseEntity.ok(role);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Boolean> deleteRole(@PathVariable String id) {
        roleService.deleteRole(id);
        return ResponseEntity.ok(true);
    }
}
