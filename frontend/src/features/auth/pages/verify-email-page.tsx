import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { CheckCircle2Icon } from 'lucide-react'
import { FormAlert } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { getAuthService } from '../api/auth-service'
import type { VerifyEmailResult } from '../api/types'
import { AuthShell } from '../components/auth-shell'

/**
 * El token es de un solo uso: se guarda la promesa por token para no consumirlo
 * dos veces (StrictMode monta los efectos dos veces en desarrollo).
 */
const verifications = new Map<string, Promise<VerifyEmailResult>>()

function verify(token: string): Promise<VerifyEmailResult> {
  let pending = verifications.get(token)
  if (!pending) {
    pending = getAuthService().then((service) => service.verifyEmail(token))
    verifications.set(token, pending)
  }
  return pending
}

type State = { status: 'loading' } | { status: 'success'; result: VerifyEmailResult } | { status: 'error'; message: string }

/**
 * `/verificar-correo?token=` y `/verify-email?token=`: el correo del backend enlaza a
 * `{APP_FRONTEND_BASE_URL}/verify-email` (`RegistrationService`). Llama a `GET /auth/verify-email`.
 */
export default function VerifyEmailPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [state, setState] = useState<State>({ status: 'loading' })

  useEffect(() => {
    if (!token) return
    let active = true
    verify(token).then(
      (result) => active && setState({ status: 'success', result }),
      (error: unknown) =>
        active &&
        setState({
          status: 'error',
          message: error instanceof ApiError ? error.message : 'No pudimos verificar tu correo. Inténtalo de nuevo.',
        }),
    )
    return () => {
      active = false
    }
  }, [token])

  const current: State = token ? state : { status: 'error', message: 'El enlace no trae el código de verificación.' }

  return (
    <AuthShell crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Verificar correo' }]} label="Cuenta Autopartco" title="Verificar correo">
      <title>Verificar correo | Autopartco</title>
      {current.status === 'loading' ? (
        <p role="status" className="text-muted-foreground">
          Verificando tu correo…
        </p>
      ) : current.status === 'success' ? (
        <div role="status" className="flex flex-col items-start gap-4">
          <p className="flex items-start gap-2 text-lg font-semibold">
            <CheckCircle2Icon className="mt-1 size-5 shrink-0 text-stock" aria-hidden />
            {current.result.message}
          </p>
          <p className="text-muted-foreground">Tu cuenta {current.result.email} ya está activa.</p>
          <Button className="h-11 px-5" nativeButton={false} render={<Link to="/ingresar" />}>
            Ingresar
          </Button>
        </div>
      ) : (
        <div className="flex flex-col items-start gap-4">
          <FormAlert title={current.message}>
            <p>
              Si ya habías verificado la cuenta, solo ingresa. Si el enlace venció, todavía no podemos reenviarlo desde
              aquí; lo estamos habilitando.
            </p>
          </FormAlert>
          {/* TODO(api): no hay endpoint para reenviar el correo de verificación. */}
          <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/ingresar" />}>
            Ir a ingresar
          </Button>
        </div>
      )}
    </AuthShell>
  )
}
