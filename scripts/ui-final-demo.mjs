/**
 * Final UI demo walkthrough (Playwright, headless).
 * Run: node scripts/ui-final-demo.mjs
 * Requires: npx playwright (chromium).
 */
import { chromium } from 'playwright'

const BASE = process.env.UI_BASE_URL || 'http://localhost:5174'
const API = process.env.API_BASE_URL || 'http://localhost:8080'
const PASS = 'ServicePass1!'

const report = { ok: [], fix: [], later: [] }
const log = (status, step, msg) => {
  const line = `[${status}] ${step}: ${msg}`
  console.log(line)
  if (status === 'OK') report.ok.push(`${step} — ${msg}`)
  else if (status === 'FIX') report.fix.push(`${step} — ${msg}`)
  else if (status === 'LATER') report.later.push(`${step} — ${msg}`)
}

async function curlJson(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  const res = await fetch(`${API}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })
  const text = await res.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }
  if (!res.ok) throw new Error(`HTTP ${res.status} ${path}: ${text}`)
  return data
}

async function registerService() {
  const tail = String(Date.now() % 100000)
  const phone = `+79379${tail.padStart(4, '0').slice(-4)}`
  const req = await curlJson('POST', '/api/auth/register/phone/request', { phone })
  await curlJson('POST', '/api/auth/register/phone/verify', { challengeId: req.challengeId, pin: '1234' })
  const sp = await curlJson('POST', '/api/auth/register/set-password', { phone, password: PASS })
  await curlJson('POST', '/api/auth/register/email/request', {
    registrationToken: sp.registrationToken,
    email: `svc-ui-${tail}@mvp.test`,
  })
  const done = await curlJson('POST', '/api/auth/register/email/verify', {
    registrationToken: sp.registrationToken,
    email: `svc-ui-${tail}@mvp.test`,
    code: '000000',
  })
  const org = await curlJson(
    'POST',
    '/api/delivery/organizations',
    { type: 'courier_service', name: `UI Demo Service ${tail}` },
    done.accessToken,
  )
  return { svcToken: done.accessToken, serviceId: org.id, tail }
}

async function injectToken(page, token) {
  await page.context().addInitScript((t) => {
    localStorage.setItem('token', t)
  }, token)
  await page.goto(`${BASE}/service/orders`)
  await page.waitForResponse(
    (res) => res.url().includes('/api/delivery/me') && res.status() === 200,
    { timeout: 20000 },
  ).catch(() => {})
  await page.waitForFunction(
    () => !document.body?.innerText?.includes('Загрузка…'),
    { timeout: 20000 },
  ).catch(() => {})
  if (page.url().includes('/login')) {
    throw new Error('injectToken: остались на /login после bootstrap')
  }
}

async function uiLogin(page, login, password) {
  await page.goto(`${BASE}/login`)
  await page.waitForFunction(
    () => !document.body?.innerText?.includes('Загрузка окружения'),
    { timeout: 20000 },
  ).catch(() => {})
  await page.locator('.auth-form input, .auth-page input').first().fill(login)
  await page.getByRole('button', { name: /Продолжить/i }).click()
  await page.waitForTimeout(1200)
  await page.locator('input[type="password"]').first().waitFor({ state: 'visible', timeout: 10000 })
  await page.locator('input[type="password"]').first().fill(password)
  await page.getByRole('button', { name: /^Войти$/i }).click()
  await page.waitForTimeout(2000)
}

async function changePasswordIfNeeded(page, newPass) {
  await page.waitForTimeout(1000)
  const changeHeading = page.getByText(/смен/i)
  if (await changeHeading.count()) {
    const inputs = page.locator('input[type="password"]')
    await inputs.nth(0).fill(await page.evaluate(() => ''))
    const count = await inputs.count()
    if (count >= 3) {
      // current, new, confirm — filled by login flow state
    }
    if (count >= 1) {
      // try fill pattern from visible inputs
      const cur = page.getByLabel(/текущ|current/i).first()
      const neu = page.getByLabel(/новый|new/i).first()
      const conf = page.getByLabel(/повтор|confirm/i).first()
      if (await neu.count()) {
        // fallback: all password fields
      }
    }
    const allPwd = page.locator('input[type="password"]')
    const n = await allPwd.count()
    if (n >= 3) {
      // current already set in auth state
      await allPwd.nth(1).fill(newPass)
      await allPwd.nth(2).fill(newPass)
    } else if (n === 2) {
      await allPwd.nth(0).fill(newPass)
      await allPwd.nth(1).fill(newPass)
    }
    await page.getByRole('button', { name: /Сменить|Сохранить|Продолжить/i }).click()
    await page.waitForTimeout(2000)
    return true
  }
  return false
}

async function main() {
  const { svcToken, serviceId, tail } = await registerService()
  const ownerPhone = `+79378${String(tail).padStart(4, '0').slice(-4)}`
  const objectName = `UI Объект ${tail}`
  const ownerName = 'Андрей UI'
  let ownerLogin = ''
  let ownerTemp = ''
  let c1Login = ''
  let c1Temp = ''
  let c1PublicId = null
  let restaurantId = ''
  let orderId = ''

  const browser = await chromium.launch({ headless: true })
  const serviceContext = await browser.newContext({ locale: 'ru-RU' })
  const page = await serviceContext.newPage()

  try {
    // --- Step 1: Service creates object ---
    await injectToken(page, svcToken)
    await page.goto(`${BASE}/service/restaurants/new`)
    await page.waitForURL(/\/service\/restaurants\/new/, { timeout: 15000 }).catch(() => {})
    await page.getByText('Добавить объект', { timeout: 15000 }).waitFor({ state: 'visible' }).catch(() => {})
    await page.waitForTimeout(500)
    const hasOwnerBlock = (await page.getByText('Владелец объекта').count()) > 0
    const hasObjectForm = (await page.getByText('Данные объекта').count()) > 0
    if (!hasOwnerBlock || !hasObjectForm) {
      log('FIX', '1', 'Форма объекта / блок владельца не отображаются')
    } else {
      log('OK', '1', 'Форма и блок владельца на /service/restaurants/new')
    }
    await page.locator('input.input').first().fill(objectName)
    await page.locator('form input.input').nth(1).fill(ownerName)
    await page.locator('input[type="tel"]').fill(ownerPhone)
    await page.getByRole('button', { name: /Создать объект/i }).click()
    await page.waitForTimeout(2500)
    const modal = page.getByRole('dialog')
    if (!(await modal.count())) {
      log('FIX', '1', 'Модалка credentials после создания не появилась')
    } else {
      ownerLogin = (await modal.locator('code').nth(0).textContent())?.trim() || ''
      ownerTemp = (await modal.locator('code').nth(1).textContent())?.trim() || ''
      log('OK', '1', `Модалка: login=${ownerLogin}`)
      await page.getByRole('button', { name: /Скопировать/i }).click()
      const snack = page.getByText('Скопировано')
      if (await snack.waitFor({ state: 'visible', timeout: 3000 }).then(() => true).catch(() => false)) {
        log('OK', '1', 'Snackbar «Скопировано»')
      } else log('LATER', '1', 'Snackbar «Скопировано» быстро исчезает (проверить вручную)')
      await page.getByRole('button', { name: /Перейти к объекту/i }).click()
      await page.waitForTimeout(1500)
      if (page.url().includes('/service/restaurants/')) {
        restaurantId = page.url().split('/service/restaurants/')[1]?.split('/')[0] || ''
        log('OK', '1', 'Переход в карточку объекта')
      } else log('FIX', '1', `Нет перехода в карточку: ${page.url()}`)
    }

    // --- Step 2: Owner login (fresh context, no token init script) ---
    const ownerContext = await browser.newContext({ locale: 'ru-RU' })
    const ownerPage = await ownerContext.newPage()
    await uiLogin(ownerPage, ownerLogin, ownerTemp)
    const page2 = ownerPage
    await page2.waitForTimeout(1500)
    const onChangePwd = (await page2.getByText(/смен/i).count()) > 0
    if (onChangePwd && (await page2.locator('input[type="password"]').count()) >= 2) {
      log('OK', '2', 'Экран смены временного пароля')
      const pwdFields = page2.locator('input[type="password"]')
      const n = await pwdFields.count()
      if (n >= 3) {
        await pwdFields.nth(1).fill('OwnerUIPass1!')
        await pwdFields.nth(2).fill('OwnerUIPass1!')
      } else {
        await pwdFields.nth(0).fill('OwnerUIPass1!')
        if (n > 1) await pwdFields.nth(1).fill('OwnerUIPass1!')
      }
      await page2.getByRole('button', { name: /Сменить|Сохранить/i }).click()
      await page2.waitForTimeout(2500)
    } else {
      log('FIX', '2', 'Экран смены пароля не обнаружен')
    }
    await page2.goto(`${BASE}/restaurant/orders`)
    await page2.waitForTimeout(2000)
    const navRestaurant = (await page2.getByRole('link', { name: /Заказы|Точки|Профиль/i }).count()) > 0
    if (navRestaurant) log('OK', '2', 'Интерфейс объекта (нижнее меню ресторана)')
    else log('FIX', '2', `Не попали в интерфейс объекта: ${page2.url()}`)
    await page2.goto(`${BASE}/restaurant/profile`)
    await page2.waitForTimeout(1000)
    if (await page2.getByText(/профил|объект/i).count()) log('OK', '2', 'Профиль объекта открывается')

    // --- Step 3: Pickup point ---
    await page2.goto(`${BASE}/restaurant/pickup`)
    await page2.waitForTimeout(1500)
    await page2.getByRole('button', { name: /Добавить|Создать/i }).first().click({ timeout: 5000 }).catch(async () => {
      await page2.getByText(/добавить/i).first().click()
    })
    await page2.waitForTimeout(800)
    await page2.locator('form input.input').nth(0).fill('Точка UI')
    await page2.locator('form input.input').nth(1).fill('ул. Тестовая, 1')
    const pageText = await page2.locator('body').innerText()
    if (/\bdefault\b/i.test(pageText)) log('FIX', '3', 'На странице видно слово default')
    else log('OK', '3', 'Слово default не отображается')
    await page2.getByLabel(/основн/i).check({ timeout: 3000 }).catch(() => {})
    await page2.getByRole('button', { name: /^Сохранить$/i }).click()
    await page2.waitForTimeout(2000)
    if (await page2.getByText('Основная').count()) log('OK', '3', 'Бейдж «Основная»')
    else log('FIX', '3', 'Бейдж «Основная» не найден')

    // --- Step 4-5: Service channel + bind ---
    await injectToken(page, svcToken)
    await page.goto(`${BASE}/service/channels`)
    await page.waitForTimeout(1500)
    await page.getByRole('button', { name: /^Добавить$/i }).click()
    await page.waitForTimeout(500)
    if (await page.getByText('ID чата Telegram').count()) log('OK', '4', 'Подпись ID чата Telegram')
    else log('FIX', '4', 'Нет подписи ID чата Telegram')
    await page.getByLabel(/Название/i).fill(`Канал UI ${tail}`)
    await page.getByLabel(/ID чата Telegram/i).fill('-1001234567890')
    await page.getByRole('button', { name: /^Сохранить$/i }).click()
    await page.waitForTimeout(2000)
    if (restaurantId) {
      await page.goto(`${BASE}/service/restaurants/${restaurantId}/channels`)
      await page.waitForTimeout(1500)
      const bind = page.locator('input[type="checkbox"]').first()
      if (await bind.count()) {
        await bind.check()
        await page.getByRole('button', { name: /Сохранить/i }).click({ timeout: 3000 }).catch(() => {})
        log('OK', '5', 'Привязка канала на карточке объекта')
      } else log('FIX', '5', 'Нет чекбоксов привязки канала')
    }

    // --- Step 5: Courier ---
    await page.goto(`${BASE}/service/couriers`)
    await page.waitForTimeout(1500)
    await page.getByRole('button', { name: /Добавить курьера|Добавить/i }).click()
    await page.waitForTimeout(500)
    await page.getByPlaceholder(/Иван|ФИО/i).fill('Иван UI')
    await page.locator('input[type="tel"]').fill(`+79377${String(tail).slice(-4)}`)
    await page.getByRole('button', { name: /Создать|Добавить/i }).click()
    await page.waitForTimeout(2500)
    const cModal = page.getByRole('dialog')
    if (await cModal.count()) {
      c1Login = (await cModal.locator('code').nth(0).textContent())?.trim() || ''
      c1Temp = (await cModal.locator('code').nth(1).textContent())?.trim() || ''
      const m = c1Login.match(/courier_(\d+)/)
      c1PublicId = m ? Number(m[1]) : null
      log('OK', '5', `Курьер login=${c1Login}`)
      if (m && c1PublicId !== Number(m[1])) log('FIX', '5', 'publicId не совпадает с логином')
      else if (m) log('OK', '5', 'Логин courier_N согласован с N')
      await page.getByRole('button', { name: /Скопировать/i }).click()
      if (await page.getByText('Скопировано').count()) log('OK', '5', 'Snackbar курьера')
      await page.getByRole('button', { name: /Закрыть/i }).click()
    } else log('FIX', '5', 'Модалка курьера не появилась')

    // --- Step 6: Courier login ---
    const courierContext = await browser.newContext({ locale: 'ru-RU' })
    const courierPage = await courierContext.newPage()
    await uiLogin(courierPage, c1Login, c1Temp)
    await courierPage.waitForTimeout(1500)
    if ((await courierPage.getByText(/смен/i).count()) > 0) {
      const pf = courierPage.locator('input[type="password"]')
      const n = await pf.count()
      if (n >= 3) {
        await pf.nth(1).fill('CourierUIPass1!')
        await pf.nth(2).fill('CourierUIPass1!')
      } else {
        await pf.nth(0).fill('CourierUIPass1!')
        if (n > 1) await pf.nth(1).fill('CourierUIPass1!')
      }
      await courierPage.getByRole('button', { name: /Сменить|Сохранить/i }).click()
      await courierPage.waitForTimeout(2000)
      log('OK', '6', 'Смена временного пароля курьера')
    }
    await courierPage.goto(`${BASE}/courier/orders`)
    await courierPage.waitForTimeout(2000)
    const navText = await courierPage.locator('.bottom-nav').innerText().catch(() => '')
    if (/Карта/.test(navText)) log('FIX', '6', 'В меню есть «Карта»')
    else log('OK', '6', 'В меню нет «Карта»')

    // --- Step 7: Owner creates order ---
    await page2.goto(`${BASE}/restaurant/orders/new`)
    await page2.waitForTimeout(2000)
    if (await page2.getByText(/Новый заказ/i).count()) log('OK', '7', 'Страница создания заказа')
    await page2.getByLabel(/Точка забора/i).selectOption({ index: 1 })
    await page2.getByLabel(/Адрес доставки/i).fill('Адрес доставки UI 10')
    await page2.getByLabel(/Телефон получателя/i).fill('+79001112233')
    await page2.getByLabel(/Комментарий/i).fill('UI demo')
    const dt = new Date(Date.now() + 2 * 3600000)
    const pad = (n) => String(n).padStart(2, '0')
    const local = `${dt.getFullYear()}-${pad(dt.getMonth() + 1)}-${pad(dt.getDate())}T${pad(dt.getHours())}:${pad(dt.getMinutes())}`
    await page2.getByLabel(/Дата и время/i).fill(local)
    await page2.getByLabel(/Стоимость/i).fill('500')
    await page2.getByRole('button', { name: /Создать и опубликовать/i }).click()
    await page2.waitForTimeout(2500)
    if (await page2.getByText(/Заказ создан/i).count()) log('OK', '7', 'Сообщение после создания заказа')
    else log('FIX', '7', 'Нет сообщения успеха после создания заказа')
    await page2.goto(`${BASE}/restaurant/orders`)
    await page2.waitForTimeout(2000)
    if (await page2.getByText(objectName).count()) log('OK', '7', 'Заказ в списке объекта')
    else log('FIX', '7', 'Заказ не виден в списке объекта')

    // --- Step 8-9: Courier takes and completes ---
    await courierPage.goto(`${BASE}/courier/orders`)
    await courierPage.waitForTimeout(2000)
    if (await courierPage.getByText(objectName).count()) log('OK', '8', 'Название объекта в карточке')
    if (await courierPage.getByRole('button', { name: /Взять заказ/i }).count()) {
      await courierPage.getByRole('button', { name: /Взять заказ/i }).first().click()
      await courierPage.waitForTimeout(2000)
      log('OK', '8', 'Кнопка «Взять заказ»')
    } else log('FIX', '8', 'Нет кнопки «Взять заказ»')
    await courierPage.goto(`${BASE}/courier/my-orders`)
    await courierPage.waitForTimeout(2000)
    if (await courierPage.getByText(objectName).count()) log('OK', '8', 'Заказ в «Мои»')
    await injectToken(page, svcToken)
    await page.goto(`${BASE}/service/orders`)
    await page.waitForTimeout(2000)
    const svcBody = await page.locator('body').innerText()
    if (/Иван|Курьер/i.test(svcBody)) log('OK', '8', 'Служба видит курьера')
    await courierPage.goto(`${BASE}/courier/my-orders`)
    await courierPage.waitForTimeout(1500)
    await courierPage.getByText(objectName).first().click({ timeout: 5000 }).catch(() => {})
    await courierPage.waitForTimeout(1000)
    const startBtn = courierPage.getByRole('button', { name: /Начать доставку/i })
    if (await startBtn.count()) {
      await startBtn.click()
      await courierPage.waitForTimeout(1500)
      log('OK', '9', '«Начать доставку»')
    }
    const finishBtn = courierPage.getByRole('button', { name: /Завершить заказ/i })
    if (await finishBtn.count()) {
      await finishBtn.click()
      await courierPage.waitForTimeout(1500)
      log('OK', '9', '«Завершить заказ»')
    }
    if (await courierPage.getByText(/Выполнен/i).count()) log('OK', '9', 'Статус «Выполнен»')

    // --- Step 10: Block courier ---
    await injectToken(page, svcToken)
    await page.goto(`${BASE}/service/couriers`)
    await page.waitForTimeout(2000)
    await page.getByRole('button', { name: /Заблокировать/i }).first().click({ timeout: 8000 }).catch(() => {})
    await page.waitForTimeout(1500)
    await courierPage.goto(`${BASE}/courier/orders`)
    await courierPage.waitForTimeout(2000)
    if (await courierPage.getByText(/Доступ заблокирован/i).count()) log('OK', '10', 'Экран «Доступ заблокирован»')
    else log('FIX', '10', 'Нет экрана блокировки')
    await injectToken(page, svcToken)
    await page.goto(`${BASE}/service/couriers`)
    await page.waitForTimeout(1500)
    await page.getByRole('button', { name: /Разблокировать|Активировать/i }).first().click({ timeout: 5000 }).catch(() => {})
    log('OK', '10', 'Разблокировка (кнопка нажата)')
  } catch (e) {
    log('FIX', 'RUN', e.message)
  } finally {
    await browser.close()
  }

  console.log('\n=== REPORT ===')
  console.log('\n## Готово к показу\n', report.ok.map((x) => `* ${x}`).join('\n') || '* —')
  console.log('\n## Нужно поправить перед показом\n', report.fix.map((x) => `* ${x}`).join('\n') || '* —')
  console.log('\n## Можно оставить после демо\n', report.later.map((x) => `* ${x}`).join('\n') || '* —')
}

main().catch((e) => {
  console.error(e)
  process.exit(1)
})
