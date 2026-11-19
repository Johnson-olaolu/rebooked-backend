import { liveClient } from "./liveClient";
import { mockClient } from "./mockClient";
import type { ApiClient } from "./types";

export const api: ApiClient = import.meta.env.VITE_MOCK_API === "false" ? liveClient : mockClient;
export { ApiError } from "./client";
export type {
  AdminUser,
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
  LoginResponse,
  Page,
  PreviewCredentials,
  ReviewItem,
  ReviewQuery,
} from "./types";
