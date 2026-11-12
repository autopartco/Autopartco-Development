import { useState } from 'react'
import { toast } from 'sonner'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { ApiError } from '@/lib/api'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { AccountSection } from '../account-layout'
import { getSettings, patchSettings, type AccountSettings } from '../api'

type NotificationKey = keyof AccountSettings['notifications']

const EMAIL_OPTIONS: { key: NotificationKey; label: string; hint: string }[] = [
  { key: 'orderUpdates', label: 'Mis pedidos', hint: 'Confirmación, envío y entrega de tus compras.' },
  { key: 'reviewReminders', label: 'Calificar lo que recibí', hint: 'Una invitación al entregar y como máximo dos recordatorios.' },
  { key: 'storeReplies', label: 'Respuestas de los almacenes', hint: 'Cuando responden tus preguntas.' },
  { key: 'priceAlerts', label: 'Alertas de precio', hint: 'Bajas de precio en lo que sigues.' },
  { key: 'marketing', label: 'Novedades y promociones', hint: 'Ofertas de Autopartco y sus almacenes. Desactivado salvo que lo actives.' },
]

function Toggle({ id, label, hint, checked, onChange }: { id: string; label: string; hint: string; checked: boolean; onChange: (next: boolean) => void }) {
  return (
    <li className="flex items-start justify-between gap-4 py-3">
      <div className="flex min-w-0 flex-col gap-0.5 text-sm">
        <span id={`${id}-label`} className="font-medium text-ap-ink">
          {label}
        </span>
        <span id={`${id}-hint`} className="text-ap-muted">
          {hint}
        </span>
      </div>
      <Switch checked={checked} onCheckedChange={onChange} aria-labelledby={`${id}-label`} aria-describedby={`${id}-hint`} className="mt-0.5" />
    </li>
  )
}

function Preferences({ settings }: { settings: AccountSettings }) {
  const [notifications, setNotifications] = useState(settings.notifications)

  async function change(key: NotificationKey, next: boolean) {
    const previous = notifications
    setNotifications({ ...notifications, [key]: next })
    try {
      await patchSettings({ notifications: { [key]: next } })
      invalidateAsync('portal:account:settings')
    } catch (error) {
      setNotifications(previous)
      toast.error(error instanceof ApiError ? error.message : 'No pudimos guardar el cambio.')
    }
  }

  return (
    <AccountSection id="pref-correos" title="Correos que quieres recibir" description="Los avisos de seguridad (contraseña, correo, ingresos nuevos) siempre se envían. Los cambios se guardan al instante.">
      <ul className="max-w-2xl divide-y divide-ap-line border-y border-ap-line">
        {EMAIL_OPTIONS.map((option) => (
          <Toggle key={option.key} id={`pref-${option.key}`} label={option.label} hint={option.hint} checked={notifications[option.key]} onChange={(next) => void change(option.key, next)} />
        ))}
      </ul>
      <p className="text-xs text-ap-muted">Las notificaciones dentro de Autopartco (la campana) siguen llegando aunque desactives el correo.</p>
    </AccountSection>
  )
}

/** `/cuenta/preferencias`: qué correos se envían (Ley 1581: el marketing solo con consentimiento). */
export default function AccountPreferencesPage() {
  const settings = useAsync('portal:account:settings', getSettings)
  if (settings.status === 'error') return <ErrorState title="No pudimos cargar tus preferencias" onRetry={settings.retry} />
  if (!settings.data) return <Skeleton className="h-64 max-w-2xl" />
  return <Preferences settings={settings.data} />
}
