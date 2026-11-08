import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import { MoreHorizontalIcon, RotateCwIcon, ShieldAlertIcon, TriangleAlertIcon } from 'lucide-react'
import { FormAlert } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Switch } from '@/components/ui/switch'
import type { AsyncResult } from '@/lib/use-async'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { cn } from '@/lib/utils'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { actionErrorMessage, describeError } from '@/features/portal/lib/errors'
import { PageHeader } from '@/features/portal/components/page-header'

/** Contenedor de cada pantalla de la consola: mismo ancho, márgenes y encabezado que el portal. */
export function AdminPage({ title, label, description, actions, meta, children }: { title: string; label?: string; description?: ReactNode; actions?: ReactNode; meta?: ReactNode; children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      <title>{`${title} | Administración Autopartco`}</title>
      <PageHeader title={title} label={label} description={description} actions={actions} meta={meta} />
      {children}
    </div>
  )
}

/**
 * Error de carga de la consola (§12): causa y siguiente paso. Los textos de permiso y sesión son los de
 * administración (el `QueryError` del portal habla de almacenes).
 */
export function AdminQueryError({ error, what, onRetry, className }: { error: unknown; what: string; onRetry?: () => void; className?: string }) {
  const view = describeError(error, what)
  const forbidden = view.kind === 'forbidden'
  const Icon = forbidden ? ShieldAlertIcon : TriangleAlertIcon
  return (
    <div role="alert" className={cn('flex flex-col items-start gap-3 rounded-lg border border-ap-danger/30 bg-ap-danger-soft p-5', className)}>
      <p className="flex items-center gap-2 font-semibold text-ap-danger">
        <Icon className="size-5" aria-hidden />
        {forbidden ? 'Tu cuenta ya no tiene acceso de administración' : view.title}
      </p>
      <p className="max-w-prose text-sm text-ap-ink">
        {forbidden ? `El servidor rechazó la consulta de ${what}. Ingresa con una cuenta de administración para continuar.` : view.description}
      </p>
      <div className="flex flex-wrap gap-2">
        {view.retryable && onRetry ? (
          <Button variant="outline" className="h-11 px-4" onClick={onRetry}>
            <RotateCwIcon aria-hidden />
            Reintentar
          </Button>
        ) : null}
        {view.kind === 'unauthorized' || forbidden ? (
          <Button className="h-11 px-4" nativeButton={false} render={<Link to="/ingresar" />}>
            Ingresar
          </Button>
        ) : null}
      </div>
    </div>
  )
}

/** Carga, error y éxito de una consulta de la consola, igual que `QueryBoundary` del portal pero con los textos de administración. */
export function AdminBoundary<T>({ result, what, skeleton, keepPrevious, children }: { result: AsyncResult<T>; what: string; skeleton?: ReactNode; keepPrevious?: boolean; children: (data: T) => ReactNode }) {
  if (result.status === 'error') return <AdminQueryError error={result.error} what={what} onRetry={result.retry} />
  if (result.data !== undefined && (result.status === 'success' || keepPrevious)) {
    return <div aria-busy={result.status === 'loading' || undefined}>{children(result.data)}</div>
  }
  return <>{skeleton ?? <ListSkeleton label={`Cargando ${what}`} />}</>
}

/**
 * Bloque de una pantalla de trabajo: sección abierta con línea de estructura superior (§3.5, §6.3),
 * título de bloque, contexto en mono y acción a la derecha. Sin tarjeta ni sombra.
 */
