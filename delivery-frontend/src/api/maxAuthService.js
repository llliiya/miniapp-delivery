import { API_URL, USE_NGROK_HEADER } from "../config";
import { getToken } from "../utils/tokenStorage";
import { getClientChannelHeaderValue } from "./clientChannel";

const authBase = `${API_URL}/auth`;

function baseHeaders() {
  const h = {
    "Content-Type": "application/json",
    "X-Client-Channel": getClientChannelHeaderValue(),
  };
  if (USE_NGROK_HEADER) h["ngrok-skip-browser-warning"] = "1";
  return h;
}

function getMaxProfilePayload(maxUserId) {
  if (typeof window === "undefined") {
    return { maxUserId: String(maxUserId).trim(), firstName: null, lastName: null, username: null };
  }
  const maxUser = window.WebApp?.initDataUnsafe?.user || {};
  const tgUser = window.Telegram?.WebApp?.initDataUnsafe?.user || {};
  const user = Object.keys(maxUser).length ? maxUser : tgUser;

  const firstName = String(user.first_name ?? user.firstName ?? "").trim();
  const lastName = String(user.last_name ?? user.lastName ?? "").trim();
  const username = String(user.username ?? "").trim().replace(/^@/, "");

  return {
    maxUserId: String(maxUserId).trim(),
    firstName: firstName || null,
    lastName: lastName || null,
    username: username || null,
  };
}

/**
 * POST /api/auth/max
 * @param {string} maxUserId
 * @returns {Promise<{ token?: string, status?: string }>} token или { status: "LINK_REQUIRED" }
 */
export async function authMax(maxUserId) {
  const res = await fetch(`${authBase}/max`, {
    method: "POST",
    headers: baseHeaders(),
    body: JSON.stringify(getMaxProfilePayload(maxUserId)),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    throw new Error(text || `Ошибка ${res.status}`);
  }
  return res.json();
}

/**
 * POST /api/auth/max/link — требует JWT.
 * @param {string} maxUserId
 * @returns {Promise<{ token: string }>}
 * @throws при 409 (identity already linked)
 */
export async function linkMax(maxUserId) {
  const token = getToken();
  if (!token) {
    console.warn("[MAX link] linkMax: no token in localStorage");
    throw new Error("Требуется авторизация");
  }
  const url = `${authBase}/max/link`;
  console.info("[MAX link] POST", url, "maxUserId:", maxUserId);
  const res = await fetch(url, {
    method: "POST",
    headers: {
      ...baseHeaders(),
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ maxUserId: String(maxUserId).trim() }),
    credentials: "include",
  });
  const data = await res.json().catch(() => ({}));
  console.info("[MAX link] POST /api/auth/max/link status:", res.status, "body:", data?.token ? "token" : data);
  if (!res.ok) {
    if (res.status === 409) throw new Error("MAX identity уже привязана к другому аккаунту");
    throw new Error(data.message || data.error || `Ошибка ${res.status}`);
  }
  return data;
}

/**
 * POST /api/auth/max/register — создаёт нового пользователя для MAX.
 * @param {string} maxUserId
 * @returns {Promise<{ token: string }>}
 */
export async function registerMax(maxUserId) {
  const res = await fetch(`${authBase}/max/register`, {
    method: "POST",
    headers: baseHeaders(),
    body: JSON.stringify(getMaxProfilePayload(maxUserId)),
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "");
    let msg = `Ошибка ${res.status}`;
    try {
      const j = JSON.parse(text);
      if (j?.message) msg = j.message;
      else if (j?.error) msg = j.error;
    } catch {
      if (text) msg = text;
    }
    throw new Error(msg);
  }
  return res.json();
}

/** POST /api/auth/max/complete-web-link — без JWT. */
export async function completeMaxWebLink(linkToken, maxUserId) {
  const res = await fetch(`${authBase}/max/complete-web-link`, {
    method: "POST",
    headers: baseHeaders(),
    body: JSON.stringify({ linkToken, maxUserId: String(maxUserId).trim() }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 409) throw new Error(data.message || "MAX уже привязан к другому аккаунту");
    throw new Error(data.message || data.error || `Ошибка ${res.status}`);
  }
  return data;
}
