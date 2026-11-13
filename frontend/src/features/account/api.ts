import { ApiError, DATA_SOURCE, apiBlob, apiRequest, type RequestOptions } from '@/lib/api'
import { requireAccessToken } from '@/features/checkout/api/http/http-checkout-service'

/**
 * «Mi cuenta», igual para todos los roles. Contratos en `core/identity/web` (`MeController`,
 * `AccountManagementController`, `TwoFactorSettingsController`) y `core/settings/SettingsController`.
 * Con la fuente `mock` responde con datos simulados en memoria.
 */

export interface Profile {
  accountId: string
  personId: string
  email: string
  name: string
  surname: string
  identification: string
  dateBirth: string
  numberPhone: string
  /** `false`: cuenta creada con Google que aún no tiene contraseña; la crea con el enlace de recuperación. */
  passwordSet: boolean
}

export type ProfileUpdate = Partial<Pick<Profile, 'name' | 'surname' | 'identification' | 'dateBirth' | 'numberPhone'>>

export interface AccountSettings {
  security: { twoFactor: { enabled: boolean; method: string }; loginAlerts: boolean }
  notifications: { orderUpdates: boolean; priceAlerts: boolean; storeReplies: boolean; marketing: boolean; reviewReminders: boolean }
  preferences: { theme: 'system' | 'light' | 'dark'; defaultCityCode: string | null }
}

/** Parche parcial: solo las claves que cambian (`PATCH /account/settings`). */
export type SettingsPatch = {
  security?: { loginAlerts?: boolean }
  notifications?: Partial<AccountSettings['notifications']>
  preferences?: Partial<AccountSettings['preferences']>
}

export interface AccountSession {
  sessionId: string
  ipAddress: string | null
  userAgent: string | null
  createdAt: string
  expiresAt: string
  current: boolean
}

export interface CodeRequest {
  sent: boolean
  retryAfterSeconds: number
  codeValidMinutes: number
}

export interface LinkedIdentity {
  provider: string
  email: string | null
  linkedAt: string
}

export interface DeletionRequest {
  id: string
  status: 'PENDING' | 'CANCELLED' | 'COMPLETED' | 'REJECTED'
  reason: string | null
  createdAt: string
}

export interface SavedAddress {
  id: string
  label: string
  isDefault: boolean
  addressLine: string
  addressComplement: string | null
  city: string
  department: string
  postalCode: string | null
}

const http = DATA_SOURCE === 'http'

async function call<T>(path: string, options: Omit<RequestOptions, 'token'> = {}): Promise<T> {
  return apiRequest<T>(path, { ...options, token: await requireAccessToken() })
}

// ------------------------------------------------------------------ simulado

const mock = {
  profile: { accountId: 'acc-1', personId: 'per-1', email: 'cliente@ejemplo.co', name: 'Ana', surname: 'Pérez', identification: '1020304050', dateBirth: '1992-04-18', numberPhone: '3001234567', passwordSet: true } as Profile,
  settings: {
    security: { twoFactor: { enabled: false, method: 'EMAIL' }, loginAlerts: true },
    notifications: { orderUpdates: true, priceAlerts: true, storeReplies: true, marketing: false, reviewReminders: true },
    preferences: { theme: 'system', defaultCityCode: null },
  } as AccountSettings,
  sessions: [
    { sessionId: 's1', ipAddress: '190.0.0.1', userAgent: 'Chrome en Windows', createdAt: new Date().toISOString(), expiresAt: new Date(Date.now() + 864e5 * 7).toISOString(), current: true },
    { sessionId: 's2', ipAddress: '181.0.0.2', userAgent: 'Safari en iPhone', createdAt: new Date(Date.now() - 864e5 * 2).toISOString(), expiresAt: new Date(Date.now() + 864e5 * 5).toISOString(), current: false },
  ] as AccountSession[],
  identities: [{ provider: 'google', email: 'cliente@ejemplo.co', linkedAt: new Date(Date.now() - 864e5 * 30).toISOString() }] as LinkedIdentity[],
  addresses: [{ id: 'a1', label: 'Casa', isDefault: true, addressLine: 'Calle 10 # 20-30', addressComplement: 'Apto 401', city: 'Bogotá', department: 'Cundinamarca', postalCode: '110111' }] as SavedAddress[],
  deletion: null as DeletionRequest | null,
}

const wait = (ms = 350) => new Promise((resolve) => setTimeout(resolve, ms))

function mergeSettings(patch: SettingsPatch): AccountSettings {
  mock.settings = {
    security: { ...mock.settings.security, ...patch.security },
    notifications: { ...mock.settings.notifications, ...patch.notifications },
    preferences: { ...mock.settings.preferences, ...patch.preferences },
  }
  return structuredClone(mock.settings)
}

// ------------------------------------------------------------------ perfil y ajustes

export async function getProfile(): Promise<Profile> {
  if (!http) return wait().then(() => structuredClone(mock.profile))
  return call<Profile>('/me/profile')
}

export async function updateProfile(input: ProfileUpdate): Promise<Profile> {
  if (!http) return wait().then(() => Object.assign(mock.profile, input) && structuredClone(mock.profile))
  return call<Profile>('/me/profile', { method: 'PATCH', body: input })
}

export async function getSettings(): Promise<AccountSettings> {
  if (!http) return wait().then(() => structuredClone(mock.settings))
  return call<AccountSettings>('/account/settings')
}

