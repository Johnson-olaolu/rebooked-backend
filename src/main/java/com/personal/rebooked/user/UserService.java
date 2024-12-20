package com.personal.rebooked.user;

import com.personal.rebooked.file.FileService;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.service.MailService;
import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.dto.UpdateProfileDTO;
import com.personal.rebooked.user.dto.UpdateUserDTO;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.repositories.ProfileRepository;
import com.personal.rebooked.user.repositories.UserRepository;
import com.personal.rebooked.user.role.RoleService;
import com.personal.rebooked.user.role.models.Role;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.Misc;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final MailService mailService;
    private  final FileService fileService;
    private  final ProfileRepository profileRepository;

    public User createUser(CreateUserDto createUserDto) {
        String roleName = createUserDto.role() != null ? createUserDto.role() : "user";
        Role role = roleService.getRoleByName(roleName);
        User user = new User();
        user.setFullName(createUserDto.fullName());
        user.setPassword(createUserDto.password());
        user.setEmail(createUserDto.email());
        user.setRole(role);
        user.setPassword(createUserDto.password());
        user.setRegistrationType(Constants.RegistrationType.EMAIL);
        User userWithEmail = sendConfirmEmailToken(user);
        Profile profile = new Profile();
        profile.setProfilePictureUrl(createUserDto.profilePictureUrl());
        Profile savedProfile = profileRepository.save(profile);
        user.setProfile(savedProfile);
        return  userRepository.save(userWithEmail);
    }

    public User createUserOauth(CreateUserDto createUserDto , Constants.RegistrationType registrationType) {
        String roleName = createUserDto.role() != null ? createUserDto.role() : "user";
        Role role = roleService.getRoleByName(roleName);
        User user = new User();
        user.setFullName(createUserDto.fullName());
        user.setPassword(createUserDto.password());
        user.setEmail(createUserDto.email());
        user.setRole(role);
        user.setRegistrationType(registrationType);
        user.setPassword(createUserDto.password());
        user.setEmailVerified(true);
        Profile profile = new Profile();
        profile.setProfilePictureUrl(createUserDto.profilePictureUrl());
        Profile savedProfile = profileRepository.save(profile);
        user.setProfile(savedProfile);
        return  userRepository.save(user);
    }


    public User sendConfirmEmailToken ( User user) {
        String confirmEmailToken = Misc.getToken();
        LocalDate dateAfter2weeks = LocalDate.now().plusWeeks(2);
        user.setConfirmEmailToken(confirmEmailToken);
        user.setConfirmEmailTokenTTL(dateAfter2weeks);
        mailService.sendConfirmEmailMail(user);
        return userRepository.save(user);
    }

    public User confirmEmail (User user, String token){
        if(user.getConfirmEmailToken().equals(token) && user.getConfirmEmailTokenTTL().isAfter(LocalDate.now())){
            user.setConfirmEmailToken(null);
            user.setConfirmEmailTokenTTL(null);
            user.setEmailVerified(true);
            return userRepository.save(user);
        }else {
            throw  new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
        }
    }

    public User sendChangePasswordToken ( User user) {
        String changePasswordToken = Misc.getToken();
        LocalDate dateAfter2weeks = LocalDate.now().plusWeeks(2);
        user.setChangePasswordToken(changePasswordToken);
        user.setChangePasswordTokenTTL(dateAfter2weeks);
        mailService.sendChangepasswordMail(user);
        return userRepository.save(user);
    }

    public User changePassword (User user, String token, String password){
        if(user.getChangePasswordToken().equals(token) && user.getChangePasswordTokenTTL().isAfter(LocalDate.now())){
            user.setChangePasswordToken(null);
            user.setChangePasswordTokenTTL(null);
            user.setPassword(password);
            return userRepository.save(user);
        }else {
            throw  new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid token");
        }
    }

    public User findUserById(String id) {
        return  userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public User findUserByEmail(String email) {
        return  userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<User> getAllUsers () {
        return userRepository.findAll();
    }

    public  User updateUser( String userId, UpdateUserDTO updateUserDTO) {
        User user = findUserById(userId);

        Field[] dtoFields = UpdateUserDTO.class.getDeclaredFields();
        Field[] entityFields = User.class.getDeclaredFields();

        for (Field dtoField : dtoFields) {
            dtoField.setAccessible(true); // Access private fields
            try {
                Object value = dtoField.get(updateUserDTO); // Get value of the field in the DTO
                if (value != null) {
                    for (Field entityField : entityFields) {
                        if (entityField.getName().equals(dtoField.getName())) {
                            entityField.setAccessible(true); // Access private entity field
                            entityField.set(user, value); // Set the value
                        }
                    }
                }
            }catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }
        return userRepository.save(user);
    }

    public User updateUserProfile (String userId, UpdateProfileDTO updateProfileDTO) {
        User user = findUserById(userId);
        Profile profile = user.getProfile();
        Field[] dtoFields = UpdateProfileDTO.class.getDeclaredFields();
        Field[] entityFields = Profile.class.getDeclaredFields();

        for (Field dtoField : dtoFields) {
            dtoField.setAccessible(true);
            try {
                Object value = dtoField.get(updateProfileDTO);
                if(dtoField.getName().equals("profilePictureId")) {
                    if(value != null) {
                        File pictureFile = fileService.getFileById((String) value);
                        profile.setProfilePicture(pictureFile);
                        profile.setProfilePictureUrl(pictureFile.getUrl());
                    }

                }else {
                    if (value != null) {
                        for (Field entityField : entityFields) {
                            if (entityField.getName().equals(dtoField.getName())) {
                                entityField.setAccessible(true); // Access private entity field
                                entityField.set(profile, value); // Set the value
                            }
                        }
                    }
                }

            }catch (Exception e) {
                System.err.println(e.getStackTrace());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
            }
        }
        System.out.println(profile);
        Profile updatedprofile =  profileRepository.save(profile);
        System.out.println(updatedprofile);
        user.setProfile(updatedprofile);
       user.setOnboarded(true);
        return userRepository.save(user);
    }


    public  void deleteUser(String userId) {
        User user = findUserById(userId);
        user.setDeleted(true);
        userRepository.save(user);
    }

}
