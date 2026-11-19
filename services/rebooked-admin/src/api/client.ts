const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:5001/api";

export const isMockMode = import.meta.env.VITE_MOCK_API !== "false";

export interface ApiEnvelope<T> {
  data: T;
  message: string;
  status: number;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status?: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function request<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = localStorage.getItem("rebooked-admin-token");
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...options,
      headers: {
        "Content-Type": "application/json",
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...options.headers,
      },
    });
  } catch {
    throw new ApiError("Unable to reach the Rebooked API. Check that the backend is running on port 5001.");
  }

  const body = await response.json().catch(() => null) as ApiEnvelope<T> | { message?: string } | null;
  if (!response.ok) {
    throw new ApiError(body && "message" in body ? body.message ?? "Request failed" : "Request failed", response.status);
  }
  if (body && "data" in body) return body.data;
  return body as T;
}
