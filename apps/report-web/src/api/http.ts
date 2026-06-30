import { getToken, getTokenType } from "@platform/core";

interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

const DEFAULT_API_BASE_URL = "http://localhost:8088";

export const apiBaseUrl = (
  import.meta.env.VITE_GATEWAY_BASE_URL ||
  import.meta.env.VITE_API_BASE ||
  import.meta.env.VITE_API_BASE_URL ||
  DEFAULT_API_BASE_URL
).replace(/\/$/, "");
export const enableMock = import.meta.env.VITE_ENABLE_MOCK === "true";

function endpoint(path: string) {
  if (/^https?:\/\//i.test(path)) return path;
  return `${apiBaseUrl}${path.startsWith("/") ? path : `/${path}`}`;
}

function authHeaders() {
  const token = getToken();
  if (!token) return {};
  return { Authorization: `${getTokenType()} ${token}` };
}

function fileNameFromDisposition(disposition: string | null) {
  if (!disposition) return undefined;
  const utf8Name = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (utf8Name) return decodeURIComponent(utf8Name);
  return disposition.match(/filename="?([^";]+)"?/i)?.[1];
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const isFormData = init.body instanceof FormData;
  const response = await fetch(endpoint(path), {
    ...init,
    headers: {
      ...(isFormData ? {} : { "Content-Type": "application/json; charset=UTF-8" }),
      ...authHeaders(),
      ...(init.headers || {})
    }
  });

  let body: ApiEnvelope<T> | undefined;
  try {
    body = (await response.json()) as ApiEnvelope<T>;
  } catch {
    body = undefined;
  }

  if (!response.ok) {
    throw new Error(body?.message || `接口请求失败：${response.status}`);
  }
  if (!body) {
    return undefined as T;
  }
  if (body.code !== 200) {
    throw new Error(body?.message || "接口返回失败");
  }
  return body.data;
}

export async function apiDownload(path: string) {
  const response = await fetch(endpoint(path), {
    headers: authHeaders()
  });
  if (!response.ok) throw new Error(`文件下载失败：${response.status}`);
  const contentType = response.headers.get("Content-Type") || "";
  if (/application\/json|text\/plain/i.test(contentType)) {
    const message = await response.text();
    throw new Error(message || "文件下载接口未返回文件流");
  }
  return {
    blob: await response.blob(),
    fileName: fileNameFromDisposition(response.headers.get("Content-Disposition"))
  };
}

export function buildApiUrl(path: string) {
  return endpoint(path);
}

export function buildAuthHeaders() {
  return authHeaders();
}
