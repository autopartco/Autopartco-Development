import { useEffect, useRef, type ReactNode } from 'react'
import { MailCheckIcon } from 'lucide-react'

function formatExpiry(value: string | undefined): string | undefined {
  if (!value) return undefined
  // El backend envía `LocalDateTime` sin zona: se muestra como hora local.
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return undefined
  return new Intl.DateTimeFormat('es-CO', { hour: 'numeric', minute: '2-digit', day: 'numeric', month: 'long' }).format(date)
}

interface CheckEmailProps {
  email: string
  sent: boolean
  expiresAt?: string
  children?: ReactNode
}

/** Pantalla final del registro: el título recibe el foco al aparecer. */
export function CheckEmail({ email, sent, expiresAt, children }: CheckEmailProps) {
  const headingRef = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    headingRef.current?.focus()
  }, [])
  const expiry = formatExpiry(expiresAt)
  return (
    <div className="flex flex-col items-start gap-4" role="status">
      <span className="grid size-12 place-items-center rounded-full bg-concrete text-brand">
        <MailCheckIcon className="size-6" aria-hidden />
      </span>
      <h2 ref={headingRef} tabIndex={-1} className="text-block-title outline-none">
        {sent ? 'Revisa tu correo para verificar tu cuenta' : 'Tu cuenta quedó creada'}
      </h2>
      {sent ? (
        <p className="text-muted-foreground">
          Te enviamos un enlace a <strong className="font-semibold text-foreground">{email}</strong>. Ábrelo para
          activar tu cuenta; hasta entonces no podrás ingresar.
          {expiry ? ` El enlace vence el ${expiry}.` : ''}
        </p>
      ) : (
        <p className="text-muted-foreground">
          No pudimos confirmar el envío del correo de verificación a <strong className="font-semibold text-foreground">{email}</strong>.
          Revisa tu bandeja en unos minutos; si no llega, intenta ingresar más tarde.
        </p>
      )}
      <p className="text-sm text-muted-foreground">¿No lo ves? Busca en correo no deseado o promociones.</p>
      {children}
    </div>
  )
}
