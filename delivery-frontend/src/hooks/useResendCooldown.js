import { useState, useEffect, useCallback } from "react";

export const RESEND_COOLDOWN_SEC = 60;

/**
 * Таймер до следующей доступной повторной отправки (код/звонок).
 * @param {number} durationSec — пауза в секундах (по умолчанию 60)
 */
export function useResendCooldown(durationSec = RESEND_COOLDOWN_SEC) {
  const [remainingSec, setRemainingSec] = useState(0);

  useEffect(() => {
    if (remainingSec <= 0) return undefined;
    const id = setTimeout(() => setRemainingSec((s) => (s <= 1 ? 0 : s - 1)), 1000);
    return () => clearTimeout(id);
  }, [remainingSec]);

  const startCooldown = useCallback(() => {
    setRemainingSec(durationSec);
  }, [durationSec]);

  const resetCooldown = useCallback(() => {
    setRemainingSec(0);
  }, []);

  return {
    canResend: remainingSec <= 0,
    remainingSec,
    startCooldown,
    resetCooldown,
  };
}
