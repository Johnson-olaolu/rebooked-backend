import { liveClient } from "./liveClient";
import { mockClient } from "./mockClient";
import type { ApiClient } from "./types";

const useMockClient = import.meta.env.VITE_MOCK_API === "true"
  || (import.meta.env.DEV && import.meta.env.VITE_MOCK_API !== "false");

export const api: ApiClient = useMockClient ? mockClient : liveClient;
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
