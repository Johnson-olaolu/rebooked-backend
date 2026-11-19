# Integration Plan

## Services
- Backend: repository root; Spring Boot / Gradle. Run `./gradlew bootRun` with local `.env` values, including `PORT=5001`; API base is `http://localhost:5001/api`. Build: `./gradlew build`. Tests: `./gradlew test`.
- Health: no `/health` endpoint exists. Swagger UI: `http://localhost:5001/api/documentation`; OpenAPI JSON: `http://localhost:5001/api/v3/api-docs`.
- Frontend: `services/rebooked-admin/`; build `npm --prefix services/rebooked-admin run build`; dev `npm --prefix services/rebooked-admin run dev -- --host 0.0.0.0`.
- Live API origin: `VITE_API_BASE_URL=http://localhost:5001/api`; mock mode is default; live mode uses `VITE_MOCK_API=false`.

## Frontend Integration Seam
- Swap point: `services/rebooked-admin/src/api/index.ts`; it currently chooses `mockClient` or `liveClient`.
- Mock-only files to remove after live wiring: `src/api/mockClient.ts`, `src/api/previewState.ts`, `src/components/PreviewStateSwitcher.tsx`; remove the switcher render/import from `src/App.tsx` as well.
- Keep pages consuming the `api` object and contracts from `src/api/types.ts`; do not move request logic into pages.
- Existing sign-in/refresh token flow uses `/auth/login` and `/auth/refresh-token`. Public `/auth/register` creates regular marketplace users, not super-admins; do not expose it as admin-account creation. Continue to rely on existing super-admin provisioning/authority. No dedicated current-user endpoint has been identified; `getCurrentUser()` currently derives the admin identity from the authenticated local session.
- Spring responses use `{ status, message, data }`; `src/api/client.ts` unwraps `data`. Preserve this handling and inspect actual DTO field names before mapping.

## API Routes
All paths below are relative to the `/api` context path.

### Auth
- `POST /auth/login` — `{ email, password }`
- `POST /auth/refresh-token` — `{ refreshToken }`
- `POST /auth/logout` — `{ refreshToken }`
- `POST /auth/register` — existing public regular-user registration; not an admin workflow

### Author Catalogue
- `GET /admin/author?q=&status=&origin=&page=&pageSize=`
- `GET /admin/author/{id}`
- `PATCH /admin/author/{id}`
- `POST /admin/author/{id}/verify`
- `POST /admin/author/{id}/external-ids` — `{ source, sourceId }`
- `DELETE /admin/author/{id}/external-ids/{source}/{sourceId}`
- `GET /admin/author/{id}/books?page=&pageSize=`
- `GET /admin/author/{id}/audit?page=&pageSize=`
- `GET /admin/author/{id}/merge-preview?intoId=`
- `POST /admin/author/{id}/merge` — `{ intoId }`
- `DELETE /admin/author/{id}`

### Reviews and Imports
- `GET /admin/author/review?type=&status=&page=&pageSize=`
- `POST /admin/author/review/{itemId}/keep-separate` — optional reason payload
- `GET /admin/author-import?page=&pageSize=`
- `GET /admin/author-import/{id}`
- `GET /admin/author-import/{id}/errors?page=&pageSize=`
- `POST /admin/author-import` — `{ source }`

### Homepage and Categories
- `GET /admin/homepage/featured?week=`
- `PUT /admin/homepage/featured` — existing `FeaturedOverride` DTO; map actual DTO fields, not display-only mock fields
- `DELETE /admin/homepage/featured?week=`
- `POST /admin/homepage/refresh/{section}`
- `GET /category`
- `POST /category` — `{ name }` (confirm optional DTO fields against backend)
- `PATCH /category/{id}`
- `DELETE /category/{id}`

## Data and Types
- Database: existing MongoDB (`MONGODB_URI`, default local URI in plan); no new database or schema is planned. Migration tool/directory: none for this change. Explicitly create **NO seed data** and do not run demo-data reset/seeding commands.
- Existing service configuration: MongoDB Essential; Redis Enhancement (`REDIS_HOST`, `REDIS_PORT`); Cloudinary Enhancement (`CLOUDINARY_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_FOLDER_NAME`). JWT config: `SECRET_KEY`, `JWT_EXPIRATION_TIME`, `JWT_REFRESH_EXPIRATION_TIME`.
- Shared package: none. Frontend API types currently live in `services/rebooked-admin/src/api/types.ts`; there is no cross-workspace import alias.

## Verification
- Build frontend and backend; run `./gradlew test`.
- Smoke-test listed routes against the running backend with a provisioned super-admin JWT. Verify the frontend response mapping against actual DTOs and authorization. No health probe is available; use the OpenAPI endpoint as the backend availability check.
