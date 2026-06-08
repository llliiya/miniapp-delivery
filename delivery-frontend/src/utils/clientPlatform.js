const STORAGE_KEY = "app_platform_state_v1";
const LEGACY_STORAGE_KEY = "app_platform";
const BRIDGE_POLL_MS = 100;
const BRIDGE_WAIT_MS_DESKTOP = 3000;
const BRIDGE_WAIT_MS_MOBILE = 3500;

/** @typedef {"unknown"|"resolving"|"resolved"} PlatformStatus */
/** @typedef {"unknown"|"web"|"telegram"|"max"} ClientChannel */

let _platformLogSeq = 0;

/** Dev или в консоли: localStorage.setItem("debug_platform", "1") */
function platformLogEnabled() {
  try {
    if (typeof import.meta !== "undefined" && import.meta.env && import.meta.env.DEV) return true;
    if (typeof window !== "undefined" && window.localStorage?.getItem("debug_platform") === "1") return true;
  } catch (_) {}
  return false;
}

/** @param {string} phase @param {Record<string, unknown>} [detail] */
function platformLog(phase, detail) {
  if (!platformLogEnabled()) return;
  const seq = ++_platformLogSeq;
  const tNav =
    typeof performance !== "undefined" && typeof performance.now === "function"
      ? performance.now().toFixed(1)
      : "?";
  if (detail && Object.keys(detail).length > 0) {
    console.info(`[platform #${seq} +${tNav}ms nav]`, phase, detail);
  } else {
    console.info(`[platform #${seq} +${tNav}ms nav]`, phase);
  }
}

/** Снимок признаков для логов (без PII в initData). */
function platformSignalsSnapshot() {
  if (typeof window === "undefined") return { context: "ssr" };
  const wa = window.Telegram?.WebApp;
  const combined = (window.location.search || "") + (window.location.hash || "");
  return {
    tgPlatform: wa?.platform ?? null,
    tgInitDataLen: typeof wa?.initData === "string" ? wa.initData.length : 0,
    hasLaunchParams: hasTelegramLaunchParamsInLocation(),
    hasUnsafeSignal: hasTelegramWebAppContentSignal(),
    hasTelegramWebviewProxy: hasTelegramNativeBridge(),
    hasMaxWebApp: !!window.WebApp,
    combinedUrlLen: combined.length,
  };
}

let state = {
  status: "unknown",
  channel: "unknown",
  source: "boot",
  bridgeWaited: false,
  updatedAt: 0,
};

let resolvePromise = null;
const listeners = new Set();

/** Нельзя вызывать setState из getClientChannel во время React render (createServices → detectPlatform). */
let lateMaxWebFixScheduled = false;

function now() {
  return Date.now();
}

/** Явный override платформы из URL для быстрого старта без ожидания bridge. */
function getPlatformOverrideFromLocation() {
  if (typeof window === "undefined") return null;
  try {
    const raw = new URLSearchParams(window.location.search || "").get("platform");
    const value = typeof raw === "string" ? raw.trim().toLowerCase() : "";
    if (value === "tg" || value === "telegram") return "telegram";
    if (value === "max") return "max";
  } catch (_) {}
  return null;
}

function isMobileRuntime() {
  if (typeof window === "undefined") return false;
  return window.innerWidth <= 768 || "ontouchstart" in window;
}

function hasTelegramInitData() {
  if (typeof window === "undefined") return false;
  const initData = window.Telegram?.WebApp?.initData;
  return typeof initData === "string" && initData.length > 0;
}

/** tgWebApp* в query или hash (часто initData пуст, пока клиент не применит hash). */
function hasTelegramLaunchParamsInLocation() {
  if (typeof window === "undefined") return false;
  const combined = (window.location.search || "") + (window.location.hash || "");
  if (/tgWebAppData=/i.test(combined)) return true;
  if (/tgWebAppVersion=/i.test(combined)) return true;
  if (/tgWebAppBotName=/i.test(combined)) return true;
  if (/tgWebAppPlatform=/i.test(combined) && !/tgWebAppPlatform=max/i.test(combined)) return true;
  return false;
}

