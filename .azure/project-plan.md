# Project Plan

**Status**: In Progress
**Created**: 2026-11-19
**Mode**: AUGMENT

---

## 1. Project Overview

**Goal**: Build a presentation-quality, responsive super-admin frontend for the existing Rebooked Java backend. The interface will cover author catalogue management, author review decisions, author imports, homepage featured configuration, and category management while preserving the backend's existing JWT authorization and REST contracts.

**App Type**: SPA + API

**API Login**: Yes

**Mode**: AUGMENT

**Deployment Plan**: No deployment plan found

---

## 2. Backend - Spring Boot

| Component | Technology |
|-----------|-----------|
| **Language** | Java |
| **Runtime** | Java 21 |
| **Framework** | Spring Boot |
| **Package Manager** | Gradle |
| **Test Runner** | JUnit 5 |
| **Mocking Library** | Mockito |
| **Test Command** | ./gradlew test |
| **Orchestration** | docker-compose |

The existing backend remains the source of truth. The frontend will consume the existing `/api/auth`, `/api/admin/author`, `/api/admin/author-import`, `/api/admin/homepage`, and `/api/category` endpoints. Super-admin routes will continue to use the JWT bearer token and `superAdmin` authority; no backend authorization changes or new datastore are assumed.

---

## 3. Frontend — Web App

| Component | Technology |
|-----------|-----------|
| **Language** | TypeScript |
| **Framework** | React + Vite |
| **Package Manager** | npm |
| **Test Runner** | vitest |
| **Mocking Library** | vi.mock |
| **Test Command** | npm test |

---

## 4. Services Required

| Azure Service | Role in App | Environment Variable | Default Value (Local) | Classification |
|---------------|------------|---------------------|----------------------|----------------|
| MongoDB | Existing primary document datastore for authors, imports, homepage configuration, and categories | MONGODB_URI | mongodb://localhost:27017/rebooked | Essential |
| Redis | Existing cache and homepage/view support | REDIS_URL | redis://localhost:6379 | Enhancement |
| Cloudinary | Existing external image/file storage integration | CLOUDINARY_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET, CLOUDINARY_FOLDER_NAME | Configure with local account credentials | Enhancement |

---

## 5. Prerequisites

### Run

| Tool | Service(s) | Installed | Version |
|------|------------|-----------|---------|
| JDK 21 or later | rebooked-api | ✅ | 24.0.1 |
| Gradle 8 | rebooked-api | ✅ | 8.11.1 |
| MongoDB | rebooked-api | ❓ | Unknown |
| Redis | rebooked-api | ❓ | Unknown |
| Node.js 20 or later | admin-frontend | ✅ | v24.11.1 |
| npm | admin-frontend | ✅ | 11.6.2 |

### Debug

| Tool | Service(s) | Installed | Version |
|------|------------|-----------|---------|
| Docker | rebooked-api | ✅ | 28.5.1 |
| Docker Compose | rebooked-api | ✅ | v2.40.3-desktop.1 |
| Extension Pack for Java | rebooked-api | ❓ | Unknown |
| Spring Boot Tools | rebooked-api | ❓ | Unknown |
| TypeScript and JavaScript Language Features | admin-frontend | ❓ | Unknown |
| Debugger for Java | rebooked-api | ❓ | Unknown |

---

## 6. Design System & UI

**Component Library**: Fluent UI v9

**Style Direction**: Editorial-console interface for a book marketplace: warm paper surfaces, ink-dark text, deep teal navigation, restrained gold accents, and compact data-density for catalogue workflows.

**Typography**: Segoe UI Variable, Segoe UI, system-ui

### Color Palette

| Token | Hex | Usage |
|-------|-----|-------|
| `primary` | `#16605A` | Primary author actions, active navigation, verification controls |
| `accent` | `#C78B35` | Import and featured-homepage highlights |
| `surface` | `#F7F5F0` | Page canvas and warm neutral backgrounds |
| `text` | `#1E2932` | Primary author names, labels, and table values |
| `muted` | `#67756F` | Captions, pagination, timestamps, and secondary metadata |
| `border` | `#DDE3DF` | Dividers, fields, cards, and table rules |

### Pages

