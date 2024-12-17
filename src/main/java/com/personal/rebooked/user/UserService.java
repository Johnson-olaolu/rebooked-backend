package com.personal.rebooked.user;

import com.personal.rebooked.service.MailService;
import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.dto.UpdateUserDTO;
import com.personal.rebooked.user.models.User;
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

    public User createUser(CreateUserDto createUserDto) {
        Role role = roleService.getRoleByName(createUserDto.role());
        User user = new User();
        user.setFullName(createUserDto.fullName());
        user.setPassword(createUserDto.password());
        user.setEmail(createUserDto.email());
        user.setRole(role);
        user.setPassword(createUserDto.password());
        User userWithEmail = sendConfirmEmailToken(user);

        return  userRepository.save(userWithEmail);
    }

    public User createUser(CreateUserDto createUserDto , Constants.RegistrationType registrationType) {
        Role role = roleService.getRoleByName(createUserDto.role());
        User user = new User();
        user.setFullName(createUserDto.fullName());
        user.setPassword(createUserDto.password());
        user.setEmail(createUserDto.email());
        user.setRole(role);
        user.setRegistrationType(registrationType);
        user.setPassword(createUserDto.password());
        User userWithEmail = sendConfirmEmailToken(user);

        return  userRepository.save(userWithEmail);
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
        user.setConfirmEmailTokenTTL(dateAfter2weeks);
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

    public  void deleteUser(String userId) {
        User user = findUserById(userId);
        user.setDeleted(true);
        userRepository.save(user);
    }

}
