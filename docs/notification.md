# Notifications

Every notification Rebooked sends goes through **one call**:

```java
notificationService.send(notifications.bookEnquiry(sellerId, buyerId, book, conversationId, firstMessage));
```

`Notifications` has one factory method per notification type. `NotificationService.send` then does the rest:

1. **Picks the channels.** It uses the request's own `channels` if set, otherwise the type's defaults (`NotificationType`).
2. **Never notifies whoever caused it.** If the recipient is the actor, nothing is sent.
3. **Respects the recipient's settings.** Channels the user turned off are skipped. Mandatory (account) types can't be turned off.
4. **Hands the request to each channel's provider** (`NotificationProvider`), and records every attempt in `notification_deliveries` as `SENT`, `SKIPPED` or `FAILED`. Records are kept for 90 days.
5. **Dedupes.** A request with a `dedupeKey` is delivered at most once per channel, however many times a job runs.
6. **Mandatory types are synchronous.** Account emails are delivered before `send` returns, and a failure is reported to the caller. Everything else is delivered in the background (`notificationExecutor`), and failures are only logged.

Code: `src/main/java/com/personal/rebooked/notification/`.

## Channels and providers

| Channel | Provider | What it does |
|---|---|---|
| `IN_APP` | `providers/InAppProvider` | Stores the notification in `notifications` and pushes `{type: "notification"}` on `/user/queue/events`. Shown on the notifications page and the bell. |
| `EMAIL` | `providers/EmailProvider` | Renders `EmailContent` (subject and body per type) into `templates/mail/notification.html` and sends it through SMTP. The account emails keep their own templates. Only verified addresses get anything other than account emails. |
| `PUSH` | — | Planned |
| `WHATSAPP` | — | Planned |

### Adding a channel (e.g. push)
1. **Add the value** to `NotificationChannel` (`PUSH`).
2. **Write a provider** as a Spring bean implementing `NotificationProvider`:
   - `channel()` returns `PUSH`.
   - `supports(type)` says which types it can render.
   - `deliver(request, recipient)` sends it. Throw `SkipDelivery` to record it as SKIPPED (for example, no device token), or any other exception to record it as FAILED.
3. **Pick the types it goes out for**, by adding `PUSH` to their default channels in `NotificationType`.
4. **Update the table below.**

`ProviderRegistry` picks the bean up automatically. Senders don't change.

## Settings and unsubscribing
- **`GET /notification/preferences`** lists every type a user can turn off, with each of its channels on or off.
- **`PATCH /notification/preferences`** takes `{ "type": "BOOK_ENQUIRY", "channel": "EMAIL", "enabled": false }`.
- **Unsubscribe link:** every non-account email has a one-click link to `<CLIENT_URL>/unsubscribe?token=…`. The frontend posts the token to the public `POST /notification/unsubscribe`, which turns off that type's email.
- **Account emails** (marked 🔒 below) can't be turned off.

## Catalog

✅ = sent on that channel by default. 🔒 = mandatory: always sent and can't be turned off.

### Account
| Notification | Description | Trigger | Recipient | In-app | Email | Push | WhatsApp | Opt-out |
|---|---|---|---|---|---|---|---|---|
| `EMAIL_VERIFICATION` | 6-digit code to confirm the email address | Sign-up, or "resend code" | The new user | | ✅ | | | 🔒 |
| `PASSWORD_RESET` | Link to choose a new password | Forgot password | The user | | ✅ | | | 🔒 |
| `PASSWORD_CHANGED` | Security notice that the password changed | Password reset completed, or changed from Settings (`POST /auth/change-password`) | The user | | ✅ | | | 🔒 |
| `WELCOME` | Welcome, with a link to finish the profile | Email confirmed | The user | ✅ | ✅ | | | Yes |
| `ONBOARDING_REMINDER` 🧠 | Nudge to add a display name and city | Daily job: verified, signed up 1–14 days ago, onboarding not finished. Once per user | The user | | ✅ | | | Yes |

### Chat
| Notification | Description | Trigger | Recipient | In-app | Email | Push | WhatsApp | Opt-out |
|---|---|---|---|---|---|---|---|---|
| `BOOK_ENQUIRY` | A buyer asked about one of your books (with their first message) | A book is attached to a conversation (`/app/conversation.start`), including a second book in an existing chat | Seller | ✅ | ✅ | | | Yes |
| `NEW_MESSAGES` | New messages while you were away, grouped per conversation ("Tunde sent 3 messages") | A TEXT message arrives while the recipient has no open socket. Merges into the open notification for that conversation, and is read automatically when the conversation is read | Recipient | ✅ | | | | Yes |
| `UNREPLIED_MESSAGE` 🧠 | "Tunde is waiting for your reply", with the message and a Reply button | Every 5 min: the last message in a conversation is TEXT, is 30 min to 24 h old, and is still unread by its recipient. Once per message | Whoever hasn't replied (seller or buyer) | | ✅ | | | Yes |

