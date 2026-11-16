package com.personal.rebooked.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.personal.rebooked.auth.dto.*;
import com.personal.rebooked.auth.jwt.JwtService;
import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.service.RedisService;
import com.personal.rebooked.service.FacebookVerificationService;
import com.personal.rebooked.service.GoogleVerificationService;
import com.personal.rebooked.user.UserService;
import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.utils.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final GoogleVerificationService googleVerificationService;
    private final FacebookVerificationService facebookVerificationService;
    private final RefreshTokenService refreshTokenService;
    private final RedisService redisService;
    private final BCryptPasswordEncoder passwordEncoder;

    private static final String CONFIRM_EMAIL_LIMIT = "confirm-email";
    private static final String FORGOT_PASSWORD_LIMIT = "forgot-password";
    private static final String CHANGE_PASSWORD_LIMIT = "ratelimit:change-password:";
    private static final int CHANGE_PASSWORD_MAX_ATTEMPTS = 5;
    private static final long CHANGE_PASSWORD_WINDOW_MINUTES = 15;


    public LoginResponseDTO register(CreateUserDto createUserDto) {
        User user = userService.createUser(createUserDto);
        // A code was just sent on register; start the resend cooldown
        startCooldown(CONFIRM_EMAIL_LIMIT, user.getEmail());
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
            googleDetails.put("roleName", googleLoginDTO.role());
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
                    (String) googleDetails.get("roleName"),
                    true,
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
            facebookDetails.put("roleName", facebookLoginDTO.role());
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
                    (String) facebookDetails.get("roleName"),
                    true,
                    facebookDetails.get("picture") != null ? (String) facebookDetails.get("picture") : null
            );
            User newUser = userService.createUserOauth(createUserDto, Constants.RegistrationType.FACEBOOK);
            return loginUser(newUser);
        }
    }


    public LoginResponseDTO loginUser(User user) {
        String token = jwtService.createToken(user);
        String refreshToken = refreshTokenService.issue(user);
        return new LoginResponseDTO(token, jwtService.getJwtExpiration(), refreshToken, user);
    }

    public LoginResponseDTO refresh(RefreshTokenDTO refreshTokenDTO) {
        User user = refreshTokenService.consume(refreshTokenDTO.refreshToken());
        return loginUser(user);
    }

    public void logout(RefreshTokenDTO refreshTokenDTO) {
        refreshTokenService.revoke(refreshTokenDTO.refreshToken());
    }

    private void checkCooldown(String purpose, String email) {
        if (redisService.exists(cooldownKey(purpose, email))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code");
        }
    }

    private void startCooldown(String purpose, String email) {
        redisService.save(cooldownKey(purpose, email), "1",
                Constants.EMAIL_CODE_RESEND_COOLDOWN_SECONDS, TimeUnit.SECONDS);
    }

    private static String cooldownKey(String purpose, String email) {
        return "ratelimit:" + purpose + ":" + email.trim().toLowerCase();
    }

    public User sendConfirmEmail(SendConfirmEmailDTO sendConfirmEmailDTO) {
        checkCooldown(CONFIRM_EMAIL_LIMIT, sendConfirmEmailDTO.email());
        User user = userService.findUserByEmail(sendConfirmEmailDTO.email());
        startCooldown(CONFIRM_EMAIL_LIMIT, sendConfirmEmailDTO.email());
        return userService.sendConfirmEmailToken(user);
    }

    public User confirmEmail(ConfirmEmailDTO confirmEmailDTO) {
        User user = userService.findUserByEmail(confirmEmailDTO.email());
        return userService.confirmEmail(user, confirmEmailDTO.token());
    }

    /**
     * Sends the reset email. An unknown email gets the same 200 (and the same cooldown),
     * so the endpoint doesn't reveal which emails are registered.
     */
    public void sendForgotPasswordMail(ForgotPasswordDTO forgotPasswordDTO) {
        checkCooldown(FORGOT_PASSWORD_LIMIT, forgotPasswordDTO.email());
        startCooldown(FORGOT_PASSWORD_LIMIT, forgotPasswordDTO.email());
        userService.findByEmail(forgotPasswordDTO.email())
                .ifPresent(userService::sendChangePasswordToken);
    }

    /** Sets a new password from the reset link, then signs the user out of every device. */
    public User changePassword(ChangePasswordDTO changePasswordDTO) {
        // An unknown email gets the same answer as a wrong code
        User user = userService.findByEmail(changePasswordDTO.email())
                .orElseThrow(UserService::invalidCode);
        User saved = userService.changePassword(user, changePasswordDTO.token(), changePasswordDTO.password());
        refreshTokenService.revokeAll(saved.getId());
        return saved;
    }

    /**
     * Settings → Password for a logged-in user. Wrong current passwords are limited to
     * 5 every 15 minutes per user. On success every other device is signed out and this
     * session gets a new token pair.
     */
    public LoginResponseDTO updatePassword(User currentUser, UpdatePasswordDTO dto) {
        User user = userService.findUserById(currentUser.getId());
        if (user.getRegistrationType() != Constants.RegistrationType.EMAIL) {
            String provider = capitalize(user.getRegistrationType().name());
            throw new ApiException(HttpStatus.BAD_REQUEST, "You sign in with " + provider,
                    Map.of("currentPassword", "You sign in with " + provider + ", so there's no password to change"));
        }

        String attemptsKey = CHANGE_PASSWORD_LIMIT + user.getId();
        Object attempts = redisService.get(attemptsKey);
        if (attempts != null && Long.parseLong(attempts.toString()) >= CHANGE_PASSWORD_MAX_ATTEMPTS) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong attempts. Try again in 15 minutes, or reset your password by email.");
        }
        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            redisService.increment(attemptsKey, CHANGE_PASSWORD_WINDOW_MINUTES, TimeUnit.MINUTES);
            throw new ApiException(HttpStatus.BAD_REQUEST, "That's not your current password",
                    Map.of("currentPassword", "That's not your current password"));
        }
        if (passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose a password you're not using now",
                    Map.of("newPassword", "Choose a password you're not using now"));
        }

        redisService.delete(attemptsKey);
        User saved = userService.setPassword(user, dto.newPassword());
        refreshTokenService.revokeAll(saved.getId());
        return loginUser(saved);
    }

    private static String capitalize(String value) {
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
