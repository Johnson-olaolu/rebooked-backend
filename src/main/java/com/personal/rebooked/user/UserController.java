package com.personal.rebooked.user;

import com.personal.rebooked.user.dto.PublicUserDTO;

import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.user.dto.UpdateProfileDTO;
import com.personal.rebooked.user.dto.UpdateUserDTO;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.privacy.AddressPrivacy;
import com.personal.rebooked.utils.ApiResponseDocs;
import com.personal.rebooked.utils.ResponseHandler;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/user")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    ResponseEntity<Object> getUser() {
        User currentUser = currentUser();
        AddressPrivacy.showOwnerFields();
        return ResponseHandler.generateResponse(currentUser, "User fetched successfully");
    }

    @GetMapping("")
    ResponseEntity<Object> getAllUser() {
        List<PublicUserDTO> users = userService.getAllUsers().stream().map(PublicUserDTO::from).toList();
        return ResponseHandler.generateResponse(users, "Users fetched successfully");
    }

    @GetMapping("/{id}")
    ResponseEntity<Object> getUserById(@PathVariable String id) {
        User user = userService.findUserById(id);
        if (isCurrentUser(id)) {
            AddressPrivacy.showOwnerFields();
            return ResponseHandler.generateResponse(user, "User fetched successfully");
        }
        // Everyone else gets the public summary: no email, phone or precise address
        return ResponseHandler.generateResponse(PublicUserDTO.from(user), "User fetched successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.UserResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PatchMapping("/me")
    ResponseEntity<Object> updateMe(@RequestBody @Valid UpdateUserDTO updateUserDTO) {
        return updateUser(currentUser().getId(), updateUserDTO);
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Profile incomplete or invalid",
                    content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PatchMapping("/me/profile")
    ResponseEntity<Object> updateMyProfile(@RequestBody @Valid UpdateProfileDTO updateProfileDTO) {
        return updateProfile(currentUser().getId(), updateProfileDTO);
    }

    @DeleteMapping("/me")
    ResponseEntity<Object> deleteMe() {
        return deleteUser(currentUser().getId());
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.UserResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PatchMapping("/{id}")
    ResponseEntity<Object> updateUser(@PathVariable String id, @RequestBody @Valid UpdateUserDTO updateUserDTO) {
        requireCurrentUser(id);
        User user = userService.updateUser(id, updateUserDTO);
        AddressPrivacy.showOwnerFields();
        return ResponseHandler.generateResponse(user, "User Updated successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Profile incomplete or invalid",
                    content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class))),
            @ApiResponse(responseCode = "403", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PatchMapping("/{id}/profile")
    ResponseEntity<Object> updateProfile(@PathVariable String id, @RequestBody @Valid UpdateProfileDTO updateProfileDTO) {
        requireCurrentUser(id);
        User user = userService.updateUserProfile(id, updateProfileDTO);
        AddressPrivacy.showOwnerFields();
        return ResponseHandler.generateResponse(user, "User Profile Updated successfully");
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Object> deleteUser(@PathVariable String id) {
        requireCurrentUser(id);
        userService.deleteUser(id);
        return ResponseHandler.generateResponse(null, "User deleted successfully");
    }

    private static User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            // Security config already requires a token here; this is a backstop
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return user;
    }

    private static void requireCurrentUser(String userId) {
        if (!isCurrentUser(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only change your own account");
        }
    }

    private static boolean isCurrentUser(String userId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.getPrincipal() instanceof User current
                && current.getId().equals(userId);
    }
}