/** Признаки контекста Mini App без обязательной непустой строки initData. */
function hasTelegramWebAppContentSignal() {
  if (typeof window === "undefined") return false;
  const wa = window.Telegram?.WebApp;
  if (!wa) return false;
  if (typeof wa.initData === "string" && wa.initData.length > 0) return true;
  const u = wa.initDataUnsafe;
  if (!u || typeof u !== "object") return false;
  if (u.user != null && typeof u.user === "object" && Object.keys(u.user).length > 0) return true;
  if (u.query_id != null && String(u.query_id).length > 0) return true;
  if (typeof u.auth_date === "number") return true;
  if (u.hash != null && String(u.hash).length > 0) return true;
  return false;
}

function hasTelegramNativeBridge() {
  if (typeof window === "undefined") return false;
  try {
    if (typeof window.TelegramWebviewProxy !== "undefined" && window.TelegramWebviewProxy != null) return true;
  } catch (_) {}
  try {
    const wh = window.webkit?.messageHandlers;
    if (wh && wh.TelegramWebviewProxy != null) return true;
  } catch (_) {}
  return false;
}

function looksLikeTelegramHostNotMax() {
  if (hasMaxBridgeUserSignal()) return false;
  if (isClearlyMaxByUrlOrReferrer()) return false;
  return true;
}

function hasTelegramMiniAppContextExtended() {
  return hasTelegramLaunchParamsInLocation() || hasTelegramWebAppContentSignal();
}

function hasLiveTelegramAuthContext() {
  if (!looksLikeTelegramHostNotMax()) return false;
  return hasTelegramMiniAppContextExtended() || isTelegramHostMiniAppContext();
}

function isTelegramHostMiniAppContext() {
  return hasTelegramNativeBridge() && looksLikeTelegramHostNotMax();
}

/**
 * Wait for initData only when real client signals exist (URL, native platform, TelegramWebviewProxy).
 * Plain Chrome has WebApp stub but no TelegramWebviewProxy — no long wait.
 */
function shouldWaitForTelegramInitData() {
  if (typeof window === "undefined") return false;
  if (hasTelegramInitData() || hasTelegramWebAppContentSignal()) return false;
  const wa = window.Telegram?.WebApp;
  if (!wa) return false;
  if (!looksLikeTelegramHostNotMax()) return false;
  if (hasTelegramNativeBridge()) return true;
  if (hasTelegramLaunchParamsInLocation()) return true;
  const p = wa.platform;
  if (p === "ios" || p === "android" || p === "tdesktop" || p === "macos") return true;
  return false;
}

/**
 * Один поллинг: Telegram initData / unsafe и MAX window.WebApp (без двух последовательных таймаутов).
 * @param {{ telegram: boolean, max: boolean }} opts
 * @returns {Promise<"telegram"|"max"|"timeout">}
 */
