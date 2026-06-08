import React, { useState, useEffect, useRef, useCallback } from "react";
import PageContainer from "../../components/PageContainer";
import {
  loginPhone,
  verifyLoginEmailCode,
  resendLoginEmailCode,
  logout,
  forgotPasswordRequest,
  forgotPasswordVerify,
  forgotPasswordComplete,
  changeSecurityPassword,
} from "../../api/webAuthService";
import RegistrationRequestScreen from "./RegistrationRequestScreen.jsx";
import { linkMax } from "../../api/maxAuthService";
import { linkTelegram } from "../../api/authService";
import {
  getMaxLinkUserId,
  clearMaxLinkUserId,
  getTelegramLinkLoginExisting,
  clearTelegramLinkLoginExisting,
  setTelegramLinkLoginExisting,
} from "./messengerLinkSession";
import { toE164, looksLikePhone } from "../../utils/phone";
import {
  normalizeCredentialPaste,
  parseCredentialsFromClipboard,
} from "../../utils/copyToClipboard.js";
import PhoneInput from "../../components/PhoneInput";
import { getPlatform } from "../../utils/platform";
import { setToken, removeToken } from "../../utils/tokenStorage";
import { useResendCooldown } from "../../hooks/useResendCooldown";
import ResendCooldownButton from "../../components/ResendCooldownButton";
import "./AuthPage.css";

const LOGIN_ERROR_MESSAGE = "Неверный логин или пароль";

const STEPS = {
  LOGIN: "login",
  REGISTRATION_REQUEST: "registration_request",
  REGISTRATION_SUCCESS: "registration_success",
  LOGIN_EMAIL_CODE: "login_email_code",
  FORGOT_PHONE: "forgot_phone",
  FORGOT_CODE: "forgot_code",
  FORGOT_NEW_PASSWORD: "forgot_new_password",
  FORGOT_DONE: "forgot_done",
  CHANGE_PASSWORD: "change_password",
};

const LOGIN_CHALLENGE_KEY = "login_email_challenge_id";
const LOGIN_STEP_KEY = "login_auth_step";
const LOGIN_MASKED_KEY = "login_email_masked";
const AUTH_METHOD_KEY = "auth_login_method";

function getStoredLoginChallenge() {
  try {
    return sessionStorage.getItem(LOGIN_CHALLENGE_KEY) || "";
  } catch {
    return "";
  }
}

function restoreLoginEmailStep() {
  try {
    if (sessionStorage.getItem(LOGIN_STEP_KEY) !== STEPS.LOGIN_EMAIL_CODE) return null;
    const cid = sessionStorage.getItem(LOGIN_CHALLENGE_KEY);
    const masked = sessionStorage.getItem(LOGIN_MASKED_KEY);
    return cid ? { challengeId: cid, masked: masked || "***" } : null;
  } catch {
    return null;
  }
}

function authTitle(step, brandTitle) {
  if (step === STEPS.LOGIN && brandTitle) {
    return brandTitle;
  }
  if (step === STEPS.REGISTRATION_REQUEST) {
    return "Заявка на доступ";
  }
  if (step === STEPS.REGISTRATION_SUCCESS) {
    return "Заявка отправлена";
  }
  if (
    step === STEPS.FORGOT_PHONE ||
    step === STEPS.FORGOT_CODE ||
    step === STEPS.FORGOT_NEW_PASSWORD ||
    step === STEPS.FORGOT_DONE
  ) {
    return "Восстановление пароля";
  }
  if (step === STEPS.CHANGE_PASSWORD) {
    return "Смена временного пароля";
  }
  return "Вход";
}

async function doMaxLinkIfNeeded(onMaxLinkSuccess) {
  const maxUserId = getMaxLinkUserId();
  console.info("[MAX link] doMaxLinkIfNeeded: max_link_user_id from sessionStorage:", maxUserId ?? "absent");
  if (!maxUserId) return false;
  try {
    console.info("[MAX link] calling POST /api/auth/max/link with maxUserId:", maxUserId);
    const data = await linkMax(maxUserId);
    console.info("[MAX link] /api/auth/max/link response:", data?.token ? "token received" : "no token", data);
    if (data?.token) {
      setToken(data.token);
      console.info("[MAX link] new token saved to localStorage, dispatching reauth");
      window.dispatchEvent(new Event("reauth"));
    }
  } catch (err) {
    console.warn("[MAX link] /api/auth/max/link failed:", err?.message ?? err);
    clearMaxLinkUserId();
    throw err;
  }
  clearMaxLinkUserId();
  console.info("[MAX link] calling onMaxLinkSuccess after link");
  if (typeof onMaxLinkSuccess === "function") onMaxLinkSuccess();
  return true;
}

