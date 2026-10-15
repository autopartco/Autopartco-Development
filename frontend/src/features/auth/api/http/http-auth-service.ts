import { apiRequest } from '@/lib/api'
import { rolesFromAccessToken } from '../../lib/jwt'
import { getSession, setSession } from '../../session-store'
import type { AuthService, Session } from '../types'

/**
 * Cuentas contra el backend real (`core/identity/web/AuthController`, `ApiPaths.BASE + "/auth"`,
 * y `MeController`, `ApiPaths.ME`). Rutas públicas según `WebSecurityConfig.PUBLIC_ROUTES`;
 * `/auth/logout` y `/me/**` exigen el access token.
 *
 * Tokens: el refresh token va SOLO en la cookie HttpOnly `autopartco_refresh`
 * (`SameSite=Lax`, `Path=/api/v1/auth`), así que JavaScript nunca lo lee. El access token
 * vive en memoria de este módulo. Al cargar la página, `restore()` pide `/auth/refresh` con
 * la cookie: si sigue válida, la sesión se recupera sin volver a ingresar, también en pestañas nuevas.
 * El logout la borra en el servidor.
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

/** `LoginResponse` (core/identity). `refreshToken` no viene: está en la cookie. `expiresIn` en segundos. */
interface LoginResponseDto {
  accessToken: string
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
  /** Momento (ms) a partir del cual conviene renovar el access token. */
  renewAt: number
}

let tokenProvider: (() => Promise<string | undefined>) | undefined

/** Access token vigente para otros servicios http (portal). Sin sesión devuelve `undefined`. */
export function getAccessToken(): Promise<string | undefined> {
  return tokenProvider ? tokenProvider() : Promise.resolve(undefined)
}

function toUser(profile: ProfileResponseDto): Session['user'] {
  return { name: [profile.name, profile.surname].filter(Boolean).join(' '), email: profile.email }
}

export function createHttpAuthService(): AuthService {
  let tokens: Tokens | undefined

  function keep(dto: LoginResponseDto) {
    // Renueva 30 s antes de que venza para no fallar en mitad de una petición.
    tokens = { access: dto.accessToken, renewAt: Date.now() + Math.max(0, dto.expiresIn - 30) * 1000 }
  }

  /** Roles del access token vigente (solo para la UI: ver `lib/jwt.ts`). */
  function currentRoles(): string[] {
    return tokens ? rolesFromAccessToken(tokens.access) : []
  }

  /** Tras renovar el token, refleja en la sesión visible un cambio de roles (p. ej. la cuenta pasó a almacén). */
  function syncRoles() {
    const session = getSession()
    if (!session || session.simulated) return
    const roles = currentRoles()
    if (roles.join() !== session.roles.join()) setSession({ ...session, roles })
  }

  /** Pide un access token nuevo usando la cookie de refresh (el navegador la envía sola). */
  function refreshSession(): Promise<LoginResponseDto> {
    return apiRequest<LoginResponseDto>(PATHS.refresh, { method: 'POST', credentials: 'include' })
  }

  /** Access token vigente; si está por vencer, lo renueva con la cookie. */
  async function accessToken(): Promise<string | undefined> {
    if (!tokens) return undefined
    if (Date.now() < tokens.renewAt) return tokens.access
    try {
      keep(await refreshSession())
      syncRoles()
      return tokens.access
    } catch {
      tokens = undefined
      return undefined
    }
  }

  tokenProvider = accessToken

  return {
    simulated: false,

    async restore() {
      // Sin sesión previa, el refresh falla (401) y la app muestra la pantalla de ingreso.
      try {
        keep(await refreshSession())
      } catch {
        tokens = undefined
        return undefined
      }
      try {
        const profile = await apiRequest<ProfileResponseDto>(PATHS.profile, { token: tokens?.access })
        return { user: toUser(profile), roles: currentRoles(), simulated: false }
      } catch {
        tokens = undefined
        return undefined
      }
    },

    getAccessToken: accessToken,

    async login({ email, password }) {
      keep(await apiRequest<LoginResponseDto>(PATHS.login, {
        body: { email: email.trim(), password },
        credentials: 'include',
      }))
      try {
        const profile = await apiRequest<ProfileResponseDto>(PATHS.profile, { token: tokens?.access })
        return { user: toUser(profile), roles: currentRoles(), simulated: false }
      } catch {
        // El perfil es informativo: si falla, la sesión sigue siendo válida.
        return { user: { name: email.trim(), email: email.trim() }, roles: currentRoles(), simulated: false }
      }
    },

    async logout() {
      // Renueva el access token con la cookie si hace falta: el logout exige access token.
      const token = await accessToken()
      tokens = undefined
      if (!token) return
      // Revoca la sesión y el servidor borra la cookie; si falla, la sesión local ya se cerró igual.
      await apiRequest<void>(PATHS.logout, { method: 'POST', token, credentials: 'include' }).catch(() => undefined)
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