function waitForTelegramOrMaxBridge(opts) {
  const wantTg = !!opts.telegram;
  const wantMax = !!opts.max;
  if (typeof window === "undefined") return Promise.resolve("timeout");
  if (!wantTg && !wantMax) return Promise.resolve("timeout");

  // В явном Telegram-контексте Telegram должен выигрывать.
  if (wantTg && hasTelegramInitData() && !isClearlyMaxByUrlOrReferrer()) {
    platformLog("waitTelegramOrMaxBridge: telegram initData already (skip wait)", platformSignalsSnapshot());
    return Promise.resolve("telegram");
  }
  if (
    wantMax &&
    hasMaxBridgeUserSignal() &&
    (isClearlyMaxByUrlOrReferrer() || !hasTelegramInitData())
  ) {
    platformLog("waitTelegramOrMaxBridge: max already (skip wait)", platformSignalsSnapshot());
    return Promise.resolve("max");
  }
  if (wantTg && (hasTelegramInitData() || hasTelegramWebAppContentSignal())) {
    platformLog("waitTelegramOrMaxBridge: telegram already (skip wait)", platformSignalsSnapshot());
    return Promise.resolve("telegram");
  }

  const waitMs = isMobileRuntime() ? BRIDGE_WAIT_MS_MOBILE : BRIDGE_WAIT_MS_DESKTOP;
  const tWait0 = typeof performance !== "undefined" ? performance.now() : 0;
  platformLog("waitTelegramOrMaxBridge: polling", {
    wantTg,
    wantMax,
    waitMs,
    mobile: isMobileRuntime(),
    pollMs: BRIDGE_POLL_MS,
    ...platformSignalsSnapshot(),
  });

  return new Promise((resolve) => {
    const deadline = now() + waitMs;
    let pollN = 0;
    const t = setInterval(() => {
      pollN++;
      if (
        wantMax &&
        hasMaxBridgeUserSignal() &&
        (isClearlyMaxByUrlOrReferrer() || !hasTelegramInitData())
      ) {
        clearInterval(t);
        const elapsed = typeof performance !== "undefined" ? (performance.now() - tWait0).toFixed(1) : "?";
        platformLog("waitTelegramOrMaxBridge: → max", { polls: pollN, waitElapsedMs: elapsed });
        resolve("max");
        return;
      }
      if (wantTg && hasTelegramInitData() && !isClearlyMaxByUrlOrReferrer()) {
        clearInterval(t);
        const elapsed = typeof performance !== "undefined" ? (performance.now() - tWait0).toFixed(1) : "?";
        platformLog("waitTelegramOrMaxBridge: → telegram by initData", { polls: pollN, waitElapsedMs: elapsed });
        resolve("telegram");
        return;
      }
      if (wantTg && (hasTelegramInitData() || hasTelegramWebAppContentSignal())) {
        clearInterval(t);
        const elapsed = typeof performance !== "undefined" ? (performance.now() - tWait0).toFixed(1) : "?";
        platformLog("waitTelegramOrMaxBridge: → telegram", { polls: pollN, waitElapsedMs: elapsed });
        resolve("telegram");
        return;
      }
      if (now() >= deadline) {
        clearInterval(t);
        const elapsed = typeof performance !== "undefined" ? (performance.now() - tWait0).toFixed(1) : "?";
        platformLog("waitTelegramOrMaxBridge: timeout", { polls: pollN, waitElapsedMs: elapsed, waitMs });
        resolve("timeout");
      }
    }, BRIDGE_POLL_MS);
  });
}

function hasMaxBridgeUserSignal() {
  if (typeof window === "undefined") return false;
  const wa = window.WebApp;
  if (!wa) return false;
  if (wa.initDataUnsafe?.user != null) return true;
  return typeof wa.initData === "string" && wa.initData.length > 0;
}

function isMaxMessengerUserAgent() {
  try {
    return typeof navigator !== "undefined" && /\bMAX\/[\d.]+/i.test(navigator.userAgent || "");
  } catch (_) {
    return false;
  }
}

function isClearlyMaxByUrlOrReferrer() {
  if (typeof window === "undefined") return false;
  if (isMaxMessengerUserAgent()) return true;
  const search = window.location.search || "";
  if (/tgWebAppPlatform=max/i.test(search)) return true;
  try {
    const referrer = typeof document.referrer === "string" ? document.referrer : "";
    if (/max\.(ru|com|ai)/i.test(referrer)) return true;
  } catch (_) {}
  return false;
}

function isMaybeMaxContainer() {
  if (typeof window === "undefined") return false;
  if (isMaxMessengerUserAgent()) return true;
  const search = window.location.search || "";
  if (/WebAppStartParam|startapp=|tgWebAppPlatform=max/i.test(search)) return true;
  if (hasMaxBridgeUserSignal()) return true;
  try {
    const referrer = typeof document.referrer === "string" ? document.referrer : "";
    if (/max\.(ru|com|ai)/i.test(referrer)) return true;
  } catch (_) {}
  return false;
}

function emit() {
  for (const l of listeners) {
    try {
      l(state);
    } catch (_) {}
  }
}

function setState(next) {
  state = { ...state, ...next, updatedAt: now() };
  persistState();
  emit();
}

