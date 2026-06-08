/**
 * MAX mini app environment adapter.
 * Основной сценарий — запуск через нижнюю кнопку «Старт» в чате MAX.
 * window.WebApp создаётся MAX Bridge (max-web-app.js); в MAX может быть и window.Telegram.WebApp.
 */

import { getPlatform } from "./platform";
import { getClientChannel, getPlatformState, waitForPlatformResolved } from "./clientPlatform";

const MAX_USER_ID_SOURCE = {
  INIT_DATA_UNSAFE_USER: "initDataUnsafe.user",
  INIT_DATA_STRING: "initData",
  QUERY_PARAMS: "query_params",
  POST_MESSAGE: "post_message",
  TELEGRAM_COMPAT: "telegram_compat_in_max",
};

/**
 * Безопасный дамп объекта для лога (без функций, циклических ссылок).
 */
function safeDump(obj, seen = new Set(), depth = 0) {
  if (depth > 3) return "[max depth]";
  if (obj == null) return obj;
  if (typeof obj !== "object") return obj;
  if (seen.has(obj)) return "[cycle]";
  if (Array.isArray(obj)) return obj.map((v) => safeDump(v, seen, depth + 1));
  seen.add(obj);
  const out = {};
  for (const k of Object.keys(obj)) {
    try {
      const v = obj[k];
      if (typeof v === "function") out[k] = "[function]";
      else out[k] = safeDump(v, seen, depth + 1);
    } catch {
      out[k] = "[error]";
    }
  }
  return out;
}

/**
 * Извлекает user id из строки initData (URL-encoded key=value&...).
 */
function parseInitDataString(initData) {
  if (!initData || typeof initData !== "string") return null;
  try {
    const params = new URLSearchParams(initData);
    const vk = params.get("vk_user_id") || params.get("user_id");
    if (vk) return String(vk).trim() || null;
    const userJson = params.get("user");
    if (userJson) {
      const user = JSON.parse(decodeURIComponent(userJson));
      const id = user?.id ?? user?.user_id;
      return id != null ? String(id) : null;
    }
    return null;
  } catch {
    return null;
  }
}

/**
 * WebView клиента MAX (без URL/referrer initData иногда пустой; в UA остаётся «MAX/26.x»).
 */
export function isMaxMessengerUserAgent() {
  if (typeof navigator === "undefined") return false;
  try {
    return /\bMAX\/[\d.]+/i.test(navigator.userAgent || "");
  } catch (_) {
    return false;
  }
}

/**
 * Признак «вероятно открыто в MAX» (URL/реферрер), но bridge ещё не готов.
 */
export function isMaybeMaxContainer() {
  const st = getPlatformState();
  if (st.channel === "max") return true;
  if (st.status === "resolving") return true;
  if (typeof window === "undefined") return false;
  if (isMaxMessengerUserAgent()) return true;
  const search = window.location.search || "";
  return /WebAppStartParam|startapp=|tgWebAppPlatform=max/i.test(search);
}

/**
 * Контекст явно MAX по URL или referrer (не по наличию window.WebApp).
 * В Telegram может быть и window.WebApp (общий скрипт), поэтому для выбора
 * «вход через Telegram» проверяем только URL/referrer.
 */
export function isClearlyMaxByUrlOrReferrer() {
  if (typeof window === "undefined") return false;
  if (isMaxMessengerUserAgent()) return true;
  const search = window.location.search || "";
  if (/tgWebAppPlatform=max/i.test(search)) return true;
  try {
    if (typeof document.referrer === "string" && /max\.(ru|com|ai)/i.test(document.referrer)) return true;
  } catch (_) {}
  return false;
}

/** Признак «точно контейнер MAX»: только tgWebAppPlatform=max или referrer max. Не startapp= (Telegram). */
export function isMaxContainerOnly() {
  if (getClientChannel() === "max") return true;
  if (typeof window === "undefined") return false;
  return !!(window.__maxBridgeLoaded && window.WebApp);
}

/**
 * Окружение MAX. Источник истины — platform.getPlatform() (определяется один раз при старте).
 */
export function isMaxEnv() {
  return getClientChannel() === "max" || getPlatform() === "max";
}

/**
 * Надёжный признак MAX без URL/referrer:
 * bridge отдал user в initDataUnsafe или user_id в initData.
 */
function hasMaxWebAppUserSignal() {
  if (typeof window === "undefined" || !window.WebApp) return false;
  const webApp = window.WebApp;
  const user = webApp.initDataUnsafe?.user;
  if (user && (user.id != null || user.user_id != null)) return true;
  if (typeof webApp.initData === "string" && webApp.initData) {
    const parsed = parseInitDataString(webApp.initData);
    if (parsed) return true;
  }
  return false;
}

