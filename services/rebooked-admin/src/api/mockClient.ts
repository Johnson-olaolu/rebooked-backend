import {
  mockAccount,
  mockAuthors,
  mockAudit,
  mockCategories,
  mockFeatured,
  mockImportErrors,
  mockImports,
  mockReviews,
  mockUser,
} from "../mocks/data";
import { getPreviewDataState } from "./previewState";
import type {
  ApiClient,
  Author,
  AuthorAuditEntry,
  AuthorBook,
  AuthorPatchInput,
  Category,
  ExternalId,
  FeaturedSet,
  ImportError,
  ImportJob,
  Page,
  ReviewQuery,
} from "./types";

const authors = structuredClone(mockAuthors);
const reviews = structuredClone(mockReviews);
const imports = structuredClone(mockImports);
let categories = structuredClone(mockCategories);
let featured = structuredClone(mockFeatured);

function pageOf<T>(items: T[], page = 0, pageSize = items.length): Page<T> {
  const start = page * pageSize;
  return {
    content: items.slice(start, start + pageSize),
    page,
    pageSize,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / pageSize),
  };
}

async function applyState(): Promise<"data" | "empty"> {
  const state = getPreviewDataState();
  if (state === "loading") await new Promise<void>(() => undefined);
  if (state === "error") throw new Error("The Rebooked preview API is temporarily unavailable.");
  await new Promise<void>((resolve) => window.setTimeout(resolve, 180));
  return state === "empty" ? "empty" : "data";
}

function requiredAuthor(id: string): Author {
  const author = authors.find((item) => item.id === id);
  if (!author) throw new Error("Author not found.");
  return author;
}

