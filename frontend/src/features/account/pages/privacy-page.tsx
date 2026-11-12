import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { DownloadIcon } from 'lucide-react'
import { toast } from 'sonner'
import { FormAlert, PasswordField } from '@/components/common/form-fields'
import { ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { saveBlob } from '@/lib/download'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { AccountSection } from '../account-layout'
import { cancelDeletion, exportData, getDeletionRequest, listIdentities, requestDeletion } from '../api'

function message(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

function ExportSection() {
  const [busy, setBusy] = useState(false)
  async function download() {
    setBusy(true)
    try {
      saveBlob(await exportData(), `autopartco-mis-datos-${new Date().toISOString().slice(0, 10)}.json`)
    } catch (error) {
      toast.error(message(error, 'No pudimos preparar tus datos.'))
    } finally {
      setBusy(false)
    }
  }
  return (
    <AccountSection id="priv-export" title="Descargar mis datos" description="Un archivo con tu perfil, direcciones, pedidos, reseñas, preguntas y preferencias.">
      <Button variant="outline" className="h-11 w-fit" disabled={busy} onClick={() => void download()}>
        <DownloadIcon aria-hidden />
        {busy ? 'Preparando…' : 'Descargar (JSON)'}
      </Button>
    </AccountSection>
  )
}

function DeletionSection() {
  const request = useAsync('portal:account:deletion', getDeletionRequest)
  const identities = useAsync('portal:account:identities', listIdentities)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const social = (identities.data?.length ?? 0) > 0

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    setBusy(true)
    setError(undefined)
    try {
      await requestDeletion(String(data.get('password') ?? ''), String(data.get('reason') ?? '').trim())
      toast.success('Recibimos tu solicitud.')
      setOpen(false)
      invalidateAsync('portal:account:deletion')
    } catch (failure) {
      setError(message(failure, 'No pudimos registrar la solicitud.'))
    } finally {
      setBusy(false)
    }
  }

  async function cancel() {
    setBusy(true)
    try {
      await cancelDeletion()
      toast.success('Cancelamos la solicitud. Tu cuenta sigue activa.')
      invalidateAsync('portal:account:deletion')
    } catch (failure) {
      toast.error(message(failure, 'No pudimos cancelarla.'))
    } finally {
      setBusy(false)
    }
  }

  const pending = request.data?.status === 'PENDING'
  return (
    <AccountSection
      id="priv-baja"
      title="Eliminar mi cuenta"
      description="Revisamos la solicitud y eliminamos tu cuenta y tus datos personales, salvo lo que la ley obliga a conservar (como facturas). Si eres dueño de un almacén, primero transfiérelo o ciérralo."
    >
      {request.status === 'error' ? (
        <ErrorState title="No pudimos cargar el estado" onRetry={request.retry} />
      ) : request.data === undefined ? (
        <Skeleton className="h-11 w-48" />
      ) : pending ? (
        <div className="flex max-w-2xl flex-col gap-3">
          <FormAlert tone="info" title="Solicitud en revisión">
            La enviaste el {new Intl.DateTimeFormat('es-CO', { dateStyle: 'long' }).format(new Date(request.data!.createdAt))}. Puedes cancelarla mientras no se procese.
          </FormAlert>
          <Button variant="outline" className="h-11 w-fit" disabled={busy} onClick={() => void cancel()}>
            Cancelar solicitud
          </Button>
        </div>
      ) : open ? (
        <form noValidate onSubmit={(event) => void submit(event)} className="grid max-w-md gap-4">
          {error ? <FormAlert title="No se envió la solicitud">{error}</FormAlert> : null}
          <PasswordField id="baja-password" name="password" label="Confirma tu contraseña" autoComplete="current-password" hint={social ? 'Si ingresas con Google y no tienes contraseña, créala con «¿Olvidaste tu contraseña?».' : undefined} />
          <label htmlFor="baja-reason" className="flex flex-col gap-1.5 text-sm font-medium text-ap-ink">
            Motivo (opcional)
            <textarea id="baja-reason" name="reason" maxLength={500} rows={3} className="rounded-md border border-input bg-ap-white px-3 py-2 text-base font-normal outline-none focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm" />
          </label>
          <div className="flex flex-wrap gap-2">
            <Button type="submit" variant="destructive" className="h-11 px-5" disabled={busy}>
              {busy ? 'Enviando…' : 'Solicitar eliminación'}
            </Button>
            <Button type="button" variant="ghost" className="h-11" onClick={() => setOpen(false)}>
              Cancelar
            </Button>
          </div>
        </form>
      ) : (
        <Button variant="outline" className="h-11 w-fit text-destructive" onClick={() => setOpen(true)}>
          Solicitar eliminación de la cuenta
        </Button>
      )}
    </AccountSection>
  )
}

/** `/cuenta/privacidad`: derechos de acceso y supresión (Ley 1581 de 2012). */
export default function AccountPrivacyPage() {
  return (
    <div className="flex flex-col">
      <ExportSection />
      <DeletionSection />
      <p className="text-sm text-ap-muted">
        Más información en la{' '}
        <Link to="/privacidad" className="font-medium text-ap-ink underline underline-offset-4">
          política de privacidad
        </Link>
        .
      </p>
    </div>
  )
}
