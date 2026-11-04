import { useState, type ReactNode } from 'react'
import {
  AlertDialog,
  AlertDialogClose,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { actionErrorMessage } from '../lib/errors'

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description: ReactNode
  confirmLabel: string
  /** Estilo destructivo para borrar o desactivar. */
  destructive?: boolean
  /** Si rechaza, el diálogo sigue abierto y muestra el motivo. */
  onConfirm: () => Promise<void>
}

/**
 * Confirmación de acciones con consecuencias (borrar, desactivar, cancelar). El foco entra al
 * diálogo y vuelve al botón que lo abrió (lo gestiona Base UI). No se cierra mientras trabaja.
 */
export function ConfirmDialog({ open, onOpenChange, title, description, confirmLabel, destructive, onConfirm }: ConfirmDialogProps) {
  // Candado síncrono: un segundo clic en el mismo tick no repite la acción.
  const confirm = useSubmitOnce(onConfirm)
  const pending = confirm.pending
  const [error, setError] = useState<string>()

  async function handleConfirm() {
    setError(undefined)
    const result = await confirm.run()
    if (result.status === 'ok') onOpenChange(false)
    else if (result.status === 'error') setError(actionErrorMessage(result.error, 'No pudimos completar la acción. Inténtalo de nuevo.'))
  }

  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => {
        if (pending) return
        if (!next) setError(undefined)
        onOpenChange(next)
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        {error ? (
          <p role="alert" className="rounded-lg border border-destructive/25 bg-destructive/5 p-3 text-sm font-medium text-destructive">
            {error}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogClose render={<Button variant="outline" className="h-11 px-4" disabled={pending} />}>Cancelar</AlertDialogClose>
          <Button
            variant={destructive ? 'destructive' : 'default'}
            className="h-11 px-4"
            disabled={pending}
            onClick={() => void handleConfirm()}
          >
            {pending ? 'Procesando…' : confirmLabel}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
