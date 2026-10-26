import { reactive } from "vue";
export type Row = Record<string, any>;
export const state = reactive({
  user: { authenticated: false } as Row,
  site: {} as Row,
  categories: [] as Row[],
  notice: "",
  error: "",
  centerEnabled: false,
  ready: false,
  totalVisits: null as number | null,
  contentCategoryId: "",
});
let timer: ReturnType<typeof setTimeout>;
export function notify(message: string, error = false) {
  state.notice = error ? "" : message;
  state.error = error ? message : "";
  clearTimeout(timer);
  timer = setTimeout(() => {
    state.notice = "";
    state.error = "";
  }, 6500);
}
export async function api<T = any>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (options.body && !(options.body instanceof FormData))
    headers.set("Content-Type", "application/json");
  if (state.user.csrf) headers.set("X-CSRF-Token", state.user.csrf);
  const res = await fetch(path, {
    ...options,
    headers,
    credentials: "same-origin",
  });
  const data = await res.json().catch(() => ({ message: "服务响应异常" }));
  if (!res.ok) {
    if (res.status === 401) state.user = { authenticated: false };
    throw new Error(data.message || "请求失败");
  }
  return data;
}
export const send = (path: string, body: unknown = {}, method = "POST") =>
  api(path, { method, body: JSON.stringify(body) });
export async function recordVisit() {
  try {
    const data = await send("/api/public/visits");
    state.totalVisits = Math.max(state.totalVisits ?? 0, data.totalVisits);
  } catch {
    // Statistics must not interrupt browsing if the service is unavailable.
  }
}
export async function refreshUser() {
  state.user = await api("/api/auth/me");
}
export async function refreshSite() {
  const [site, categories] = await Promise.all([
    api("/api/public/site"),
    api("/api/public/categories"),
  ]);
  state.site = site;
  state.categories = categories;
  document.title = site.name;
}
export async function initialize() {
  try {
    await Promise.all([
      refreshSite(),
      refreshUser(),
      api("/api/public/auth-config").then(
        (v) => (state.centerEnabled = v.centerEnabled),
      ),
    ]);
  } catch (e) {
    notify((e as Error).message, true);
  } finally {
    state.ready = true;
  }
}
export async function upload(file: File) {
  const form = new FormData();
  form.append("file", file);
  return api("/api/operator/media", { method: "POST", body: form });
}
export function date(value: unknown) {
  return value
    ? new Date(value as string | number).toLocaleString("zh-CN", {
        hour12: false,
      })
    : "—";
}
export function categoryPath(id: string) {
  const c = state.categories.find((c) => c.id === id);
  return "/columns/" + (c?.slug || id);
}
export async function logout() {
  try {
    await send("/api/auth/logout");
    state.user = { authenticated: false };
    location.assign("/");
  } catch (e) {
    notify((e as Error).message, true);
  }
}
