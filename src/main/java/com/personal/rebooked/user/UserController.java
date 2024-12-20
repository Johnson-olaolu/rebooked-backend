package com.personal.rebooked.user;

import com.personal.rebooked.user.dto.UpdateProfileDTO;
import com.personal.rebooked.user.dto.UpdateUserDTO;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.ResponseHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    ResponseEntity<Object> getUser (){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) authentication.getPrincipal();
        return ResponseHandler.generateResponse(currentUser, "User fetched successfully");
    }

    @GetMapping("")
    ResponseEntity<Object> getAllUser(){
        List<User> users = userService.getAllUsers();
        return  ResponseHandler.generateResponse(users, "Users fetched successfully");
    }

    @GetMapping("/{id}")
    ResponseEntity<Object> getUserById(@PathVariable  String id){
        User user = userService.findUserById(id);
        return ResponseHandler.generateResponse(user,"User fetched successfully");
    }

    @PatchMapping("/{id}")
    ResponseEntity<Object> updateUser(@PathVariable String id, @RequestBody UpdateUserDTO updateUserDTO){
        User user = userService.updateUser(id, updateUserDTO);
        return ResponseHandler.generateResponse( user, "User Updated successfully");
    }

    @PatchMapping("/{id}/profile")
    ResponseEntity<Object> updateProfile(@PathVariable String id, @RequestBody UpdateProfileDTO updateProfileDTO){
        User user = userService.updateUserProfile(id, updateProfileDTO);
        return ResponseHandler.generateResponse( user, "User Profile Updated successfully");
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Object> deleteUser(@PathVariable String id){
        userService.deleteUser(id);
        return ResponseHandler.generateResponse( null, "User deleted successfully");
    }
}
