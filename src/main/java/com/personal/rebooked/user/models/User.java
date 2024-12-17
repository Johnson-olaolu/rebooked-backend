package com.personal.rebooked.user.models;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.personal.rebooked.user.role.models.Role;
import com.personal.rebooked.utils.Constants;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Data
@Document("user")
public class User implements UserDetails {
    @Id
    private String id;

    @NotNull()
    @Indexed(unique = true)
    private String email;

    @JsonIgnore
    @NotNull()
    private String password;

    @NotNull()
    private String fullName;

    @NotNull
    private Constants.RegistrationType registrationType = Constants.RegistrationType.EMAIL;

    @DBRef
    private Role role;

    @NotNull()
    private boolean isEmailVerified = false;

    @JsonIgnore
    private String confirmEmailToken ;

    @JsonIgnore
    private LocalDate confirmEmailTokenTTL;

    @JsonIgnore
    private String changePasswordToken;

    @JsonIgnore
    private LocalDate changePasswordTokenTTL;

    private boolean isDeleted = false;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getName()));
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return !isDeleted;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}

