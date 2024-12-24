package com.personal.rebooked.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.personal.rebooked.auth.dto.*;
import com.personal.rebooked.auth.jwt.JwtService;
import com.personal.rebooked.service.FacebookVerificationService;
import com.personal.rebooked.service.GoogleVerificationService;
import com.personal.rebooked.user.UserService;
import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final GoogleVerificationService googleVerificationService;
    private final FacebookVerificationService facebookVerificationService;


    public LoginResponseDTO register(CreateUserDto createUserDto) {
        User user = userService.createUser(createUserDto);
        return loginUser(user);
    }

    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO) {
        User user = userService.findUserByEmail(loginRequestDTO.email());
        if (user.getRegistrationType() != Constants.RegistrationType.EMAIL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please login using %s".formatted(user.getRegistrationType().toString().toLowerCase()));
        }
        return loginUser(user);
    }

    public LoginResponseDTO handleGoogleLoginToken(GoogleLoginDTO googleLoginDTO) {
        try {
            GoogleIdToken.Payload payload = googleVerificationService.verifyToken(googleLoginDTO.accessToken());
            Map<String, Object> googleDetails = new HashMap<>();
            googleDetails.put("email", payload.getEmail());
            googleDetails.put("role", googleLoginDTO.role());
            googleDetails.put("name", payload.get("name"));
            googleDetails.put("googleId", payload.getSubject());
            googleDetails.put("picture", payload.get("picture"));
            return googleLogin(googleDetails);
        } catch (Exception e) {
            e.printStackTrace();
            throw new BadCredentialsException("Invalid Token");
        }
    }

    private LoginResponseDTO googleLogin(Map<String, Object> googleDetails) {
        try {
            String email = (String) googleDetails.get("email");
            User user = userService.findUserByEmail(email);
            return loginUser(user);
        } catch (Exception e) {
//            System.out.println(googleDetails);
            CreateUserDto createUserDto = new CreateUserDto(
                    (String) googleDetails.get("email"),
                    (String) googleDetails.get("name"),
                    (String) googleDetails.get("googleId"),
                    true,
                    (String) googleDetails.get("roleName"),
                    googleDetails.get("picture") != null ? (String) googleDetails.get("picture") : null
            );
//            System.out.println(createUserDto);
            User newUser = userService.createUserOauth(createUserDto, Constants.RegistrationType.GOOGLE);
            return loginUser(newUser);
        }
    }

    public LoginResponseDTO handleFacebookLoginToken(FacebookLoginDTO facebookLoginDTO) {
        try {
            var payload = facebookVerificationService.verifyToken(facebookLoginDTO.accessToken());
            Map<String, Map> pictureData = (Map) payload.get("picture");
            Map<String, Object> facebookDetails = new HashMap<>();
            facebookDetails.put("email", payload.get("email"));
            facebookDetails.put("role", facebookLoginDTO.role());
            facebookDetails.put("name", payload.get("name"));
            facebookDetails.put("facebookId", payload.get("id"));
            if (pictureData != null) facebookDetails.put("picture", pictureData.get("data").get("url"));
            return facebookLogin(facebookDetails);
        } catch (Exception e) {
            e.printStackTrace();
            throw new BadCredentialsException("Invalid Token");
        }
    }

    private LoginResponseDTO facebookLogin(Map<String, Object> facebookDetails) {
        try {
            String email = (String) facebookDetails.get("email");
            User user = userService.findUserByEmail(email);
            return loginUser(user);
        } catch (Exception e) {
            CreateUserDto createUserDto = new CreateUserDto(
                    (String) facebookDetails.get("email"),
                    (String) facebookDetails.get("name"),
                    (String) facebookDetails.get("facebookId"),
                    true,
                    (String) facebookDetails.get("roleName"),
                    facebookDetails.get("picture") != null ? (String) facebookDetails.get("picture") : null
            );
            User newUser = userService.createUserOauth(createUserDto, Constants.RegistrationType.FACEBOOK);
            return loginUser(newUser);
        }
    }


    public LoginResponseDTO loginUser(User user) {
        String token = jwtService.createToken(user);
        return new LoginResponseDTO(token, jwtService.getJwtExpiration(), user);
    }

    public User sendConfirmEmail(SendConfirmEmailDTO sendConfirmEmailDTO) {
        User user = userService.findUserByEmail(sendConfirmEmailDTO.email());
        return userService.sendConfirmEmailToken(user);
    }

    public User confirmEmail(ConfirmEmailDTO confirmEmailDTO) {
        User user = userService.findUserByEmail(confirmEmailDTO.email());
        return userService.confirmEmail(user, confirmEmailDTO.token());
    }

    public User sendForgotPasswordMail(ForgotPasswordDTO forgotPasswordDTO) {
        User user = userService.findUserByEmail(forgotPasswordDTO.email());
        return userService.sendChangePasswordToken(user);
    }

    public User changePassword(ChangePasswordDTO changePasswordDTO) {
        User user = userService.findUserByEmail(changePasswordDTO.email());
        return userService.changePassword(user, changePasswordDTO.token(), changePasswordDTO.password());
    }
}