| Page | Route | Purpose | Layout |
|------|-------|---------|--------|
| Sign In | `/login` | Authenticate a super-admin and establish the JWT session | `header,main,form` |
| Author Catalogue | `/authors` | Search and manage authors, verification, identifiers, and review state | `header,sidebar,main,table,actions` |
| Author Detail | `/authors/:id` | Review an author's detail, books, identifiers, merge options, and audit history | `header,sidebar,main,split(a|b),tabs,action-bar` |
| Review Queue | `/reviews` | Resolve author review items and keep conflicting records separate | `header,sidebar,main,table,actions` |
| Import Jobs | `/imports` | Start catalogue imports and inspect job history and errors | `header,sidebar,main,table,actions,modal` |
| Featured Homepage | `/homepage` | Edit the featured weekly set and refresh homepage sections | `header,sidebar,main,two-column(a+b),form,actions` |
| Category Management | `/categories` | Create, edit, and remove marketplace categories | `header,sidebar,main,table,form,actions,modal` |

### Sample Content

`Sign In` — super-admin:
| Field | Realistic default |
|-------|-------------------|
| Email | superadmin@rebooked.test |
| Password | Password input hidden until submitted |

`Author Catalogue` — authors:
| Author | Status | Origin | Open reviews | Identifiers |
|--------|--------|--------|--------------|-------------|
| Chimamanda Ngozi Adichie | Verified | OPEN_LIBRARY | 0 | Open Library: OL26514A |
| Haruki Murakami | Verified | WIKIDATA | 1 | Wikidata: Q160221 |
| Ngũgĩ wa Thiong'o | Pending review | PROPOSAL | 2 | Open Library: OL24925A |
| Mariama Bâ | Merged | OPEN_LIBRARY | 0 | Wikidata: Q273709 |

`Author Detail` — author:
| Field | Value |
|-------|-------|
| Name | Chimamanda Ngozi Adichie |
| Birth year | 1977 |
| Status | Verified |
| Linked books | Half of a Yellow Sun · Americanah · Purple Hibiscus |

`Review Queue` — review items:
| Name | Type | Status | Oldest first |
|------|------|--------|--------------|
| Gabriel Garcia Marquez | ALIAS_CONFLICT | OPEN | 2026-11-14 |
| Gabriel García Márquez | DUPLICATE_CANDIDATE | OPEN | 2026-11-15 |
| Haruki Murakami | AUTHOR_SPLIT | OPEN | 2026-11-16 |

`Import Jobs` — jobs:
| Source | Status | Progress | Started |
|--------|--------|----------|---------|
| OPEN_LIBRARY | COMPLETED | 48,216 authors | 2026-11-18 03:00 |
| WIKIDATA | RUNNING | 62% | 2026-11-19 03:00 |
| OPEN_LIBRARY | FAILED | 12,804 authors | 2026-11-17 03:00 |

`Featured Homepage` — fields:
| Field | Realistic default |
|-------|-------------------|
| Week | 2026-11-16 |
| Hero | Purple Hibiscus — Chimamanda Ngozi Adichie |
| Featured authors | Mariama Bâ · Ngũgĩ wa Thiong'o · Haruki Murakami |

`Category Management` — categories:
| Category | Books | Status |
|----------|-------|--------|
| Fiction | 1,842 | Active |
| African Literature | 386 | Active |
| Biography | 294 | Active |
| Science Fiction | 219 | Active |

---

## 7. Project Structure

```
rebooked-backend/
├── src/main/java/com/personal/rebooked/   ← existing Spring Boot backend
├── src/test/java/com/personal/rebooked/   ← existing JUnit tests
├── services/
│   └── rebooked-admin/
│       ├── package.json
│       ├── vite.config.ts
│       └── src/
│           ├── api/
│           │   ├── client.ts
│           │   └── admin.ts
│           ├── auth/
│           ├── components/
│           ├── pages/
│           │   ├── authors/
│           │   ├── reviews/
│           │   ├── imports/
│           │   ├── homepage/
│           │   └── categories/
│           └── test/
├── docker-compose.yml
└── .azure/
    └── project-plan.md
```

---

## 8. Route Definitions