export const mockClient: ApiClient = {
  async login(email: string, password: string) {
    await applyState();
    if (email.toLowerCase() !== mockAccount.email || password !== mockAccount.password) {
      throw new Error("Use the preview account shown on this sign-in page.");
    }
    return {
      token: "mock-super-admin-session",
      refreshToken: "mock-refresh-token",
      expiresIn: 3600,
      user: { ...mockUser, email },
    };
  },
  async refreshToken(refreshToken: string) {
    await applyState();
    if (refreshToken !== "mock-refresh-token") throw new Error("The preview session has expired.");
    return {
      token: "mock-super-admin-session",
      refreshToken,
      expiresIn: 3600,
      user: { ...mockUser },
    };
  },
  async getCurrentUser() {
    return { ...mockUser };
  },
  getPreviewCredentials() {
    return { ...mockAccount };
  },
  async listAuthors(query = "") {
    const state = await applyState();
    const matches = state === "empty" ? [] : authors.filter((author) => author.name.toLowerCase().includes(query.toLowerCase()));
    return pageOf(matches, 0, 50);
  },
  async getAuthor(id: string) {
    if ((await applyState()) === "empty") return null;
    return requiredAuthor(id);
  },
  async updateAuthor(id: string, input: AuthorPatchInput) {
    if ((await applyState()) === "empty") return null;
    const author = requiredAuthor(id);
    Object.assign(author, input);
    return author;
  },
  async verifyAuthor(id: string) {
    if ((await applyState()) === "empty") return null;
    const author = requiredAuthor(id);
    author.verified = true;
    author.status = "VERIFIED";
    return author;
  },
  async addExternalId(id: string, input: ExternalId) {
    if ((await applyState()) === "empty") return null;
    const author = requiredAuthor(id);
    author.externalIds = [...(author.externalIds ?? []), input];
    author.sources = [...author.sources, input.sourceId];
    return author;
  },
  async removeExternalId(id: string, source: string, sourceId: string) {
    if ((await applyState()) === "empty") return null;
    const author = requiredAuthor(id);
    author.externalIds = (author.externalIds ?? []).filter((item) => item.source !== source || item.sourceId !== sourceId);
    author.sources = author.sources.filter((item) => item !== sourceId);
    return author;
  },
  async listAuthorBooks(id: string, page = 0, pageSize = 50) {
    const state = await applyState();
    const books = state === "empty" ? [] : (requiredAuthor(id).books ?? []);
    return pageOf<AuthorBook>(books, page, pageSize);
  },
  async listAuthorAudit(_id: string, page = 0, pageSize = 50) {
    const state = await applyState();
    return pageOf<AuthorAuditEntry>(state === "empty" ? [] : mockAudit, page, pageSize);
  },
  async getMergePreview(id: string, intoId: string) {
    if ((await applyState()) === "empty") return null;
    return { source: requiredAuthor(id), target: requiredAuthor(intoId) };
  },
  async mergeAuthor(id: string, intoId: string) {
    await applyState();
    const target = requiredAuthor(intoId);
    const source = requiredAuthor(id);
    target.sources = [...new Set([...target.sources, ...source.sources])];
    const index = authors.findIndex((author) => author.id === id);
    if (index >= 0) authors.splice(index, 1);
    return target;
  },
  async deleteAuthor(id: string) {
    await applyState();
    const index = authors.findIndex((author) => author.id === id);
    if (index >= 0) authors.splice(index, 1);
  },
  async listReviews(query: ReviewQuery = {}) {
    const state = await applyState();
    const matches =
      state === "empty" ? [] : reviews.filter((item) => (!query.status || item.status === query.status) && (!query.type || item.type === query.type));
    return pageOf(matches, query.page ?? 0, query.pageSize ?? 50);
  },
  async keepSeparate(id: string, reason?: string) {
    await applyState();
    const item = reviews.find((review) => review.id === id);
    if (!item) throw new Error("Review item not found.");
    item.status = "RESOLVED";
    if (reason) item.reason = reason;
    return item;
  },
  async listImports() {
    const state = await applyState();
    return pageOf<ImportJob>(state === "empty" ? [] : imports, 0, 50);
  },
  async getImport(id: string) {
    if ((await applyState()) === "empty") return null;
    return imports.find((item) => item.id === id) ?? null;
  },
  async listImportErrors(id: string, page = 0, pageSize = 50) {
    const state = await applyState();
    const errors = state === "empty" || id !== "import-3" ? [] : mockImportErrors;
    return pageOf<ImportError>(errors, page, pageSize);
  },
  async startImport(source: string) {
    await applyState();
    if (imports.some((job) => job.status === "RUNNING")) throw new Error("An import is already running.");
    const job: ImportJob = {
      id: `import-${Date.now()}`,
      source,
      status: "PENDING",
      recordsRead: 0,
      recordsCreated: 0,
      recordsUpdated: 0,
      recordsFailed: 0,
      createdAt: new Date().toISOString(),
    };
    imports.unshift(job);
    return job;
  },
  async listCategories() {
    const state = await applyState();
    return state === "empty" ? [] : [...categories];
  },
  async createCategory(name: string, description = "") {
    await applyState();
    if (categories.some((item) => item.name.toLowerCase() === name.toLowerCase())) {
      throw new Error("A category with this name already exists.");
    }
    const category = { id: `category-${Date.now()}`, name, description, booksCount: 0 };
    categories = [...categories, category];
    return category;
  },
  async renameCategory(id: string, name: string) {
    await applyState();
    let updated: Category | undefined;
    categories = categories.map((item) => {
      if (item.id !== id) return item;
      updated = { ...item, name };
      return updated;
    });
    if (!updated) throw new Error("Category not found.");
    return updated;
  },
  async deleteCategory(id: string) {
    await applyState();
    categories = categories.filter((item) => item.id !== id);
  },
  async getFeatured(week: string) {
    if ((await applyState()) === "empty") return null;
    return { ...featured, week };
  },
  async saveFeatured(value: FeaturedSet) {
    await applyState();
    featured = { ...value, overrideActive: true };
    return { ...featured };
  },
  async removeFeatured(week: string) {
    await applyState();
    featured = { ...featured, week, overrideActive: false };
    return { ...featured };
  },
  async refreshSection(section: string) {
    await applyState();
    return { section, status: "QUEUED" };
  },
};