/**
 * Ждёт появления window.WebApp (до maxWaitMs), опрос каждые intervalMs.
 * @returns {Promise<boolean>} true если WebApp появился
 */
export function waitForMaxBridge(maxWaitMs = 3000, intervalMs = 100) {
  // maxWaitMs/intervalMs сохранены для обратной совместимости API.
  void maxWaitMs;
  void intervalMs;
  return waitForPlatformResolved().then(() => !!window.WebApp);
}

/**
 * @returns {{ userId: string|null, source: string|null }}
 */
export function getMaxUserIdWithSource() {
  if (typeof window === "undefined") return { userId: null, source: null };

  const webApp = window.WebApp;
  if (webApp) {
    const user = webApp.initDataUnsafe?.user;
    if (user != null && (user.id != null || user.user_id != null)) {
      const id = user.id ?? user.user_id;
      const s = id != null ? String(id).trim() : null;
      if (s) return { userId: s, source: MAX_USER_ID_SOURCE.INIT_DATA_UNSAFE_USER };
    }
    if (webApp.initData) {
      const fromInit = parseInitDataString(webApp.initData);
      if (fromInit) return { userId: fromInit, source: MAX_USER_ID_SOURCE.INIT_DATA_STRING };
    }
  }

  const params = new URLSearchParams(window.location.search);
  const fromUrl =
    params.get("vk_user_id") || params.get("max_user_id") || params.get("maxUserId");
  if (fromUrl) {
    const s = String(fromUrl).trim();
    if (s) return { userId: s, source: MAX_USER_ID_SOURCE.QUERY_PARAMS };
  }

  if (postMessageUserId) return { userId: postMessageUserId, source: MAX_USER_ID_SOURCE.POST_MESSAGE };

  if (isMaybeMaxContainer() && window.Telegram?.WebApp?.initDataUnsafe?.user) {
    const user = window.Telegram.WebApp.initDataUnsafe.user;
    const id = user?.id ?? user?.user_id;
    if (id != null) {
      const s = String(id).trim();
      if (s) return { userId: s, source: MAX_USER_ID_SOURCE.TELEGRAM_COMPAT };
    }
  }

  return { userId: null, source: null };
}

/**
 * Ожидание maxUserId после старта MAX (bridge и initDataUnsafe.user часто появляются позже первого кадра).
 * @returns {Promise<{ userId: string|null, source: string|null }>}
 */
export async function waitForMaxUserIdWithSource(maxWaitMs = 8000, intervalMs = 100) {
  if (typeof window === "undefined") return { userId: null, source: null };
  const first = getMaxUserIdWithSource();
  if (first.userId) return first;
  const deadline = Date.now() + maxWaitMs;
  while (Date.now() < deadline) {
    await new Promise((r) => setTimeout(r, intervalMs));
    const next = getMaxUserIdWithSource();
    if (next.userId) return next;
  }
  return { userId: null, source: null };
}

/**
 * @returns {string|null} maxUserId или null
 */
export function getMaxUserId() {
  const { userId } = getMaxUserIdWithSource();
  return userId;
}

/** Хранилище user id, полученного через postMessage (MAX может передать асинхронно). */
let postMessageUserId = null;

let postMessageLogged = false;

function setupPostMessageLogging() {
  if (postMessageLogged || typeof window === "undefined") return;
  postMessageLogged = true;
  const log = (e) => {
    try {
      console.info("[MAX postMessage]", {
        origin: e.origin,
        data: safeDump(e.data),
        type: typeof e.data,
      });
      const d = e.data;
      if (d && typeof d === "object" && postMessageUserId === null) {
        const id = d.user_id ?? d.userId ?? d.vk_user_id ?? d.max_user_id;
        if (id != null) {
          postMessageUserId = String(id).trim();
          console.info("[MAX postMessage] saved maxUserId from message:", postMessageUserId);
        }
      }
    } catch (err) {
      console.info("[MAX postMessage] log error", err?.message);
    }
  };
  window.addEventListener("message", log);
  setTimeout(() => window.removeEventListener("message", log), 8000);
}

/**
 * Состояние загрузки скрипта MAX Bridge (из index.html).
 */
export function getMaxBridgeLoadState() {
  if (typeof window === "undefined") return null;
  return {
    loadedMaxBridgeScript: !!window.__maxBridgeLoaded,
    maxBridgeScriptUrl: window.__maxBridgeScriptUrl ?? null,
    maxBridgeError: window.__maxBridgeError ?? null,
  };
}

