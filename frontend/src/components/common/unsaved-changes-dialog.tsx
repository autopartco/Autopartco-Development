import type { Blocker } from 'react-router'
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

/** Pregunta qué hacer cuando `useUnsavedChanges` bloqueó una navegación. Cerrar con Esc equivale a «Seguir editando». */
export function UnsavedChangesDialog({ blocker }: { blocker: Blocker }) {
  return (
    <AlertDialog open={blocker.state === 'blocked'} onOpenChange={(open) => (!open && blocker.state === 'blocked' ? blocker.reset() : undefined)}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Tienes cambios sin guardar</AlertDialogTitle>
          <AlertDialogDescription>Si sales ahora, se perderá lo que escribiste. Nada se ha enviado todavía.</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogClose render={<Button variant="outline" className="h-11 px-4" />}>Seguir editando</AlertDialogClose>
          <Button variant="destructive" className="h-11 px-4" onClick={() => (blocker.state === 'blocked' ? blocker.proceed() : undefined)}>
            Salir sin guardar
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
