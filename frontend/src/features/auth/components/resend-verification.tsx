import { useEffect, useState, type FormEvent } from 'react'
import { TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { getAuthService } from '../api/auth-service'
import { emailError } from '../lib/validation'

interface ResendVerificationProps {
  /** Correo conocido (registro, ingreso). Sin él se pide en un campo (enlace vencido). */
  email?: string
}

/**
 * Reenvía el correo de verificación (`POST /auth/verify-email/resend`): nuevo enlace y código. El backend responde lo
 * mismo exista o no la cuenta y limita a uno por minuto y cinco por hora; aquí se respeta la espera que devuelve.
 */
export function ResendVerification({ email }: ResendVerificationProps) {
  const [busy, setBusy] = useState(false)
  const [wait, setWait] = useState(0)
  const [sentTo, setSentTo] = useState<string>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    if (wait <= 0) return
    const timer = window.setTimeout(() => setWait((seconds) => seconds - 1), 1000)
    return () => window.clearTimeout(timer)
  }, [wait])

  async function send(target: string) {
    setBusy(true)
    setError(undefined)
    try {
      const result = await (await getAuthService()).resendVerification(target)
      setSentTo(target)
      setWait(result.retryAfterSeconds)
    } catch (failure) {
      setError(failure instanceof ApiError ? failure.message : 'No pudimos reenviar el correo. Inténtalo de nuevo.')
    } finally {
      setBusy(false)
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const target = String(new FormData(event.currentTarget).get('email') ?? '').trim()
    const invalid = emailError(target)
    if (invalid) {
      setError(invalid)
      return
    }
    void send(target)
  }

  const label = busy ? 'Enviando…' : wait > 0 ? `Reenviar en ${wait} s` : sentTo ? 'Reenviar otra vez' : 'Reenviar correo de verificación'
  const status = sentTo ? (
    <p className="text-sm text-ap-muted" role="status">
      Si <strong className="break-all font-semibold text-ap-ink">{sentTo}</strong> tiene una cuenta sin verificar, te enviamos
      un enlace nuevo. Los anteriores ya no sirven.
    </p>
  ) : null
  const failure = error ? (
    <p className="text-sm text-destructive" role="alert">
      {error}
    </p>
  ) : null

  if (email) {
    return (
      <div className="flex flex-col items-start gap-2">
        <Button type="button" variant="outline" className="h-11" disabled={busy || wait > 0} onClick={() => void send(email)}>
          {label}
        </Button>
        {status}
        {failure}
      </div>
    )
  }

  return (
    <form noValidate onSubmit={submit} className="flex w-full max-w-md flex-col gap-3">
      <TextField id="reenvio-email" name="email" type="email" label="Tu correo" autoComplete="email" maxLength={100} required />
      <Button type="submit" variant="outline" className="h-11 w-fit" disabled={busy || wait > 0}>
        {label}
      </Button>
      {status}
      {failure}
    </form>
  )
}
