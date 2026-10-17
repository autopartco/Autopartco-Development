import { useState } from 'react'
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
import { TextAreaField } from '@/features/portal/components/form-controls'
import { actionErrorMessage } from '@/features/portal/lib/errors'
import { REVIEW_NOTES_MAX, type StoreApplication } from '../api/types'

interface ReviewDialogProps {
  application: StoreApplication
  /** `true` aprueba, `false` rechaza. */
  approved: boolean
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Si rechaza, el diálogo sigue abierto y muestra el motivo. */
  onSubmit: (notes: string) => Promise<void>
}

/**
 * Confirmación de la decisión sobre una solicitud. Rechazar exige un motivo (lo verá quien
 * solicitó); aprobar admite un comentario opcional. El servidor valida el máximo de 2000 caracteres.
 */
export function ReviewDialog({ application, approved, open, onOpenChange, onSubmit }: ReviewDialogProps) {
  const [notes, setNotes] = useState('')
  const [fieldError, setFieldError] = useState<string>()
  const [error, setError] = useState<string>()
  const send = useSubmitOnce(onSubmit)
  const pending = send.pending

  function reset(next: boolean) {
    if (pending) return
    if (!next) {
      setNotes('')
      setFieldError(undefined)
      setError(undefined)
    }
    onOpenChange(next)
  }

  async function submit() {
    const clean = notes.trim()
    if (!approved && clean.length === 0) {
      setFieldError('Escribe el motivo del rechazo: la persona lo verá para corregir su solicitud.')
      document.getElementById('review-notes')?.focus()
      return
    }
    if (clean.length > REVIEW_NOTES_MAX) {
      setFieldError(`El comentario puede tener máximo ${REVIEW_NOTES_MAX} caracteres (tiene ${clean.length}).`)
      return
    }
    setFieldError(undefined)
    setError(undefined)
    // ÚNICA escritura: la decisión se guarda al pulsar el botón final.
    const result = await send.run(clean)
    if (result.status === 'ok') {
      setNotes('')
      onOpenChange(false)
    } else if (result.status === 'error') {
      setError(actionErrorMessage(result.error, 'No pudimos guardar la revisión. Inténtalo de nuevo.'))
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={reset}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{approved ? `Aprobar a ${application.businessName}` : `Rechazar a ${application.businessName}`}</AlertDialogTitle>
          <AlertDialogDescription>
            {approved
              ? 'El almacén pasa a configuración y quien lo solicitó recibe un correo para completar su perfil.'
              : 'La solicitud queda rechazada y la persona podrá enviar una nueva con los datos corregidos.'}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <TextAreaField
          id="review-notes"
          label={approved ? 'Comentario' : 'Motivo del rechazo'}
          optional={approved}
          hint={`${notes.trim().length} / ${REVIEW_NOTES_MAX}`}
          error={fieldError}
          value={notes}
          maxLength={REVIEW_NOTES_MAX + 200}
          placeholder={approved ? 'Opcional: algo que quieras dejar anotado.' : 'Ej.: el RUT está vencido; sube la versión actualizada.'}
          onChange={(event) => setNotes(event.target.value)}
        />
        {error ? (
          <p role="alert" className="rounded-lg border border-destructive/25 bg-destructive/5 p-3 text-sm font-medium text-destructive">
            {error}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogClose render={<Button variant="outline" className="h-11 px-4" disabled={pending} />}>Cancelar</AlertDialogClose>
          <Button variant={approved ? 'default' : 'destructive'} className="h-11 px-4" disabled={pending} onClick={() => void submit()}>
            {pending ? 'Guardando…' : approved ? 'Aprobar solicitud' : 'Rechazar solicitud'}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
