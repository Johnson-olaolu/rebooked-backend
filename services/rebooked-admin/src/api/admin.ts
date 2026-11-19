import { isMockMode, request } from "./client";

export interface Page<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export interface Author {
  id: string;
  name: string;
  status: string;
  origin: string;
  verified: boolean;
  birthYear?: number;
  openReviewItems: number;
  sources: string[];
  normalizedName?: string;
  biography?: string;
  description?: string;
  workCount?: number;
  externalIds?: { source: string; sourceId: string }[];
  aliases?: { name: string; source: string }[];
  books?: { id: string; title: string; status: string }[];
}

export interface ReviewItem {
  id: string;
  type: string;
  status: string;
  reason?: string;
  createdAt: string;
  author: { id: string; name: string } | null;
  otherAuthor?: { id: string; name: string } | null;
}

export interface ImportJob {
  id: string;
  source: string;
  status: string;
  recordsRead: number;
  recordsCreated: number;
  recordsUpdated: number;
  recordsFailed: number;
  createdAt: string;
  errorSummary?: string;
}

export interface Category {
  id: string;
  name: string;
  description?: string;
  booksCount?: number;
}

export interface FeaturedSet {
  week: string;
  hero: string;
  author: string;
  featuredAuthors: string[];
  overrideActive: boolean;
}

const authors: Author[] = [
  { id: "chimamanda-adichie", name: "Chimamanda Ngozi Adichie", status: "VERIFIED", origin: "OPEN_LIBRARY", verified: true, birthYear: 1977, openReviewItems: 0, sources: ["OL26514A", "Q230352"], normalizedName: "chimamanda ngozi adichie", workCount: 18, biography: "Award-winning Nigerian writer known for vivid, humane stories about identity, family, and belonging.", books: [{ id: "b1", title: "Half of a Yellow Sun", status: "Published" }, { id: "b2", title: "Americanah", status: "Published" }, { id: "b3", title: "Purple Hibiscus", status: "Published" }], externalIds: [{ source: "OPEN_LIBRARY", sourceId: "OL26514A" }, { source: "WIKIDATA", sourceId: "Q230352" }] },
  { id: "haruki-murakami", name: "Haruki Murakami", status: "VERIFIED", origin: "WIKIDATA", verified: true, birthYear: 1949, openReviewItems: 1, sources: ["OL139435A", "Q160221"], workCount: 42, books: [{ id: "b4", title: "Kafka on the Shore", status: "Published" }, { id: "b5", title: "Norwegian Wood", status: "Published" }], externalIds: [{ source: "WIKIDATA", sourceId: "Q160221" }] },
  { id: "ngugi-wa-thiongo", name: "Ngũgĩ wa Thiong'o", status: "PENDING_REVIEW", origin: "PROPOSAL", verified: false, birthYear: 1938, openReviewItems: 2, sources: ["OL24925A"], workCount: 15 },
  { id: "mariama-ba", name: "Mariama Bâ", status: "MERGED", origin: "OPEN_LIBRARY", verified: false, birthYear: 1929, openReviewItems: 0, sources: ["Q273709"], workCount: 4 },
];

const reviews: ReviewItem[] = [
  { id: "review-1", type: "ALIAS_CONFLICT", status: "OPEN", createdAt: "2026-11-14", author: { id: "gabo", name: "Gabriel Garcia Marquez" }, reason: "Alias spelling differs from the verified display name." },
  { id: "review-2", type: "DUPLICATE_CANDIDATE", status: "OPEN", createdAt: "2026-11-15", author: { id: "gabo-accent", name: "Gabriel García Márquez" }, otherAuthor: { id: "gabo", name: "Gabriel Garcia Marquez" }, reason: "Possible duplicate records share multiple works." },
  { id: "review-3", type: "AUTHOR_SPLIT", status: "OPEN", createdAt: "2026-11-16", author: { id: "haruki-murakami", name: "Haruki Murakami" }, reason: "Review records before consolidating this author." },
];
const jobs: ImportJob[] = [
  { id: "import-1", source: "OPEN_LIBRARY", status: "COMPLETED", recordsRead: 48216, recordsCreated: 48216, recordsUpdated: 0, recordsFailed: 0, createdAt: "2026-11-18T03:00:00Z" },
  { id: "import-2", source: "WIKIDATA", status: "RUNNING", recordsRead: 31582, recordsCreated: 0, recordsUpdated: 31582, recordsFailed: 0, createdAt: "2026-11-19T03:00:00Z" },
  { id: "import-3", source: "OPEN_LIBRARY", status: "FAILED", recordsRead: 12804, recordsCreated: 0, recordsUpdated: 0, recordsFailed: 18, createdAt: "2026-11-17T03:00:00Z", errorSummary: "18 records could not be parsed." },
];
let categories: Category[] = [
  { id: "fiction", name: "Fiction", booksCount: 1842 },
  { id: "african-literature", name: "African Literature", booksCount: 386 },
  { id: "biography", name: "Biography", booksCount: 294 },
  { id: "science-fiction", name: "Science Fiction", booksCount: 219 },
];
let featured: FeaturedSet = {
  week: "2026-11-16",
  hero: "Purple Hibiscus — Chimamanda Ngozi Adichie",
  author: "Chimamanda Ngozi Adichie",
  featuredAuthors: ["Mariama Bâ", "Ngũgĩ wa Thiong'o", "Haruki Murakami"],
  overrideActive: true,
};
const pause = () => new Promise((resolve) => window.setTimeout(resolve, 220));
function pageOf<T>(items: T[]): Page<T> {
  return { content: items, page: 0, pageSize: items.length, totalElements: items.length, totalPages: items.length ? 1 : 0 };
}
function mockRequired(): never {
  throw new Error("This action is available when connected to the Rebooked API.");
}

