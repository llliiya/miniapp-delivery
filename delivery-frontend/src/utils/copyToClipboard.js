/**
 * Копирует текст в буфер без лишних пробелов/переносов в конце.
 * @param {string} text
 * @returns {Promise<void>}
 */
export async function copyToClipboard(text) {
  const normalized = text.replace(/\r\n/g, '\n').replace(/\n+$/, '')
  if (!normalized) {
    throw new Error('empty')
  }
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(normalized)
    return
  }
  const textarea = document.createElement('textarea')
  textarea.value = normalized
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.left = '-9999px'
  textarea.style.top = '0'
  document.body.appendChild(textarea)
  textarea.select()
  const ok = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!ok) {
    throw new Error('copy failed')
  }
}

/**
 * Текст для передачи логина и временного пароля (ровно две строки).
 * @param {string} login
 * @param {string} temporaryPassword
 */
export function buildCredentialsCopyText(login, temporaryPassword) {
  const safeLogin = (login ?? '').trim()
  const safePassword = (temporaryPassword ?? '').trim()
  return `Логин: ${safeLogin}\nВременный пароль: ${safePassword}`
}

/**
 * Разбирает текст из буфера после «Скопировать логин и пароль».
 * @param {string} text
 * @returns {{ login: string, password: string }}
 */
export function parseCredentialsFromClipboard(text) {
  if (!text || typeof text !== 'string') {
    return { login: '', password: '' }
  }
  const normalized = text.replace(/\r\n/g, '\n').trim()
  let login = ''
  let password = ''
  for (const line of normalized.split('\n')) {
    const trimmed = line.trim()
    const loginMatch = trimmed.match(/^логин:\s*(.+)$/i)
    const passMatch = trimmed.match(/^временный\s+пароль:\s*(.+)$/i)
    if (loginMatch) {
      login = loginMatch[1].trim()
    }
    if (passMatch) {
      password = passMatch[1].trim()
    }
  }
  return { login, password }
}

/**
 * Нормализует вставку одной строки с префиксом «Логин:» / «Временный пароль:».
 * @param {string} value
 * @param {'login' | 'password'} field
 */
export function normalizeCredentialPaste(value, field) {
  const parsed = parseCredentialsFromClipboard(value)
  if (field === 'login' && parsed.login) {
    return parsed.login
  }
  if (field === 'password' && parsed.password) {
    return parsed.password
  }
  const single = (value ?? '').trim()
  if (field === 'login') {
    return single.replace(/^логин:\s*/i, '').trim()
  }
  return single.replace(/^временный\s+пароль:\s*/i, '').trim()
}
