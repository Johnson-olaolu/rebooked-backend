package com.personal.rebooked.auth;

import com.personal.rebooked.auth.dto.*;
import com.personal.rebooked.security.CurrentUser;
import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.privacy.AddressPrivacy;
import com.personal.rebooked.utils.ApiResponseDocs;
import com.personal.rebooked.utils.ResponseHandler;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final AuthService authService;

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody @Valid CreateUserDto createUserDto) {
        AddressPrivacy.showOwnerFields(); // response is about the caller
        LoginResponseDTO loginResponseDTO = authService.register(createUserDto);
        return ResponseHandler.generateResponse(loginResponseDTO, "User registered successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthResponse.class))),
            @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody @Valid LoginRequestDTO loginRequestDTO) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequestDTO.email(),
                        loginRequestDTO.password()
                )
        );
        AddressPrivacy.showOwnerFields(); // response is about the caller
        LoginResponseDTO loginResponseDTO = authService.login(loginRequestDTO);
        return ResponseHandler.generateResponse(loginResponseDTO, "User logged in successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthResponse.class))),
            @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping("/refresh-token")
    public ResponseEntity<Object> refreshToken(@RequestBody @Valid RefreshTokenDTO refreshTokenDTO) {
        AddressPrivacy.showOwnerFields(); // response is about the caller
        LoginResponseDTO loginResponseDTO = authService.refresh(refreshTokenDTO);
        return ResponseHandler.generateResponse(loginResponseDTO, "Token refreshed successfully");
    }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.EmptyResponse.class)))
    @PostMapping("/logout")
    public ResponseEntity<Object> logout(@RequestBody @Valid RefreshTokenDTO refreshTokenDTO) {
        authService.logout(refreshTokenDTO);
        return ResponseHandler.generateResponse(null, "Logged out successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthResponse.class))),
            @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping("/google/login")
    public ResponseEntity<Object> googleLogin(@RequestBody @Valid GoogleLoginDTO googleLoginDTO) {
        AddressPrivacy.showOwnerFields(); // response is about the caller
        LoginResponseDTO loginResponseDTO = authService.handleGoogleLoginToken(googleLoginDTO);
        return ResponseHandler.generateResponse(loginResponseDTO, "User logged in successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthResponse.class))),
            @ApiResponse(responseCode = "401", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping("/facebook/login")
    public ResponseEntity<Object> facebookLogin(@RequestBody @Valid FacebookLoginDTO facebookLoginDTO) {
        AddressPrivacy.showOwnerFields(); // response is about the caller
        LoginResponseDTO loginResponseDTO = authService.handleFacebookLoginToken(facebookLoginDTO);
        return ResponseHandler.generateResponse(loginResponseDTO, "User logged in successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.EmptyResponse.class))),
            @ApiResponse(responseCode = "429", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @GetMapping("/confirm-email")
    public ResponseEntity<Object> sendConfirmEmail(@ParameterObject @ModelAttribute @Valid SendConfirmEmailDTO sendConfirmEmailDTO) {
        authService.sendConfirmEmail(sendConfirmEmailDTO);
        return ResponseHandler.generateResponse(null, "Email sent successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.UserResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping("/confirm-email")
    public ResponseEntity<Object> confirmEmail(@RequestBody @Valid ConfirmEmailDTO confirmEmailDTO) {
        AddressPrivacy.showOwnerFields(); // response is about the caller
        User user = authService.confirmEmail(confirmEmailDTO);
        return ResponseHandler.generateResponse(user, "Email verified successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.EmptyResponse.class))),
            @ApiResponse(responseCode = "429", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @GetMapping("/forgot-password")
    public ResponseEntity<Object> sendForgotPasswordMail(@ParameterObject @ModelAttribute @Valid ForgotPasswordDTO forgotPasswordDTO) {
        authService.sendForgotPasswordMail(forgotPasswordDTO);
        // Same answer whether or not the email has an account
        return ResponseHandler.generateResponse(null, "If that email has an account, a reset link is on its way");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.UserResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping("/forgot-password")
    public ResponseEntity<Object> changePassword(@RequestBody @Valid ChangePasswordDTO changePasswordDTO) {
        AddressPrivacy.showOwnerFields(); // response is about the caller
        User user = authService.changePassword(changePasswordDTO);
        return ResponseHandler.generateResponse(user, "Password changed successfully");
    }

    @ApiResponses({
            @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ApiResponseDocs.AuthResponse.class))),
            @ApiResponse(responseCode = "400", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class))),
            @ApiResponse(responseCode = "429", content = @Content(schema = @Schema(implementation = ApiResponseDocs.ErrorResponse.class)))
    })
    @PostMapping("/change-password")
    public ResponseEntity<Object> updatePassword(@RequestBody @Valid UpdatePasswordDTO updatePasswordDTO) {
        User currentUser = CurrentUser.require();
        AddressPrivacy.showOwnerFields(); // response is about the caller
        LoginResponseDTO loginResponseDTO = authService.updatePassword(currentUser, updatePasswordDTO);
        return ResponseHandler.generateResponse(loginResponseDTO, "Password changed successfully");
    }
}