export const adminApi = {
  async login(email: string, password: string) {
    if (isMockMode) {
      await pause();
      if (email.toLowerCase() !== "superadmin@rebooked.test" || password !== "Admin_123") {
        throw new Error("Use the preview account shown on this sign-in page.");
      }
      return { token: "mock-super-admin-session", refreshToken: "mock-refresh-token", expiresIn: 3600, user: { id: "preview-admin", email, role: "superAdmin" } };
    }
    return request<{ token: string; refreshToken: string; expiresIn: number; user: { id: string; email: string } }>("/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
  },
  async listAuthors(query = "") {
    if (!isMockMode) return request<Page<Author>>(`/admin/author?q=${encodeURIComponent(query)}&page=0&pageSize=50`);
    await pause();
    return pageOf(authors.filter((author) => author.name.toLowerCase().includes(query.toLowerCase())));
  },
  async getAuthor(id: string) {
    if (!isMockMode) return request<Author>(`/admin/author/${encodeURIComponent(id)}`);
    await pause();
    const author = authors.find((item) => item.id === id);
    if (!author) throw new Error("Author not found.");
    return author;
  },
  async verifyAuthor(id: string) {
    if (!isMockMode) return request<Author>(`/admin/author/${encodeURIComponent(id)}/verify`, { method: "POST" });
    const author = authors.find((item) => item.id === id);
    if (author) { author.verified = true; author.status = "VERIFIED"; }
    await pause();
    return author!;
  },
  async listReviews() {
    if (!isMockMode) return request<Page<ReviewItem>>("/admin/author/review?status=OPEN&page=0&pageSize=50");
    await pause();
    return pageOf(reviews.filter((item) => item.status === "OPEN"));
  },
  async keepSeparate(id: string) {
    if (!isMockMode) return request<ReviewItem>(`/admin/author/review/${encodeURIComponent(id)}/keep-separate`, { method: "POST", body: JSON.stringify({}) });
    const item = reviews.find((review) => review.id === id);
    if (item) item.status = "RESOLVED";
    await pause();
  },
  async listImports() {
    if (!isMockMode) return request<Page<ImportJob>>("/admin/author-import?page=0&pageSize=50");
    await pause();
    return pageOf(jobs);
  },
  async startImport(source: string) {
    if (!isMockMode) return request<ImportJob>("/admin/author-import", { method: "POST", body: JSON.stringify({ source }) });
    if (jobs.some((job) => job.status === "RUNNING")) throw new Error("An import is already running.");
    const job: ImportJob = { id: `import-${Date.now()}`, source, status: "PENDING", recordsRead: 0, recordsCreated: 0, recordsUpdated: 0, recordsFailed: 0, createdAt: new Date().toISOString() };
    jobs.unshift(job);
    await pause();
    return job;
  },
  async listCategories() {
    if (!isMockMode) return request<Category[]>("/category");
    await pause();
    return [...categories];
  },
  async createCategory(name: string, description = "") {
    if (!isMockMode) return request<Category>("/category", { method: "POST", body: JSON.stringify({ name, description }) });
    if (categories.some((item) => item.name.toLowerCase() === name.toLowerCase())) throw new Error("A category with this name already exists.");
    const category = { id: `category-${Date.now()}`, name, description, booksCount: 0 };
    categories = [...categories, category];
    await pause();
    return category;
  },
  async renameCategory(id: string, name: string) {
    if (!isMockMode) return request<Category>(`/category/${encodeURIComponent(id)}`, { method: "PATCH", body: JSON.stringify({ name }) });
    categories = categories.map((item) => item.id === id ? { ...item, name } : item);
    await pause();
  },
  async deleteCategory(id: string) {
    if (!isMockMode) return request<void>(`/category/${encodeURIComponent(id)}`, { method: "DELETE" });
    categories = categories.filter((item) => item.id !== id);
    await pause();
  },
  async getFeatured(week: string) {
    if (!isMockMode) return request<FeaturedSet>(`/admin/homepage/featured?week=${encodeURIComponent(week)}`);
    await pause();
    return { ...featured, week };
  },
  async saveFeatured(value: FeaturedSet) {
    if (!isMockMode) return request<FeaturedSet>("/admin/homepage/featured", { method: "PUT", body: JSON.stringify({ bookIds: [], heroBookIds: [], authorId: "", note: value.hero }) });
    featured = { ...value, overrideActive: true };
    await pause();
    return featured;
  },
  async removeFeatured(week: string) {
    if (!isMockMode) return request<FeaturedSet>(`/admin/homepage/featured?week=${encodeURIComponent(week)}`, { method: "DELETE" });
    featured = { ...featured, overrideActive: false };
    await pause();
  },
  async refreshSection(section: string) {
    if (!isMockMode) return request<unknown>(`/admin/homepage/refresh/${encodeURIComponent(section)}`, { method: "POST" });
    await pause();
  },
};

export const mockAccount = { email: "superadmin@rebooked.test", password: "Admin_123" };
export function unavailableAction(): never { return mockRequired(); }