function persistState() {
  if (typeof sessionStorage === "undefined") return;
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    if (state.channel === "web" || state.channel === "telegram" || state.channel === "max") {
      sessionStorage.setItem(LEGACY_STORAGE_KEY, state.channel);
    }
  } catch (_) {}
}

function applyPlatformOverrideFromLocation() {
  const channel = getPlatformOverrideFromLocation();
  if (!channel) return false;
  state = {
    status: "resolved",
    channel,
    source: "query_platform",
    bridgeWaited: false,
    updatedAt: now(),
  };
  persistState();
  platformLog("applyPlatformOverrideFromLocation", { channel });
  return true;
}

function restoreState() {
  if (typeof sessionStorage === "undefined") return false;
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (parsed && (parsed.channel === "web" || parsed.channel === "telegram" || parsed.channel === "max")) {
        state = {
          status: parsed.status === "resolved" ? "resolved" : "unknown",
          channel: parsed.channel,
          source: parsed.source || "session_cache",
          bridgeWaited: !!parsed.bridgeWaited,
          updatedAt: parsed.updatedAt || 0,
        };
        platformLog("restoreState from app_platform_state_v1", {
          status: state.status,
          channel: state.channel,
          source: state.source,
        });
        return true;
      }
    }
    const legacy = sessionStorage.getItem(LEGACY_STORAGE_KEY);
    if (legacy === "web" || legacy === "telegram" || legacy === "max") {
      state = {
        status: "resolved",
        channel: legacy,
        source: "legacy_session_cache",
        bridgeWaited: false,
        updatedAt: now(),
      };
      platformLog("restoreState from legacy app_platform", { channel: state.channel });
      return true;
    }
  } catch (_) {}
  return false;
}

function detectImmediate() {
  if (typeof window === "undefined") {
    return { channel: "web", source: "ssr" };
  }
  const platformOverride = getPlatformOverrideFromLocation();
  if (platformOverride) return { channel: platformOverride, source: "query_platform" };
  if (hasTelegramInitData() && !isClearlyMaxByUrlOrReferrer()) {
    return { channel: "telegram", source: "telegram_init_data" };
  }
  if (hasMaxBridgeUserSignal() && (isClearlyMaxByUrlOrReferrer() || !hasTelegramInitData())) {
    return { channel: "max", source: "max_bridge_signal" };
  }
  if (isClearlyMaxByUrlOrReferrer()) return { channel: "max", source: "max_url_or_referrer" };
  if (hasTelegramMiniAppContextExtended())
    return { channel: "telegram", source: "telegram_launch_or_unsafe" };
  if (isTelegramHostMiniAppContext())
    return { channel: "telegram", source: "telegram_native_webview" };
  if (isMaybeMaxContainer()) return { channel: "unknown", source: "maybe_max_pending_bridge" };
  return { channel: "web", source: "web_fallback" };
}

/** sessionStorage мог содержать web с прошлого визита; при открытии из Telegram пересчитать. */
function invalidateStaleResolvedWeb() {
  if (typeof window === "undefined") return;
  if (state.status !== "resolved" || state.channel !== "web") return;
  const d = detectImmediate();
  platformLog("invalidateStaleResolvedWeb: had cached web", {
    detectNow: d,
    signals: platformSignalsSnapshot(),
    willWaitTg: shouldWaitForTelegramInitData(),
  });
  if (d.channel === "telegram" || d.channel === "max") {
    setState({ status: "resolved", channel: d.channel, source: "stale_web_overridden_" + d.source });
    platformLog("invalidateStaleResolvedWeb: overridden cache →", { channel: d.channel });
    return;
  }
  if (shouldWaitForTelegramInitData()) {
    setState({
      status: "unknown",
      channel: "unknown",
      source: "telegram_pending_after_cached_web",
      bridgeWaited: false,
    });
    platformLog("invalidateStaleResolvedWeb: opened re-resolve (telegram wait)", {
      status: state.status,
      source: state.source,
    });
  }
}