| # | Method | Path | Description | Request Body | Response Body | Status Codes |
|---|--------|------|-------------|-------------|--------------|-------------|
| 1 | POST | `/api/auth/login` | Sign in and receive JWT tokens | `{ email, password }` | `{ accessToken, refreshToken, user }` | 200, 400, 401 |
| 2 | POST | `/api/auth/refresh-token` | Refresh an admin session | `{ refreshToken }` | `{ accessToken, refreshToken }` | 200, 400, 401 |
| 3 | GET | `/api/admin/author?q=&status=&origin=&page=&pageSize=` | Search paginated author catalogue | — | `{ content, page, pageSize, totalElements, totalPages }` | 200, 403 |
| 4 | GET | `/api/admin/author/{id}` | Load author detail | — | Author detail object | 200, 403, 404 |
| 5 | PATCH | `/api/admin/author/{id}` | Edit author fields | Author patch object | Author detail object | 200, 400, 403, 404 |
| 6 | POST | `/api/admin/author/{id}/verify` | Verify an author | — | Author detail object | 200, 403, 404 |
| 7 | POST | `/api/admin/author/{id}/external-ids` | Add an external identifier | `{ source, sourceId }` | Author detail object | 200, 400, 403, 404 |
| 8 | DELETE | `/api/admin/author/{id}/external-ids/{source}/{sourceId}` | Remove an external identifier | — | Author detail object | 200, 403, 404 |
| 9 | GET | `/api/admin/author/{id}/books?page=&pageSize=` | List all books linked to an author | — | Paginated books | 200, 403, 404 |
| 10 | GET | `/api/admin/author/{id}/audit?page=&pageSize=` | Inspect author audit entries | — | Paginated audit entries | 200, 403, 404 |
| 11 | GET | `/api/admin/author/{id}/merge-preview?intoId=` | Preview a merge | — | Merge preview object | 200, 403, 404 |
| 12 | POST | `/api/admin/author/{id}/merge` | Merge the author into another record | `{ intoId }` | Surviving author detail | 200, 400, 403, 404, 409 |
| 13 | DELETE | `/api/admin/author/{id}` | Delete an author | — | Empty response | 200, 403, 404 |
| 14 | GET | `/api/admin/author/review?type=&status=&page=&pageSize=` | List review queue items | — | Paginated review items | 200, 403 |
| 15 | POST | `/api/admin/author/review/{itemId}/keep-separate` | Keep two records separate | Optional reason object | Review item response | 200, 400, 403, 404 |
| 16 | GET | `/api/admin/author-import?page=&pageSize=` | List author import jobs | — | Paginated import jobs | 200, 403 |
| 17 | GET | `/api/admin/author-import/{id}` | Load import job detail | — | Import job object | 200, 403, 404 |
| 18 | GET | `/api/admin/author-import/{id}/errors?page=&pageSize=` | List import errors | — | Paginated import errors | 200, 403, 404 |
| 19 | POST | `/api/admin/author-import` | Start an import | `{ source }` | Import job object | 202, 400, 403, 409 |
| 20 | GET | `/api/admin/homepage/featured?week=` | Load featured homepage configuration | — | Featured homepage admin view | 200, 403 |
| 21 | PUT | `/api/admin/homepage/featured` | Apply a featured override | Featured override object | Featured homepage admin view | 200, 400, 403 |
| 22 | DELETE | `/api/admin/homepage/featured?week=` | Remove a featured override | — | Featured homepage admin view | 200, 400, 403 |
| 23 | POST | `/api/admin/homepage/refresh/{section}` | Rebuild a homepage section | — | Rebuild status | 200, 400, 403 |
| 24 | GET | `/api/category` | List categories | — | Category list | 200 |
| 25 | POST | `/api/category` | Create a category | `{ name }` | Category object | 200, 400, 403 |
| 26 | PATCH | `/api/category/{id}` | Update a category | Category update object | Category object | 200, 400, 403, 404 |
| 27 | DELETE | `/api/category/{id}` | Delete a category | — | Empty response | 200, 403, 404 |

---

## 9. Next Steps

1. Run **azure-project-scaffold** to execute this plan
2. Run **azure-project-integrate** to wire the frontend to live data, smoke-test the backend, and create the migrations
3. Run **azure-debug-plan** → **azure-debug-generate** for Docker emulators and VS Code debugging
4. Run the **azure-deploy** agent when ready; it uses **azure-app-onboard** for architecture, cost estimation, IaC generation, provisioning, and health verification
