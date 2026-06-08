import React from "react";

/**
 * Кнопка повторной отправки с блокировкой на время таймера.
 * @param {string} idleLabel — текст когда можно нажать (например «Отправить код повторно»)
 */
export default function ResendCooldownButton({
  idleLabel = "Отправить повторно",
  canResend,
  remainingSec,
  loading = false,
  onClick,
  className = "auth-link",
}) {
  const disabled = loading || !canResend;
  const label = canResend ? idleLabel : `${idleLabel} (${remainingSec} с)`;
  return (
    <button type="button" className={className} disabled={disabled} onClick={onClick}>
      {label}
    </button>
  );
}
