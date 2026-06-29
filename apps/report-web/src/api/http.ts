interface ApiEnvelope<T> {
  success: boolean;
  message: string;
  data: T;
}

const DEFAULT_API_BASE_URL = "http://127.0.0.1:8080";

export const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || DEFAULT_API_BASE_URL).replace(/\/$/, "");
export const enableMock = import.meta.env.VITE_ENABLE_MOCK !== "false";

function endpoint(path: string) {
  if (/^https?:\/\//i.test(path)) return path;
  return `${apiBaseUrl}${path.startsWith("/") ? path : `/${path}`}`;
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(endpoint(path), {
    ...init,
    headers: {
      "Content-Type": "application/json; charset=UTF-8",
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
  if (!body?.success) {
    throw new Error(body?.message || "接口返回失败");
  }
  return body.data;
}

export async function apiDownload(path: string) {
  const response = await fetch(endpoint(path));
  if (!response.ok) throw new Error(`文件下载失败：${response.status}`);
  const contentType = response.headers.get("Content-Type") || "";
  if (/application\/json|text\/plain/i.test(contentType)) {
    const message = await response.text();
    throw new Error(message || "文件下载接口未返回 DOCX 文件流");
  }
  return response.blob();
}