export async function patchSettings(patch: SettingsPatch): Promise<AccountSettings> {
  if (!http) return wait(200).then(() => mergeSettings(patch))
  return call<AccountSettings>('/account/settings', { method: 'PATCH', body: patch })
}

// ------------------------------------------------------------------ seguridad

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  if (!http) {
    await wait()
    if (currentPassword !== 'Actual#123') throw new ApiError('La contraseña no es correcta.', { status: 400, code: 'INVALID_PASSWORD' })
    return
  }
  // `include`: envía la cookie de refresh para que el servidor conserve esta sesión y cierre las demás.
  await call<void>('/me/password', { method: 'POST', body: { currentPassword, newPassword }, credentials: 'include' })
}

export async function requestEmailChange(newEmail: string, currentPassword?: string): Promise<CodeRequest> {
  if (!http) return wait().then(() => ({ sent: true, retryAfterSeconds: 60, codeValidMinutes: 15 }))
  return call<CodeRequest>('/me/email', { method: 'POST', body: { newEmail, currentPassword: currentPassword || undefined } })
}

export async function confirmEmailChange(newEmail: string, code: string): Promise<void> {
  if (!http) {
    await wait()
    if (code !== '123456') throw new ApiError('El código no es válido o expiró.', { status: 400, code: 'INVALID_EMAIL_CODE' })
    mock.profile.email = newEmail
    return
  }
  await call<void>('/me/email/confirm', { method: 'POST', body: { newEmail, code } })
}

export async function listSessions(): Promise<AccountSession[]> {
  if (!http) return wait().then(() => structuredClone(mock.sessions))
  return call<AccountSession[]>('/me/sessions')
}

export async function revokeSession(sessionId: string): Promise<void> {
  if (!http) {
    await wait(200)
    mock.sessions = mock.sessions.filter((session) => session.sessionId !== sessionId)
    return
  }
  await call<void>(`/me/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' })
}

export async function requestTwoFactor(action: 'ENABLE' | 'DISABLE'): Promise<CodeRequest> {
  if (!http) return wait().then(() => ({ sent: true, retryAfterSeconds: 60, codeValidMinutes: 10 }))
  return call<CodeRequest>('/account/settings/two-factor/request', { method: 'POST', body: { action } })
}

export async function confirmTwoFactor(code: string, action: 'ENABLE' | 'DISABLE'): Promise<{ enabled: boolean }> {
  if (!http) {
    await wait()
    if (code !== '123456') throw new ApiError('El código no es válido o expiró.', { status: 400, code: 'INVALID_TWO_FACTOR_CODE' })
    mock.settings.security.twoFactor.enabled = action === 'ENABLE'
    return { enabled: action === 'ENABLE' }
  }
  return call<{ enabled: boolean }>('/account/settings/two-factor/confirm', { method: 'POST', body: { code, action } })
}

export async function disableTwoFactorWithPassword(password: string): Promise<{ enabled: boolean }> {
  if (!http) {
    await wait()
    mock.settings.security.twoFactor.enabled = false
    return { enabled: false }
  }
  return call<{ enabled: boolean }>('/account/settings/two-factor/disable', { method: 'POST', body: { password } })
}

export async function listIdentities(): Promise<LinkedIdentity[]> {
  if (!http) return wait().then(() => structuredClone(mock.identities))
  return call<LinkedIdentity[]>('/me/identities')
}

export async function unlinkIdentity(provider: string, currentPassword: string): Promise<void> {
  if (!http) {
    await wait()
    mock.identities = mock.identities.filter((identity) => identity.provider !== provider)
    return
  }
  await call<void>(`/me/identities/${encodeURIComponent(provider)}/unlink`, { method: 'POST', body: { currentPassword } })
}

// ------------------------------------------------------------------ direcciones

export async function listAddresses(): Promise<SavedAddress[]> {
  if (!http) return wait().then(() => structuredClone(mock.addresses))
  return call<SavedAddress[]>('/me/addresses')
}

export async function deleteAddress(id: string): Promise<void> {
  if (!http) {
    await wait(200)
    mock.addresses = mock.addresses.filter((address) => address.id !== id)
    return
  }
  await call<void>(`/me/addresses/${encodeURIComponent(id)}`, { method: 'DELETE' })
}

// ------------------------------------------------------------------ privacidad

export async function exportData(): Promise<Blob> {
  if (!http) return wait().then(() => new Blob([JSON.stringify({ simulated: true, account: mock.profile }, null, 2)], { type: 'application/json' }))
  return apiBlob('/me/data-export', { token: await requireAccessToken() })
}

export async function getDeletionRequest(): Promise<DeletionRequest | null> {
  if (!http) return wait().then(() => mock.deletion && structuredClone(mock.deletion))
  // 204 sin cuerpo cuando nunca hubo solicitud.
  return (await call<DeletionRequest | undefined>('/me/deletion-request')) ?? null
}

export async function requestDeletion(currentPassword: string, reason: string): Promise<DeletionRequest> {
  if (!http) {
    await wait()
    mock.deletion = { id: 'd1', status: 'PENDING', reason: reason || null, createdAt: new Date().toISOString() }
    return structuredClone(mock.deletion)
  }
  return call<DeletionRequest>('/me/deletion-request', { method: 'POST', body: { currentPassword: currentPassword || undefined, reason: reason || undefined } })
}

export async function cancelDeletion(): Promise<void> {
  if (!http) {
    await wait()
    if (mock.deletion) mock.deletion = { ...mock.deletion, status: 'CANCELLED' }
    return
  }
  await call<void>('/me/deletion-request', { method: 'DELETE' })
}
