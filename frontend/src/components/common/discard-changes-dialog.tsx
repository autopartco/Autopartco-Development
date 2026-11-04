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
import type { CloseGuard } from '@/lib/use-close-guard'

/** Pregunta si descartar lo escrito al cerrar un panel o diálogo con cambios (ver `useCloseGuard`). */
export function DiscardChangesDialog({ guard }: { guard: CloseGuard }) {
  return (
    <AlertDialog open={guard.asking} onOpenChange={(open) => (!open ? guard.keepEditing() : undefined)}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>¿Descartar lo que escribiste?</AlertDialogTitle>
          <AlertDialogDescription>Si cierras ahora, se perderán los cambios. Nada se ha enviado todavía.</AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogClose render={<Button variant="outline" className="h-11 px-4" />}>Seguir editando</AlertDialogClose>
          <Button variant="destructive" className="h-11 px-4" onClick={guard.discard}>
            Descartar cambios
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
