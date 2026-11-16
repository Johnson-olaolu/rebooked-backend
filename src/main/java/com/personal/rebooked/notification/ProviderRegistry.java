package com.personal.rebooked.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Every {@link NotificationProvider} bean, by channel. */
@Slf4j
@Component
public class ProviderRegistry {
    private final Map<NotificationChannel, NotificationProvider> providers = new EnumMap<>(NotificationChannel.class);
    private final Set<NotificationChannel> warned = ConcurrentHashMap.newKeySet();

    public ProviderRegistry(List<NotificationProvider> beans) {
        for (NotificationProvider provider : beans) {
            NotificationProvider previous = providers.put(provider.channel(), provider);
            if (previous != null) {
                throw new IllegalStateException("Two notification providers for " + provider.channel() + ": "
                        + previous.getClass().getSimpleName() + " and " + provider.getClass().getSimpleName());
            }
        }
        log.info("Notification providers: {}", providers.keySet());
    }

    public Optional<NotificationProvider> get(NotificationChannel channel) {
        NotificationProvider provider = providers.get(channel);
        if (provider == null && warned.add(channel)) log.warn("No notification provider for channel {}", channel);
        return Optional.ofNullable(provider);
    }
}
