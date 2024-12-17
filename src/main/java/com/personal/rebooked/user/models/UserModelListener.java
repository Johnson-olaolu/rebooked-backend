package com.personal.rebooked.user.models;

import com.personal.rebooked.utils.Misc;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@RequiredArgsConstructor
@Configuration
public class UserModelListener extends AbstractMongoEventListener<User> {

    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void onBeforeConvert(BeforeConvertEvent<User> event) {
        super.onBeforeConvert(event);
        User user = event.getSource();
        if (user.getPassword() != null && !Misc.isBcryptHash(user.getPassword())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            System.out.println(user.toString());
        }
    }
}