/**
 * start_param для deeplink (MAX: кнопка open_app; Telegram: startapp в URL).
 * Приоритет: MAX (window.WebApp), затем Telegram.
 */
export function getStartParam() {
  if (typeof window === "undefined") return null;
  const webApp = window.WebApp;
  const tg = window.Telegram?.WebApp;
  const fromMax = webApp?.initDataUnsafe?.start_param ?? (typeof webApp?.initDataUnsafe?.start_param !== "undefined" ? String(webApp?.initDataUnsafe?.start_param) : null);
  const fromTg = tg?.initDataUnsafe?.start_param ?? tg?.startParam ?? (typeof tg?.getStartParam === "function" ? tg.getStartParam() : null);
  const fromBridge = fromMax ?? fromTg;
  if (fromBridge) return fromBridge;
  const params = new URLSearchParams(window.location.search || "");
  // Fallback: некоторые клиенты открывают mini-app по URL `startapp=...` без инициализации WebApp bridge.
  return params.get("start_param") ?? params.get("startapp") ?? null;
}

/**
 * Диагностика для логов и debug-экрана: bridge, initData, userId.
 * Для MAX источник истины — window.WebApp; userId из initDataUnsafe.user.id или initData.
 */
export function getRuntimeDiagnostics() {
  if (typeof window === "undefined") return null;
  const webApp = window.WebApp;
  const tg = window.Telegram?.WebApp;
  setupPostMessageLogging();
  const { userId, source } = getMaxUserIdWithSource();
  const search = window.location.search || "";
  const startParam = getStartParam();
  const bridge = getMaxBridgeLoadState();
  const initDataRaw = webApp?.initData ?? tg?.initData ?? "";
  const initDataUnsafeObj = webApp?.initDataUnsafe ?? tg?.initDataUnsafe ?? null;
  return {
    ...bridge,
    platform: getPlatform(),
    runtimeSource: webApp ? "MAX (window.WebApp)" : tg ? "Telegram (window.Telegram.WebApp)" : "unknown",
    isMaxEnv: isMaxEnv(),
    isMaybeMaxContainer: isMaybeMaxContainer(),
    isMaxContainerOnly: isMaxContainerOnly(),
    hasWebApp: !!webApp,
    hasTelegramWebApp: !!tg,
    hasInitData: !!initDataRaw,
    hasInitDataUnsafe: !!initDataUnsafeObj,
    initData: initDataRaw ? String(initDataRaw).slice(0, 500) : null,
    initDataUnsafe: initDataUnsafeObj ? safeDump(initDataUnsafeObj) : null,
    initDataLength: initDataRaw.length,
    startParam: startParam ?? (typeof webApp?.initDataUnsafe?.start_param !== "undefined" ? String(webApp.initDataUnsafe.start_param) : null),
    userId,
    userIdSource: source,
    locationSearch: search.slice(0, 200),
    isMaxMessengerUserAgent: isMaxMessengerUserAgent(),
  };
}

/**
 * Логирует полную диагностику при старте.
 */
export function logMaxEnvDiagnostics() {
  if (typeof window === "undefined") return;
  const webApp = window.WebApp;
  const search = window.location.search;
  const hasWebApp = !!webApp;
  const hasTelegram = !!window.Telegram?.WebApp;

  console.info("[MAX env] window.WebApp", hasWebApp ? safeDump(webApp) : "absent");
  console.info("[MAX env] window.location.search", search || "(empty)");
  console.info("[MAX env] window.Telegram?.WebApp", hasTelegram ? "present" : "absent");

  setupPostMessageLogging();

  const diag = getRuntimeDiagnostics();
  console.info("[MAX env] result", diag);
}

/** Логи только в MAX/возможном MAX (по URL или window.WebApp). Для диагностики белого экрана. */
function shouldLogMaxDebug() {
  if (typeof window === "undefined") return false;
  if (getPlatform() === "max") return true;
  if (window.WebApp) return true;
  if (isMaxMessengerUserAgent()) return true;
  const search = window.location.search || "";
  if (/tgWebAppPlatform=max|WebAppStartParam|startapp=/i.test(search)) return true;
  try {
    if (typeof document.referrer === "string" && /max\.(ru|com|ai)/i.test(document.referrer)) return true;
  } catch (_) {}
  return false;
}

export function logMaxDebug(label, data) {
  if (!shouldLogMaxDebug()) return;
  if (data !== undefined) {
    console.info("[MAX debug]", label, data);
  } else {
    console.info("[MAX debug]", label);
  }
}
