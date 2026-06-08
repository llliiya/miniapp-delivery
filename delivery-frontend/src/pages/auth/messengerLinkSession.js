/** Session keys для привязки MAX / Telegram после веб-входа по телефону */

const MESSENGER_NEED_LINK_KEY = "messenger_need_link";
const MAX_LINK_USER_ID_KEY = "max_link_user_id";
const MAX_PENDING_INVITE_TOKEN_KEY = "max_pending_invite_token";
const TELEGRAM_LINK_LOGIN_EXISTING_KEY = "telegram_link_login_existing";

export function setMaxLinkUserId(maxUserId) {
  try {
    if (maxUserId != null && maxUserId !== "") sessionStorage.setItem(MAX_LINK_USER_ID_KEY, String(maxUserId));
  } catch (_) {}
}

export function getMaxLinkUserId() {
  try {
    return sessionStorage.getItem(MAX_LINK_USER_ID_KEY);
  } catch {
    return null;
  }
}

export function clearMaxLinkUserId() {
  try {
    sessionStorage.removeItem(MAX_LINK_USER_ID_KEY);
  } catch (_) {}
}

export function setMaxPendingInviteToken(token) {
  try {
    if (token) sessionStorage.setItem(MAX_PENDING_INVITE_TOKEN_KEY, token);
    else sessionStorage.removeItem(MAX_PENDING_INVITE_TOKEN_KEY);
  } catch (_) {}
}

export function getMaxPendingInviteToken() {
  try {
    return sessionStorage.getItem(MAX_PENDING_INVITE_TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setTelegramLinkLoginExisting() {
  try {
    sessionStorage.setItem(TELEGRAM_LINK_LOGIN_EXISTING_KEY, "1");
  } catch (_) {}
}

export function getTelegramLinkLoginExisting() {
  try {
    return sessionStorage.getItem(TELEGRAM_LINK_LOGIN_EXISTING_KEY) === "1";
  } catch {
    return false;
  }
}

export function clearTelegramLinkLoginExisting() {
  try {
    sessionStorage.removeItem(TELEGRAM_LINK_LOGIN_EXISTING_KEY);
  } catch (_) {}
}

export function setMessengerNeedLink() {
  try {
    sessionStorage.setItem(MESSENGER_NEED_LINK_KEY, "1");
  } catch (_) {}
}

export function getMessengerNeedLink() {
  try {
    return sessionStorage.getItem(MESSENGER_NEED_LINK_KEY) === "1";
  } catch {
    return false;
  }
}

export function clearMessengerNeedLink() {
  try {
    sessionStorage.removeItem(MESSENGER_NEED_LINK_KEY);
  } catch (_) {}
}