async function doTelegramLinkIfNeeded(onTelegramLinkSuccess) {
  if (!getTelegramLinkLoginExisting()) return false;
  const initData = window.Telegram?.WebApp?.initData;
  if (!initData) {
    clearTelegramLinkLoginExisting();
    throw new Error("Нет данных Telegram. Закройте мини-приложение и откройте снова.");
  }
  try {
    const data = await linkTelegram(initData);
    if (data?.token) {
      setToken(data.token);
      window.dispatchEvent(new Event("reauth"));
    }
  } catch (err) {
    console.warn("[Telegram link] /api/auth/telegram/link failed:", err?.message ?? err);
    clearTelegramLinkLoginExisting();
    throw err;
  }
  clearTelegramLinkLoginExisting();
  if (typeof onTelegramLinkSuccess === "function") onTelegramLinkSuccess();
  return true;
}

function finishWebSession(onDone) {
  if (typeof onDone === "function") {
    onDone();
  }
}

export default function AuthPage({
  onAuthSuccess,
  onMaxLinkSuccess,
  platformResolving = false,
  brandTitle = "Курьерская доставка",
  initialOpenRegistration = false,
  messengerIdentity = null,
  messengerApplicationPending = false,
}) {
  const onDone = onAuthSuccess || onMaxLinkSuccess;
  const [step, setStep] = useState(() => {
    const restored = restoreLoginEmailStep();
    if (restored) return STEPS.LOGIN_EMAIL_CODE;
    if (initialOpenRegistration) return STEPS.REGISTRATION_REQUEST;
    return STEPS.LOGIN;
  });
  const [phone, setPhone] = useState("");
  const [authMethod, setAuthMethod] = useState("PHONE");
  const [password, setPasswordVal] = useState("");
  const [challengeId, setChallengeId] = useState("");
  const [pin, setPin] = useState("");
  const [loginEmailChallengeId, setLoginEmailChallengeId] = useState(() => {
    const restored = restoreLoginEmailStep();
    return restored ? restored.challengeId : "";
  });
  const [loginEmailMasked, setLoginEmailMasked] = useState(() => {
    const restored = restoreLoginEmailStep();
    return restored ? restored.masked : "";
  });
  const [loginEmailCode, setLoginEmailCode] = useState("");
  const [forgotResetToken, setForgotResetToken] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [currentPasswordForChange, setCurrentPasswordForChange] = useState("");
  const [newPasswordVal, setNewPasswordVal] = useState("");
  const [confirmPasswordVal, setConfirmPasswordVal] = useState("");
  const pastedPasswordRef = useRef("");

  const loginEmailCooldown = useResendCooldown();
  const forgotPhoneCooldown = useResendCooldown();

  useEffect(() => {
    if (messengerApplicationPending) {
      setStep(STEPS.REGISTRATION_SUCCESS);
      setError("");
    }
  }, [messengerApplicationPending]);

  useEffect(() => {
    try {
      if (sessionStorage.getItem(LOGIN_STEP_KEY) === STEPS.LOGIN_EMAIL_CODE && getStoredLoginChallenge()) {
        loginEmailCooldown.startCooldown();
      }
    } catch (_) {}
    // eslint-disable-next-line react-hooks/exhaustive-deps -- один раз при монтировании для восстановленного шага
  }, []);

  useEffect(() => {
    if (platformResolving) return;
    if (getPlatform() !== "web") return;
    try {
      localStorage.removeItem("selectedRole");
      localStorage.removeItem("activeOrganizationId");
    } catch (_) {}
    removeToken();
    logout().catch(() => {});
  }, [platformResolving]);


  useEffect(() => {
    if (!platformResolving) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = prev;
    };
  }, [platformResolving]);

  const platformOverlay = platformResolving ? (
    <div
      className="auth-page-platform-overlay"
      aria-busy="true"
      aria-live="polite"
      role="progressbar"
      aria-label="Загрузка окружения, подождите"
    >
      <div className="auth-page-platform-spinner-card">
        <div className="auth-page-platform-spinner-ring" aria-hidden />
        <p className="auth-page-platform-spinner-text">Загрузка окружения</p>
        <p className="auth-page-platform-spinner-hint">Подождите, подключаем Telegram или MAX</p>
      </div>
    </div>
  ) : null;

  const finishAuthenticatedSession = async (data, passwordUsed, onDone) => {
    const mustChange =
      data?.mustChangePassword === true || data?.userProfile?.mustChangePassword === true;
    const accessToken = data?.accessToken ?? data?.token;
    if (mustChange) {
      if (!accessToken) throw new Error("Токен не получен от сервера");
      setToken(accessToken);
      setCurrentPasswordForChange(passwordUsed || "");
      setNewPasswordVal("");
      setConfirmPasswordVal("");
      setStep(STEPS.CHANGE_PASSWORD);
      setError("");
      return;
    }
    if (!accessToken) throw new Error("Токен не получен от сервера");
    setToken(accessToken);
    window.dispatchEvent(new Event("reauth"));
    const didMaxLink = await doMaxLinkIfNeeded(onDone);
    const didTelegramLink = await doTelegramLinkIfNeeded(onDone);
    if (didMaxLink || didTelegramLink) return;
    finishWebSession(onDone);
  };

  const handleIdentifierPaste = useCallback((e) => {
    const raw = e.clipboardData?.getData("text");
    if (!raw || !/логин:|временный\s+пароль:/i.test(raw)) {
      return;
    }
    const { login, password } = parseCredentialsFromClipboard(raw);
    if (!login && !password) {
      return;
    }
    e.preventDefault();
    if (login) {
      setPhone(login);
    }
    if (password) {
      pastedPasswordRef.current = password;
      setPasswordVal(password);
    }
  }, []);

  const handlePasswordPaste = useCallback((e) => {
    const raw = e.clipboardData?.getData("text");
    if (!raw) {
      return;
    }
    if (/логин:|временный\s+пароль:/i.test(raw)) {
      const { password } = parseCredentialsFromClipboard(raw);
      if (password) {
        e.preventDefault();
        setPasswordVal(password);
      }
      return;
    }
    const normalized = normalizeCredentialPaste(raw, "password");
    if (normalized !== raw.trim()) {
      e.preventDefault();
      setPasswordVal(normalized);
    }
  }, []);

  const goToLogin = () => {
    try {
      sessionStorage.removeItem(AUTH_METHOD_KEY);
    } catch (_) {}
    pastedPasswordRef.current = "";
    setStep(STEPS.LOGIN);
    setError("");
    setPasswordVal("");
    setCurrentPasswordForChange("");
    setNewPasswordVal("");
    setConfirmPasswordVal("");
    setChallengeId("");
    setPin("");
    setForgotResetToken("");
    loginEmailCooldown.resetCooldown();
    forgotPhoneCooldown.resetCooldown();
  };

  const openRegistrationRequest = () => {
    setError("");
    setStep(STEPS.REGISTRATION_REQUEST);
  };

  const handleLogin = async (e) => {
    e.preventDefault();
    setError("");
    const raw = (phone || "").trim();
    const passwordTrimmed = (password || "").trim();
    if (!raw) {
      setError("Введите телефон или логин");
      return;
    }
    if (!passwordTrimmed) {
      setError("Введите пароль");
      return;
    }
    const isPhone = looksLikePhone(raw);
    const method = isPhone ? "PHONE" : "LOGIN";
    setAuthMethod(method);
    try {
      sessionStorage.setItem(AUTH_METHOD_KEY, method);
    } catch (_) {}
    setLoading(true);
    try {
      const loginArg = method === "LOGIN" ? raw : null;
      const phoneArg = method === "PHONE" ? (toE164(raw) || raw) : null;
      const data = await loginPhone(phoneArg, passwordTrimmed, loginArg);
      if (data.status === "EMAIL_CODE_REQUIRED") {
        const cid = data.challengeId || "";
        const masked = data.maskedEmail || "***";
        setLoginEmailChallengeId(cid);
        setLoginEmailMasked(masked);
        setLoginEmailCode("");
        setStep(STEPS.LOGIN_EMAIL_CODE);
        loginEmailCooldown.startCooldown();
        try {
          sessionStorage.setItem(LOGIN_CHALLENGE_KEY, cid);
          sessionStorage.setItem(LOGIN_STEP_KEY, STEPS.LOGIN_EMAIL_CODE);
          sessionStorage.setItem(LOGIN_MASKED_KEY, masked);
        } catch (_) {}
        return;
      }
      await finishAuthenticatedSession(data, passwordTrimmed, onDone);
    } catch (err) {
      if (err?.status === 401 || err?.status === 400) {
        setError(LOGIN_ERROR_MESSAGE);
      } else {
        setError(err.message || LOGIN_ERROR_MESSAGE);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleChangePassword = async (e) => {
    e.preventDefault();
    setError("");
    if (newPasswordVal.length < 8) {
      setError("Пароль не менее 8 символов");
      return;
    }
    if (newPasswordVal !== confirmPasswordVal) {
      setError("Пароли не совпадают");
      return;
    }
    if (!currentPasswordForChange) {
      setError("Сессия истекла. Войдите снова.");
      setStep(STEPS.LOGIN);
      return;
    }
    setLoading(true);
    try {
      await changeSecurityPassword(currentPasswordForChange, newPasswordVal);
      setCurrentPasswordForChange("");
      window.dispatchEvent(new Event("reauth"));
      const didMaxLink = await doMaxLinkIfNeeded(onDone);
      const didTelegramLink = await doTelegramLinkIfNeeded(onDone);
      if (didMaxLink || didTelegramLink) return;
      finishWebSession(onDone);
    } catch (err) {
      setError(err.message || "Не удалось сохранить пароль");
    } finally {
      setLoading(false);
    }
  };

  const clearLoginEmailSession = () => {
    try {
      sessionStorage.removeItem(LOGIN_CHALLENGE_KEY);
      sessionStorage.removeItem(LOGIN_STEP_KEY);
      sessionStorage.removeItem(LOGIN_MASKED_KEY);
    } catch (_) {}
  };

  const handleVerifyLoginEmailCode = async (e) => {
    e.preventDefault();
    setError("");
    const code = (loginEmailCode || "").replace(/\D/g, "").slice(0, 6);
    if (code.length !== 6) {
      setError("Введите 6 цифр кода");
      return;
    }
    const cid = loginEmailChallengeId || getStoredLoginChallenge();
    if (!cid) {
      setError("Сессия истекла. Нажмите «Назад», войдите снова (телефон и пароль) и введите новый код из письма.");
      return;
    }
    setLoading(true);
    try {
      const data = await verifyLoginEmailCode(cid, code);
      clearLoginEmailSession();
      await finishAuthenticatedSession(data, password, onDone);
    } catch (err) {
      setError(err.message || "Ошибка проверки");
    } finally {
      setLoading(false);
    }
  };

  const handleResendLoginEmailCode = async () => {
    setError("");
    const cid = loginEmailChallengeId || getStoredLoginChallenge();
    if (!cid) {
      setError("Сессия истекла. Нажмите «Назад», войдите снова (телефон и пароль) и введите новый код из письма.");
      return;
    }
    setLoading(true);
    try {
      await resendLoginEmailCode(cid);
      loginEmailCooldown.startCooldown();
    } catch (err) {
      setError(err.message || "Ошибка отправки");
    } finally {
      setLoading(false);
    }
  };

  const switchToForgot = () => {
    clearLoginEmailSession();
    setStep(STEPS.FORGOT_PHONE);
    setError("");
    setChallengeId("");
    setPin("");
    setForgotResetToken("");
    loginEmailCooldown.resetCooldown();
    forgotPhoneCooldown.resetCooldown();
  };

  const finishForgotGoToLogin = () => {
    setStep(STEPS.LOGIN);
    setError("");
    forgotPhoneCooldown.resetCooldown();
  };

  const handleForgotRequest = async (e) => {
    e.preventDefault();
    setError("");
    setLoading(true);
    try {
      const data = await forgotPasswordRequest(toE164(phone.trim()) || phone.trim());
      setChallengeId(data.challengeId);
      setPin("");
      setStep(STEPS.FORGOT_CODE);
      forgotPhoneCooldown.startCooldown();
    } catch (err) {
      setError(err.message || "Не удалось отправить звонок");
    } finally {
      setLoading(false);
    }
  };

  const handleForgotVerifyPin = async (e) => {
    e.preventDefault();
    setError("");
    const digits = (pin || "").replace(/\D/g, "");
    if (digits.length !== 4) {
      setError("Введите 4 цифры номера");
      return;
    }
    setLoading(true);
    try {
      const data = await forgotPasswordVerify(challengeId, digits);
      const token = data.resetToken;
      if (!token) throw new Error("Токен не получен");
      setForgotResetToken(token);
      setPasswordVal("");
      setStep(STEPS.FORGOT_NEW_PASSWORD);
    } catch (err) {
      setError(err.message || "Неверные последние 4 цифры");
    } finally {
      setLoading(false);
    }
  };

  const handleForgotResendCall = async () => {
    setError("");
    setLoading(true);
    try {
      const data = await forgotPasswordRequest(toE164(phone.trim()) || phone.trim());
      setChallengeId(data.challengeId);
      setPin("");
      forgotPhoneCooldown.startCooldown();
    } catch (err) {
      setError(err.message || "Ошибка");
    } finally {
      setLoading(false);
    }
  };

  const handleForgotSetPassword = async (e) => {
    e.preventDefault();
    setError("");
    if (password.length < 8) {
      setError("Пароль не менее 8 символов");
      return;
    }
    setLoading(true);
    try {
      await forgotPasswordComplete(forgotResetToken, password);
      setStep(STEPS.FORGOT_DONE);
      setError("");
    } catch (err) {
      setError(err.message || "Ошибка сохранения пароля");
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <PageContainer>
        <div className="auth-page">
          <h1 className="auth-title">{authTitle(step, brandTitle)}</h1>

          {error && <div className="auth-error">{error}</div>}

          {step === STEPS.LOGIN && (
            <form className="auth-form" onSubmit={handleLogin}>
              <label className="auth-label">Телефон или логин</label>
              <input
                type="text"
                className="auth-input"
                placeholder="Телефон или логин"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                onPaste={handleIdentifierPaste}
                required
                autoComplete="username"
              />
              <label className="auth-label">Пароль</label>
              <input
                type="password"
                className="auth-input"
                placeholder="Пароль"
                value={password}
                onChange={(e) => setPasswordVal(e.target.value)}
                onPaste={handlePasswordPaste}
                required
                autoComplete="current-password"
              />
              {looksLikePhone(phone) && (
                <button type="button" className="auth-link auth-forgot-password" onClick={switchToForgot}>
                  Забыли пароль?
                </button>
              )}
              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? "Вход…" : "Войти"}
              </button>
              <button
                type="button"
                className="auth-link auth-request-link"
                onClick={openRegistrationRequest}
              >
                Нет аккаунта? Оставить заявку
              </button>
            </form>
          )}

          {step === STEPS.REGISTRATION_REQUEST && (
            <RegistrationRequestScreen
              messengerIdentity={messengerIdentity}
              onBack={goToLogin}
              onSuccess={() => {
                setError("");
                setStep(STEPS.REGISTRATION_SUCCESS);
              }}
            />
          )}

          {step === STEPS.REGISTRATION_SUCCESS && (
            <div className="auth-form">
              <p className="auth-hint">
                Спасибо! Мы получили вашу заявку и свяжемся с вами для выдачи доступа.
              </p>
              <button type="button" className="auth-submit" onClick={goToLogin}>
                Ко входу
              </button>
            </div>
          )}

          {step === STEPS.CHANGE_PASSWORD && (
            <form className="auth-form" onSubmit={handleChangePassword}>
              <p className="auth-hint">
                Вы вошли с временным паролем. Для безопасности задайте новый пароль.
              </p>
              <label className="auth-label">Новый пароль</label>
              <input
                type="password"
                className="auth-input"
                placeholder="Не менее 8 символов"
                value={newPasswordVal}
                onChange={(e) => setNewPasswordVal(e.target.value)}
                required
                autoComplete="new-password"
              />
              <label className="auth-label">Повторите пароль</label>
              <input
                type="password"
                className="auth-input"
                placeholder="Повторите пароль"
                value={confirmPasswordVal}
                onChange={(e) => setConfirmPasswordVal(e.target.value)}
                required
                autoComplete="new-password"
              />
              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? "Сохранение…" : "Сохранить пароль"}
              </button>
            </form>
          )}

          {step === STEPS.LOGIN_EMAIL_CODE && (
            <form className="auth-form" onSubmit={handleVerifyLoginEmailCode}>
              <p className="auth-hint">Мы отправили код на вашу почту {loginEmailMasked}. Введите 6 цифр из письма.</p>
              <label className="auth-label">Код из письма</label>
              <input
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={6}
                className="auth-input"
                placeholder="123456"
                value={loginEmailCode}
                onChange={(e) => setLoginEmailCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                required
                autoComplete="one-time-code"
              />
              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? "Проверка…" : "Подтвердить"}
              </button>
              <ResendCooldownButton
                idleLabel="Отправить код повторно"
                canResend={loginEmailCooldown.canResend}
                remainingSec={loginEmailCooldown.remainingSec}
                loading={loading}
                onClick={handleResendLoginEmailCode}
              />
              <button
                type="button"
                className="auth-link"
                onClick={() => {
                  clearLoginEmailSession();
                  loginEmailCooldown.resetCooldown();
                  setStep(STEPS.LOGIN);
                  setError("");
                }}
              >
                Назад
              </button>
              <button type="button" className="auth-link" onClick={switchToForgot}>
                Забыли пароль?
              </button>
            </form>
          )}

          {step === STEPS.FORGOT_PHONE && (
            <form className="auth-form" onSubmit={handleForgotRequest}>
              <p className="auth-hint">Укажите номер аккаунта. Мы позвоним для подтверждения, затем вы зададите новый пароль.</p>
              <label className="auth-label">Номер телефона</label>
              <PhoneInput className="auth-input" value={phone} onChange={setPhone} required />
              <p className="auth-hint">На номер поступит звонок — введите последние 4 цифры номера звонящего.</p>
              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? "Отправка…" : "Получить звонок"}
              </button>
              <button type="button" className="auth-link" onClick={() => { setStep(STEPS.LOGIN); setError(""); }}>
                Назад ко входу
              </button>
            </form>
          )}

          {step === STEPS.FORGOT_CODE && (
            <form className="auth-form" onSubmit={handleForgotVerifyPin}>
              <p className="auth-hint">Введите последние 4 цифры номера, с которого поступил вызов.</p>
              <label className="auth-label">Последние 4 цифры</label>
              <input
                type="text"
                inputMode="numeric"
                pattern="[0-9]*"
                maxLength={4}
                className="auth-input"
                placeholder="1234"
                value={pin}
                onChange={(e) => setPin(e.target.value.replace(/\D/g, "").slice(0, 4))}
                required
                autoComplete="one-time-code"
              />
              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? "Проверка…" : "Подтвердить"}
              </button>
              <ResendCooldownButton
                idleLabel="Отправить звонок повторно"
                canResend={forgotPhoneCooldown.canResend}
                remainingSec={forgotPhoneCooldown.remainingSec}
                loading={loading}
                onClick={handleForgotResendCall}
              />
              <button type="button" className="auth-link" onClick={() => { forgotPhoneCooldown.resetCooldown(); setStep(STEPS.FORGOT_PHONE); setError(""); }}>
                Изменить номер
              </button>
            </form>
          )}

          {step === STEPS.FORGOT_NEW_PASSWORD && (
            <form className="auth-form" onSubmit={handleForgotSetPassword}>
              <label className="auth-label">Новый пароль (не менее 8 символов)</label>
              <input
                type="password"
                className="auth-input"
                placeholder="Новый пароль"
                value={password}
                onChange={(e) => setPasswordVal(e.target.value)}
                required
                minLength={8}
                autoComplete="new-password"
              />
              <button type="submit" className="auth-submit" disabled={loading}>
                {loading ? "Сохранение…" : "Сохранить пароль"}
              </button>
            </form>
          )}

          {step === STEPS.FORGOT_DONE && (
            <div className="auth-form">
              <p className="auth-hint">Пароль обновлён. Войдите, используя номер телефона и новый пароль.</p>
              <button type="button" className="auth-submit" onClick={finishForgotGoToLogin}>
                Ко входу
              </button>
            </div>
          )}

        </div>
      </PageContainer>
      {platformOverlay}
    </>
  );
}
