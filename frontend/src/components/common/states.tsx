import type { ReactNode } from 'react'
import { LockIcon, RotateCwIcon, TriangleAlertIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

/**
 * Estados de interfaz obligatorios (§12): carga, vacío, error recuperable y
 * permisos/sesión. Sin tarjetas decorativas: borde fino y texto que explica el siguiente paso.
 */

interface ErrorStateProps {
  title?: string
  description?: string
  onRetry?: () => void
  className?: string
}

/** Error recuperable con acción clara para reintentar. */
export function ErrorState({
  title = 'No pudimos cargar esta información',
  description = 'Revisa tu conexión e inténtalo de nuevo. Si el problema sigue, vuelve en unos minutos.',
  onRetry,
  className,
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn('flex flex-col items-start gap-3 rounded-xl border border-ap-danger/30 bg-ap-danger-soft p-5', className)}
    >
      <div className="flex items-center gap-2 font-semibold text-ap-danger">
        <TriangleAlertIcon className="size-5 shrink-0" aria-hidden />
        <p>{title}</p>
      </div>
      <p className="max-w-prose text-sm text-ap-ink">{description}</p>
      {onRetry ? (
        <Button variant="outline" onClick={onRetry}>
          <RotateCwIcon aria-hidden />
          Reintentar
        </Button>
      ) : null}
    </div>
  )
}

interface EmptyStateProps {
  icon?: ReactNode
  title: string
  description?: ReactNode
  /** Siguiente paso posible (botón o enlace). */
  children?: ReactNode
  className?: string
}

/** Estado vacío: explica qué falta y ofrece el siguiente paso. */
export function EmptyState({ icon, title, description, children, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-start gap-4 rounded-xl border border-ap-line bg-ap-white px-5 py-8 sm:px-8', className)}>
      {icon ? <div className="text-ap-muted [&_svg]:size-6" aria-hidden>{icon}</div> : null}
      <div className="flex flex-col gap-1.5">
        <h2 className="text-block-title text-ap-ink">{title}</h2>
        {description ? <div className="max-w-prose text-ap-muted">{description}</div> : null}
      </div>
      {children}
    </div>
  )
}

interface LoadingStateProps {
  /** Texto que anuncian los lectores de pantalla. */
  label?: string
  /** Filas de esqueleto (proporcionales al contenido que se espera). */
  rows?: number
  className?: string
}

/** Carga: esqueletos sobrios mientras llegan los datos; nunca datos ficticios. */
export function LoadingState({ label = 'Cargando información', rows = 3, className }: LoadingStateProps) {
  return (
    <div className={cn('flex flex-col gap-3', className)}>
      <span className="sr-only" role="status">
        {label}
      </span>
      <div className="flex flex-col gap-3" aria-hidden>
        {Array.from({ length: rows }, (_, index) => (
          <Skeleton key={index} className={cn('h-12 w-full', index === rows - 1 && 'w-2/3')} />
        ))}
      </div>
    </div>
  )
}

interface AccessStateProps {
  /** `session`: la sesión terminó o no hay sesión. `permission`: la cuenta no tiene acceso. */
  kind?: 'session' | 'permission'
  title?: string
  description?: ReactNode
  /** Acción de recuperación (p. ej. enlace a ingresar o volver al inicio). */
  children?: ReactNode
  className?: string
}

const ACCESS_COPY = {
  session: {
    title: 'Ingresa para continuar',
    description: 'Tu sesión terminó o aún no has ingresado. Ingresa de nuevo para ver esta información.',
  },
  permission: {
    title: 'No tienes acceso a esta sección',
    description: 'Tu cuenta no tiene permiso para ver esta información. Si necesitas acceso, pídelo al administrador de tu cuenta.',
  },
} as const

/** Error de permisos o sesión: dice qué pasó y cómo recuperar el acceso. */
export function AccessState({ kind = 'permission', title, description, children, className }: AccessStateProps) {
  const copy = ACCESS_COPY[kind]
  return (
    <div role="alert" className={cn('flex flex-col items-start gap-3 rounded-xl border border-ap-line bg-ap-white p-5', className)}>
      <div className="flex items-center gap-2 font-semibold text-ap-ink">
        <LockIcon className="size-5 shrink-0 text-ap-muted" aria-hidden />
        <p>{title ?? copy.title}</p>
      </div>
      <div className="max-w-prose text-sm text-ap-muted">{description ?? copy.description}</div>
      {children ? <div className="flex flex-wrap gap-2">{children}</div> : null}
    </div>
  )
}
