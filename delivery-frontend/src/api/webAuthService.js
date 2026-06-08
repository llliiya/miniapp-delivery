import { API_URL, USE_NGROK_HEADER } from "../config";
import { getToken } from "../utils/tokenStorage";
import { getClientChannelHeaderValue } from "./clientChannel";
import { accountApi } from "./http.js";

const authBase = `${API_URL}/auth`;

function headers({ includeAuth = true } = {}) {
  const h = {
    "Content-Type": "application/json",
    "X-Client-Channel": getClientChannelHeaderValue(),
  };
  if (includeAuth) {
    const token = getToken();
    if (token) h["Authorization"] = `Bearer ${token}`;
  }
  if (USE_NGROK_HEADER) h["ngrok-skip-browser-warning"] = "1";
  return h;
}

function fetchPublicAuth(url, options = {}) {
  return fetch(url, {
    ...options,
    headers: { ...headers({ includeAuth: false }), ...options.headers },
    credentials: "include",
  });
}

function fetchAuth(url, options = {}) {
  return fetch(url, {
    ...options,
    headers: { ...headers(), ...options.headers },
    credentials: "include",
  });
}

export async function forgotPasswordRequest(phone) {
  const res = await fetchAuth(`${authBase}/forgot-password/request`, {
    method: "POST",
    body: JSON.stringify({ phone }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Ошибка ${res.status}`);
  }
  return res.json();
}

export async function forgotPasswordVerify(challengeId, pin) {
  const res = await fetchAuth(`${authBase}/forgot-password/verify`, {
    method: "POST",
    body: JSON.stringify({ challengeId, pin }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Ошибка ${res.status}`);
  }
  return res.json();
}

export async function forgotPasswordComplete(resetToken, password) {
  const res = await fetchAuth(`${authBase}/forgot-password/complete`, {
    method: "POST",
    body: JSON.stringify({ resetToken, password }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Ошибка ${res.status}`);
  }
  return res.json();
}

export async function registerPhoneRequest(phone) {
  const res = await fetchAuth(`${authBase}/register/phone/request`, {
    method: "POST",
    body: JSON.stringify({ phone }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Ошибка ${res.status}`);
  }
  return res.json();
}

/** Подтверждение по последним 4 цифрам номера звонящего. body: { challengeId, pin } */
export async function registerPhoneVerify(challengeId, pin) {
  const res = await fetchAuth(`${authBase}/register/phone/verify`, {
    method: "POST",
    body: JSON.stringify({ challengeId, pin }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    const msg = err.message || err.error || `Ошибка ${res.status}`;
    throw new Error(msg);
  }
  return res.json();
}

export async function setPassword(phone, password) {
  const res = await fetchAuth(`${authBase}/register/set-password`, {
    method: "POST",
    body: JSON.stringify({ phone, password }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Ошибка ${res.status}`);
  }
  return res.json();
}

export async function registerEmailRequest(registrationToken, email) {
  const res = await fetchAuth(`${authBase}/register/email/request`, {
    method: "POST",
    body: JSON.stringify({ registrationToken, email }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Ошибка ${res.status}`);
  }
  return res.json();
}

export async function registerEmailVerify(registrationToken, email, code) {
  const res = await fetchAuth(`${authBase}/register/email/verify`, {
    method: "POST",
    body: JSON.stringify({ registrationToken, email, code }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    const e = new Error(err.message || err.error || err.detail || `Ошибка ${res.status}`);
    e.status = res.status;
    throw e;
  }
  return res.json();
}

export async function loginPhone(phone, password, login) {
  const trimmedPassword = password != null ? String(password).trim() : "";
  const body = { password: trimmedPassword };
  if (login) body.login = String(login).trim();
  else if (phone) body.phone = String(phone).trim();
  const res = await fetchPublicAuth(`${authBase}/login`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    const e = new Error(err.message || err.error || err.detail || `Ошибка ${res.status}`);
    e.status = res.status;
    throw e;
  }
  return res.json();
}

export async function verifyLoginEmailCode(challengeId, code) {
  const payload = { challengeId: challengeId != null ? String(challengeId).trim() : "", code: code != null ? String(code).trim() : "" };
  const res = await fetchAuth(`${authBase}/login/verify-email-code`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Ошибка ${res.status}`);
  }
  return res.json();
}

export async function resendLoginEmailCode(challengeId) {
  const res = await fetchAuth(`${authBase}/login/resend-email-code`, {
    method: "POST",
    body: JSON.stringify({ challengeId }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Ошибка ${res.status}`);
  }
  return res.json();
}

export async function refreshTokens() {
  const res = await fetchAuth(`${authBase}/refresh`, { method: "POST" });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    const e = new Error(err.message || err.error || "Ошибка обновления токена");
    e.status = res.status;
    throw e;
  }
  return res.json();
}

export async function getAuthMe() {
  const res = await fetchAuth(`${authBase}/me`);
  if (!res.ok) throw new Error("Не авторизован");
  return res.json();
}

export async function logout() {
  await fetchAuth(`${authBase}/logout`, { method: "POST" });
}

export async function changeSecurityPassword(currentPassword, newPassword) {
  return accountApi('/profile/security/password', 'POST', {
    currentPassword,
    newPassword,
  });
}

export async function updateProfile(body) {
  const res = await fetchAuth(`${API_URL}/profile`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || err.error || `Ошибка ${res.status}`);
  }
  return res.json();
}

export function isWebSessionPayload(payload) {
  const phone = payload?.phone;
  return typeof phone === "string" && phone.trim().length > 0;
}

export function isAccountToken() {
  try {
    const token = getToken();
    if (!token) return false;
    const payload = JSON.parse(atob(token.split(".")[1]));
    return isWebSessionPayload(payload);
  } catch {
    return false;
  }
}
