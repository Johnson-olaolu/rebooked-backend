export interface Page<T> {
  content: T[];
  page: number;
  pageSize: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminUser {
  id: string;
  email: string;
  role?: string;
}

export interface PreviewCredentials {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  refreshToken: string;
  expiresIn: number;
  user: AdminUser;
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
  externalIds?: ExternalId[];
  aliases?: AuthorAlias[];
  books?: AuthorBook[];
}

export interface ExternalId {
  source: string;
  sourceId: string;
}

export interface AuthorAlias {
  name: string;
  source: string;
}

export interface AuthorBook {
  id: string;
  title: string;
  status: string;
}

export interface AuthorAuditEntry {
  id: string;
  action: string;
  actor: string;
  createdAt: string;
  details?: string;
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

export interface ImportError {
  id: string;
  recordId: string;
  message: string;
  createdAt: string;
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

export interface AuthorPatchInput {
  name?: string;
  birthYear?: number;
  biography?: string;
  description?: string;
}

export interface FeaturedOverrideInput {
  bookIds: string[];
  heroBookIds: string[];
  authorId: string;
  note: string;
}

export interface ReviewQuery {
  type?: string;
  status?: string;
  page?: number;
  pageSize?: number;
}

export interface ApiClient {
  login(email: string, password: string): Promise<LoginResponse>;
  refreshToken(refreshToken: string): Promise<LoginResponse>;
  getCurrentUser(): Promise<AdminUser | null>;
  getPreviewCredentials(): PreviewCredentials | null;
  listAuthors(query?: string): Promise<Page<Author>>;
  getAuthor(id: string): Promise<Author | null>;
  updateAuthor(id: string, input: AuthorPatchInput): Promise<Author | null>;
  verifyAuthor(id: string): Promise<Author | null>;
  addExternalId(id: string, input: ExternalId): Promise<Author | null>;
  removeExternalId(id: string, source: string, sourceId: string): Promise<Author | null>;
  listAuthorBooks(id: string, page?: number, pageSize?: number): Promise<Page<AuthorBook>>;
  listAuthorAudit(id: string, page?: number, pageSize?: number): Promise<Page<AuthorAuditEntry>>;
  getMergePreview(id: string, intoId: string): Promise<unknown>;
  mergeAuthor(id: string, intoId: string): Promise<Author>;
  deleteAuthor(id: string): Promise<void>;
  listReviews(query?: ReviewQuery): Promise<Page<ReviewItem>>;
  keepSeparate(id: string, reason?: string): Promise<ReviewItem>;
  listImports(): Promise<Page<ImportJob>>;
  getImport(id: string): Promise<ImportJob | null>;
  listImportErrors(id: string, page?: number, pageSize?: number): Promise<Page<ImportError>>;
  startImport(source: string): Promise<ImportJob>;
  listCategories(): Promise<Category[]>;
  createCategory(name: string, description?: string): Promise<Category>;
  renameCategory(id: string, name: string): Promise<Category>;
  deleteCategory(id: string): Promise<void>;
  getFeatured(week: string): Promise<FeaturedSet | null>;
  saveFeatured(value: FeaturedSet): Promise<FeaturedSet>;
  removeFeatured(week: string): Promise<FeaturedSet>;
  refreshSection(section: string): Promise<unknown>;
}
