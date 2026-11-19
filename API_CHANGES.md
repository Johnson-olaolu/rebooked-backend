# Backend changes needed before frontend integration (signup + onboarding)

These items come from comparing the frontend's signup, email verification and onboarding flows with the current API (`http://localhost:5001/api/v3/api-docs`) and its source code. They were found by reading the code, not by running it.

Paths are relative to `src/main/java/com/personal/rebooked/`.

| Priority | Meaning |
|---|---|
| 🔴 Must | Blocks or breaks the integration |
| 🟠 Should | Wrong behaviour or a mismatch with the UI |
| 🟢 Nice | Docs and later features |

---

## 🔴 1. Profile update crashes without `address`
**Where:** `user/UserService` (the profile update method)

**What's wrong:**
- `updateProfileDTO.address().getCountry()` runs with no null check, so a request without `address` throws a NullPointerException.
- The address is replaced completely on every call.

**Change:**
- Merge only the fields that were sent, including the address one field at a time.
- Ignore null fields.

---

## 🔴 2. The backend decides when onboarding is complete
**Where:** `user/models/Profile.java`, `user/models/User.java`, `user/UserService`

**What's wrong:**
- `PATCH /user/{id}/profile` calls `user.setOnboarded(true)` on every request, even when required fields are empty.

**Change:**
- Add `boolean onboardingCompleted` (default `false`) to `Profile`. In JSON it appears as `user.profile.onboardingCompleted`.
- Remove `User.isOnboarded`, or make it read from the profile, so there's only one source of truth.
- Keep `onboardingCompleted` **out of** `UpdateProfileDTO`. The client can never set it.
- Remove `user.setOnboarded(true)`.
- In `PATCH /user/{id}/profile`:
  1. Merge the fields that were sent into the existing profile (see #1).
  2. Check the merged result. The required fields are `userName` and `address.city`. The profile picture stays optional.
  3. If either is blank, return **400** and save nothing:
     ```json
     {
       "status": 400,
       "message": "Profile incomplete",
       "errors": {
         "userName": "Display name is required",
         "address.city": "City is required"
       }
     }
     ```
  4. Otherwise set `onboardingCompleted = true`, save, and return `200 { message, status, data: User }`.
- **Existing users:** run a one-off script that sets `onboardingCompleted = true` where `userName` and `address.city` are already filled in.

> Because the check runs on the merged profile, later profile edits that send only some fields still pass, since `userName` and `city` are already stored.

---

## 🔴 3. Global error handler (one error format)
**Where:** a new `@RestControllerAdvice`, plus `security/SecurityConfig`

**What's wrong:**
- There's no exception handler.
- Validation failures return a ProblemDetail that only says `"Invalid request content."`, with no per-field messages.
- Bad credentials on login go to `/error`, which isn't public. That likely produces an empty 401/403.
- Errors don't follow the success envelope `{ message, status, data }`.

**Change:** always return this shape:
```json
{ "status": 400, "message": "Human readable message", "errors": { "field": "message" } }
```
`errors` is optional and only included for validation failures.

| Exception | Status | `message` |
|---|---|---|
| `MethodArgumentNotValidException` | 400 | `"Validation failed"`, plus a message for each field in `errors` |
| `ResponseStatusException` | its own status | its reason |
| `BadCredentialsException`, `UsernameNotFoundException` | 401 | `"Invalid email or password"` |
| Anything else | 500 | `"Something went wrong"` |

Also add `"/error"` to the public routes in `SecurityConfig`.

---

## 🔴 4. JWT effectively never expires
**Where:** `auth/jwt/JwtService`

**What's wrong:**
- The expiry is set to `System.currentTimeMillis() + (jwtExpiration * 100000000)`.
- The login response reports `expiresIn: 3600000` (1 hour), which doesn't match.

**Change:**
- Use `jwtExpiration` on its own, so the real expiry and `expiresIn` agree.

---

## 🔴 5. Token refresh — **decision needed**
**What's wrong:**
- There's no refresh token or refresh endpoint. Once #4 is fixed, users get logged out every hour.

**Options (choose one):**
- **A. Refresh token.** Add `refreshToken` to `LoginResponseDTO`, returned by register, login and the OAuth logins. Add `POST /auth/refresh-token { refreshToken }`, which returns `{ token, expiresIn, refreshToken }`.
- **B. Long token life for now.** Set `JWT_EXPIRATION_TIME` to 7 days and add refresh later.

---

## 🟠 6. Unverified users can log in — **decision needed**
**Where:** `auth/AuthService` (login)

**What's wrong:**
- Nothing checks `emailVerified`.

**Options (choose one):**
- **A. Block login.** Return `403 { message: "Email not verified" }` and send a new verification code.
- **B. Allow login** (current behaviour). The frontend sends users with `emailVerified: false` to the verify screen.

---

## 🟠 7. Password rule rejects common symbols
**Where:** `user/dto/CreateUserDto`, `auth/dto/ChangePasswordDTO`

**What's wrong:**
- The special-character class `[_@#$%^&+=]` rejects `!`, `*`, `.`, `-` and others.

**Change:**
- Use this regex:
  ```
  ^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9])(?=\S+$).{8,}$
  ```
- Return a readable message: `"Password must be at least 8 characters with an uppercase letter, a lowercase letter, a number and a symbol"`.

---

## 🟠 8. Email verification code lasts 2 weeks
**Where:** `auth/AuthService` (`sendConfirmEmailToken`)

**What's wrong:**
- The 6-digit verification code stays valid for 2 weeks.
- Resends aren't rate-limited.

**Change:**
- Expire the code after 10–15 minutes.
- Rate-limit `GET /auth/confirm-email` to about 1 request per 30 s per email. The frontend already has a 30 s resend cooldown.
- Apply the same rules to the forgot-password code.

---

## 🟢 9. Swagger accuracy
**What's wrong:**
- No endpoint documents its response body, because they all return `ResponseEntity<Object>`.
- `GET /auth/confirm-email` and `GET /auth/forgot-password` show the parameter as `sendConfirmEmailDTO` / `forgotPasswordDTO`, but the real query param is `email`.

**Change:**
- Return typed responses or add `@ApiResponse(content = @Content(schema = @Schema(implementation = ...)))`.
- Add `@ParameterObject` to the `@ModelAttribute` DTOs.

---

## 🟢 10. Google/Facebook users get a null role
**Where:** `auth/AuthService` (`createUserOauth`), `user/models/User.java`

**What's wrong:**
- The role is saved under `"role"` but read back as `"roleName"`.
- `createUserOauth` never sets a role.
- `getAuthorities()` calls `new SimpleGrantedAuthority(null)` and throws. That likely breaks every authenticated request made by an OAuth user.

**Change:**
- Default the role to `"user"`.
- Read and write it under the same key.

This only matters once Google sign-in is wired up.

---

## 🟢 11. Spring Security fields in the user JSON
**Where:** `user/models/User.java`

**What's wrong:**
- `username`, `accountNonExpired`, `accountNonLocked`, `credentialsNonExpired` and `enabled` (plus `authorities`) are serialised in the user JSON.

**Change:**
- Add `@JsonIgnore` to those getters, or return a `UserResponseDTO`.

---

## 🟢 12. Categories
**Where:** `security/SecurityConfig`, `category/`

**What's wrong:**
- `GET /category` and `GET /category/top` require login, so the public home page can't list categories.
- `/category/top` computes `booksCount` in its query, but `Category` has no field for it, so the count is never returned.

**Change:**
- Make both GET routes public.
- Return `booksCount`.

---

## 🔴 13. Structured Nigerian address + coordinates
**Where:** `user/models/Address.java`, `user/dto/UpdateProfileDTO.java` (and a new `user/dto/AddressDTO.java`), `user/UserService.updateUserProfile`, public user/seller responses

**Why:** onboarding step 2 used to send a single free-text `address.city`, which is too loose to query. The frontend now fills a structured address from a Google Places search (restricted to Nigeria), and the user can also type it by hand.

**`Address` model:**

| Field | Type | Public? | Notes |
|---|---|---|---|
| `country` | String | ✅ | ISO code. Only `"NG"` for now |
| `state` | String | ✅ | Canonical name from the list below |
| `city` | String | ✅ | |
| `addressLine` | String | ❌ owner only | Optional street address |
| `formattedAddress` | String | ❌ owner only | Google's full address |
| `placeId` | String | ❌ owner only | Google place id |
| `location` | `GeoJsonPoint` | ❌ never in JSON | `@GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)`. Not used for queries yet, but stored |
| `latitude`, `longitude` | Double (JSON only) | ❌ owner only | Computed from `location`, or stored alongside it |

- Use Spring's `org.springframework.data.mongodb.core.geo.GeoJsonPoint`. Note that the constructor takes `(x = longitude, y = latitude)`.
- If `spring.data.mongodb.auto-index-creation` is off, create the index on the profile collection by hand: `address.location: "2dsphere"`.
- `@JsonIgnore` the raw `location`. Expose `latitude` and `longitude` instead, so the frontend never deals with GeoJSON.

**Request body:** `UpdateProfileDTO.address` becomes an `AddressDTO`:
```json
{
  "country": "NG",
  "state": "Lagos",
  "city": "Ikeja",
  "addressLine": "12 Allen Avenue",
  "formattedAddress": "12 Allen Ave, Ikeja, Lagos, Nigeria",
  "placeId": "ChIJ…",
  "latitude": 6.6018,
  "longitude": 3.3515
}
```
- Every field is optional in the body, because it's merged field by field as now.
- Send `latitude` and `longitude` together, or leave both out.
- If the body includes `placeId` but no coordinates, clear the stored `location`/`placeId` so stale coordinates don't stay attached to a hand-typed address. The frontend clears all four Google fields itself when the user changes the state by hand.

**Validation** runs on the merged profile and uses the existing 400 `errors` format:

| Key | Rule |
|---|---|
| `address.country` | Must be `"NG"`. Default to `"NG"` when missing |
| `address.state` | Required. Must match the list, case-insensitively. Normalise `"Lagos State"` to `"Lagos"`, and `"Abuja"`, `"FCT"` or `"Federal Capital Territory"` to `"Federal Capital Territory"` |
| `address.city` | Required, not blank |
| `address.location` | Both coordinates or neither. Must fall roughly inside Nigeria: lat 4–14, lng 2.5–15 |

`onboardingCompleted` now needs **`userName` + `country` + `state` + `city`**. The street address and coordinates stay optional.

**States list** (store it in `Constants`; the frontend uses the same names): Abia, Adamawa, Akwa Ibom, Anambra, Bauchi, Bayelsa, Benue, Borno, Cross River, Delta, Ebonyi, Edo, Ekiti, Enugu, Gombe, Imo, Jigawa, Kaduna, Kano, Katsina, Kebbi, Kogi, Kwara, Lagos, Nasarawa, Niger, Ogun, Ondo, Osun, Oyo, Plateau, Rivers, Sokoto, Taraba, Yobe, Zamfara, Federal Capital Territory.

**Privacy:**
- `GET /user/me` and the `PATCH /user/{id}/profile` response return the full address to the owner.
- Any response about someone else returns only `country`, `state` and `city`. That covers `GET /user/{id}`, the seller embedded in books, and chat participants. Use a public DTO or `@JsonView`.

**Migration:** set `country = "NG"` on existing addresses that have none. Run the state normalisation over existing `state` values as well.

---

## 14. Profile editing (Settings page)
The frontend now has an edit-profile form at `/dashboard/settings`. Saving calls `PATCH /user/{id}/profile` (display name, bio, photo, contact details, address). If the full name changed, it also calls `PATCH /user/{id}`. Most of this already works. These gaps remain:

### 🔴 14a. Anyone can edit or delete any account
**Where:** `security/SecurityConfig` (`"/user/**"` is `permitAll`), `user/UserController`

**What's wrong:**
- `PATCH /user/{id}`, `PATCH /user/{id}/profile` and `DELETE /user/{id}` never check that `{id}` is the logged-in user.
- They don't require a token at all.
- A logged-out request can rename, re-address or delete any user.
- An expired token sent to these routes, or to `/user/me`, returns **500** instead of 401. That means the frontend's refresh-on-401 never runs for them.

**Change:**
- Only `GET /user/{id}` stays public. Everything else under `/user/**` requires authentication.
- In the controller, return **403** `{ status: 403, message: "You can only change your own account" }` when `{id}` ≠ the current user. `isCurrentUser` already exists; reuse it. Add an admin exception only if you need one.
- Use `/user/me` as the target, for example `PATCH /user/me/profile`. That removes the id-guessing problem entirely. The frontend can switch to `/me` easily if you add it.

### 🟠 14b. No way to remove a profile photo
**What's wrong:** `profilePictureId: null` means "leave unchanged", so a photo can be replaced but never removed.

**Change:** treat `profilePictureId: ""` as "remove". Set `profilePicture` and `profilePictureUrl` to null. That matches how `addressLine: ""` already clears. The frontend's **Remove photo** button sends exactly this.

### 🟠 14c. `PATCH /user/{id}` (full name / email)
**What's wrong:**
- `updateUser` copies DTO fields onto `User` by reflection. That makes it easy to accidentally expose a field later.
- **The email can be changed without verification.** That allows account takeover and breaks login for the user.
- The full name can be set to blank.
- The response doesn't call `AddressPrivacy.showOwnerFields()`, so the owner gets their own address back without the private fields.

**Change:**
- Set fields explicitly and drop `email` from `UpdateUserDTO`. Changing an email should be its own flow that verifies the new address with a code. The frontend shows the email as read-only.
- Validate `fullName` as not blank and at most 80 characters when it's present.
- Call `AddressPrivacy.showOwnerFields()` when `{id}` is the current user, as `updateProfile` already does.

### 🟢 14d. Field limits
| Field | Rule |
|---|---|
| `userName` | 2–40 characters, trimmed |
| `bio` | ≤ 300 characters. `""` clears it |
| `contactPhone` | Optional. Nigerian format `^(\+234|0)[789][01]\d{8}$`. Store as E.164 `+234…`. `""` clears it |
| `contactEmail` | Optional, valid email. `""` clears it |

Return errors in the usual `errors` map, keyed `userName`, `bio`, `contactPhone` and `contactEmail`. The frontend applies the same limits, so these are a backstop. Right now `bio`, `contactEmail` and `contactPhone` store `""` rather than null. Use `blankToNull` for consistency with the address fields.

### 🟢 14e. Replaced photos are never deleted
**What's wrong:** uploading a new photo leaves the old `File` and its Cloudinary asset behind.

**Change:** delete the previous file, or queue it for clean-up, when `profilePictureId` changes or is cleared.

---

## 15. Add to shelf (create books + My shelf)

**Status of #14:** all five items are implemented. The `/user/me` endpoints exist, other users' ids get a 403, an expired token gets a 401, photos can be removed, and the field limits are enforced.

The frontend builds the add-a-book page (mockup 3c) and the My shelf list against the contract below. Items 15a–15e block the feature. Items 15f–15j are needed soon after.

**Agreed product rules:**
- Condition is required to publish. ISBN is optional.
- A book can be given away free.
- There is a real draft state.
- A book can have any number of categories.
- Handover is out of scope.
- Prices are in naira.

### 🔴 15a. New book fields
| Field | Type | Rule |
|---|---|---|
| `condition` | enum `NEW`, `LIKE_NEW`, `GOOD`, `FAIR` | Required to publish |
| `isbn` | string | Optional. 10 or 13 digits after stripping spaces and dashes (ISBN-10 may end in `X`). Store it normalised. `""` clears it |
| `isFree` | boolean, default `false` | When `true`, store `price = 0`. When `false`, `price` must be > 0 |

Add these to `Book`, `CreateBookDto` and `UpdateBookDTO`. `UpdateBookDTO` is also missing `description` and `tagNames`, which the edit page will need.

### 🔴 15b. `DRAFT` status
- Add `DRAFT` to `BookStatus`.
- `POST /book` accepts an optional `status` of `"ACTIVE"` (the default) or `"DRAFT"`. Any other value returns a 400.
- **A draft only needs `title`.** Everything else may be missing or partial.
- **Publishing runs the full validation.** That covers creating with `ACTIVE`, and moving a draft to `ACTIVE` through `POST /book/{id}/status`. Failures return 400 with field errors.
- The shop and all public queries (`/book/query`, `most-viewed`, `ai-picks`, a public `GET /book/{id}`) return `ACTIVE` books only. Drafts are visible only to their owner.

### 🔴 15c. My shelf endpoint
The only option today is `GET /book/query?userId=…`:
- it has no status filter and no sort order;
- it returns a 500 when `page` or `pageSize` is missing (`page < 0` on a null `Integer`);
- it returns Spring's `PageImpl` (`pageable`, `sort`, and so on).

Add **`GET /book/me?status=ACTIVE&page=0&pageSize=20`**:
- The owner comes from the JWT, so no `userId` param.
- `status` is optional and filters to one status. It includes `DRAFT`.
- Sort is `updatedAt` descending. `page` defaults to 0 and `pageSize` to 20 (max 50).
- Response `data`:
```json
{
  "content": [ /* Book */ ],
  "page": 0, "pageSize": 20, "totalElements": 12, "totalPages": 1,
  "counts": { "ACTIVE": 8, "DRAFT": 1, "SOLD": 21, "INACTIVE": 2 }
}
```
`counts` covers all of the caller's books, whatever `status` is. The shelf tabs and the dashboard's "Books on shelf" stat use it.

### 🔴 15d. Create validation fixes (`POST /book`)
| Problem today | Change |
|---|---|
| Leaving out `tagNames` causes an NPE and a 500 (`BookService` ~L81) | Make it optional and default to `[]`. Trim and dedupe names, and drop blanks |
| `title` is only `@NotNull`, so `""` passes | `@NotBlank`, max 200 characters |
| No length limits | `author` ≤ 120 characters. `description` ≤ 600 (the form's counter) |
| `price` has no lower limit, so negative prices are accepted | ≥ 0, following the 15a rule |
| `imageIds` has no maximum and no owner check | 1–5 ids when publishing. Each must be a file **uploaded by the caller** |
| `coverImageId` accepts any existing file id, including one in neither list or owned by someone else | **It stays mandatory; the frontend chooses it.** It must be one of `imageIds`. Otherwise return 400 on `coverImageId`. Drafts may have no photos at all, but whenever `imageIds` is sent, `coverImageId` is required. PATCH uses the same rule, so changing the cover is just a new `coverImageId` from the book's images |
| An unknown category or file id returns a 404 (or a 500 on PATCH) | Return 400 with field errors keyed `categoryIds`, `imageIds` or `coverImageId` |
| `categoryIds` | At least 1 when publishing, with no maximum |

Use field error keys that match the DTO names, in the usual `errors` map, so the form can put each message under the right field.

### 🔴 15e. Book responses expose the seller's private data
Every book embeds the whole `User`, including `email`, `contactEmail`, `contactPhone`, `role` and `deleted`. Anyone who can read a book can collect sellers' emails and phone numbers.

**Change:** serialise the owner as a public summary:
```json
"seller": { "id": "...", "userName": "Ola J.", "profilePictureUrl": "...", "city": "Ibadan", "state": "Oyo" }
```
Do the same in every book endpoint. Separately, `GET /user/{id}` (public) and `GET /user` still return `email`, `contactEmail` and `contactPhone` to anyone. They should return the same public summary unless the caller is that user.

### 🟠 15f. Ownership checks on books and files
- `PATCH /book/{id}`, `POST /book/{id}/status` and `DELETE /book/{id}` don't check the owner. Return 403 if the caller isn't the owner.
- `PATCH /file/{id}` and `DELETE /file/{id}` have the same problem. Today anyone logged in can replace another user's book cover.
- `DELETE /book/{id}` returns 200 for an id that doesn't exist. Return 404 instead.
- PATCH catches its own 404s and returns them as 500s (`BookService` ~L142). Return 404/400 instead.
- `POST /book/{id}/status` with a null `status` saves null. Reject it with a 400.

### 🟠 15g. Uploads (`POST /file`)
- Accept images only: `image/jpeg`, `image/png`, `image/webp` and `image/heic`. Anything else gets a 400 with `errors.file`.
- Over 10 MB (`MaxUploadSizeExceededException`) returns a 500 "Something went wrong" today. Return 413 with "Photos must be 10 MB or smaller."

### 🟢 15h. Shop browsing (next pass)
- `GET /book/{id}` and `GET /book/query` require login today, so logged-out visitors can't browse. Make them public.
- Both should return `ACTIVE` books only, sorted newest first by default, with defaults for `page` and `pageSize`.
- A bad ObjectId in `userId` or `categoryIds` returns a 500. Return 400.
- `PATCH /book/{id}/view` is public and has no rate limit, so anyone can inflate view counts. Consider once per viewer per day.

### 🟢 15i. Categories are writable by any user
- `POST`, `PATCH` and `DELETE /category` work for any logged-in user, and `POST` even accepts `isSeeded` from the client. Restrict all three to admins.
- A duplicate name returns a 500. Return 409.
- Deleting a category that books use leaves broken references.

### 🟢 15j. Bugs found while reading the code
- `GET /book/sold` ignores `userId` and always returns everyone's sold books. The query matches `'user.id'`, but the DBRef is stored as `user.$id`, and the `{ ?0: null }` fallback is always true.
- `GET /book/ai-picks` crashes when there are fewer than 50 active books (`subList(0, 50)`). Its 7-day cache also keeps showing books that have been sold or deleted.
- `POST /book` returns 200. Return 201.

### What the frontend sends: `POST /book`
```json
{
  "title": "Half of a Yellow Sun", "author": "Chimamanda Ngozi Adichie", "isbn": "9780007200283",
  "condition": "LIKE_NEW", "isFree": false, "price": 8500,
  "description": "...", "categoryIds": ["...", "..."], "tagNames": [],
  "imageIds": ["f1", "f2", "f3"], "coverImageId": "f2",
  "status": "ACTIVE"
}
```
A draft sends the same shape with `"status": "DRAFT"`, and any field may be empty except `title`. `coverImageId` is always one of `imageIds`: it's the photo the seller picked, or the first photo if they didn't pick one.

---

## 16. Edit, status and remove + public book page (follow-ups, not blockers)

The frontend now uses `PATCH /book/{id}`, `POST /book/{id}/status`, `DELETE /book/{id}`, `GET /book/{id}` (public) and `PATCH /book/{id}/view`. All of these work as they are. Two follow-ups:

- **16a. Photos are left behind on delete.** `DELETE /book/{id}` deletes the book document, but its `File` records and Cloudinary uploads stay. The same happens to photos removed during an edit. Suggestion: delete the files (and their Cloudinary assets) that no other book points to, after the book is deleted or after a PATCH that drops them from `imageIds`.
- **16b. No slug for book URLs.** Book pages live at `/book/{id}`. A `slug` (e.g. `half-of-a-yellow-sun-4821`, as in mockup 2c) with `GET /book/slug/{slug}` would give nicer, shareable links. This is optional.

## 17. Shop browsing: filters, state location, facets, public seller page

The shop now lists books from `GET /book/query`, with a filter sidebar (mockup 2a). There are also category pages (`/shop/category/:slug`), state pages (`/shop/in/:state`), a free-books page (`/shop/free`) and a public seller page (`/seller/:id`, mockup 2d). The frontend already sends the params below. Until they're supported, unknown params are ignored, so those filters won't narrow the results.

### 17a. 🔴 Seller location stored on the book
To filter by state, each book needs its seller's location. Today it lives at book → user (DBRef) → profile (DBRef) → address.
- Add `location: { state, city }` to `Book`. Fill it from the seller's `profile.address` on create and on every PATCH.
- When a user's address changes (`PATCH /user/me/profile`, `PATCH /user/{id}/profile`), update `location` on all of their books.
- Write a one-off backfill for existing books.
- Add indexes on `{ status: 1, "location.state": 1, createdAt: -1 }` and `{ status: 1, price: 1 }`.
- Returning `location` in book responses is fine. `seller.city` and `seller.state` already carry the same information.

### 17b. 🔴 More filters and sort on `GET /book/query`
All of these are optional. List params are comma-separated, as `categoryIds` already is.

| Param | Example | Meaning |
| --- | --- | --- |
| `states` | `Lagos,Oyo` | Match `location.state`. Names as in the onboarding list (e.g. `Federal Capital Territory`, `Akwa Ibom`). |
| `conditions` | `LIKE_NEW,GOOD` | Match any of these conditions. |
| `minPrice`, `maxPrice` | `1000`, `5000` | Naira, inclusive. Free books count as price 0. |
| `free` | `true` | Free books only. Leaving it out means everything. |
| `sort` | `NEWEST` / `PRICE_ASC` / `PRICE_DESC` | `NEWEST` is the default. Break ties by `createdAt` descending. |

- `search` should also match `isbn`, normalised (strip spaces and dashes).
- A bad enum value, `minPrice > maxPrice`, or a negative price should return a 400 with `errors` keyed by param name.

### 17c. Facet counts (nice to have, for the sidebar counts)
Add to the `/book/query` response:
```json
"facets": {
  "categories": { "<categoryId>": 12 },
  "states": { "Lagos": 40, "Oyo": 12 },
  "conditions": { "NEW": 3, "LIKE_NEW": 9, "GOOD": 20, "FAIR": 4 },
  "free": 5
},
"addedToday": 7
```
- Count each group with all the other active filters applied but not its own. For example, the state counts ignore `states` but respect category, condition, price and search. That way the numbers show what you'd get by changing that group.
- Only ACTIVE books count.
- The UI hides counts when `facets` is missing.

### 17d. `booksCount` on `GET /category`
The category list currently returns `booksCount: null` (it's only filled in on `/top`). Return the number of ACTIVE books per category. The categories index and the home page tiles show it.

### 17e. Public seller summary on `GET /user/{id}`
When the caller isn't that user, the response (`PublicUserDTO`) is `{ id, userName, profilePictureUrl, city, state }`. Mockup 2d also needs:
- `bio`
- `joinedAt` (the user's `createdAt`)
- `availableCount` (ACTIVE books)
- `soldCount` (SOLD books, shown as "books sold")
- `hasPhone` (a boolean, true when `contactPhone` is set; never send the number)

### 17f. A seller's sold books, public
The seller page has an Available tab and a Rehomed tab. For the Rehomed tab:
- Accept `status` on `/book/query` with only `ACTIVE` (the default) or `SOLD`.
- `SOLD` requires `userId`, otherwise return a 400. It sorts by `soldDate` descending.
- Never allow `DRAFT` or `INACTIVE`.
- Today `GET /book/sold` needs a login and ignores `userId` (15j), so it can't be used for a public page.

## 18. Chat: one conversation per buyer–seller pair (mockups 4a–4f)

The frontend replaces the "Messaging is coming soon" toast with real chat:
- **One conversation per pair of users.** Each book they talk about is attached to it, so a buyer asking about two of your books stays in one thread, and a bundle deal ("both for ₦10k") works.
- **Writes and live events go over STOMP. Paged reads go over REST.**

The existing `chat/` package is a scaffold that can't be used as it is:
- `createChat` ignores its input.
- The buyer is stored as an email.
- Messages are a DBRef list inside `Chat`, with no `chatId`.
- The sender comes from the client.
- The socket isn't authenticated.
- `/topic.chat.{id}` and `/topic.notifications.{userId}` are public, so anyone can subscribe.

Replace the package as described below. Paths are relative to `src/main/java/com/personal/rebooked/`.

### 18a. 🔴 Socket authentication and private delivery (`config/WebSocketConfig.java`, `security/SecurityConfig.java`)
- **Handshake:** permit `/ws/**` in `SecurityConfig`. Browser SockJS can't send an `Authorization` header on the handshake, so authentication happens on CONNECT instead.
- **CONNECT:** add a `ChannelInterceptor` in `configureClientInboundChannel`.
  - On `CONNECT`, read the `Authorization: Bearer <token>` **STOMP header** and validate it with `JwtService`.
  - Load the user and call `accessor.setUser(principal)`, where the **principal name is the user id**, not the email. `convertAndSendToUser` routes by that name.
  - A missing or invalid token throws, and the client gets a STOMP ERROR frame.
- **On every SEND**, reject the frame if the session has no user, or if the token's `exp` has passed. Use error code `TOKEN_EXPIRED` (see 18e) so the client knows to refresh and reconnect.
- **Broker setup:**
  - `registry.enableSimpleBroker("/queue", "/topic")`
  - `registry.setUserDestinationPrefix("/user")`
  - keep `setApplicationDestinationPrefixes("/app")`
- **Origins:**
  - Seen today: the browser blocks SockJS's first request (`GET /api/ws/info`) with "The value of the 'Access-Control-Allow-Origin' header … must not be the wildcard '*' when the request's credentials mode is 'include'". SockJS always sends credentials, so the response has to name the exact origin.
  - Use `setAllowedOriginPatterns(...)`, set to the frontend origins (`http://localhost:5173`, `http://localhost:5179`, and production from config).
  - Don't use `setAllowedOrigins("*")`, which fails with SockJS on Spring 6.
- **Path:** the endpoint stays `/ws` with SockJS. With the `/api` context path, the frontend connects to `http://localhost:5001/api/ws`.
- **Subscriptions:** only allow SUBSCRIBE to `/user/queue/**`. Reject `/topic/**` from clients.

### 18b. 🔴 Models (replace `Chat` / `ChatMessage`)
**`Conversation`** (`@Document("conversations")`):
```
id
participantIds: [String, String]      sorted
pairKey: String                       "<minId>_<maxId>", unique index; used for find-or-create
books: [ConversationBook]
lastMessage: { id, preview (≤140 chars), senderId, bookId, type, createdAt }
lastMessageAt: Date                   index { participantIds: 1, lastMessageAt: -1 }
readState: Map<userId, { lastReadMessageId, lastReadAt, unread: int }>
createdAt, updatedAt
```
**`ConversationBook`** (embedded):
```
bookId, sellerId, buyerId             roles are per book, so A can sell to B and buy from B in one conversation
addedAt, addedBy
state: OPEN | SOLD_TO_BUYER | SOLD_ELSEWHERE | UNAVAILABLE
lastMessageAt
```
**`Message`** (`@Document("messages")`):
```
id, conversationId, senderId
bookId                                optional: which attached book this is about
type: TEXT | SYSTEM
body                                  TEXT: 1–2000 chars, trimmed
systemEvent                           SYSTEM only: BOOK_ADDED | SOLD_TO_BUYER | SOLD_ELSEWHERE | BOOK_UNAVAILABLE | BOOK_AVAILABLE
clientId                              unique index { senderId, clientId }
createdAt
```
- Index `{ conversationId: 1, _id: -1 }` for history paging.
- `senderId` on SYSTEM messages is the user whose action caused them.
- **`Book` gains `buyerId`** (nullable, `@JsonIgnore` on public responses). It's set by 18d and cleared when the book goes back to `ACTIVE`. Reviews will build on it later.

### 18c. 🔴 STOMP destinations (client → server, `/app/...`)
**Common rules:**
- Every payload has a `clientId` (uuid string) from the client.
- **Idempotent:** if `{senderId, clientId}` already exists, don't do anything again. Re-send the same `ack`.
- **Validation:** failures send an `error` event (18e) and never close the session.
- **Unknown or private:** a conversation the caller isn't in, and an unknown book, both return `NOT_FOUND`.

| Destination | Payload | Rules and effect |
|---|---|---|
| `/app/conversation.start` | `{ clientId, bookId, body? }` | **Rules:** the book must be `ACTIVE`, and the caller can't be its seller (`OWN_BOOK`). **Effect:** find or create the conversation by `pairKey`. If the book isn't attached yet, attach it (`sellerId` = owner, `buyerId` = caller, `OPEN`) and add a SYSTEM `BOOK_ADDED` message. If `body` is present, save it as a TEXT message with `bookId`. **Ack:** `conversation` (18f). Both users get `conversation`, plus a `message` event for each message created. |
| `/app/message.send` | `{ clientId, conversationId, bookId?, body }` | **Rules:** the caller must be a participant. `bookId`, if sent, must already be attached. **Effect:** save, set `lastMessage`/`lastMessageAt` (and the book's `lastMessageAt`), and add 1 to the other user's `readState.unread`. **Ack:** the saved `message`. Both users get a `message` event. |
| `/app/conversation.read` | `{ clientId, conversationId, upToMessageId }` | **Effect:** set the caller's `lastReadMessageId`/`lastReadAt` and set `unread = 0`. Ignore it if `upToMessageId` is older than the stored one. **Ack:** `{ unreadTotal }`. The other user gets a `read` event. |
| `/app/book.sold` | `{ clientId, conversationId, bookId }` | **Rules:** the caller must be the book's seller, and the book must be `ACTIVE` or `INACTIVE`. **Effect:** set the book to `SOLD` with `soldDate` and `buyerId` = the other participant. That conversation book becomes `SOLD_TO_BUYER`, and gets a SYSTEM `SOLD_TO_BUYER` message. Every other conversation holding this book becomes `SOLD_ELSEWHERE`, with a SYSTEM message, and gets a `conversation` event. **Ack:** `conversation`. |

### 18d. 🔴 Book status changes outside chat (`BookService.updateStatus` and delete)
When a seller changes a book from My shelf, update every conversation that holds it, add a SYSTEM message, and push `conversation` (plus `message`) events to both users:

| From My shelf | Conversation book becomes | SYSTEM message |
|---|---|---|
| `SOLD` (no buyer known) | `SOLD_ELSEWHERE` | `SOLD_ELSEWHERE` |
| `INACTIVE` or delete | `UNAVAILABLE` | `BOOK_UNAVAILABLE` |
| back to `ACTIVE` | `OPEN`; also clear `Book.buyerId` | `BOOK_AVAILABLE` |

A deleted book keeps its conversation entries, so the frontend still shows them as "No longer available". The REST reads below should return a snapshot (title, cover) even when the book is gone. Store `title` and `coverUrl` on `ConversationBook` when the book is attached, and refresh them on each book PATCH.

### 18e. 🔴 Server → client events (one private queue)
Use `messagingTemplate.convertAndSendToUser(userId, "/queue/events", event)`. The client subscribes once to `/user/queue/events`.
```jsonc
{ "type": "ack", "clientId": "…", "data": <message | conversation | { unreadTotal }> }      // only to the sender
{ "type": "message", "conversationId": "…", "message": <Message>, "conversation": <ConversationRow for this recipient> }
{ "type": "read", "conversationId": "…", "userId": "…", "upToMessageId": "…", "readAt": "…" }
{ "type": "conversation", "conversation": <Conversation (18f) for this recipient> }
{ "type": "error", "clientId": "…", "code": "VALIDATION|NOT_FOUND|FORBIDDEN|OWN_BOOK|BOOK_NOT_AVAILABLE|TOKEN_EXPIRED|SERVER", "message": "…", "errors": { "body": "…" } }
```
- **Errors** are sent by `@MessageExceptionHandler` + `@SendToUser("/queue/events")`, with the `clientId` from the failed payload.
- **Per recipient:** `unread`, `role` and `other` depend on who is reading, so the `ConversationRow` / `Conversation` in each event is built separately for each recipient.
- **Notifications:** when the recipient has no connected session, also create a `NEW_MESSAGE` `Notification`. Track sessions with `SessionConnectEvent`/`SessionDisconnectEvent`, in a map of userId to count (in memory for now).
- **Remove the old notification push:** `NotificationGateway`'s public `/topic.notifications.{userId}` should go. Its notifications can go to the same private queue as `{ "type": "notification", ... }` later.

### 18f. 🔴 REST reads (authenticated; a non-participant gets 404)
All responses use the usual `{ message, status, data }` envelope.

**`GET /conversation?filter=ALL|UNREAD|SELLING|BUYING&search=&page=0&pageSize=20`**
- Rows are sorted by `lastMessageAt` descending.
- **Filters:**
  - `SELLING`: any attached book has `sellerId` = me.
  - `BUYING`: any attached book has `buyerId` = me.
  - `UNREAD`: `readState[me].unread > 0`.
- **Search:** matches the other user's `userName` or any attached book `title`, case-insensitive.
```jsonc
{ "content": [ConversationRow], "page": 0, "pageSize": 20, "totalElements": 9, "totalPages": 1,
  "counts": { "ALL": 9, "UNREAD": 3, "SELLING": 6, "BUYING": 3 } }   // counts ignore filter and search
```
**`ConversationRow` / `Conversation`.** The detail version is the same shape. `role` is from the caller's point of view.
```jsonc
{ "id": "…",
  "other": <PublicUserDTO>,
  "books": [ { "bookId": "…", "title": "…", "coverUrl": "…", "price": 7000, "isFree": false, "condition": "LIKE_NEW",
               "status": "ACTIVE", "state": "OPEN", "role": "SELLER|BUYER", "addedAt": "…", "lastMessageAt": "…" } ],
  "lastMessage": { "id": "…", "preview": "…", "senderId": "…", "bookId": "…", "type": "TEXT", "createdAt": "…" },
  "unread": 2,
  "otherLastReadMessageId": "…",   // for "Seen"
  "createdAt": "…", "updatedAt": "…" }
```
- `books` is sorted by `lastMessageAt` descending.
- `status` is the live book status, and `null` once the book is deleted.

**`GET /conversation/{id}`** returns a `Conversation`.

**`GET /conversation/{id}/messages?before=<messageId>&limit=30`**
- Returns `{ "content": [Message], "hasMore": true }`, newest first.
- `limit` is at most 50.
- With no `before`, it returns the latest messages.

**`GET /conversation/with?bookId=…`** or **`?userId=…`**
- Returns the caller's conversation with that book's seller (or with that user), if there is one: `{ "conversationId": "…", "hasBook": true }`. With `userId`, `hasBook` is always `false`.
- The seller page uses `userId` for its "Message" button.
- `data: null` when there's no conversation. That isn't a 404, because the book page calls this on every load.

**`GET /conversation/unread-count`** returns `{ "total": 3 }`, the sum of the caller's `unread` across conversations.

### 18g. 🟠 Clean-up
- Delete `chat/` (`Chat`, `ChatMessage`, `ChatGateway`, `ChatController`, `CreateChatDTO`, `CreateChatMessageDto`) and `Constants.ChatType`. There's no existing data worth migrating.
- `Notification.metaData.chat` should point at a `Conversation`.
- Change the comment in `PublicUserDTO` that mentions "chat participants" to say conversations. Chat responses must only ever expose `PublicUserDTO` for the other user: never their email or phone.

### 18h. 🟠 Follow-ups from the end-to-end test
- **Unread counts include system lines.** `ConversationService.record()` adds 1 to the other user's `unread` for every message, SYSTEM ones included. Starting a chat with a first message therefore shows "2 unread" for one message, and marking a book sold adds an unread to the buyer.
  - Only count `TEXT` messages, both in `record()` and in the recount in `markRead` (add `.and("type").is("TEXT")`).
  - The system lines still show in the thread. They just shouldn't light up the badge.
- **No heartbeats.** `enableSimpleBroker(...)` has no heartbeat or `TaskScheduler`, so STOMP negotiates `0,0`. A connection that dies silently (wifi drop, laptop asleep) isn't noticed until the user sends something.
  - Add `.setHeartbeatValue(new long[]{10000, 10000}).setTaskScheduler(<a ThreadPoolTaskScheduler bean>)`. The frontend already asks for 20s heartbeats and reconnects when they stop.
  - The frontend now also reconnects when the browser comes back online, which covers the common case.

### Not in this pass
Report and block (mockup 4c), typing indicators, quick replies, price offers (the old `BID` type), image messages, email or push for messages, and a Redis broker relay for more than one instance.

## 19. Notifications (live over the chat socket)

The frontend now has:
- a notifications page (`/dashboard/notifications`);
- a bell with an unread badge in the public header;
- an unread count on the dashboard's Notifications menu item.

Like chat, live events and mark-as-read go over the STOMP connection (`/user/queue/events`). REST is only for paged reads.

Today `Notification` holds DBRefs, and the only writer is `ChatPublisher.notifyOffline`, which creates one notification per offline message. Nothing can read the notifications. This section replaces all of it.

### 19a. 🔴 Model (replaces `notification/models/Notification.java`)
`@Document("notifications")`:
```
id
userId                     who it's for
type                       BOOK_ENQUIRY | SOLD_TO_YOU | SOLD_ELSEWHERE | BOOK_UNAVAILABLE | BOOK_AVAILABLE
                           | NEW_MESSAGES | STALE_LISTING | DRAFT_REMINDER
actor                      { id, userName, profilePictureUrl } snapshot of who caused it; null for reminders. Never email or phone
book                       { id, title, coverUrl } snapshot, so it still renders after the book is removed
conversationId             for chat-related types
count                      NEW_MESSAGES: how many messages it groups (≥1); otherwise 1
preview                    NEW_MESSAGES: the latest message, ≤140 chars
groupKey                   "NEW_MESSAGES:<conversationId>", "STALE:<bookId>:<yyyyMM>", "DRAFT:<bookId>"; null for the others
readAt                     null = unread
createdAt, updatedAt       updatedAt is bumped whenever a grouped notification takes another message
```
- **Indexes:**
  - `{ userId: 1, updatedAt: -1 }` and `{ userId: 1, readAt: 1 }`.
  - A unique partial index on `{ userId: 1, groupKey: 1 }` where `readAt` is null and `groupKey` exists, so there's at most one open grouped notification per key. Once it's read, the next message starts a new one.
- **Never notify the person whose own action caused it.**
- **Snapshots:** refresh `book.title`/`coverUrl` on open notifications when the book is edited. That's nice to have; a stale snapshot is acceptable.

### 19b. 🔴 What creates them

| Type | Who gets it | When | `actor` / `book` |
|---|---|---|---|
| `BOOK_ENQUIRY` | the seller | A book is attached to a conversation (`/app/conversation.start`), including a second book added to an existing chat | buyer / that book |
| `SOLD_TO_YOU` | the buyer | `/app/book.sold` | seller / book |
| `SOLD_ELSEWHERE` | every **other** buyer who has that book in a conversation | `/app/book.sold`, or Mark as sold from My shelf | seller / book |
| `BOOK_UNAVAILABLE` | buyers who have that book in a conversation | archived (`INACTIVE`) or removed | seller / book |
| `BOOK_AVAILABLE` | buyers whose conversation book was `SOLD_ELSEWHERE` or `UNAVAILABLE` | put back on the shelf (`ACTIVE`) | seller / book |
| `NEW_MESSAGES` | the recipient | A TEXT message arrives while the recipient has **no connected session** (`ChatSessions`). Upsert on `groupKey`: `count += 1`, `preview` = this message, `actor` = sender, `book` = the message's book, `updatedAt` = now. This replaces `notifyOffline` | sender / book |
| `STALE_LISTING` | the seller | Daily `@Scheduled` job (enable scheduling): an `ACTIVE` book listed 30+ days ago with no `BOOK_ENQUIRY` in the last 30 days. `groupKey` stops it repeating within the month | null / book |
| `DRAFT_REMINDER` | the seller | Same daily job: a `DRAFT` not updated for 3 days. Once per draft (`groupKey` `DRAFT:<bookId>`, even after it's read) | null / book |

- **Auto-read:** when a user reads a conversation (`/app/conversation.read`), mark that conversation's open `NEW_MESSAGES` notification read, and push `notification.read` (19c).
- **Retention:** the daily job also deletes read notifications older than 90 days.

### 19c. 🔴 Socket (same private queue as chat)
**Server → client**, on `/user/queue/events`:
```jsonc
{ "type": "notification", "notification": <Notification>, "unreadTotal": 4 }
// new, or a grouped NEW_MESSAGES updated (same id, higher count); sent to every session of that user
{ "type": "notification.read", "ids": ["…"], "all": false, "unreadTotal": 3 }
// after a mark-read from any of the user's tabs, or an auto-read
```
**Client → server:** `/app/notification.read`:
```jsonc
{ "clientId": "…", "ids": ["…"] }   // or
{ "clientId": "…", "all": true }
```
- **Rules:** send exactly one of `ids` or `all`, otherwise return `VALIDATION`. Ids that aren't the caller's are ignored. It's idempotent by `clientId`, as in chat.
- **Response:**
  - `ack` with `data: { "unreadTotal": n }`;
  - then `notification.read` to all of the user's sessions, the sender's included, so their other tabs update.

### 19d. 🔴 REST reads
All responses use the usual `{ message, status, data }` envelope and require a login.

**`GET /notification?filter=ALL|UNREAD&page=0&pageSize=30`**
- Sorted by `updatedAt` descending.
- `pageSize` is at most 50.
```jsonc
{ "content": [Notification], "page": 0, "pageSize": 30, "totalElements": 70, "totalPages": 3, "unreadTotal": 4 }
```
**`GET /notification/unread-count`** returns `{ "total": 4 }`.

**`Notification` JSON:**
```jsonc
{ "id": "…", "type": "BOOK_ENQUIRY",
  "actor": { "id": "…", "userName": "Tunde A.", "profilePictureUrl": null },
  "book": { "id": "…", "title": "Purple Hibiscus", "coverUrl": "…" },
  "conversationId": "…", "count": 1, "preview": null,
  "read": false, "readAt": null, "createdAt": "…", "updatedAt": "…" }
```

### 19e. 🟠 Clean-up
- Remove the DBRef `Notification.MetaData`, `NotificationService.findAllByUserId`, `CreateNotificationDto` and `Constants.NotificationMetaType`. Use a small typed factory per event instead, e.g. `notifications.bookEnquiry(seller, buyer, book, conversation)`.
- Existing documents in the old `notification` collection can be dropped.

### Not in this pass
Email and push delivery (`NEW_MESSAGES` is the hook for them later), per-type mute settings, and "saved book" price-drop alerts (saving doesn't exist yet).

### 19f. 🟢 Backend additions (for the frontend)
The backend went further than 19a–19e: email delivery and per-type settings are in. See `docs/notification.md` for the full catalog.

- **More in-app types.** The frontend should render these as well:
  - `WELCOME`: sent after the email is confirmed. It has no `actor` and no `book`.
  - `VIEWS_MILESTONE`: has a `book`, and a `preview` such as `"100 views"`.
  - `MARK_AS_SOLD_NUDGE`: has a `book`. It asks the seller "Did you sell X?".
- **Settings:**
  - `GET /notification/preferences` returns `[{ type, category, channels: { IN_APP: true, EMAIL: false } }]`. It covers only the types a user can turn off.
  - `PATCH /notification/preferences` takes `{ type, channel, enabled }` and returns the same list.
- **Unsubscribe page:** emails link to `<CLIENT_URL>/unsubscribe?token=…`.
  - The page should `POST /notification/unsubscribe { token }`. It's public.
  - The response is 200 with `{ type, channel }`, or 400 `{ errors: { token } }`.
- **Email links:** emails link to these routes. Tell the backend if any differ; they're all in `notification/NotificationLinks.java`.
  - `/dashboard/messages/<conversationId>`
  - `/dashboard/notifications`
  - `/dashboard/shelf`
  - `/dashboard/shelf/<bookId>/edit`
  - `/book/<slug>`
  - `/welcome`
  - `/dashboard/settings/notifications`
  - `/unsubscribe`
- **Unread counts:** chat `unread` now counts only TEXT messages (18h).

## 20. Change password from Settings

**Frontend:** Settings → Security (`/dashboard/settings/security`). It asks for the current password, a new one, and confirmation. "Forgot your current password?" uses the existing reset email (`GET /auth/forgot-password?email=`). Google and Facebook accounts see a note instead, because they can't log in with a password.

The reset pages (`/auth/forgot-password` and `/auth/change-password?email=&token=`) were stubs. They now call `GET` and `POST /auth/forgot-password` for real.

### 20a. 🔴 `POST /auth/change-password` (logged in)

**Body:** `{ "currentPassword": "…", "newPassword": "…" }`

- **Rules:**
  - `newPassword` must match `PASSWORD_REGEX`, as at sign-up.
  - Only `EMAIL` accounts can use it. For Google and Facebook accounts, return 400 with `errors.currentPassword`.
- **Errors:** 400 `{ status, message, errors }`, using these field keys, because the form shows them under the fields:
  - `errors.currentPassword`: "That's not your current password"
  - `errors.newPassword`: the `PASSWORD_MESSAGE`, or "Choose a password you're not using now" when it's the same as the current one
- **Rate limit:** please limit wrong current-password attempts, for example 5 every 15 minutes per user, then 429 with a message. Otherwise someone holding a stolen session could keep guessing.
- **On success:**
  - Save the new password.
  - **Revoke all of the user's refresh tokens**, so every other device is signed out.
  - Issue a new pair for this session.
  - Send the `PASSWORD_CHANGED` email (mandatory), as the reset flow already does.
  - Return the same shape as login: `data: { token, expiresIn, refreshToken, user }`. The frontend swaps in the new tokens and stays logged in.

### 20b. 🟠 Reset-flow fixes

- **`POST /auth/forgot-password`** (setting the new password from the link) should also **revoke all refresh tokens**. Right now someone who already holds a refresh token stays logged in after the owner resets the password.
- **`GET /auth/forgot-password?email=`** for an email with no account: return 200, not 404. Otherwise the endpoint tells anyone whether an email is registered. The frontend already shows "Check your inbox" either way.
- **Invalid or expired code:** a 400 with `errors.token` would let the page tell it apart from a password-rule error. For now it matches the message "Invalid or expired code".

## 21. Saved books and Recently viewed

**Frontend:**
- **Heart buttons:** on every book card and on the book page. Your own books don't get one.
- **Dashboard overview:** two new sections, "Recently viewed" and "Saved books".
- **New page:** `/dashboard/saved`.
- **Word:** the UI says **"Saved"**, never favourites or wishlist.

**Goal for views:** no database write per view. Redis takes every view, and Mongo gets small batch writes every 5 minutes. **Storage grows with the number of users, never with the number of views.**

### 21a. 🔴 Recording a view (`PATCH /book/{id}/view`, same endpoint)

The frontend already calls this once per visit to an ACTIVE book it doesn't own. Replace `BookService.viewed` with **one Redis round trip** (a pipeline or Lua script) and **no Mongo write**. Today it saves the whole book document on every first daily view. That costs one write per view, and it can overwrite a seller's edit made at the same moment.

| Key | Type | What | Lifetime |
|---|---|---|---|
| `viewed:{viewer}:{yyyyMMdd}` | SET of bookIds | Daily dedupe, one key per viewer per day; it replaces `view:{bookId}:{viewer}`. `SADD` returns 1 → count the view | Expires after 26 h |
| `views:pending` | HASH bookId → n | Counts not yet written to Mongo (`HINCRBY bookId 1`) | **No expiry** |
| `recent:{userId}` | ZSET bookId → viewedAt (epoch ms) | `ZADD` (refreshes the time when a book is viewed again), then `ZREMRANGEBYRANK key 0 -21` to keep the newest 20 | Expires after 30 days, renewed on each view |
| `recent:dirty` | SET of userIds | Users whose recents changed since the last flush | **No expiry** |

- **Viewer** is `user:{id}` or `ip:{ip}`, as today.
- **Logged-in:** dedupe → `HINCRBY` if it's a new view today → `ZADD` and trim recents → `SADD recent:dirty`. The book goes into recents on **every** view, even when it doesn't count again.
- **Logged-out:** only the count. There's no recents list.
- **Skip entirely** when the viewer owns the book, or when the book isn't ACTIVE (404 as today).
- **If Redis is down:** log it and still return 200. Viewing must never break the book page.
- **Response:** 200 with `data: null` is fine. The frontend ignores the body, so the book doesn't need to be loaded at all. One small cached read to check the status and owner is fine.

### 21b. 🔴 Flush job (`@Scheduled(fixedDelayString = "${views.flush-interval:PT5M}")`)

Please make the interval a property, so the end-to-end test can run it every 15 seconds.

**1. View counts:**
- If `views:flushing` exists (left over from a crashed run), process it first.
- Otherwise `RENAME views:pending views:flushing`, skipping this step when there's no pending hash. The swap is atomic, so views that arrive mid-flush go into a fresh `views:pending`.
- `HGETALL`, then an unordered `BulkOperations` `updateOne({_id}, {$inc: {viewCount: n}})`, in chunks of 1,000.
- One `find({_id: {$in: ids}})` projected to `viewCount, user, title, slug, coverImage` for the new totals. With `before = after − n`, send `VIEWS_MILESTONE` for every milestone in `(before, after]`. This replaces the per-view check, so milestones arrive up to 5 minutes later.
- `DEL views:flushing`.

**2. Recents:**
- `SPOP recent:dirty 500` in a loop.
- For each user: `ZREVRANGE recent:{id} 0 19 WITHSCORES`.
- One bulk upsert per batch into **`recent_views`**: `{ _id: userId, items: [{ bookId, viewedAt }], updatedAt }`, replacing the whole array (at most 20 items).

**3. Shutdown:** `@PreDestroy` runs one flush, so a normal restart loses nothing.

**More than one instance later:** wrap the job in `SET lock:views-flush <id> NX EX 240`.

### 21c. 🔴 Recently viewed

**`GET /book/recently-viewed`** (logged in):
- Read `ZREVRANGE recent:{me} 0 19`.
- If the key is missing (expired or evicted), load `recent_views`, refill the ZSET with the 30-day expiry, then read it.
- **One** `find({_id: {$in}})` for the books, returned **in viewed order**.
- Include ACTIVE and SOLD books. Drop DRAFT, INACTIVE, deleted and your own books.
- Response: `data: IBook[]`, the same book shape as the shop, newest first, at most 20.

**`DELETE /book/recently-viewed`:** `DEL recent:{me}`, `SREM recent:dirty me`, and delete the `recent_views` document. Returns 200.

### 21d. 🔴 Saved

**Collection `saved_books { _id, userId, bookId, createdAt }`**, with indexes:
- unique `(userId, bookId)`;
- `(userId, createdAt: -1)`;
- `(bookId)`.

**Endpoints:**
- **`PUT /book/{id}/save`:** idempotent; saving again is 200.
  - 404 if the book is a DRAFT or deleted.
  - 400 `errors.book` "That's your own book".
  - 400 `errors.book` "You can save up to 500 books" when the user is at the cap.
  - Returns `data: { saved: true }`.
- **`DELETE /book/{id}/save`:** idempotent. Returns `data: { saved: false }`.
- **`GET /book/saved?page=0&pageSize=24`:** newest saved first. Returns `{ content: IBook[], page, pageSize, totalElements, totalPages }`. Includes SOLD and INACTIVE books; the frontend shows Sold and Not available badges. Drops deleted ones.
- **`GET /book/saved/ids`:** `data: string[]` of every saved bookId. The frontend loads it once to fill in every heart.

**Clean-up:** deleting a book runs `saved_books.deleteMany({ bookId })`.

### 21e. 🟠 Redis config (`docker-compose.yml` and production)

```yaml
redis:
  image: redis:7
  command: ["redis-server", "--appendonly", "yes", "--appendfsync", "everysec", "--maxmemory", "256mb", "--maxmemory-policy", "volatile-lru"]
  volumes: ["redis-data:/data"]
```

- **AOF every second:** a crash loses at most 1 second of views.
- **`volatile-lru`:** Redis only evicts keys that have an expiry (the dedupe sets and recents). `views:pending` and `recent:dirty` have none, so they're never evicted. An evicted recents list is reloaded from Mongo.

**Rough sizes:**
- dedupe: 10k daily viewers × 30 books ≈ 20 MB;
- recents: 50k users active in the last month ≈ 40–60 MB;
- Mongo `recent_views`: about 1.5 KB per user, so 100k users ≈ 150 MB;
- Mongo writes per flush: at most (distinct books viewed + distinct users who viewed) in the last 5 minutes, however many views there were.

### Not in this pass
- a "a book you saved was sold or reduced" notification;
- recents for logged-out users;
- a save count for sellers.

## 22. Seller ratings (a sale always names the buyer)

**Frontend:**
- **Marking a book as sold requires saying who bought it**, picked from the people who chatted with the seller about that book. This works from My shelf, the edit page, and the chat's existing "Mark as sold to X". There is no skip.
- **Why:** sellers can't take sales off the platform and still collect "sold" credit, and there's no "who bought it?" race to resolve.
- **No chats about the book:** it can't be marked as sold. The seller can archive or remove it instead.
- **Ratings:** buyers rate the seller from 1 to 5 stars, with an optional comment of up to 300 characters.
- **Where ratings show:** on the seller page (summary, breakdown and reviews), on the seller card on the book page, and in the chat header.
- **New page:** `/dashboard/bought/{bookId}` shows the rating form. Emails and notifications link there.

**Ratings are stored on insert.** The seller's profile keeps a running `sellerRating`, updated when a rating is saved. Reads never recalculate it.

### 22a. 🔴 Marking as sold always names the buyer

**`GET /book/{id}/chat-buyers`** (only the seller):
- The people who have this book in a conversation whose entry is `OPEN` or `UNAVAILABLE`, most recent first.
- `data: [{ userId, userName, profilePictureUrl, conversationId, lastMessageAt }]`.

**`POST /book/{id}/status { "status": "SOLD", "buyerId": "…" }`:**
- **`buyerId` is now required for SOLD.**
  - Missing → 400 `errors.buyerId` "Choose who bought it".
  - Not in the list above → 400 `errors.buyerId` "Pick someone who chatted with you about this book".
- **Then do exactly what `/app/book.sold` does.** Refactor `ConversationService.bookSold` into a shared `markSoldTo(book, conversation, actorId)`:
  - set `status` SOLD, `soldDate` and `buyerId` (an atomic `updateFirst` on `status in [ACTIVE, INACTIVE]`);
  - that conversation → `SOLD_TO_BUYER` with a system line, plus `SOLD_TO_YOU`;
  - every other open entry → `SOLD_ELSEWHERE`, plus `SOLD_ELSEWHERE`;
  - then send `RATE_SELLER` (22b).
- **The chat's `/app/book.sold`** also sends `RATE_SELLER` now.
- **Clean-up:** the "Sold from My shelf: no buyer known" branch in `BookService` goes away, because SOLD always has a buyer. Going back to ACTIVE still clears `buyerId`, as today.

### 22b. 🔴 Ratings

**Collection `seller_ratings { _id, sellerId, buyerId, bookId, stars, comment, createdAt, updatedAt }`**, with indexes:
- **unique `(bookId, buyerId)`:** one rating per sale;
- `(sellerId, createdAt: -1)`.

**Rules:**
- The caller can rate only when `book.status == SOLD` and `book.buyerId == me`.
- `stars` is an integer from 1 to 5; `comment` is optional, trimmed, at most 300 characters.
- **Errors:** 400 `errors.stars` or `errors.comment`. 403 when not the buyer.

**`GET /book/{id}/rating`** (logged in), for the rating form and `/dashboard/bought/{id}`:
```json
{
  "canRate": true,
  "rating": { "stars": 4, "comment": "…", "updatedAt": "…" },
  "seller": { "id": "…", "userName": "…", "profilePictureUrl": "…" },
  "book": { "id": "…", "title": "…", "slug": "…", "coverUrl": "…" },
  "conversationId": "…"
}
```
- `canRate` is false when the caller isn't the recorded buyer.
- `rating` is null until they rate.
- 404 if the book is gone.

**`PUT /book/{id}/rating { "stars": 4, "comment"?: "…" }`:** creates or edits the caller's rating. Returns the `GET` shape.

**Updating the profile on insert.** It's one atomic pipeline update on the seller's profile; there's no read of the ratings collection.
```
new rating:  count +1, total +stars, stars.<n> +1
edit:        total + (new − old), stars.<old> −1, stars.<new> +1
then:        average = round(total / count, 1)
```
- **Profile field:** `sellerRating: { average, count, total, stars: { "1": 0, …, "5": 0 } }`.
- **Public seller summary** (everywhere `ISellerProfile` or the book's `seller` summary is returned, including `book.seller` and conversation `other`): add `rating: { average, count }`. **`GET /user/{id}`** for the seller page also gets `rating.stars`.

**`GET /user/{id}/ratings?page=0&pageSize=10`** (public), newest first:
- `content: [{ id, stars, comment, createdAt, buyer: { userName, profilePictureUrl }, book: { id, title, slug } }]`, plus `page`, `pageSize`, `totalElements`, `totalPages`.

**Notifications** (add to `NotificationType`, `docs/notification.md` and the preference list):

| Type | To | Channels | Link | Notes |
|---|---|---|---|---|
| `RATE_SELLER` | the buyer | In-app, Email | `/dashboard/bought/{bookId}` | "How was buying X from Tola?" Has `actor` (the seller), `book` and `conversationId`. Sent whenever a book is marked as sold to them |
| `RATE_SELLER` reminder 🟠 | the buyer | Email | same | The daily job, once, 3 days later, if they still haven't rated |

### Not in this pass
- reporting a review;
- sellers rating buyers;
- sellers replying to reviews;
- disputes ("I didn't buy this").

## 23. Authors (a catalogue instead of a free-text string)

The full design is in the frontend repo's `author-implementation.md`. This section is the contract the frontend is built against.

**Frontend:**
- **Picking authors.** The add/edit book form replaces the Author text box with a searchable multi-select: chips, in display order, up to 10. It sends `authorIds`, never names.
- **Adding a missing author.** "Add “{name}” as a new author" in the picker, with a "Did you mean…?" step when there are close matches. New authors can be used by everyone straight away and wait in an admin review queue.
- **New pages:**
  - `/author/{slug}`: the author's details and their books on Rebooked, with the shop's filters;
  - the home page's "Most-shelved authors" uses real data;
  - an admin area at `/admin/authors`, `/admin/authors/review`, `/admin/authors/{id}` and `/admin/imports`, for `superAdmin` only.
- **Showing authors.** Book cards, the book page and My shelf show `book.authors` ("A & B", "A, B + 2 more"). On the book page, each name links to the author page.

**Decisions:**
- **New authors** are `UNVERIFIED` but usable at once. Publishing is never blocked on review.
- **MongoDB is treated as self-hosted.** Search is indexed exact and word-prefix matching on normalised names and aliases. Typo tolerance (Atlas Search or a search engine) comes later.
- **The seed** is a quality-filtered Open Library import, enriched from Wikidata.

**Ship order:**
- 23a–23e (with the migration) block the frontend. They ship in the same release as the frontend's switch from `author` to `authorIds`.
- `author` disappears from book responses in that release.
- 23f–23i can follow.

Paths are relative to `src/main/java/com/personal/rebooked/`. Put the new code in a new `author/` package.

### 23a. 🔴 Author model and one name normaliser

**`authors` collection:**
```
id
name                    display name, as written
normalizedName          AuthorNames.normalize(name); indexed, NOT unique (two people can share a name)
slug                    unique; set once at creation, never changed by a rename
searchKeys: [String]    AuthorNames.searchKeys(name, aliases); multikey index
aliases: [{ name, normalizedName, source }]          source: OPEN_LIBRARY | WIKIDATA | ADMIN | MIGRATION
biography               plain text
description             short disambiguation line, e.g. "Nigerian novelist"
birthYear, deathYear    Integer, nullable
photo: { url, source }
links: { openLibrary, wikidata, wikipedia }
countries: [String]     ISO codes (from Wikidata)
workCount               works in Open Library, saturating at 127; used for ranking
origin                  IMPORTED | USER_PROPOSED | ADMIN_CREATED | MIGRATED
status                  UNVERIFIED | VERIFIED | NEEDS_REVIEW | MERGED
mergedIntoAuthorId      set when MERGED
fieldSources: { name: "OPEN_LIBRARY", biography: "ADMIN", … }    which source last set each field
createdBy               userId, for USER_PROPOSED
availableCount          ACTIVE books with this author (stored on write, see below)
soldCount               SOLD books with this author
createdAt, updatedAt
```

**`author_external_identifiers { authorId, source, sourceId, createdAt }`:**
- `source` is one of `OPEN_LIBRARY` (`OL…A`), `WIKIDATA` (`Q…`), `VIAF` or `ISNI`.
- Unique index on `{ source: 1, sourceId: 1 }`, plus an index on `{ authorId: 1 }`.
- This is a separate collection rather than an array on the author, because unique multikey indexes don't enforce per-pair uniqueness the way we need.

**`AuthorNames`, the only normaliser.** Author creation, search, proposals, the import, aliases and duplicate checks all use it.
- **`normalize(s)`:**
  - NFKC, then trim;
  - collapse whitespace;
  - `toLowerCase(Locale.ROOT)`.

  Punctuation stays. "CHINUA   Achebe" becomes "chinua achebe".
- **`fold(s)`:** NFD with accents stripped, punctuation turned into spaces, whitespace collapsed, lower case. "Ngũgĩ wa Thiong'o" becomes "ngugi wa thiong o". The result is only `[a-z0-9 ]` plus non-Latin letters.
- **`searchKeys(name, aliases)`:**
  - for the name and for each alias: the folded full string, plus each word-suffix ("chinua achebe" gives `chinua achebe` and `achebe`);
  - deduplicated, at most 40 keys.

**Indexes.** Create these in a `@PostConstruct ensureIndexes()`, as `ConversationService` does:
- `normalizedName`;
- `slug` (unique);
- `searchKeys`;
- a partial index on `{ searchKeys: 1 }` with `partialFilterExpression: { availableCount: { $gt: 0 } }`;
- `{ status: 1, updatedAt: -1 }`;
- `{ availableCount: -1 }`.

**Slug:**
- Start from `Misc.slugify(name)`, falling back to "author", not "book".
- If it's taken, add `-2`, `-3` and so on.
- Renames never change it.

**Status:**
- Imported records that pass the selection policy (23g) are stored as `VERIFIED`, because a source backs them.
- Seller proposals and migrated strings start as `UNVERIFIED`.
- In JSON, `verified` is `status == VERIFIED`.

**Counts are stored on write, never computed on read.** `AuthorStats.recount(Collection<String> authorIds)` works on the 1–10 affected ids:
- `countDocuments({ "authors.authorId": id, status: ACTIVE })` and the same for `SOLD`;
- then it `$set`s `availableCount` and `soldCount`.

It recounts rather than incrementing, so it can't drift. Call it after every write that changes a book's status or authors:
- `BookService` create, update (for the old and new author ids), `updateStatus` and delete;
- `ConversationService.markSoldTo`;
- merges (23f).

### 23b. 🔴 Books reference authors by id

**`Book.authors: [{ authorId, position, name, slug }]`:**
- `name` and `slug` are a **display cache**. `authorId` is the source of truth.
- `AuthorService` refreshes the cache whenever an author is renamed or merged:
  ```
  updateMulti({ "authors.authorId": id },
              { $set: { "authors.$[a].name": …, "authors.$[a].slug": … } },
              arrayFilters: [{ "a.authorId": id }])
  ```
- Add an index on `authors.authorId`.
- **The old `author` string field** is renamed `legacyAuthor` and gets `@JsonIgnore`. Only the migration (23e) writes it.

**JSON**, on every book response (they all serialise `Book`, so one getter covers them):
```json
"authors": [ { "id": "…", "name": "Terry Pratchett", "slug": "terry-pratchett" },
             { "id": "…", "name": "Neil Gaiman", "slug": "neil-gaiman" } ]
```
- Sorted by `position`.
- `author` is no longer returned.

**`CreateBookDto` / `UpdateBookDTO`:** `authorIds: string[]` replaces `author`.
- On create, it's optional for a draft.
- On PATCH, `null` means "keep" and `[]` means "clear". Clearing is only possible while the book is a draft, because a live listing must keep its authors (`validateForPublish`).

**Rules,** in `BookService.applyChanges`, through `AuthorService.resolveForBook(ids)`:

| Check | Result |
|---|---|
| An id doesn't exist | 400 `errors.authorIds` "One of these authors no longer exists" |
| An id is `MERGED` | Replaced by its canonical author, silently (follow `mergedIntoAuthorId`, at most 5 hops) |
| The same author twice, after resolving merges | 400 `errors.authorIds` "Add each author once" |
| More than 10 | 400 `errors.authorIds` "Up to 10 authors" |

- **Order:** keep the order sent, as `position` 0…n.
- **Names:** never turn submitted names into authors here. New authors only come from 23c proposals, the import or admins.

**Publishing:** `validateForPublish` replaces the `author` check with:
```java
if (book.getAuthors() == null || book.getAuthors().isEmpty()) errors.putIfAbsent("authorIds", "Author is required");
```

**Other readers of the old string:**
- `getAIRecommendedBooks` (the prompt map) and `MarketingService.testAIService` should use the joined names: `String.join(", ", names)`.
- `Constants.SAMPLE_BOOKS` / `seedBooks()` should create or find authors through `AuthorService`.

### 23c. 🔴 Public author endpoints

**`AuthorSummary`,** used by search, proposals and top authors:
```json
{ "id": "…", "name": "Chinua Achebe", "slug": "chinua-achebe", "verified": true,
  "birthYear": 1930, "deathYear": 2013, "description": "Nigerian novelist",
  "availableCount": 12, "matchedAlias": null }
```
- `matchedAlias` is the alias that matched the query, when the match came from an alias rather than the name. The picker shows "Also known as …".
- Never include `fieldSources`, `createdBy`, `searchKeys` or import metadata.

**`GET /author/search?q=&limit=10`** (public, rate-limited, see 23i):
- **Limits:** `q` is at least 2 characters after `fold`, and at most 80. `limit` defaults to 10, with a maximum of 20. A short `q` → 400 `errors.q`.
- **Lookups:** `fold(q)` once, then two indexed lookups. Both exclude `MERGED`.
  1. **Authors with books on Rebooked:** the same range on the partial index (`availableCount > 0`).
  2. **The catalogue:** `searchKeys: { $gte: q, $lt: q + "￿" }`, capped at 300 candidates.

  Use a range rather than a `^…` regex, so Mongo uses the index bounds. Don't use `\Q…\E`.
- **Ranking,** done in memory over both sets:
  1. **Tier:** 0 for an exact name or alias (`fold` equal); 1 when the folded full name starts with `q`; 2 when a later word or an alias starts with `q`;
  2. `availableCount`, descending;
  3. `verified` first;
  4. `workCount`, descending;
  5. name.
- **Response:** `data: AuthorSummary[]`.

**`GET /author/slug/{slug}`** and **`GET /author/{id}`** (public):
- **Merged authors:** a `MERGED` author resolves to the canonical one. The response then carries the **canonical** `slug`, and the frontend swaps the URL.
- **Unknown:** 404 "Author not found".
- **Response:**
```json
{ "id": "…", "name": "…", "slug": "…", "verified": true, "birthYear": 1930, "deathYear": 2013,
  "description": "…", "availableCount": 12, "soldCount": 30,
  "biography": "…", "photoUrl": null,
  "aliases": ["Albert Chinualumogu Achebe"],
  "links": { "openLibrary": "https://openlibrary.org/authors/OL…A", "wikidata": "…", "wikipedia": "…" },
  "attribution": ["OPEN_LIBRARY", "WIKIDATA"] }
```
- **Licensing gate:**
  - `biography` and `photoUrl` that came from an external source are returned as `null` unless `authors.display.external-bios` / `authors.display.external-photos` are `true`.
  - Both default to `false` until someone reviews the Open Library and Wikimedia reuse terms.
  - A biography with `fieldSources.biography == ADMIN` always shows.
- `attribution` lists the sources behind the fields shown.

**`POST /author/proposals { "name": "…", "force": false }`** (logged in):
- **Validation:**
  - `name` is trimmed, 2–120 characters, and contains at least one letter;
  - it isn't a URL or an email address;
  - otherwise → 400 `errors.name`.
- **Candidates:** authors (not `MERGED`) whose `normalizedName`, folded name or an alias equals the proposal's.
- **Candidates found and `force` is false:** 200 `data: { "author": null, "candidates": [AuthorSummary] }`. Nothing is created.
- **Otherwise:**
  - create the author with origin `USER_PROPOSED`, status `UNVERIFIED`, `createdBy` = me and `fieldSources.name = USER`;
  - add a `PROPOSAL` review item (23f), plus a `POSSIBLE_DUPLICATE` item for each candidate the seller overrode with `force`;
  - return 201 `data: { "author": AuthorSummary, "candidates": [] }`.
- **Availability:** the new author can be selected by anyone straight away, and a book can be published with it.

**`GET /author/top?limit=8`** (public, 🟢):
- Authors with `availableCount > 0`, sorted by `availableCount` descending. `limit` is at most 20.
- `data: AuthorSummary[]`.
- It covers the "Top Authors" TODO in `HomepageController`.

**No `/author/{id}/books`.** The author page uses `/book/query?authorId=` (23d), so it gets the shop's filters, facets and paging.

### 23d. 🔴 Book search (`BookService.query` and `facets()`; keep both in step)

- **`search`** no longer matches an `author` regex. Instead:
  1. Resolve up to 500 author ids for the text with the 23c lookup (tiers 0–2).
  2. Add `Criteria.where("authors.authorId").in(ids)` to the existing `$or` with title, category name and ISBN.
- **New param `authorId`:** only books whose `authors.authorId` is that id. A merged id resolves to its canonical author first. A bad ObjectId → 400 `errors.authorId`.
- **New sort `RELEVANCE`:**
  - It's the default when `search` is set and `sort` isn't sent.
  - Without `search`, it behaves like `NEWEST`.
  - Add a `_rank` field with `$addFields` and a `$switch`:

  | `_rank` | Match |
  |---|---|
  | 0 | Exact ISBN (normalised) |
  | 1 | Exact title, case-insensitive (`$toLower`) |
  | 2 | An author whose folded name equals the query (tier 0 ids) |
  | 3 | The title starts with the query, or a tier 1–2 author |
  | 4 | Anything else that matched |

  Sort by `_rank`, then `createdAt` descending. Leave `_rank` out of the response.

### 23e. 🔴 Migrating the existing author strings (`Seeder.migrateBookAuthors()`, after `migrateBookSlugs`)

The step must be idempotent. It only touches books with an `author` string and no `authors` field.
1. **Split** the string only on ` & ` and `;`.
   - Never split on "and" or on commas: "Achebe, Chinua" is one person.
   - Log the strings that contain them, so someone can check them by hand.
2. **Match** each name by `normalizedName` or an alias (not `MERGED`):
   - **Exactly one match:** link it.
   - **None:** create the author with origin `MIGRATED`, status `UNVERIFIED` and `fieldSources.name = MIGRATION`.
   - **Several:** link the best-ranked one (`VERIFIED` first, then `availableCount`, then `workCount`), and add a `MIGRATION_AMBIGUOUS` review item listing the others.
3. **Rewrite the book:**
   - set `authors` (positions in the original order);
   - move the string to `legacyAuthor`;
   - `$unset` `author`.

   Then run `AuthorStats.recount` for the authors involved.
4. **Log a summary:** books migrated, authors created, linked, ambiguous, and the strings that need a manual check.

`legacyAuthor` is removed in a later release, once the review queue is empty.

**After the Open Library seed (23g),** run a pass that compares `MIGRATED` and `USER_PROPOSED` authors with imported ones by exact `normalizedName` or alias. It adds `POSSIBLE_DUPLICATE` review items. **Nothing is ever merged automatically on a name match.**

### 23f. 🟠 Admin: review, edit, merge

**Access:** every route under `/admin/**` requires `hasAuthority(Constants.ROLE_SUPER_ADMIN)`. Put that rule in `SecurityConfig` before the generic ones; rules are first-match.

**`author_review_items`:**
```
type            PROPOSAL | POSSIBLE_DUPLICATE | MIGRATION_AMBIGUOUS
authorId        the author under review
otherAuthorId   the possible duplicate (pair types only)
pairKey         "<minId>_<maxId>" for pair types
reason          e.g. "Same name as an imported author"
status          OPEN | RESOLVED
resolution      VERIFIED | MERGED | KEPT_SEPARATE | DELETED
resolvedBy, resolvedAt, createdAt
```
**Indexes:**
- `{ status: 1, type: 1, createdAt: -1 }`;
- a unique partial index on `{ type: 1, pairKey: 1 }` where `pairKey` exists.

**Rules:**
- A pair that was resolved `KEPT_SEPARATE` is never suggested again.
- Never queue a pair where both authors were imported. Open Library already resolves its own duplicates with redirects (23g), and many different people share a name.

**`author_audit { authorId, actorId, action, before, after, reason, createdAt }`:**
- `action` is one of `CREATED | UPDATED | VERIFIED | MERGED | EXTERNAL_ID_ADDED | EXTERNAL_ID_REMOVED | KEPT_SEPARATE | DELETED`.
- `before` and `after` hold only the changed fields.
- Index on `{ authorId: 1, createdAt: -1 }`.

**Endpoints.** Paging uses the usual `{ content, page, pageSize, totalElements, totalPages }`, with a default page size of 20 and a maximum of 50.

| Endpoint | What it does |
|---|---|
| `GET /admin/author?q=&status=&origin=&page=&pageSize=` | Search and list. Rows are `AuthorSummary` plus `origin`, `status`, `soldCount`, `openReviewItems` and `sources` (the external id sources). Adds `counts: { UNVERIFIED, VERIFIED, NEEDS_REVIEW, MERGED }`, which ignore `status` |
| `GET /admin/author/{id}` | Every field, plus `fieldSources`, `externalIds: [{ source, sourceId, url }]`, `aliases` with their sources, `createdBy` (a public user summary) and the open review items |
| `PATCH /admin/author/{id}` `{ name?, aliases?, biography?, description?, birthYear?, deathYear? }` | Sets each changed field's `fieldSources` to `ADMIN`, so imports never overwrite it. A rename recomputes `normalizedName` and `searchKeys` and refreshes the book cache (23b). Strip HTML from `biography` and `description`. Audited. Returns the `GET` shape |
| `POST /admin/author/{id}/verify` | `status = VERIFIED`, and resolves the author's open `PROPOSAL` item as `VERIFIED`. Audited |
| `POST /admin/author/{id}/external-ids` `{ source, sourceId }` | Adds an identifier. If it belongs to another author → 409 `errors.sourceId` "Already linked to {name}". Audited |
| `DELETE /admin/author/{id}/external-ids/{source}/{sourceId}` | Removes it. Audited |
| `GET /admin/author/{id}/books?page=` | Books in **any** status with this author, including drafts, with their owner's public summary |
| `GET /admin/author/{id}/audit?page=` | Audit entries, newest first, with the actor's public summary |
| `GET /admin/author/review?type=&status=OPEN&page=` | Items with `author` and `otherAuthor` (`AuthorSummary` + `origin`, `status`, `soldCount`, `externalIds`), oldest first |
| `POST /admin/author/review/{itemId}/keep-separate` `{ reason? }` | Resolves the item as `KEPT_SEPARATE`. Audited on both authors |
| `GET /admin/author/{id}/merge-preview?intoId=` | `{ from, into, sharedBooks }`. `from` and `into` are the admin detail shape plus book counts; `sharedBooks` are books that list both authors |
| `POST /admin/author/{id}/merge` `{ intoId, reason }` | Merges `{id}` into `intoId` (below). Returns the surviving author |
| `DELETE /admin/author/{id}` | Only when no book references the author. Otherwise 409 "This author is on books. Merge it instead." Audited |

**Merge.** `reason` is required (`errors.reason`). If the two ids are the same, or either author is already `MERGED`, return 409.

The local Mongo is a standalone server with no transactions, so a merge is a **resumable job**:
1. Insert `author_merges { fromId, intoId, actorId, reason, state: STARTED, booksUpdated, createdAt }`.
2. **Books that list both authors** (`$and` on both ids): fix them one at a time. Remove the `from` entry and renumber `position`.
3. **Every other book with `from`:** `updateMulti` with arrayFilters, setting `authorId`, `name` and `slug` to `into`'s. Then `state = BOOKS_UPDATED`.
4. **Move the identity:**
   - move `from`'s aliases to `into`, and add `from.name` as an alias of `into` (source `ADMIN`);
   - re-point `from`'s external identifiers to `into`;
   - recompute `into.searchKeys`.
5. **Retire `from`:**
   - `from.status = MERGED`, `mergedIntoAuthorId = into`;
   - re-point authors already merged into `from`, so there are no chains;
   - resolve every open review item that involves `from` as `MERGED`;
   - `AuthorStats.recount` both authors;
   - audit both.
6. Set `state = COMPLETED` and `completedAt`.

**Resuming:** each step can safely run twice. A `Seeder` step resumes any merge that isn't `COMPLETED` at startup. Never delete the merged record: old ids and slugs keep resolving through `mergedIntoAuthorId`.

**Imports** (see 23g):

| Endpoint | What it does |
|---|---|
| `GET /admin/author-import?page=` | Jobs, newest first |
| `GET /admin/author-import/{id}` | One job |
| `GET /admin/author-import/{id}/errors?page=` | `{ line, sourceId, message, createdAt }` |
| `POST /admin/author-import` `{ "source": "OPEN_LIBRARY" \| "WIKIDATA" }` | Starts a job and returns it (202). If one is already running → 409 "An import is already running" |

### 23g. 🟠 Open Library seed import (`author/importer/`)

**Use the bulk dumps, never the live API,** for the seed: `https://openlibrary.org/data`.

**`author_import_jobs`:**
- the doc's fields: `source`, `datasetVersion`, `sourceFile`, `status` (PENDING | RUNNING | COMPLETED | FAILED), `recordsRead`, `recordsCreated`, `recordsUpdated`, `recordsSkipped`, `recordsNeedingReview`, `recordsFailed`, `checkpoint`, `errorSummary`, `startedAt`, `completedAt`;
- plus `actorId` (null for the scheduled run) and `coverage: { NG, africa }` (23h).

**`author_import_errors { jobId, line, sourceId, message, createdAt }`:**
- index on `{ jobId: 1, line: 1 }`;
- a TTL of 90 days.

**Running it:**
- **Executor:** a single-thread `authorImportExecutor` (`SchedulingConfig`).
- **Lock:** `SET lock:author-import <instanceId> NX EX 3600`, renewed while running and released only by its owner, as `ViewFlushJob` does.
- **Properties:**
  ```
  authors.import.enabled=false          # true in production
  authors.import.dir=./data/imports     # gitignored
  authors.import.cron=0 0 3 1 * *       # zone Africa/Lagos
  ```

**Stages:**
1. **Acquire.**
   - Resolve `ol_dump_authors_latest.txt.gz` and `ol_dump_works_latest.txt.gz` to their dated file names. The date becomes `datasetVersion`.
   - If that version already has a `COMPLETED` job, stop.
   - Otherwise stream both files to `authors.import.dir`.
2. **Works pass.**
   - **Reading:** `GZIPInputStream`, then a line reader. Each line is tab-separated: type, key, revision, last_modified, JSON. Parse the JSON column with Jackson's streaming parser (no extra library), and never hold the file in memory.
   - **Counting:** for each work, count each `authors[].author.key` (`/authors/OL123A`).
   - **Storage:** a `byte[]` indexed by the numeric part of the id, saturating at 127, growing with `Arrays.copyOf`. That's tens of MB for the whole catalogue.
3. **Authors pass.**
   - **`/type/author`:** keep the record if `workCount >= 2`, or if `remote_ids` has `wikidata`, `viaf` or `isni`. Otherwise count it as skipped.
     - **Map:**
       - `name`;
       - `alternate_names`, `personal_name` and `fuller_name` → aliases (source `OPEN_LIBRARY`);
       - `bio` (a string or `{ "value": … }`) → `biography`;
       - the first 4-digit year in `birth_date` / `death_date`;
       - `photos[0]` → `photo.url` (`https://covers.openlibrary.org/a/id/{id}-M.jpg`);
       - `remote_ids` → external identifiers;
       - `links.openLibrary`;
       - `workCount`.
     - **Batches of 1,000:**
       - look up all their `OPEN_LIBRARY` ids with one `$in`;
       - upsert the authors with bulk ops: new ones get origin `IMPORTED`, status `VERIFIED`;
       - insert the identifiers.
     - **Never overwrite** a field whose `fieldSources` entry is anything other than `OPEN_LIBRARY`. That protects admin edits and seller-entered names.
   - **`/type/redirect`** (Open Library merged two of its authors; `location` is the survivor):
     - if we have both ends, merge ours with the 23f workflow (actor `system`, reason "Open Library redirect");
     - if we have only the old one, re-point its identifier to the new id.
   - **`/type/delete`:** remove the identifier only. Never delete an author that a book uses.
4. **Checkpoint.** After each batch, save `checkpoint: { pass, lineNumber }`. A resumed job re-reads the file and skips lines up to the checkpoint. Re-running a whole dump must create no duplicates; the unique identifier index guarantees that.
5. **Report:**
   - save the counts;
   - run the 23e duplicate pass;
   - delete the dump files;
   - set `COMPLETED`, or `FAILED` with `errorSummary`;
   - after a successful run, start the Wikidata enrichment (23h).

**Monthly:** the cron checks whether a newer dated dump exists and imports only then. Only new and changed records are written, because unchanged ones compare equal and are skipped.

**Before the first production run:** check the coverage of Nigerian and other African authors (23h), academic and children's authors. Don't claim the catalogue is complete; seller proposals fill the gaps.

### 23h. 🟢 Wikidata enrichment (source `WIKIDATA`)

**Scope:** only authors that have a `WIKIDATA` identifier **provided by Open Library**. That link is strong evidence. There's no name-based matching across sources in this pass.

**Fetching:**
- `https://www.wikidata.org/w/api.php?action=wbgetentities&ids=Q1|Q2…&props=labels|descriptions|aliases|claims|sitelinks&languages=en`, with 50 ids per request and about 1 request per second.
- Send a descriptive `User-Agent` from the property `wikidata.user-agent`: a generic client name plus a contact address the operator sets. Don't hard-code a personal email.

**What it fills.** It follows the same `fieldSources` rule as 23g, so it only writes fields that are empty or that Wikidata set before.

| Wikidata | Field |
|---|---|
| English description | `description` |
| P569 / P570 | `birthYear` / `deathYear`, only when empty |
| English aliases | `aliases` (source `WIKIDATA`) |
| P27 (country of citizenship) | `countries`, through a constant map of country ids to ISO codes covering at least every African country |
| enwiki sitelink | `links.wikipedia` |
| P18 (image) | `photo` (Commons file), stored but hidden by the 23c licensing gate |

**Coverage:** the job stores `coverage: { NG: <authors with NG>, africa: <authors with any African country> }`. It's shown on the admin Imports page.

**Triggers:** it runs after each successful Open Library import, or from `POST /admin/author-import { source: "WIKIDATA" }`.

### 23i. 🟠 Security, limits and docs
- **`SecurityConfig`:**
  - `GET /author/**` is public;
  - `POST /author/proposals` needs a login;
  - `/admin/**` is superAdmin only (23f).
- **Rate limits,** with `RedisService.increment`, as in `AuthService`:
  - search: 60 per minute per user, or per IP when logged out (`ratelimit:author-search:<who>`);
  - proposals: 20 per day per user;
  - over the limit → 429 "Too many requests, try again shortly".
- **Input:**
  - strip HTML from biographies and descriptions;
  - external links and photo URLs must be `https`;
  - limit query lengths as above.
- **Logging:** imports and merges log the actor, time and outcome (the audit plus job records).
- **Swagger:** document the new DTOs with typed responses (`@ApiResponse` + `@Schema`), as #9 asks.

### 23j. 🔴 Follow-up from the end-to-end test: author search matches unrelated authors

**What's wrong:**
- Author search uses a range on the `searchKeys` **array**: `where("searchKeys").gte(q).lt(q + "￿")`.
- On an array field, MongoDB applies each condition to **any** element, not necessarily the same one. An author matches when one key is ≥ `q` and another key is < the upper bound.
- **Example:** `GET /author/search?q=someone` returns "E2E Ada Testwriter", whose keys are `["e2e ada testwriter", "ada testwriter", "testwriter"]`:
  - `"testwriter"` ≥ `"someone"`;
  - `"ada testwriter"` < `"someone￿"`.

  Neither key starts with "someone".
- **Measured on the local data:** the plain range matches 7 authors for "someone"; `$elemMatch` matches 0.

**Effects:**
- **Seller autocomplete:** unrelated authors appear at the bottom of the list. `AuthorSearch.classify` puts them in tier 3.
- **Admin list:** `GET /admin/author?q=` returns nearly the whole catalogue for most queries.
- **At catalogue scale:** the false matches fill the 300-candidate cap (`CATALOGUE_CAP`), so real matches can be dropped. This is the serious one once the Open Library seed is in.
- **Not affected:** shop search (`/book/query?search=`), because it only uses tier 0–2 author ids.

**Change.** Make one key satisfy both bounds, in both places:

| Where | Today | Change to |
|---|---|---|
| `author/AuthorSearch.java` `range()` (`GET /author/search`, and the author ids for `/book/query?search=`) | `where("searchKeys").gte(q).lt(q + "￿")` | `where("searchKeys").elemMatch(new Criteria().gte(q).lt(q + "￿"))` |
| `author/admin/AuthorAdminService.java` (the `q` filter on the admin list) | `where("searchKeys").gte(folded).lt(folded + "￿")` | `where("searchKeys").elemMatch(new Criteria().gte(folded).lt(folded + "￿"))` |

- **Indexes:** `$elemMatch` with a range on a multikey field still uses the `searchKeys` index, and the partial index, with tight bounds. No index changes are needed.
- **Tier 3:** once this is fixed, tier 3 in `classify` should only be reached by keys trimmed by the 40-key cap. Keep it as the fallback.
- **Test:**
  - `q=someone` (or any word that isn't a word start in any name or alias) returns `[]`;
  - `q=testwriter` still finds "… Testwriter";
  - `GET /admin/author?q=E2E Prolific` returns only names or aliases with a word starting "e2e prolific".

  Add a unit or integration test with two authors whose keys straddle the query, like the example above.

### Not in this pass
- typo-tolerant search (Atlas Search, or a search engine);
- splitting genres from categories (design doc §11): categories are already controlled records;
- moving aliases to their own `author_aliases` collection;
- showing external bios and photos (until the licensing review);
- following an author, or "new book by an author you follow" alerts.

## 24. Homepage (one cached bundle instead of static sections)

**Frontend:**
- **The home page is being rebuilt** from mostly static sections into live ones. In order:
  1. Hero (with search and 3 hero books)
  2. Stats counter
  3. Continue browsing
  4. Categories
  5. Featured this week
  6. AI picks
  7. Trending this week
  8. New arrivals
  9. Near you
  10. Top authors of the week
  11. Readers are saying
  12. Trusted sellers
  13. How it works
  14. Final call-to-action
- **Already live,** using existing endpoints: Continue browsing (`/book/recently-viewed`), Categories, New arrivals and Near you (`/book/query`), and Top authors (`/author/top`).
- **Waiting on this section:** the stats, featured, AI picks, trending, reviews and sellers sections. They stay hidden until `GET /homepage` returns them.
- **Full design** (data model, selection rules, prompts, tests): `rebooked-frontend/homepage-implementation.md`. This section is the summary to implement against. It replaces the TODO list in `homepage/HomepageController.java`.

**Goal:** one request renders the whole public home page, and nothing slow runs on the request path. Every section is a Redis snapshot built by a scheduled job. One broken section never breaks the page.

**"This week"** means the ISO week starting **Monday 00:00 Africa/Lagos**, the same zone as the other jobs.

### 24a. 🔴 Daily view buckets (`BookViews.RECORD`)

Every "this week" section needs views per day. Today views only go into the all-time `viewCount`. Add a fifth key to the `RECORD` Lua script and increment it **inside the `counted == 1` branch**, so it inherits the existing per-viewer daily dedupe:

```lua
redis.call('ZINCRBY', KEYS[5], 1, ARGV[1])
if redis.call('TTL', KEYS[5]) < 0 then redis.call('EXPIRE', KEYS[5], 691200) end  -- 8 days
```

| Key | Type | What | Lifetime |
|---|---|---|---|
| `views:day:{yyyyMMdd}` | ZSET bookId → views | Counted views that day (`DAY` formatter and `ZONE` already in `BookViews`) | 8 days |
| `views:week` | ZSET | `ZUNIONSTORE` of the last 7 day keys, rebuilt hourly | 2 h |

A repeat view the same day must not increment it, and neither must an owner's own view.

### 24b. 🔴 `GET /homepage` (public)

Returns every non-personalised section in the usual envelope.

```json
{
  "generatedAt": "2026-11-18T09:15:00Z",
  "weekOf": "2026-11-16",
  "hero": [ /* 3 × Book */ ],
  "stats": { "activeBooks": 2847, "booksSold": 1312, "readers": 5120, "sellers": 940, "authors": 1785, "avgSellerRating": 4.7 },
  "categories": [ /* ≤ 8 × Category with booksCount */ ],
  "featured": {
    "category": { /* Category */ },
    "books": [ /* 6 × Book */ ],
    "author": { /* AuthorDetail */ "books": [ /* ≤ 4 × Book */ ] },
    "source": "AUTO"
  },
  "aiPicks": [ { "book": { /* Book */ }, "reason": "One sentence, ≤ 90 chars." } ],
  "trending": [ /* ≤ 12 × Book */ ],
  "newArrivals": [ /* ≤ 12 × Book */ ],
  "topAuthors": [ { /* AuthorSummary */ "photoUrl": null, "weekScore": 214 } ],
  "reviews": [
    { "rating": 5, "comment": "…", "createdAt": "…",
      "reviewer": { "userName": "…", "profilePictureUrl": null },
      "book": { "id": "…", "title": "…", "coverImage": { } } }
  ],
  "sellers": [ /* ≤ 4 × SellerSummary (with rating) + "soldCount" */ ]
}
```

- **Books** use the same DTO as `/book/query`, so `BookCard` renders them unchanged.
- **Snapshots store ordered IDs, not documents.** At serve time, re-read with `BookService.booksInOrder` and keep only `ACTIVE` books, so sold books drop out. Backfill lists that fall below their target size; the rules are in the design doc §5.
- **Sections fail independently.** Build each section in its own try/catch. On failure, send `null` or `[]` for that section and log it. **The endpoint returns 200** whenever the app is healthy.
- **Missing keys:** if a snapshot key is missing (fresh deploy, flushed Redis), compute that section inline once and cache it. Also warm every section on `ApplicationReadyEvent`.
- **Privacy:** `reviews[].reviewer` is the public username and avatar only. Never send email, full name or buyer ID.
- **Security:** add `/homepage` and `/homepage/stats` to the permit-list next to `/book/query`. Send `Cache-Control: public, max-age=60`.

**`GET /homepage/stats`** (public) returns just `stats`, for other pages.

### 24c. 🔴 Phase 1 sections: stats, trending, new arrivals, categories

| Section | Rule | Redis key / TTL | Rebuilt |
|---|---|---|---|
| `stats` | **activeBooks:** ACTIVE books.<br>**booksSold:** SOLD books.<br>**readers:** users with a confirmed email.<br>**sellers:** distinct users with at least one ACTIVE or SOLD book.<br>**authors:** authors with at least one ACTIVE or SOLD book (`AuthorStats`).<br>**avgSellerRating:** mean of `seller_ratings.stars` to 1 decimal; `null` under 10 ratings. | `homepage:stats` / 30 min | every 15 min |
| `trending` | `ZREVRANGE views:week`, keeping ACTIVE books until 12. Under 6: backfill from `getTopBooks()` without duplicates. | `homepage:trending` / 2 h | hourly |
| `newArrivals` | The 12 newest ACTIVE books (same order as `sort=NEWEST`). | `homepage:new` / 5 min | evicted on `BookChangedEvent` when a book enters or leaves ACTIVE |
| `categories` | `CategoryService.fetchTopCategories(8)`. | `homepage:categories` / 1 h | on miss |

### 24d. 🟠 Featured this week and Top authors of the week

**Collection `homepage_features`:**
- Fields: `{ _id, weekStart (LocalDate, unique), categoryId, bookIds[≤6], authorId, heroBookIds[≤3], source: AUTO|ADMIN, createdBy, note, createdAt, updatedAt }`.
- It's stored in Mongo, not only Redis, for three reasons:
  - history, so picks don't repeat;
  - an audit trail for overrides;
  - it survives a Redis flush.

**Weekly job: Monday 06:00 Lagos.** If an ADMIN document exists for the week, use it, filling any omitted fields with the AUTO choices. Otherwise:
- **Category:** a seeded category with at least 6 ACTIVE books that wasn't featured in the last 8 weeks. Rank by share of 7-day views and pick randomly from the top 3.
- **Books (6):** that category's ACTIVE books by 7-day views, then newest. Prefer books with a cover.
- **Author:** the best `weekScore` author (below) with a `biography` or `photoUrl` who wasn't featured in the last 4 weeks, plus up to 4 of their ACTIVE books.
- **Hero (3):** the top 3 trending books with a cover, excluding the featured books. Backfill with the newest books that have a cover.
- **At serve time:** if the featured books drop below 4 because some sold, backfill from the same category. Don't rewrite the document.

**Top authors of the week** (`homepage:top-authors:{weekStart}`, 8 days):
- `weekScore = Σ views:week of the author's books + 5 × sold this week (soldDate) + 2 × newly listed this week`.
- Only authors with at least one ACTIVE book qualify. Exclude MERGED authors.
- Take the top 6; break ties by available count.
- Snapshot on Monday and refresh daily, so early-week data isn't empty.
- If fewer than 3 qualify, use the current `/author/top` logic. **`GET /author/top` itself is unchanged.**

**Admin endpoints (admin role):**

| Method | Endpoint | What |
|---|---|---|
| `GET` | `/admin/homepage/featured?week=` | The week's set (default: current) and recent history |
| `PUT` | `/admin/homepage/featured` | Override: `{ categoryId?, bookIds?, authorId?, heroBookIds?, note? }`. Omitted fields keep the AUTO choice. Validate that the IDs exist and the books are ACTIVE; at most 6 books and 3 hero books. Sets `source=ADMIN`, records `createdBy`, and evicts `homepage:featured:{week}` |
| `DELETE` | `/admin/homepage/featured?week=` | Drop the override and regenerate AUTO |
| `POST` | `/admin/homepage/refresh/{section}` | Rebuild one snapshot now: `stats`, `trending`, `featured`, `ai-picks`, `top-authors`, `reviews`, `sellers`, `new`, `categories` or `all` |

### 24e. 🟠 AI picks move into a job (and get reasons)

Today `BookService.getAIRecommendedBooks()` calls Gemini on the request when the cache is empty, and returns **500** if the call fails. Move generation into a daily job (**03:00 Lagos**). The request path only reads the cache.

1. **Candidate pool** (about 60 ACTIVE books): 20 trending + 20 newest + 20 random, deduplicated.
2. **Send** `id`, `title`, `author`, `description` (truncated to 300 chars), `categories` and `condition`.
3. **New `AiService` method.** It returns `[{ "id": "...", "reason": "..." }]`, with 8 picks, variety across categories and authors, and one-sentence reasons of at most 90 chars. The prompt is in the design doc §5.7.
4. **Parse into a typed DTO.** Drop IDs that weren't in the pool and trim the reasons. Fewer than 4 valid picks counts as a failure.
5. **On failure,** keep the previous `homepage:ai-picks` (extend its TTL to 48 h) and log it. **Never 500.**
6. **`GET /book/ai-picks`** keeps working, reading the same key and returning just the books.

### 24f. 🟢 Personalised picks: `GET /homepage/for-you` (logged in)

```json
{ "aiPicks": [ { "book": { }, "reason": "…" } ], "basedOn": ["Mystery & Thriller", "Chimamanda Ngozi Adichie"], "personalised": true }
```

- **Profile:** the top 3 categories and top 3 authors from the user's saved books and `recent:{id}`.
- **Pool:** about 40 ACTIVE books from those categories and authors, plus 10 trending. Exclude the user's own books, books already saved and books recently viewed.
- **Prompt:** the same as 24e, plus "The reader likes: …".
- **Cache:** `homepage:ai-picks:user:{id}` for 24 h.
- **Limits:** at most one generation per user per 6 h, an **8 s timeout** on the model call, and a per-user rate limit on the endpoint.
- **No history, timeout or failure:** return the global picks with `personalised: false`.
- **Logging:** don't log prompts that contain reading history at INFO.

### 24g. 🟢 Readers are saying and Trusted sellers

| Section | Rule | Redis key / TTL | Rebuilt |
|---|---|---|---|
| `reviews` | `seller_ratings` from the last 7 days with `stars ≥ 4` and a comment of at least 20 chars. Widen to 30 days if there are fewer than 3. Pick 5 at random, at most one per seller. Join the book (title and cover; SOLD is fine) and the reviewer's public username and avatar. | `homepage:reviews` / 1 h | hourly |
| `sellers` | At least 3 ratings and at least 1 ACTIVE book. Order by average, then count, then `soldCount`. Take 4 (`SellerSummary` with `rating`, plus `soldCount`). | `homepage:sellers` / 24 h | daily |

### 24h. 🟠 Jobs and config

Put everything in one `homepage/HomepageJobs`. Make each schedule a property with a default, like `views.flush-interval`, so the end-to-end test can run the jobs quickly. Every cron uses `zone = "Africa/Lagos"`.

| Job | Property | Default |
|---|---|---|
| Stats | `homepage.stats-interval` | `PT15M` |
| Trending + `views:week` | `homepage.trending-interval` | `PT1H` |
| Reviews | `homepage.reviews-interval` | `PT1H` |
| Sellers + top authors refresh | `homepage.daily-cron` | `0 30 2 * * *` |
| AI picks | `homepage.ai-picks-cron` | `0 0 3 * * *` |
| Featured + top authors snapshot | `homepage.weekly-cron` | `0 0 6 * * MON` |

- **Thread pool:** `SchedulingConfig` has 2 threads shared with views, notifications and the author import. Raise it to 4, so the AI job can't delay `ViewFlushJob`.
- **Logging and alerts:** log each job's duration and outcome. Alert after 3 consecutive AI-job failures, or when `GET /homepage` p95 is over 300 ms.

**Suggested order:**
1. 24a, 24b and 24c, so the frontend can switch to the bundle.
2. 24d and 24h.
3. 24e, then 24f.
4. 24g.

Each step can ship on its own; sections the bundle doesn't send yet come back `null` or `[]`.

### Decisions to confirm
1. **Featured:** is AUTO with an optional admin override right, or should someone approve each week first?
2. **AI picks cadence:** daily is recommended. Weekly is cheaper but goes stale as books sell.
3. **Small numbers:** is it fine to show `booksSold` while the number is small? The frontend can hide any stat under 100.
4. **Admin screen:** do featured overrides need one now, or are API calls enough for the first release?

### Not in this pass
- News and events (no content source yet).
- Real-time stats; a 15-minute delay is fine.
- Personalising anything except AI picks. Near you and Continue browsing use existing endpoints.
- An admin screen for featured overrides, unless decision 4 says otherwise.

## What the frontend will expect afterwards

**Success:** `{ message, status, data }`.

**Error:** `{ status, message, errors? }`.

**Register and login** return `data: { token, expiresIn, user }`, plus `refreshToken` if 5A is chosen.

**`user.profile.onboardingCompleted`** decides the route after login:
- `false` → `/welcome`
- `true` → `/`

**`POST /auth/confirm-email { email, token }`** verifies the email. **`GET /auth/confirm-email?email=`** resends the code.

**Onboarding** uses one call to `PATCH /user/{id}/profile` with this body:
```json
{ "userName": "...", "profilePictureId": "...",
  "address": { "country": "NG", "state": "...", "city": "...", "addressLine": "...",
               "formattedAddress": "...", "placeId": "...", "latitude": 0, "longitude": 0 } }
```
`address.state` and `address.city` are required (see #13). The other address fields are optional.
- `profilePictureId` is optional. It's the `id` returned by the multipart upload `POST /file` (field name `file`).
- The response is 200 when onboarding is complete, or 400 with `errors` when it isn't.
