import { useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { FormAlert } from '@/components/common/form-fields'
import { FormSection } from '@/components/common/form-section'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { Button } from '@/components/ui/button'
import { Switch } from '@/components/ui/switch'
import { ApiError } from '@/lib/api'
import { useDraftForm, type FieldErrors } from '@/lib/use-draft-form'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'
import { invalidateAsync } from '@/lib/use-async'
import {
  DEFAULT_WHATSAPP_SERVICE_MESSAGE,
  getStoreSettingsService,
  type StoreSettings,
  type StoreSettingsPatch,
} from '../api/store-settings-service'
import { TextAreaField } from '../components/form-controls'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { actionErrorMessage } from '../lib/errors'
import { focusById } from '../lib/forms'

const MESSAGE_MAX = 300
const MESSAGE_ID = 'config-mensaje-servicio'
const SWITCH_ID = 'config-aviso-citas'
const PLACEHOLDERS = ['{servicio}', '{sucursal}', '{enlace}']

interface Values {
  whatsappServiceMessage: string
  newBooking: boolean
}

function validate(values: Values): FieldErrors {
  const errors: FieldErrors = {}
  const message = values.whatsappServiceMessage.trim()
  if (!message) errors.whatsappServiceMessage = 'Escribe el mensaje. Debe incluir {enlace}.'
  else if (message.length > MESSAGE_MAX) errors.whatsappServiceMessage = `El mensaje no puede superar ${MESSAGE_MAX} caracteres.`
  else if (!message.includes('{enlace}')) errors.whatsappServiceMessage = 'El mensaje debe incluir {enlace}: es el vínculo al servicio que recibe el taller.'
  else if (/[<>]/.test(message)) errors.whatsappServiceMessage = 'El mensaje no puede contener < ni >.'
  return errors
}

function SettingsForm({ settings }: { settings: StoreSettings }) {
  const { storeId } = usePortal()
  const draft = useDraftForm<Values>({
    initial: {
      whatsappServiceMessage: settings.contact.whatsappServiceMessage ?? DEFAULT_WHATSAPP_SERVICE_MESSAGE,
      newBooking: settings.notifications.newBooking,
    },
    validate,
  })
  const { values } = draft
  const submit = useSubmitOnce(async (patch: StoreSettingsPatch) => (await getStoreSettingsService()).patch(storeId, patch))
  const blocker = useUnsavedChanges(draft.isDirty && !submit.pending)
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(undefined)
    const check = draft.validateAll()
    if (!check.valid) {
      focusById(MESSAGE_ID)
      return
    }
    const patch: StoreSettingsPatch = {}
    const message = values.whatsappServiceMessage.trim()
    if (message !== (settings.contact.whatsappServiceMessage ?? DEFAULT_WHATSAPP_SERVICE_MESSAGE)) patch.contact = { whatsappServiceMessage: message }
    if (values.newBooking !== settings.notifications.newBooking) patch.notifications = { newBooking: values.newBooking }
    if (!patch.contact && !patch.notifications) {
      toast.info('No hay cambios para guardar.')
      return
    }
    const result = await submit.run(patch)
    if (result.status === 'ok') {
      draft.markSaved()
      invalidateAsync(`portal:${storeId}:settings`)
      toast.success('Guardamos la configuración.')
    } else if (result.status === 'error') {
      const error = result.error
      const key = error instanceof ApiError && error.code === 'INVALID_SETTING' && error.details && typeof error.details === 'object' ? (error.details as { key?: unknown }).key : undefined
      if (key === 'contact.whatsappServiceMessage' && error instanceof ApiError) {
        draft.setServerErrors({ whatsappServiceMessage: error.message })
        requestAnimationFrame(() => focusById(MESSAGE_ID))
      }
      setFormError(actionErrorMessage(error, 'No pudimos guardar la configuración. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex max-w-2xl flex-col gap-5" aria-busy={submit.pending || undefined}>
      {formError ? (
        <FormAlert ref={alertRef} title="No se guardaron los cambios">
          {formError}
        </FormAlert>
      ) : null}
      <FormSection
        title="WhatsApp de servicios"
        description="El texto que se escribe cuando un comprador te contacta desde la página de un servicio."
        className="border-t border-ap-line-strong pt-4"
      >
        <TextAreaField
          id={MESSAGE_ID}
          label="Mensaje de WhatsApp para servicios"
          value={values.whatsappServiceMessage}
          error={draft.errorFor('whatsappServiceMessage')}
          maxLength={MESSAGE_MAX + 50}
          className="min-h-28"
          onBlur={() => draft.touch('whatsappServiceMessage')}
          onChange={(event) => draft.set('whatsappServiceMessage', event.target.value)}
          hint={
            <>
              {values.whatsappServiceMessage.length} de {MESSAGE_MAX} caracteres. Debe incluir <code>{'{enlace}'}</code>. Puedes usar{' '}
              {PLACEHOLDERS.map((item, index) => (
                <span key={item}>
                  {index > 0 ? ', ' : ''}
                  <code>{item}</code>
                </span>
              ))}
              ; se reemplazan por el nombre del servicio, la sucursal y el enlace a la página del servicio.
            </>
          }
        />
        <Button type="button" variant="ghost" className="h-11 w-fit px-3" onClick={() => draft.set('whatsappServiceMessage', DEFAULT_WHATSAPP_SERVICE_MESSAGE)}>
          Usar el mensaje por defecto
        </Button>
      </FormSection>
      <FormSection title="Avisos por correo" description="A qué correos te escribimos cuando pasa algo en tu agenda." className="border-t border-ap-line-strong pt-4">
        <div className="flex items-start gap-3 rounded-md border border-ap-line bg-ap-paper p-3">
          <Switch id={SWITCH_ID} checked={values.newBooking} onCheckedChange={(checked) => draft.set('newBooking', checked)} aria-labelledby={`${SWITCH_ID}-label`} aria-describedby={`${SWITCH_ID}-hint`} className="mt-0.5" />
          <div className="text-sm">
            <p id={`${SWITCH_ID}-label`} className="font-semibold">
              Avisarme por correo de nuevas citas
            </p>
            <p id={`${SWITCH_ID}-hint`} className="text-ap-muted">
              Recibes un correo cada vez que un comprador solicita una cita.
            </p>
          </div>
        </div>
      </FormSection>
      <div className="sticky bottom-0 z-10 -mx-4 flex flex-wrap items-center gap-3 border-t border-ap-line bg-ap-white px-4 py-3 sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0">
        <Button type="submit" className="h-11 px-6" disabled={submit.pending || !draft.isDirty}>
          {submit.pending ? 'Guardando…' : 'Guardar cambios'}
        </Button>
        <p role="status" className="text-sm text-ap-muted">
          {draft.isDirty ? 'Tienes cambios sin guardar.' : 'Sin cambios por guardar.'}
        </p>
      </div>
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}

/** `/portal/:storeId/configuracion`: mensaje de WhatsApp de servicios y aviso de nuevas citas. */
export default function PortalSettingsPage() {
  const settings = useStoreQuery('settings', async (id) => (await getStoreSettingsService()).get(id))
  return (
    <>
      <title>Configuración | Autopartco</title>
      <PageHeader title="Configuración" description="Mensajes y avisos de tu almacén." />
      <QueryBoundary result={settings} what="la configuración de tu tienda" keepPrevious skeleton={<ListSkeleton rows={3} label="Cargando la configuración" />}>
        {(data) => <SettingsForm settings={data} />}
      </QueryBoundary>
    </>
  )
}
