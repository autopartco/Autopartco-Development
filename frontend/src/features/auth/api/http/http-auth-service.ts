import { apiRequest } from '@/lib/api'
import type { AuthService, Session } from '../types'

/**
 * Cuentas contra el backend real (`core/identity/web/AuthController`, `ApiPaths.BASE + "/auth"`,
 * y `MeController`, `ApiPaths.ME`). Rutas públicas según `WebSecurityConfig.PUBLIC_ROUTES`;
 * `/auth/logout` y `/me/**` exigen el access token.
 *
 * Tokens: `POST /auth/login` devuelve `accessToken` y `refreshToken` en el cuerpo
 * (`LoginResponse`); no hay cookie HttpOnly. Opción elegida: los dos viven SOLO en
 * memoria (este módulo). No se guardan en localStorage ni sessionStorage porque
 * cualquier XSS los leería; el costo es que al recargar la página hay que volver
 * a ingresar. TODO(api): mover el refresh token a una cookie HttpOnly, Secure y
 * SameSite para poder restaurar la sesión sin exponerlo a JavaScript.
 */
const PATHS = {
  login: '/auth/login',
  refresh: '/auth/refresh',
  logout: '/auth/logout',
  register: '/auth/register',
  verifyEmail: (token: string) => `/auth/verify-email?token=${encodeURIComponent(token)}`,
  recoveryRequest: '/auth/password-recovery/request',
  recoveryReset: '/auth/password-recovery/reset',
  profile: '/me/profile',
} as const

/** `LoginResponse` (core/identity). `expiresIn` en segundos. */
interface LoginResponseDto {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

/** `RegisterResponse` (core/identity). */
interface RegisterResponseDto {
  accountId: string
  personId: string
  email: string
  accountType: string
  state: string
  verificationEmailSent: boolean
  verificationTokenExpiresAt: string | null
}

/** `ProfileResponse` (core/identity/web). */
interface ProfileResponseDto {
  email: string
  name: string
  surname: string
}

interface Tokens {
  access: string
  refresh: string
  /** Momento (ms) a partir del cual conviene renovar el access token. */
  renewAt: number
}

let tokenProvider: (() => Promise<string | undefined>) | undefined

/** Access token vigente para otros servicios http (portal). Sin sesión devuelve `undefined`. */
export function getAccessToken(): Promise<string | undefined> {
  return tokenProvider ? tokenProvider() : Promise.resolve(undefined)
}

export function createHttpAuthService(): AuthService {
  let tokens: Tokens | undefined

  function keep(dto: LoginResponseDto) {
    // Renueva 30 s antes de que venza para no fallar en mitad de una petición.
    tokens = { access: dto.accessToken, refresh: dto.refreshToken, renewAt: Date.now() + Math.max(0, dto.expiresIn - 30) * 1000 }
  }

  /** Access token vigente; si está por vencer, lo renueva (el refresh token rota en cada renovación). */
  async function accessToken(): Promise<string | undefined> {
    if (!tokens) return undefined
    if (Date.now() < tokens.renewAt) return tokens.access
    try {
      keep(await apiRequest<LoginResponseDto>(PATHS.refresh, { body: { refreshToken: tokens.refresh } }))
      return tokens.access
    } catch {
      tokens = undefined
      return undefined
    }
  }

  async function loadUser(email: string): Promise<Session['user']> {
    const token = await accessToken()
    try {
      const profile = await apiRequest<ProfileResponseDto>(PATHS.profile, { token })
      return { name: [profile.name, profile.surname].filter(Boolean).join(' '), email: profile.email }
    } catch {
      // El perfil es informativo: si falla, la sesión sigue siendo válida.
      return { name: email, email }
    }
  }

  tokenProvider = accessToken

  return {
    simulated: false,

    restore() {
      // Sin cookie HttpOnly no hay forma segura de recuperar la sesión tras recargar (ver arriba).
      return undefined
    },

    getAccessToken: accessToken,

    async login({ email, password }) {
      keep(await apiRequest<LoginResponseDto>(PATHS.login, { body: { email: email.trim(), password } }))
      return { user: await loadUser(email.trim()), simulated: false }
    },

    async logout() {
      const current = tokens
      const token = await accessToken()
      tokens = undefined
      if (!current || !token) return
      // Revoca la sesión en el servidor; si falla, la sesión local ya se cerró igual.
      await apiRequest<void>(PATHS.logout, { body: { refreshToken: current.refresh }, token }).catch(() => undefined)
    },

    async register(input) {
      const dto = await apiRequest<RegisterResponseDto>(PATHS.register, {
        body: { ...input, email: input.email.trim() },
      })
      return {
        email: dto.email,
        verificationEmailSent: dto.verificationEmailSent,
        verificationExpiresAt: dto.verificationTokenExpiresAt ?? undefined,
      }
    },

    async verifyEmail(token) {
      // `VerifyEmailResponse`: accountId, email, state, message.
      const dto = await apiRequest<{ email: string; message: string }>(PATHS.verifyEmail(token))
      return { email: dto.email, message: dto.message }
    },

    async requestPasswordRecovery(email) {
      const dto = await apiRequest<{ message: string }>(PATHS.recoveryRequest, { body: { email: email.trim() } })
      return dto.message
    },

    async resetPassword(token, password) {
      const dto = await apiRequest<{ message: string }>(PATHS.recoveryReset, { body: { token: token.trim(), password } })
      return dto.message
    },
  }
}
