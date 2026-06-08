/**
 * Runs reset-access and returns credentials only for the latest in-flight request.
 * Prevents a stale response from overwriting a newer reset (e.g. double-click).
 *
 * @param {{ current: number }} sequenceRef
 * @param {() => Promise<{ login?: string, temporaryPassword?: string }>} resetCall
 * @returns {Promise<{ applied: boolean, creds: { login?: string, temporaryPassword?: string } | null }>}
 */
export async function runLatestResetAccess(sequenceRef, resetCall) {
  const seq = ++sequenceRef.current
  const creds = await resetCall()
  if (seq !== sequenceRef.current) {
    return { applied: false, creds: null }
  }
  return { applied: true, creds }
}
