import { useEffect, useRef, useState, type FormEvent } from 'react'
import { ShieldCheckIcon } from 'lucide-react'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getAuthService } from '../api/auth-service'
import type { Session, TwoFactorChallenge } from '../api/types'

interface TwoFactorStepProps {
  challenge: TwoFactorChallenge
  /** Código válido: sesión abierta; la página decide a dónde ir. */
  onSession: (session: Session) => void | Promise<void>
  /** Volver al ingreso (otro correo u otro método). */
  onCancel: () => void
}

/** Segundo paso del ingreso con verificación en dos pasos: código del correo, reenvío y volver. */
export function TwoFactorStep({ challenge, onSession, onCancel }: TwoFactorStepProps) {
  const [current, setCurrent] = useState(challenge)
  const [error, setError] = useState<string>()
  const [notice, setNotice] = useState<string>()
  const [wait, setWait] = useState(0)
  const [resending, setResending] = useState(false)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const verify = useSubmitOnce(async (code: string) => (await getAuthService()).verifyTwoFactor(current.challengeId, code))

  useEffect(() => {
    headingRef.current?.focus()
  }, [])

  useEffect(() => {
    if (wait <= 0) return
    const timer = window.setTimeout(() => setWait((seconds) => seconds - 1), 1000)
    return () => window.clearTimeout(timer)
  }, [wait])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const code = String(new FormData(event.currentTarget).get('code') ?? '').trim()
    setNotice(undefined)
    if (!/^\d{4,12}$/.test(code)) {
      setError('Escribe el código numérico que te enviamos.')
      document.getElementById('dospasos-code')?.focus()
      return
    }
    setError(undefined)
    const result = await verify.run(code)
    if (result.status === 'ok') {
      await onSession(result.value)
    } else if (result.status === 'error') {
      setError(result.error instanceof ApiError ? result.error.message : 'No pudimos validar el código. Inténtalo de nuevo.')
      document.getElementById('dospasos-code')?.focus()
    }
  }

  async function resend() {
    setResending(true)
    setError(undefined)
    setNotice(undefined)
    try {
      const next = await (await getAuthService()).resendTwoFactor(current.challengeId)
      setCurrent({ challengeId: next.challengeId, expiresAt: next.expiresAt })
      setWait(next.retryAfterSeconds)
      setNotice('Te enviamos un código nuevo. El anterior ya no sirve.')
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'No pudimos reenviar el código.')
    } finally {
      setResending(false)
    }
  }

  const busy = verify.pending || resending
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <h2 ref={headingRef} tabIndex={-1} className="flex items-start gap-2 text-block-title text-ap-ink outline-none">
          <ShieldCheckIcon className="mt-0.5 size-5 shrink-0 text-ap-success" aria-hidden />
          Verificación en dos pasos
        </h2>
        <p className="text-ap-muted">Te enviamos un código a tu correo. Escríbelo para terminar de ingresar.</p>
      </div>
      <form noValidate onSubmit={(event) => void submit(event)} className="flex flex-col gap-4" aria-busy={verify.pending || undefined}>
        {error ? <FormAlert title={error} /> : null}
        {notice ? <FormAlert tone="info" title={notice} /> : null}
        <TextField id="dospasos-code" name="code" label="Código" inputMode="numeric" autoComplete="one-time-code" maxLength={12} required />
        <Button type="submit" className="h-11" disabled={busy}>
          {verify.pending ? 'Validando…' : 'Ingresar'}
        </Button>
      </form>
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" className="h-11" disabled={busy || wait > 0} onClick={() => void resend()}>
          {resending ? 'Enviando…' : wait > 0 ? `Reenviar en ${wait} s` : 'Reenviar código'}
        </Button>
        <Button type="button" variant="ghost" className="h-11" disabled={verify.pending} onClick={onCancel}>
          Volver
        </Button>
      </div>
    </div>
  )
}
