import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import { MoreHorizontalIcon } from 'lucide-react'
import { FormAlert } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Switch } from '@/components/ui/switch'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { actionErrorMessage } from '@/features/portal/lib/errors'
import { PageHeader } from '@/features/portal/components/page-header'

/** Contenedor de cada pantalla de la consola: mismo ancho, márgenes y encabezado que el portal. */
export function AdminPage({ title, label, description, actions, children }: { title: string; label?: string; description?: ReactNode; actions?: ReactNode; children: ReactNode }) {
  return (
    <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8 lg:py-8">
      <title>{`${title} | Administración Autopartco`}</title>
      <PageHeader title={title} label={label} description={description} actions={actions} />
      {children}
    </div>
  )
}

export interface RowAction {
  label: string
  icon: ReactNode
  onSelect: () => void
  destructive?: boolean
}

/** Menú «…» de acciones por fila, igual al de las tablas del portal. */
export function RowActions({ label, actions }: { label: string; actions: RowAction[] }) {
  if (actions.length === 0) return null
  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        aria-label={label}
        className="inline-flex size-10 items-center justify-center rounded-lg outline-none hover:bg-concrete data-popup-open:bg-concrete pointer-coarse:size-11"
      >
        <MoreHorizontalIcon className="size-5" aria-hidden />
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        {actions.map((action) => (
          <DropdownMenuItem key={action.label} variant={action.destructive ? 'destructive' : 'default'} onClick={action.onSelect}>
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
        <SheetHeader className="p-4 pr-16">
          <SheetTitle className="text-lg font-extrabold tracking-tight">{title}</SheetTitle>
          <SheetDescription>{description}</SheetDescription>
        </SheetHeader>
        <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex min-h-0 flex-1 flex-col" aria-busy={pending || undefined}>
          <div className="flex flex-1 flex-col gap-4 overflow-y-auto px-4 pb-4">
            {error ? (
              <FormAlert ref={alertRef} title={`No se guardó ${what}`}>
                {error}
              </FormAlert>
            ) : null}
            {children}
          </div>
          <div className="flex flex-col-reverse gap-2 border-t border-border p-4 sm:flex-row sm:justify-end">
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
    <div className="flex items-start justify-between gap-4 rounded-lg border border-border p-3">
      <div className="flex flex-col gap-0.5">
        <label htmlFor={id} className="text-sm font-semibold">
          {label}
        </label>
        {hint ? (
          <p id={`${id}-hint`} className="text-sm text-muted-foreground">
            {hint}
          </p>
        ) : null}
      </div>
      <Switch id={id} checked={checked} onCheckedChange={onCheckedChange} aria-describedby={hint ? `${id}-hint` : undefined} />
    </div>
  )
}
