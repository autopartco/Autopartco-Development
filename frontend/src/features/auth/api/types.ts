/**
 * Modelo de cuentas de la UI. Contratos reales en `backend/.../core/identity`:
 * `RegisterRequest`, `RegisterResponse`, `LoginRequest`, `LoginResponse`,
 * `PasswordRecoveryRequest`, `PasswordResetRequest`, `VerifyEmailResponse` y
 * `ProfileResponse` (`GET /me/profile`).
 */

export interface SessionUser {
  name: string
  email: string
}

export interface Session {
  user: SessionUser
  /** `true` con la fuente `mock`: no hay cuenta real detrás. */
  simulated: boolean
}

export interface LoginInput {
  email: string
  password: string
}

/** Igual a `RegisterRequest`. `dateBirth` en formato `AAAA-MM-DD` (`LocalDate`). */
export interface CustomerRegistration {
  name: string
  surname: string
  identification: string
  dateBirth: string
  numberPhone: string
  email: string
  password: string
}

/** Lo que la UI usa de `RegisterResponse`. */
export interface RegistrationResult {
  email: string
  verificationEmailSent: boolean
  /** `verificationTokenExpiresAt` (hora local del servidor, sin zona). */
  verificationExpiresAt?: string
}

export interface VerifyEmailResult {
  email: string
  message: string
}

export interface AuthService {
  readonly simulated: boolean
  /** Sesión que se puede recuperar al cargar la página: la simulada de sessionStorage o la cookie de refresh (http). */
  restore(): Session | undefined | Promise<Session | undefined>
  login(input: LoginInput): Promise<Session>
  logout(): Promise<void>
  register(input: CustomerRegistration): Promise<RegistrationResult>
  verifyEmail(token: string): Promise<VerifyEmailResult>
  /** Devuelve el mensaje del servidor (es el mismo exista o no la cuenta). */
  requestPasswordRecovery(email: string): Promise<string>
  resetPassword(token: string, password: string): Promise<string>
  /**
   * Access token vigente para llamar a otros servicios autenticados (lo renueva si está por vencer),
   * o `undefined` si no hay sesión. Solo se entrega al llamador: no se guarda en ningún almacenamiento.
   */
  getAccessToken(): Promise<string | undefined>
}