### Sales
| Notification | Description | Trigger | Recipient | In-app | Email | Push | WhatsApp | Opt-out |
|---|---|---|---|---|---|---|---|---|
| `SOLD_TO_YOU` | The seller marked the book as sold to you | `/app/book.sold`, or "Mark as sold" on My shelf naming you as the buyer | The buyer in that chat | ✅ | ✅ | | | Yes |
| `SOLD_ELSEWHERE` | A book you asked about was sold to someone else | The book is marked as sold to another buyer, from the chat or from My shelf | Every other buyer with that book in a chat | ✅ | | | | Yes |
| `BOOK_UNAVAILABLE` | A book you asked about was archived or removed | Status set to `INACTIVE`, or the book is deleted | Buyers with that book open in a chat | ✅ | | | | Yes |
| `BOOK_AVAILABLE` | A book you asked about is back on the shelf | Status set back to `ACTIVE` | Buyers whose book was `SOLD_ELSEWHERE` or `UNAVAILABLE` | ✅ | ✅ | | | Yes |
| `RATE_SELLER` | "How was buying X from Tola?", linking to the rating form (`/dashboard/bought/<bookId>`) | The book is marked as sold to the buyer, from the chat or from My shelf | The buyer | ✅ | ✅ | | | Yes |
| `RATE_SELLER` reminder 🧠 | "You haven't rated Tola yet" | Daily job: sold to them 3–10 days ago and still not rated. Once per sale | The buyer | | ✅ | | | Yes (same setting as `RATE_SELLER`) |
| `MARK_AS_SOLD_NUDGE` 🧠 | "Did you sell X?" so stale listings get cleaned up | Daily job: an `ACTIVE` book whose chat went quiet 7–60 days ago. At most once a month per book | Seller | ✅ | ✅ | | | Yes |

### Listings
| Notification | Description | Trigger | Recipient | In-app | Email | Push | WhatsApp | Opt-out |
|---|---|---|---|---|---|---|---|---|
| `STALE_LISTING` 🧠 | No enquiries in 30 days, with tips (photo, description, price) | Daily job: `ACTIVE`, listed 30+ days ago, no `BOOK_ENQUIRY` in the last 30 days. At most once a month per book | Seller | ✅ | ✅ | | | Yes |
| `DRAFT_REMINDER` 🧠 | Your draft is waiting to be published | Daily job: a `DRAFT` not updated for 3 days. Once per draft | Seller | ✅ | ✅ | | | Yes |
| `VIEWS_MILESTONE` 🧠 | "Your book has had 100 views" | A book's view count reaches 50, 100, 500 or 1000. Checked when views are flushed from Redis to Mongo (every 5 min, `views.flush-interval`), so it can arrive up to 5 minutes late. Once per milestone | Seller | ✅ | | | | Yes |

🧠 = smart notification: worked out from behaviour (time passing, silence, milestones) rather than sent straight away by an action.

## Smart notification timings
All of these are in `NotificationJobs`. Each carries a `dedupeKey`, so re-running a job never sends twice.

| Job | Schedule | Dedupe key |
|---|---|---|
| Unreplied messages | Every 5 min (`notifications.unreplied-check-ms`) | `UNREPLIED:<messageId>` |
| Stale listings | Daily, 09:00 Africa/Lagos (`notifications.daily-cron`) | `STALE:<bookId>:<yyyy-MM>` |
| Draft reminders | Daily | `DRAFT:<bookId>` |
| Mark-as-sold nudges | Daily | `NUDGE:<bookId>:<yyyy-MM>` |
| Onboarding reminders | Daily | `ONBOARD:<userId>` |
| Rate-seller reminders | Daily | `RATE_REMIND:<bookId>:<buyerId>` |
| Retention: delete read in-app notifications older than 90 days | Daily | — |
| Views milestones (`ViewFlushJob`) | Every 5 min, with the view flush | `VIEWS:<bookId>:<milestone>` |

## Planned
- **Push and WhatsApp channels:** good first candidates are `BOOK_ENQUIRY`, `UNREPLIED_MESSAGE` and `SOLD_TO_YOU`.
- **Weekly digest email:** unread notifications and views on your listings.
- **Price drop on saved books:** this needs "save book" first.
- **Saved-search matches:** "New listing in Lagos matching *Chimamanda*".
- **Quiet hours:** hold non-urgent email and push overnight.
