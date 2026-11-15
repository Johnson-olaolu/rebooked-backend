package com.personal.rebooked.user;

import org.springframework.data.mongodb.core.query.Criteria;

import org.springframework.data.mongodb.core.query.Query;

import com.personal.rebooked.user.dto.PublicUserDTO;

import com.personal.rebooked.book.models.BookLocation;
import org.springframework.data.mongodb.core.query.Update;
import java.util.Objects;

import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.file.FileService;
import com.personal.rebooked.file.models.File;
import com.personal.rebooked.service.MailService;
import com.personal.rebooked.user.dto.AddressDTO;
import com.personal.rebooked.user.dto.CreateUserDto;
import com.personal.rebooked.user.dto.UpdateProfileDTO;
import com.personal.rebooked.user.dto.UpdateUserDTO;
import com.personal.rebooked.user.models.Address;
import com.personal.rebooked.user.models.Profile;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.repositories.AddressRepository;
import com.personal.rebooked.user.repositories.ProfileRepository;
import com.personal.rebooked.user.repositories.UserRepository;
import com.personal.rebooked.utils.Constants;
import com.personal.rebooked.utils.Misc;
import com.personal.rebooked.book.models.Book;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final MailService mailService;
    private final FileService fileService;
    private final ProfileRepository profileRepository;
    private final MongoTemplate mongoTemplate;

    public User createUser(CreateUserDto createUserDto) {
        User user = new User();
        user.setFullName(createUserDto.fullName());
        user.setPassword(createUserDto.password());
        user.setEmail(createUserDto.email());
        user.setRole(createUserDto.roleName());
        user.setPassword(createUserDto.password());
        user.setRegistrationType(Constants.RegistrationType.EMAIL);
        User userWithEmail = sendConfirmEmailToken(user);
        Profile profile = new Profile();
        profile.setProfilePictureUrl(createUserDto.profilePictureUrl());
        Profile savedProfile = profileRepository.save(profile);
        user.setProfile(savedProfile);
        return userRepository.save(userWithEmail);
    }

    public User createUserOauth(CreateUserDto createUserDto, Constants.RegistrationType registrationType) {
        User user = new User();
        user.setFullName(createUserDto.fullName());
        user.setPassword(createUserDto.password());
        user.setEmail(createUserDto.email());
        user.setRole(createUserDto.roleName());
        user.setRegistrationType(registrationType);
        user.setEmailVerified(true);
        Profile profile = new Profile();
        profile.setProfilePictureUrl(createUserDto.profilePictureUrl());
        Profile savedProfile = profileRepository.save(profile);
        user.setProfile(savedProfile);
        return userRepository.save(user);
    }

    public User sendConfirmEmailToken(User user) {
        String confirmEmailToken = Misc.getToken();
        user.setConfirmEmailToken(confirmEmailToken);
        user.setConfirmEmailTokenTTL(LocalDateTime.now().plusMinutes(Constants.EMAIL_CODE_TTL_MINUTES));
        mailService.sendConfirmEmailMail(user);
        return userRepository.save(user);
    }

    public User confirmEmail(User user, String token) {
        if (isValidCode(user.getConfirmEmailToken(), user.getConfirmEmailTokenTTL(), token)) {
            user.setConfirmEmailToken(null);
            user.setConfirmEmailTokenTTL(null);
            user.setEmailVerified(true);
            return userRepository.save(user);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
        }
    }

    public User sendChangePasswordToken(User user) {
        String changePasswordToken = Misc.getToken();
        user.setChangePasswordToken(changePasswordToken);
        user.setChangePasswordTokenTTL(LocalDateTime.now().plusMinutes(Constants.EMAIL_CODE_TTL_MINUTES));
        mailService.sendChangepasswordMail(user);
        return userRepository.save(user);
    }

    public User changePassword(User user, String token, String password) {
        if (isValidCode(user.getChangePasswordToken(), user.getChangePasswordTokenTTL(), token)) {
            user.setChangePasswordToken(null);
            user.setChangePasswordTokenTTL(null);
            user.setPassword(password);
            return userRepository.save(user);
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired code");
        }
    }

    private boolean isValidCode(String storedCode, LocalDateTime expiresAt, String submittedCode) {
        return storedCode != null
                && expiresAt != null
                && storedCode.equals(submittedCode)
                && expiresAt.isAfter(LocalDateTime.now());
    }

    public User findUserById(String id) {
        return userRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(String userId, UpdateUserDTO updateUserDTO) {
        User user = findUserById(userId);
        // Set fields explicitly so new User fields are never writable by accident. Email changes are not allowed here.
        if (updateUserDTO.fullName() != null) {
            user.setFullName(updateUserDTO.fullName().trim());
        }
        return userRepository.save(user);
    }

    /** Seller page: the public summary plus available (ACTIVE) and sold book counts. */
    public PublicUserDTO publicProfile(User user) {
        ObjectId id = new ObjectId(user.getId());
        long available = mongoTemplate.count(new Query(Criteria.where("user.$id").is(id)
                .and("status").is(Constants.BookStatus.ACTIVE)), Book.class);
        long sold = mongoTemplate.count(new Query(Criteria.where("user.$id").is(id)
                .and("status").is(Constants.BookStatus.SOLD)), Book.class);
        return PublicUserDTO.from(user).withCounts(available, sold);
    }

    public User updateUserProfile(String userId, UpdateProfileDTO dto) {
        User user = findUserById(userId);
        Profile profile = user.getProfile() != null ? user.getProfile() : new Profile();

        Map<String, String> errors = new LinkedHashMap<>();

        // 1. Merge only the fields that were sent. null = leave unchanged, "" = clear.
        if (dto.userName() != null) profile.setUserName(blankToNull(dto.userName()));
        if (dto.bio() != null) profile.setBio(blankToNull(dto.bio()));
        if (dto.contactEmail() != null) profile.setContactEmail(blankToNull(dto.contactEmail()));
        if (dto.contactPhone() != null) {
            String phone = blankToNull(dto.contactPhone());
            if (phone == null) {
                profile.setContactPhone(null);
            } else {
                String normalised = Misc.normalizeNigerianPhone(phone);
                if (normalised == null) {
                    errors.put("contactPhone", "Enter a valid Nigerian phone number");
                } else {
                    profile.setContactPhone(normalised);
                }
            }
        }

        File previousPicture = profile.getProfilePicture();
        String previousState = profile.getAddress() == null ? null : profile.getAddress().getState();
        String previousCity = profile.getAddress() == null ? null : profile.getAddress().getCity();
        if (dto.profilePictureId() != null) {
            if (dto.profilePictureId().isBlank()) {
                // "" removes the photo
                profile.setProfilePicture(null);
                profile.setProfilePictureUrl(null);
            } else {
                File pictureFile = fileService.getFileById(dto.profilePictureId().trim());
                if (!FileService.isOwnedBy(pictureFile, userId)) {
                    errors.put("profilePictureId", "You can only use photos you uploaded");
                } else {
                    profile.setProfilePicture(pictureFile);
                    profile.setProfilePictureUrl(pictureFile.getUrl());
                }
            }
        }

        if (dto.address() != null) {
            Address address = profile.getAddress() != null ? profile.getAddress() : new Address();
            mergeAddress(address, dto.address(), errors);
            profile.setAddress(address);
        }

        // 2. Validate the merged result; nothing is saved if anything is wrong
        boolean incomplete = false;
        if (isBlank(profile.getUserName())) {
            errors.put("userName", "Display name is required");
            incomplete = true;
        } else if (profile.getUserName().length() < 2 || profile.getUserName().length() > 40) {
            errors.put("userName", "Display name must be 2 to 40 characters");
        }
        if (profile.getBio() != null && profile.getBio().length() > 300) {
            errors.put("bio", "Bio must be at most 300 characters");
        }
        if (profile.getContactEmail() != null && !Misc.isValidEmail(profile.getContactEmail())) {
            errors.put("contactEmail", "Enter a valid email address");
        }
        incomplete |= validateAddress(profile.getAddress(), errors);
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, incomplete ? "Profile incomplete" : "Validation failed", errors);
        }

        // 3. Only the server marks onboarding as complete
        profile.setOnboardingCompleted(true);
        boolean locationChanged = !Objects.equals(previousState, profile.getAddress() == null ? null : profile.getAddress().getState())
                || !Objects.equals(previousCity, profile.getAddress() == null ? null : profile.getAddress().getCity());
        user.setProfile(profileRepository.save(profile));
        User saved = userRepository.save(user);

        // Books carry a copy of the seller's location for shop filtering
        if (locationChanged) {
            Address a = profile.getAddress();
            mongoTemplate.updateMulti(
                    new BasicQuery(new org.bson.Document("user.$id", new ObjectId(userId))),
                    new Update().set("location", a == null ? null : new BookLocation(a.getState(), a.getCity())),
                    Book.class);
        }

        // 4. Clean up a replaced or removed photo once the new profile is saved
        File currentPicture = profile.getProfilePicture();
        if (previousPicture != null
                && (currentPicture == null || !previousPicture.getId().equals(currentPicture.getId()))) {
            fileService.deleteIfUnused(previousPicture, userId);
        }
        return saved;
    }

    private void mergeAddress(Address address, AddressDTO incoming, Map<String, String> errors) {
        if (incoming.country() != null) address.setCountry(incoming.country().trim().toUpperCase(java.util.Locale.ROOT));
        if (incoming.state() != null) address.setState(incoming.state().trim());
        if (incoming.city() != null) address.setCity(incoming.city().trim());
        // Optional fields: null = not sent (keep), "" = clear
        if (incoming.addressLine() != null) address.setAddressLine(blankToNull(incoming.addressLine()));
        if (incoming.formattedAddress() != null) address.setFormattedAddress(blankToNull(incoming.formattedAddress()));

        boolean hasLat = incoming.latitude() != null;
        boolean hasLng = incoming.longitude() != null;
        if (hasLat != hasLng) {
            errors.put("address.location", "Send latitude and longitude together");
            return;
        }
        if (hasLat) {
            // GeoJsonPoint takes (x = longitude, y = latitude)
            address.setLocation(new GeoJsonPoint(incoming.longitude(), incoming.latitude()));
            if (incoming.placeId() != null) address.setPlaceId(blankToNull(incoming.placeId()));
        } else if (incoming.placeId() != null) {
            // A placeId (even "") without coordinates means the Google data is stale: drop it rather than keep old coordinates
            address.setLocation(null);
            address.setPlaceId(null);
        }
    }

    /** Adds address errors; returns true if a required field is missing. */
    private boolean validateAddress(Address address, Map<String, String> errors) {
        if (address == null) {
            errors.put("address.state", "State is required");
            errors.put("address.city", "City is required");
            return true;
        }
        boolean incomplete = false;
        if (isBlank(address.getCountry())) {
            address.setCountry(Constants.DEFAULT_COUNTRY);
        } else if (!Constants.DEFAULT_COUNTRY.equals(address.getCountry())) {
            errors.put("address.country", "Only Nigeria (NG) is supported");
        }

        if (isBlank(address.getState())) {
            errors.put("address.state", "State is required");
            incomplete = true;
        } else {
            String state = Misc.normalizeNigerianState(address.getState());
            if (state == null) {
                errors.put("address.state", "Select a valid Nigerian state");
            } else {
                address.setState(state);
            }
        }

        if (isBlank(address.getCity())) {
            errors.put("address.city", "City is required");
            incomplete = true;
        }

        GeoJsonPoint location = address.getLocation();
        if (location != null && !errors.containsKey("address.location")) {
            double lat = location.getY();
            double lng = location.getX();
            if (lat < Constants.NG_MIN_LAT || lat > Constants.NG_MAX_LAT
                    || lng < Constants.NG_MIN_LNG || lng > Constants.NG_MAX_LNG) {
                errors.put("address.location", "Location must be in Nigeria");
            }
        }
        return incomplete;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public void deleteUser(String userId) {
        User user = findUserById(userId);
        user.setDeleted(true);
        userRepository.save(user);
    }

}
