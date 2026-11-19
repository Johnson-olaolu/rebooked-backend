import { request } from "./client";
import type {
  AdminUser,
  ApiClient,
  Author,
  AuthorAuditEntry,
  AuthorBook,
  AuthorPatchInput,
  Category,
  ExternalId,
  FeaturedOverrideInput,
  FeaturedSet,
  ImportError,
  ImportJob,
  LoginResponse,
  Page,
  ReviewItem,
  ReviewQuery,
} from "./types";

function pageQuery(page = 0, pageSize = 50): string {
  return `page=${page}&pageSize=${pageSize}`;
}

function currentUserFromSession(): AdminUser | null {
  const email = localStorage.getItem("rebooked-admin-email");
  return localStorage.getItem("rebooked-admin-token") && email ? { id: email, email, role: "superAdmin" } : null;
}

export const liveClient: ApiClient = {
  login(email: string, password: string): Promise<LoginResponse> {
    return request<LoginResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
  },
  refreshToken(refreshToken: string): Promise<LoginResponse> {
    return request<LoginResponse>("/auth/refresh-token", {
      method: "POST",
      body: JSON.stringify({ refreshToken }),
    });
  },
  async getCurrentUser(): Promise<AdminUser | null> {
    return currentUserFromSession();
  },
  getPreviewCredentials() {
    return null;
  },
  listAuthors(query = "") {
    return request<Page<Author>>(`/admin/author?q=${encodeURIComponent(query)}&${pageQuery()}`);
  },
  getAuthor(id: string) {
    return request<Author>(`/admin/author/${encodeURIComponent(id)}`);
  },
  updateAuthor(id: string, input: AuthorPatchInput) {
    return request<Author>(`/admin/author/${encodeURIComponent(id)}`, {
      method: "PATCH",
      body: JSON.stringify(input),
    });
  },
  verifyAuthor(id: string) {
    return request<Author>(`/admin/author/${encodeURIComponent(id)}/verify`, { method: "POST" });
  },
  addExternalId(id: string, input: ExternalId) {
    return request<Author>(`/admin/author/${encodeURIComponent(id)}/external-ids`, {
      method: "POST",
      body: JSON.stringify(input),
    });
  },
  removeExternalId(id: string, source: string, sourceId: string) {
    return request<Author>(`/admin/author/${encodeURIComponent(id)}/external-ids/${encodeURIComponent(source)}/${encodeURIComponent(sourceId)}`, {
      method: "DELETE",
    });
  },
  listAuthorBooks(id: string, page = 0, pageSize = 50) {
    return request<Page<AuthorBook>>(`/admin/author/${encodeURIComponent(id)}/books?${pageQuery(page, pageSize)}`);
  },
  listAuthorAudit(id: string, page = 0, pageSize = 50) {
    return request<Page<AuthorAuditEntry>>(`/admin/author/${encodeURIComponent(id)}/audit?${pageQuery(page, pageSize)}`);
  },
  getMergePreview(id: string, intoId: string) {
    return request<unknown>(`/admin/author/${encodeURIComponent(id)}/merge-preview?intoId=${encodeURIComponent(intoId)}`);
  },
  mergeAuthor(id: string, intoId: string) {
    return request<Author>(`/admin/author/${encodeURIComponent(id)}/merge`, {
      method: "POST",
      body: JSON.stringify({ intoId }),
    });
  },
  async deleteAuthor(id: string) {
    await request<void>(`/admin/author/${encodeURIComponent(id)}`, { method: "DELETE" });
  },
  listReviews(query: ReviewQuery = {}) {
    const params = new URLSearchParams({
      type: query.type ?? "",
      status: query.status ?? "OPEN",
      page: String(query.page ?? 0),
      pageSize: String(query.pageSize ?? 50),
    });
    return request<Page<ReviewItem>>(`/admin/author/review?${params.toString()}`);
  },
  keepSeparate(id: string, reason?: string) {
    return request<ReviewItem>(`/admin/author/review/${encodeURIComponent(id)}/keep-separate`, {
      method: "POST",
      body: JSON.stringify(reason ? { reason } : {}),
    });
  },
  listImports() {
    return request<Page<ImportJob>>(`/admin/author-import?${pageQuery()}`);
  },
  getImport(id: string) {
    return request<ImportJob>(`/admin/author-import/${encodeURIComponent(id)}`);
  },
  listImportErrors(id: string, page = 0, pageSize = 50) {
    return request<Page<ImportError>>(`/admin/author-import/${encodeURIComponent(id)}/errors?${pageQuery(page, pageSize)}`);
  },
  startImport(source: string) {
    return request<ImportJob>("/admin/author-import", {
      method: "POST",
      body: JSON.stringify({ source }),
    });
  },
  listCategories() {
    return request<Category[]>("/category");
  },
  createCategory(name: string, description = "") {
    return request<Category>("/category", {
      method: "POST",
      body: JSON.stringify({ name, description }),
    });
  },
  renameCategory(id: string, name: string) {
    return request<Category>(`/category/${encodeURIComponent(id)}`, {
      method: "PATCH",
      body: JSON.stringify({ name }),
    });
  },
  async deleteCategory(id: string) {
    await request<void>(`/category/${encodeURIComponent(id)}`, { method: "DELETE" });
  },
  getFeatured(week: string) {
    return request<FeaturedSet>(`/admin/homepage/featured?week=${encodeURIComponent(week)}`);
  },
  saveFeatured(value: FeaturedSet) {
    const input: FeaturedOverrideInput = {
      bookIds: [],
      heroBookIds: [],
      authorId: "",
      note: value.hero,
    };
    return request<FeaturedSet>("/admin/homepage/featured", {
      method: "PUT",
      body: JSON.stringify(input),
    });
  },
  removeFeatured(week: string) {
    return request<FeaturedSet>(`/admin/homepage/featured?week=${encodeURIComponent(week)}`, { method: "DELETE" });
  },
  refreshSection(section: string) {
    return request<unknown>(`/admin/homepage/refresh/${encodeURIComponent(section)}`, { method: "POST" });
  },
};