export function AdminSection({ id, title, meta, actions, className, children }: { id: string; title: ReactNode; meta?: ReactNode; actions?: ReactNode; className?: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className={cn('flex min-w-0 flex-col gap-3 border-t border-ap-line-strong pt-4', className)}>
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
        <div className="flex min-w-0 flex-col gap-1">
          <h2 id={id} className="text-base font-semibold text-ap-ink">
            {title}
          </h2>
          {meta ? <p className="ap-ref text-ap-muted">{meta}</p> : null}
        </div>
        {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
      {children}
    </section>
  )
}

export interface Figure {
  label: string
  value: ReactNode
  /** Contexto o advertencia breve. */
  hint?: ReactNode
  /** Cifra que pide revisión: texto ámbar además de la línea (no solo color). */
  attention?: boolean
  /** Destino para revisar el detalle (la cifra entera es el enlace). */
  to?: string
}

/**
 * Cifras de contexto de un bloque, en una línea de definición (no tarjetas KPI, §7.4/§20.14): valores con
 * cifras tabulares separados por divisores finos. Acompañan a un gráfico o una tabla, no la sustituyen.
 */
export function FigureList({ items, className }: { items: Figure[]; className?: string }) {
  return (
    <dl className={cn('flex flex-wrap gap-y-3 divide-x divide-ap-line', className)}>
      {items.map((item) => {
        const body = (
          <>
            <dt className="text-sm text-ap-muted">{item.label}</dt>
            <dd className={cn('ap-price text-lg leading-tight', item.attention ? 'text-ap-warning' : 'text-ap-ink')}>{item.value}</dd>
            {item.hint ? <dd className={cn('text-xs', item.attention ? 'font-medium text-ap-warning' : 'text-ap-muted')}>{item.hint}</dd> : null}
          </>
        )
        return (
          <div key={item.label} className="flex min-w-0 flex-col gap-0.5 px-4 first:pl-0">
            {item.to ? (
              <Link to={item.to} className="-mx-1 flex flex-col gap-0.5 rounded-sm px-1 underline-offset-4 hover:bg-ap-paper hover:underline">
                {body}
              </Link>
            ) : (
              body
            )}
          </div>
        )
      })}
    </dl>
  )
}

export interface RowAction {
  label: string
  icon: ReactNode
  onSelect: () => void
  destructive?: boolean
}

/**
 * Menú «…» de acciones por fila (§8.5): edición primero y, separadas por un divisor, las destructivas en
 * rojo semántico (cada una pide confirmación en la pantalla que la usa).
 */
export function RowActions({ label, actions }: { label: string; actions: RowAction[] }) {
  if (actions.length === 0) return null
  const safe = actions.filter((action) => !action.destructive)
  const destructive = actions.filter((action) => action.destructive)
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={label}
        className="inline-flex size-11 items-center justify-center rounded-md text-ap-graphite outline-none hover:bg-ap-paper focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ap-red data-popup-open:bg-ap-paper"
      >
        <MoreHorizontalIcon className="size-5" aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        {safe.map((action) => (
          <DropdownMenuItem key={action.label} onClick={action.onSelect}>
            {action.icon}
            {action.label}
          </DropdownMenuItem>
        ))}
        {safe.length > 0 && destructive.length > 0 ? <DropdownMenuSeparator /> : null}
        {destructive.map((action) => (
          <DropdownMenuItem key={action.label} variant="destructive" onClick={action.onSelect}>
            {action.icon}
            {action.label}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

interface FormSheetProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: ReactNode
  submitLabel: string
  /** Nombre de lo que se guarda, para el aviso de error ("el plan"). */
  what: string
  /** Valida y envía. Devuelve `false` si la validación local falló (el panel sigue abierto sin aviso). */
  onSubmit: () => Promise<boolean | void>
  children: ReactNode
}

/**
 * Panel lateral de alta/edición (mismo patrón que las sucursales del portal): campos con scroll, pie fijo con
 * Cancelar/Guardar, candado contra doble envío y aviso de error del servidor.
 */
export function FormSheet({ open, onOpenChange, title, description, submitLabel, what, onSubmit, children }: FormSheetProps) {
  const submit = useSubmitOnce(onSubmit)
  const [error, setError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)
  const pending = submit.pending

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(undefined)
    const result = await submit.run()
    if (result.status === 'ok') {
      if (result.value !== false) onOpenChange(false)
    } else if (result.status === 'error') {
      setError(actionErrorMessage(result.error, `No pudimos guardar ${what}. Inténtalo de nuevo.`))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  return (
    <Sheet
      open={open}
      onOpenChange={(next) => {
        if (pending) return
        if (!next) setError(undefined)
        onOpenChange(next)
      }}
    >
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-lg">
        <SheetHeader className="border-b border-ap-line p-4 pr-16">
          <SheetTitle className="text-block-title text-ap-ink">{title}</SheetTitle>
          <SheetDescription>{description}</SheetDescription>
        </SheetHeader>
        <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex min-h-0 flex-1 flex-col" aria-busy={pending || undefined}>
          <div className="flex flex-1 flex-col gap-4 overflow-y-auto p-4">
            {error ? (
              <FormAlert ref={alertRef} title={`No se guardó ${what}`}>
                {error}
              </FormAlert>
            ) : null}
            {children}
          </div>
          <div className="flex flex-col-reverse gap-2 border-t border-ap-line p-4 sm:flex-row sm:justify-end">
            <Button type="button" variant="outline" className="h-11 px-5" onClick={() => onOpenChange(false)} disabled={pending}>
              Cancelar
            </Button>
            <Button type="submit" className="h-11 px-5" disabled={pending}>
              {pending ? 'Guardando…' : submitLabel}
            </Button>
          </div>
        </form>
      </SheetContent>
    </Sheet>
  )
}

/** Interruptor con etiqueta y ayuda, accesible como un solo control. */
export function SwitchField({ id, label, hint, checked, onCheckedChange }: { id: string; label: string; hint?: string; checked: boolean; onCheckedChange: (checked: boolean) => void }) {
  return (
    <div className="flex items-start justify-between gap-4 border-b border-ap-line py-3">
      <div className="flex flex-col gap-0.5">
        <label htmlFor={id} className="text-sm font-semibold text-ap-ink">
          {label}
        </label>
        {hint ? (
          <p id={`${id}-hint`} className="text-sm text-ap-muted">
            {hint}
          </p>
        ) : null}
      </div>
      <Switch id={id} checked={checked} onCheckedChange={onCheckedChange} aria-describedby={hint ? `${id}-hint` : undefined} />
    </div>
  )
}
