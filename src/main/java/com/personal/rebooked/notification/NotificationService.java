package com.personal.rebooked.notification;

import com.personal.rebooked.exception.ApiException;
import com.personal.rebooked.notification.models.NotificationDelivery;
import com.personal.rebooked.notification.providers.SkipDelivery;
import com.personal.rebooked.user.models.User;
import com.personal.rebooked.user.repositories.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Set;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * The single way to send a notification. It resolves the channels (the request's, or the type's defaults),
 * drops the ones the recipient turned off, and hands the request to each channel's provider.
 * Every attempt is recorded in notification_deliveries, which also stops a dedupeKey being delivered twice.
 * Mandatory (account) notifications are delivered before send returns and throw on failure;
 * the rest are delivered in the background and only log failures.
 */
@Slf4j
@Service
public class NotificationService {
    private final ProviderRegistry providers;
    private final PreferenceService preferences;
    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final TaskExecutor executor;

    public NotificationService(ProviderRegistry providers, PreferenceService preferences, UserRepository userRepository,
                               MongoTemplate mongoTemplate, @Qualifier("notificationExecutor") TaskExecutor executor) {
        this.providers = providers;
        this.preferences = preferences;
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.executor = executor;
    }

    @PostConstruct
    void ensureIndexes() {
        mongoTemplate.indexOps(NotificationDelivery.class).ensureIndex(new Index()
                .on("channel", Sort.Direction.ASC).on("dedupeKey", Sort.Direction.ASC)
                .unique().named("channel_dedupe_unique")
                .partial(PartialIndexFilter.of(where("dedupeKey").exists(true))));
    }

    public void send(NotificationRequest request) {
        if (request == null || request.type() == null || request.recipientId() == null) return;
        // Never notify the person whose own action caused it
        if (request.actor() != null && request.recipientId().equals(request.actor().id())) return;

        if (request.type().mandatory()) {
            deliver(request);
        } else {
            executor.execute(() -> {
                try {
                    deliver(request);
                } catch (Exception e) {
                    log.error("Notification {} to {} failed", request.type(), request.recipientId(), e);
                }
            });
        }
    }

    private void deliver(NotificationRequest request) {
        User recipient = userRepository.findById(request.recipientId()).orElse(null);
        if (recipient == null) return; // deleted account
        Set<NotificationChannel> channels = request.channels() != null ? request.channels() : request.type().defaultChannels();

        for (NotificationChannel channel : channels) {
            NotificationProvider provider = providers.get(channel).orElse(null);
            if (provider == null || !provider.supports(request.type())) continue;

            NotificationDelivery delivery = claim(request, channel);
            if (delivery == null) continue; // already delivered under this dedupeKey

            if (!preferences.isEnabled(recipient.getId(), request.type(), channel)) {
                finish(delivery, NotificationDelivery.Status.SKIPPED, "Turned off by the user");
                continue;
            }
            try {
                provider.deliver(request, recipient);
                finish(delivery, NotificationDelivery.Status.SENT, null);
            } catch (SkipDelivery e) {
                finish(delivery, NotificationDelivery.Status.SKIPPED, e.getMessage());
            } catch (Exception e) {
                finish(delivery, NotificationDelivery.Status.FAILED, e.getMessage());
                log.warn("{} {} to {} failed: {}", channel, request.type(), recipient.getId(), e.getMessage());
                if (request.type().mandatory()) {
                    throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "We couldn't send the email. Please try again in a moment.");
                }
            }
        }
    }

    /** Records the attempt; null if this dedupeKey was already used on this channel. */
    private NotificationDelivery claim(NotificationRequest request, NotificationChannel channel) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setUserId(request.recipientId());
        delivery.setType(request.type());
        delivery.setChannel(channel);
        delivery.setDedupeKey(request.dedupeKey());
        delivery.setStatus(NotificationDelivery.Status.PENDING);
        delivery.setCreatedAt(new Date());
        delivery.setUpdatedAt(delivery.getCreatedAt());
        try {
            return mongoTemplate.insert(delivery);
        } catch (DuplicateKeyException e) {
            return null;
        }
    }

    private void finish(NotificationDelivery delivery, NotificationDelivery.Status status, String error) {
        Update update = new Update().set("status", status).set("updatedAt", new Date());
        if (error != null) update.set("error", error.length() > 500 ? error.substring(0, 500) : error);
        mongoTemplate.updateFirst(query(where("_id").is(delivery.getId())), update, NotificationDelivery.class);
    }
}
