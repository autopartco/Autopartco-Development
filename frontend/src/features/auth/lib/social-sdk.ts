/**
 * Carga diferida de los SDK de Google Identity Services y Facebook Login. Solo se piden cuando se muestra un botón
 * de ingreso social; nunca guardan tokens: entregan el token una vez para canjearlo en `POST /auth/social/{provider}`.
 */

export const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID?.trim() ?? ''
export const FACEBOOK_APP_ID = import.meta.env.VITE_FACEBOOK_APP_ID?.trim() ?? ''

interface GoogleCredentialResponse {
  credential: string
}

interface GoogleIdentity {
  accounts: {
    id: {
      initialize(options: { client_id: string; callback: (response: GoogleCredentialResponse) => void; ux_mode?: 'popup'; use_fedcm_for_prompt?: boolean }): void
      renderButton(element: HTMLElement, options: Record<string, unknown>): void
    }
  }
}

interface FacebookSdk {
  init(options: { appId: string; version: string; cookie?: boolean; xfbml?: boolean }): void
  login(callback: (response: { authResponse?: { accessToken: string } | null; status: string }) => void, options: { scope: string }): void
}

declare global {
  interface Window {
    google?: GoogleIdentity
    FB?: FacebookSdk
    fbAsyncInit?: () => void
  }
}

const scripts = new Map<string, Promise<void>>()

function loadScript(src: string): Promise<void> {
  let pending = scripts.get(src)
  if (!pending) {
    pending = new Promise<void>((resolve, reject) => {
      const script = document.createElement('script')
      script.src = src
      script.async = true
      script.defer = true
      script.onload = () => resolve()
      script.onerror = () => {
        scripts.delete(src)
        reject(new Error(`No se pudo cargar ${src}`))
      }
      document.head.appendChild(script)
    })
    scripts.set(src, pending)
  }
  return pending
}

/** Dibuja el botón oficial de Google en `element`; `onToken` recibe el ID token (JWT) tras elegir la cuenta. */
export async function renderGoogleButton(element: HTMLElement, onToken: (token: string) => void, text: 'signin_with' | 'signup_with' | 'continue_with') {
  await loadScript('https://accounts.google.com/gsi/client')
  const google = window.google
  if (!google) throw new Error('Google Identity Services no está disponible.')
  google.accounts.id.initialize({
    client_id: GOOGLE_CLIENT_ID,
    callback: (response) => onToken(response.credential),
    ux_mode: 'popup',
  })
  element.replaceChildren()
  google.accounts.id.renderButton(element, {
    type: 'standard',
    theme: 'outline',
    size: 'large',
    shape: 'rectangular',
    text,
    locale: 'es',
    logo_alignment: 'center',
    width: Math.max(200, Math.min(400, Math.round(element.getBoundingClientRect().width) || 320)),
  })
}

let facebookReady: Promise<FacebookSdk> | undefined

function loadFacebook(): Promise<FacebookSdk> {
  facebookReady ??= loadScript('https://connect.facebook.net/es_LA/sdk.js').then(() => {
    const fb = window.FB
    if (!fb) throw new Error('Facebook SDK no está disponible.')
    fb.init({ appId: FACEBOOK_APP_ID, version: 'v19.0', cookie: false, xfbml: false })
    return fb
  })
  return facebookReady
}

/** Precarga el SDK de Facebook para que `FB.login` abra la ventana dentro del clic (los navegadores bloquean popups tardíos). */
export function preloadFacebook(): void {
  void loadFacebook().catch(() => undefined)
}

/** Abre la ventana de Facebook Login con permiso de correo; `undefined` si la persona la cierra. */
export async function facebookAccessToken(): Promise<string | undefined> {
  const fb = await loadFacebook()
  return new Promise((resolve) => {
    fb.login((response) => resolve(response.authResponse?.accessToken ?? undefined), { scope: 'public_profile,email' })
  })
}