function invalidateStaleResolvedTelegram() {
  if (typeof window === "undefined") return;
  if (state.status !== "resolved" || state.channel !== "telegram") return;
  if (hasLiveTelegramAuthContext()) return;
  if (getPlatformOverrideFromLocation()) return;

  const d = detectImmediate();
  platformLog("invalidateStaleResolvedTelegram: had cached telegram", {
    detectNow: d,
    signals: platformSignalsSnapshot(),
  });
  if (d.channel === "telegram") return;
  if (d.channel === "web" || d.channel === "max") {
    setState({ status: "resolved", channel: d.channel, source: "stale_telegram_overridden_" + d.source });
    return;
  }
  setState({
    status: "unknown",
    channel: "unknown",
    source: "stale_telegram_pending_" + d.source,
    bridgeWaited: false,
  });
}

async function doResolve() {
  const t0 = typeof performance !== "undefined" ? performance.now() : 0;
  platformLog("doResolve: start", { prev: { status: state.status, channel: state.channel, source: state.source } });
  const immediate = detectImmediate();
  platformLog("doResolve: detectImmediate", { ...immediate, detectMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?", signals: platformSignalsSnapshot() });
  if (immediate.channel === "telegram" || immediate.channel === "max") {
    setState({ status: "resolved", channel: immediate.channel, source: immediate.source });
    platformLog("doResolve: done (immediate)", {
      channel: immediate.channel,
      source: immediate.source,
      totalMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?",
    });
    return state;
  }
  if (immediate.channel === "web") {
    const wantTgWait = shouldWaitForTelegramInitData();
    platformLog("doResolve: branch web", { wantTgWait, reason: wantTgWait ? "wait telegram init" : "no wait → resolve web" });
    if (wantTgWait) {
      setState({ status: "resolving", channel: "unknown", source: "telegram_pending_initdata", bridgeWaited: false });
      const tBefore = typeof performance !== "undefined" ? performance.now() : 0;
      const raced = await waitForTelegramOrMaxBridge({ telegram: true, max: true });
      platformLog("doResolve: after waitTelegramOrMaxBridge (web branch)", {
        raced,
        blockMs: typeof performance !== "undefined" ? (performance.now() - tBefore).toFixed(1) : "?",
      });
      if (raced === "telegram") {
        setState({ status: "resolved", channel: "telegram", source: "telegram_after_initdata_wait", bridgeWaited: true });
        platformLog("doResolve: done → telegram after wait", {
          totalMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?",
        });
        return state;
      }
      if (raced === "max") {
        setState({ status: "resolved", channel: "max", source: "late_max_bridge", bridgeWaited: true });
        platformLog("doResolve: done → max after parallel wait", {
          totalMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?",
        });
        return state;
      }
    }
    setState({ status: "resolved", channel: "web", source: immediate.source });
    platformLog("doResolve: done → web", {
      source: immediate.source,
      totalMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?",
    });
    return state;
  }
  platformLog("doResolve: branch unknown (maybe Max)", { source: immediate.source });
  setState({ status: "resolving", channel: "unknown", source: immediate.source, bridgeWaited: false });
  const tBeforeBridge = typeof performance !== "undefined" ? performance.now() : 0;
  const raced = await waitForTelegramOrMaxBridge({ telegram: true, max: true });
  platformLog("doResolve: after waitTelegramOrMaxBridge (unknown branch)", {
    raced,
    blockMs: typeof performance !== "undefined" ? (performance.now() - tBeforeBridge).toFixed(1) : "?",
  });
  if (raced === "max") {
    setState({ status: "resolved", channel: "max", source: "late_max_bridge", bridgeWaited: true });
    platformLog("doResolve: done → max", {
      totalMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?",
    });
    return state;
  }
  if (raced === "telegram") {
    setState({ status: "resolved", channel: "telegram", source: "telegram_after_wait", bridgeWaited: true });
    platformLog("doResolve: done → telegram after parallel wait", {
      totalMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?",
    });
    return state;
  }
  if (hasTelegramInitData() || hasTelegramWebAppContentSignal()) {
    setState({ status: "resolved", channel: "telegram", source: "telegram_after_wait", bridgeWaited: true });
    platformLog("doResolve: done → telegram after wait timeout edge", {
      totalMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?",
    });
    return state;
  }
  if (hasMaxBridgeUserSignal() && !hasTelegramInitData() && !hasTelegramWebAppContentSignal()) {
    setState({ status: "resolved", channel: "max", source: "late_max_bridge", bridgeWaited: true });
    platformLog("doResolve: done → max after wait timeout edge", {
      totalMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?",
    });
    return state;
  }
  setState({ status: "resolved", channel: "web", source: "web_after_bridge_timeout", bridgeWaited: true });
  platformLog("doResolve: done → web after max-wait timeout", {
    totalMs: typeof performance !== "undefined" ? (performance.now() - t0).toFixed(1) : "?",
  });
  return state;
}

export function getPlatformState() {
  return state;
}

export function subscribePlatformState(listener) {
  listeners.add(listener);
  try {
    listener(state);
  } catch (_) {}
  return () => listeners.delete(listener);
}

export function getClientChannel() {
  // Если кэш дал web, но bridge MAX появился поздно — для чтения возвращаем max.
  // Персист состояния откладываем в microtask, чтобы не дергать emit/setState из React render.
  if (
    state.status === "resolved" &&
    state.channel === "web" &&
    hasMaxBridgeUserSignal() &&
    !hasTelegramInitData()
  ) {
    if (!lateMaxWebFixScheduled) {
      lateMaxWebFixScheduled = true;
      queueMicrotask(() => {
        lateMaxWebFixScheduled = false;
        if (
          state.status === "resolved" &&
          state.channel === "web" &&
          hasMaxBridgeUserSignal() &&
          !hasTelegramInitData()
        ) {
          setState({ status: "resolved", channel: "max", source: "late_max_after_web_cache", bridgeWaited: true });
          platformLog("getClientChannel: deferred auto-switch web -> max", platformSignalsSnapshot());
        }
      });
    }
    return "max";
  }
  if (state.status === "resolved") return state.channel;
  const immediate = detectImmediate();
  if (immediate.channel === "web" || immediate.channel === "telegram" || immediate.channel === "max") {
    return immediate.channel;
  }
  return "unknown";
}

export function hasTelegramWebApp() {
  return hasTelegramInitData() || hasTelegramWebAppContentSignal();
}

export function isTelegramAuthContext() {
  return hasLiveTelegramAuthContext();
}

export async function resolveClientPlatform() {
  platformLog("resolveClientPlatform: enter", {
    status: state.status,
    channel: state.channel,
    source: state.source,
    signals: platformSignalsSnapshot(),
  });
  invalidateStaleResolvedWeb();
  invalidateStaleResolvedTelegram();
  if (state.status === "resolved") {
    platformLog("resolveClientPlatform: early return (already resolved)", {
      channel: state.channel,
      source: state.source,
    });
    return state;
  }
  if (!resolvePromise) {
    platformLog("resolveClientPlatform: starting doResolve()");
    resolvePromise = doResolve()
      .then((s) => {
        platformLog("resolveClientPlatform: finished", {
          channel: s.channel,
          status: s.status,
          source: s.source,
        });
        return s;
      })
      .finally(() => {
        resolvePromise = null;
      });
  } else {
    platformLog("resolveClientPlatform: awaiting in-flight doResolve");
  }
  return resolvePromise;
}

export function waitForPlatformResolved() {
  return resolveClientPlatform();
}

export function resetClientPlatform() {
  state = {
    status: "unknown",
    channel: "unknown",
    source: "reset",
    bridgeWaited: false,
    updatedAt: 0,
  };
  if (typeof sessionStorage !== "undefined") {
    try {
      sessionStorage.removeItem(STORAGE_KEY);
      sessionStorage.removeItem(LEGACY_STORAGE_KEY);
    } catch (_) {}
  }
  emit();
}

// Bootstrap from session cache once.
if (!applyPlatformOverrideFromLocation() && !restoreState()) {
  platformLog("restoreState: no session cache (fresh boot)", { signals: platformSignalsSnapshot() });
}
