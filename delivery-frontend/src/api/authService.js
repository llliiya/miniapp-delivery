import { API_URL, USE_NGROK_HEADER } from "../config";
import { getClientChannelHeaderValue } from "./clientChannel";

export async function login(initData) {
  const headers = {
    "Content-Type": "application/json",
    "X-Client-Channel": getClientChannelHeaderValue(),
  };

  if (USE_NGROK_HEADER) {
    headers["ngrok-skip-browser-warning"] = "1";
  }

  const res = await fetch(`${API_URL}/auth/telegram`, {
    method: "POST",
    headers,
    body: JSON.stringify({ initData }),
  });

  if (!res.ok) {
    const errorText = await res.text().catch(() => "");
    console.error("❌ Ошибка авторизации:", res.status, errorText);
    throw new Error(`Ошибка авторизации: ${res.status} ${errorText || "Неизвестная ошибка"}`);
  }

  return res.json();
}

function telegramAuthHeaders() {
  const h = {
    "Content-Type": "application/json",
    "X-Client-Channel": getClientChannelHeaderValue(),
  };
  if (USE_NGROK_HEADER) h["ngrok-skip-browser-warning"] = "1";
  return h;
}

/**
 * POST /api/auth/telegram/register — новый User по initData (кнопка «Создать новый аккаунт»).
 */
export async function registerTelegram(initData) {
  const res = await fetch(`${API_URL}/auth/telegram/register`, {
    method: "POST",
    headers: telegramAuthHeaders(),
    body: JSON.stringify({ initData }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(data.message || data.error || `Ошибка ${res.status}`);
  }
  return data;
}

/**
 * POST /api/auth/telegram/link — привязка Telegram к текущему JWT.
 */
export async function linkTelegram(initData) {
  const { getToken } = await import("../utils/tokenStorage");
  const token = getToken();
  if (!token) throw new Error("Требуется авторизация");
  const res = await fetch(`${API_URL}/auth/telegram/link`, {
    method: "POST",
    headers: {
      ...telegramAuthHeaders(),
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({ initData }),
    credentials: "include",
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 409) throw new Error("Этот Telegram уже привязан к другому аккаунту");
    throw new Error(data.message || data.error || `Ошибка ${res.status}`);
  }
  return data;
}

/** POST /api/auth/telegram/complete-web-link — без JWT (одноразовый токен из веб-профиля). */
export async function completeTelegramWebLink(linkToken, initData) {
  const res = await fetch(`${API_URL}/auth/telegram/complete-web-link`, {
    method: "POST",
    headers: telegramAuthHeaders(),
    body: JSON.stringify({ linkToken, initData }),
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    if (res.status === 409) throw new Error(data.message || "Этот Telegram уже привязан к другому аккаунту");
    throw new Error(data.message || data.error || `Ошибка ${res.status}`);
  }
  return data;
}
