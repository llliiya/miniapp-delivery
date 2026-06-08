import {
  getClientChannel,
  hasTelegramWebApp as hasTelegramWebAppFromResolver,
  isTelegramAuthContext as isTelegramAuthContextFromResolver,
  resetClientPlatform,
} from "./clientPlatform";

/** @returns {"web"|"telegram"|"max"} */
export function getPlatform() {
  const ch = getClientChannel();
  return ch === "max" || ch === "telegram" ? ch : "web";
}

export function isWeb() {
  return getPlatform() === "web";
}

export function isTelegram() {
  return getPlatform() === "telegram";
}

export function isMax() {
  return getPlatform() === "max";
}

/** Реальное наличие Telegram WebApp и initData (без кэша). Для разделения сценариев Telegram vs MAX/Web. */
export function hasTelegramWebApp() {
  return hasTelegramWebAppFromResolver();
}

/** Контекст авторизации: Telegram — не показывать экран входа. Используется в App вместо только isTelegram() из-за кэша платформы. */
export function isTelegramAuthContext() {
  return isTelegramAuthContextFromResolver();
}

/** Сброс (для тестов или при явной смене контекста). Следующий getPlatform() определит заново. */
export function resetPlatform() {
  resetClientPlatform();
}

/** Legacy no-op: платформа теперь определяется единым resolver lifecycle. */
export function setPlatformToMax() {
  // intentional no-op
}
