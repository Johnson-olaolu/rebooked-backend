import type {
  AdminUser,
  Author,
  AuthorAuditEntry,
  AuthorBook,
  Category,
  FeaturedSet,
  ImportError,
  ImportJob,
  PreviewCredentials,
  ReviewItem,
} from "../api/types";

export const mockAccount: PreviewCredentials = {
  email: "superadmin@rebooked.test",
  password: "Admin_123",
};

export const mockUser: AdminUser = {
  id: "preview-admin",
  email: mockAccount.email,
  role: "superAdmin",
};

export const mockAuthors: Author[] = [
  {
    id: "chimamanda-adichie",
    name: "Chimamanda Ngozi Adichie",
    status: "VERIFIED",
    origin: "OPEN_LIBRARY",
    verified: true,
    birthYear: 1977,
    openReviewItems: 0,
    sources: ["OL26514A", "Q230352"],
    normalizedName: "chimamanda ngozi adichie",
    workCount: 18,
    biography: "Award-winning Nigerian writer known for vivid, humane stories about identity, family, and belonging.",
    books: [
      { id: "b1", title: "Half of a Yellow Sun", status: "Published" },
      { id: "b2", title: "Americanah", status: "Published" },
      { id: "b3", title: "Purple Hibiscus", status: "Published" },
    ],
    externalIds: [
      { source: "OPEN_LIBRARY", sourceId: "OL26514A" },
      { source: "WIKIDATA", sourceId: "Q230352" },
    ],
  },
  {
    id: "haruki-murakami",
    name: "Haruki Murakami",
    status: "VERIFIED",
    origin: "WIKIDATA",
    verified: true,
    birthYear: 1949,
    openReviewItems: 1,
    sources: ["OL139435A", "Q160221"],
    workCount: 42,
    books: [
      { id: "b4", title: "Kafka on the Shore", status: "Published" },
      { id: "b5", title: "Norwegian Wood", status: "Published" },
    ],
    externalIds: [{ source: "WIKIDATA", sourceId: "Q160221" }],
  },
  {
    id: "ngugi-wa-thiongo",
    name: "Ngũgĩ wa Thiong'o",
    status: "PENDING_REVIEW",
    origin: "PROPOSAL",
    verified: false,
    birthYear: 1938,
    openReviewItems: 2,
    sources: ["OL24925A"],
    workCount: 15,
  },
  {
    id: "mariama-ba",
    name: "Mariama Bâ",
    status: "MERGED",
    origin: "OPEN_LIBRARY",
    verified: false,
    birthYear: 1929,
    openReviewItems: 0,
    sources: ["Q273709"],
    workCount: 4,
  },
];

export const mockReviews: ReviewItem[] = [
  {
    id: "review-1",
    type: "ALIAS_CONFLICT",
    status: "OPEN",
    createdAt: "2026-11-14",
    author: { id: "gabo", name: "Gabriel Garcia Marquez" },
    reason: "Alias spelling differs from the verified display name.",
  },
  {
    id: "review-2",
    type: "DUPLICATE_CANDIDATE",
    status: "OPEN",
    createdAt: "2026-11-15",
    author: { id: "gabo-accent", name: "Gabriel García Márquez" },
    otherAuthor: { id: "gabo", name: "Gabriel Garcia Marquez" },
    reason: "Possible duplicate records share multiple works.",
  },
  {
    id: "review-3",
    type: "AUTHOR_SPLIT",
    status: "OPEN",
    createdAt: "2026-11-16",
    author: { id: "haruki-murakami", name: "Haruki Murakami" },
    reason: "Review records before consolidating this author.",
  },
];

export const mockImports: ImportJob[] = [
  {
    id: "import-1",
    source: "OPEN_LIBRARY",
    status: "COMPLETED",
    recordsRead: 48216,
    recordsCreated: 48216,
    recordsUpdated: 0,
    recordsFailed: 0,
    createdAt: "2026-11-18T03:00:00Z",
  },
  {
    id: "import-2",
    source: "WIKIDATA",
    status: "RUNNING",
    recordsRead: 31582,
    recordsCreated: 0,
    recordsUpdated: 31582,
    recordsFailed: 0,
    createdAt: "2026-11-19T03:00:00Z",
  },
  {
    id: "import-3",
    source: "OPEN_LIBRARY",
    status: "FAILED",
    recordsRead: 12804,
    recordsCreated: 0,
    recordsUpdated: 0,
    recordsFailed: 18,
    createdAt: "2026-11-17T03:00:00Z",
    errorSummary: "18 records could not be parsed.",
  },
];

export const mockImportErrors: ImportError[] = [
  {
    id: "import-error-1",
    recordId: "OL12345A",
    message: "The source record did not include a usable author name.",
    createdAt: "2026-11-17T03:04:00Z",
  },
];

export const mockCategories: Category[] = [
  { id: "fiction", name: "Fiction", booksCount: 1842 },
  { id: "african-literature", name: "African Literature", booksCount: 386 },
  { id: "biography", name: "Biography", booksCount: 294 },
  { id: "science-fiction", name: "Science Fiction", booksCount: 219 },
];

export const mockFeatured: FeaturedSet = {
  week: "2026-11-16",
  hero: "Purple Hibiscus — Chimamanda Ngozi Adichie",
  author: "Chimamanda Ngozi Adichie",
  featuredAuthors: ["Mariama Bâ", "Ngũgĩ wa Thiong'o", "Haruki Murakami"],
  overrideActive: true,
};

export const mockAudit: AuthorAuditEntry[] = [
  {
    id: "audit-1",
    action: "AUTHOR_VERIFIED",
    actor: "superadmin@rebooked.test",
    createdAt: "2026-11-18T10:00:00Z",
    details: "Author identity confirmed.",
  },
  {
    id: "audit-2",
    action: "EXTERNAL_ID_LINKED",
    actor: "superadmin@rebooked.test",
    createdAt: "2026-11-17T09:30:00Z",
    details: "Open Library identifier linked.",
  },
];

export const mockBooks: AuthorBook[] = mockAuthors.flatMap((author) => author.books ?? []);
