package com.personal.rebooked.auth;

import com.personal.rebooked.auth.dto.*;
import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.ResponseHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping("/register")
    public ResponseEntity<Object>register ( @RequestBody @Valid CreateUserDto createUserDto){
        System.out.println( createUserDto );
        LoginResponseDTO loginResponseDTO = authService.register(createUserDto);
        return ResponseHandler.generateResponse(loginResponseDTO, "User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login (@RequestBody @Valid  LoginRequestDTO loginRequestDTO) {
       authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequestDTO.email(),
                        loginRequestDTO.password()
                )
        );
        LoginResponseDTO loginResponseDTO = authService.login(loginRequestDTO);
        return ResponseHandler.generateResponse(loginResponseDTO, "User logged in successfully");
    }

    @PostMapping("/google/login")
    public ResponseEntity<Object> googleLogin (@RequestBody @Valid  GoogleLoginDTO googleLoginDTO) {
        LoginResponseDTO loginResponseDTO = authService.handleGoogleLoginToken(googleLoginDTO);
        return ResponseHandler.generateResponse(loginResponseDTO, "User logged in successfully ");
    }

    @PostMapping("/facebook/login")
    public ResponseEntity<Object> facebookLogin (@RequestBody @Valid  FacebookLoginDTO facebookLoginDTO) {
        LoginResponseDTO loginResponseDTO = authService.handleFacebookLoginToken(facebookLoginDTO);
        return ResponseHandler.generateResponse(loginResponseDTO, "User logged in successfully ");
    }

    @GetMapping("/confirm-email")
    public ResponseEntity<Object> sendConfirmEmail(@ModelAttribute @Valid SendConfirmEmailDTO sendConfirmEmailDTO) {
        User user = authService.sendConfirmEmail(sendConfirmEmailDTO);
        return ResponseHandler.generateResponse(user, "Email sent successfully");
    }

    @PostMapping("/confirm-email")
    public ResponseEntity<Object> confirmEmail (@RequestBody @Valid ConfirmEmailDTO confirmEmailDTO) {
        User user = authService.confirmEmail(confirmEmailDTO);
        return ResponseHandler.generateResponse(user, "Email verified successfully");
    }

    @GetMapping("/forgot-password")
    public ResponseEntity<Object> sendForgotPasswordMail( @ModelAttribute @Valid ForgotPasswordDTO forgotPasswordDTO) {
        User user = authService.sendForgotPasswordMail( forgotPasswordDTO);
        return ResponseHandler.generateResponse(user, "Email sent successfully");
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Object> changePassword(@RequestBody @Valid ChangePasswordDTO changePasswordDTO){
        User user = authService.changePassword(changePasswordDTO);
        return ResponseHandler.generateResponse(user, "Password changed successfully");
    }
}
