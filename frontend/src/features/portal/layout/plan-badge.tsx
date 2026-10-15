import { Link } from 'react-router'
import { Skeleton } from '@/components/ui/skeleton'
import { usePortal } from '../context'
import { formatDays, SUBSCRIPTION_STATUS_TONE } from '../lib/labels'
import { StatusBadge } from '../components/status-badge'

/** Insignia del plan en la barra superior: nombre y días restantes; lleva a "Plan y suscripción". */
export function PlanBadge() {
  const { subscription, storeId } = usePortal()
  if (!subscription) return <Skeleton aria-hidden className="h-6 w-28 rounded-full" />

  const days = subscription.daysRemaining
  let text: string
  switch (subscription.status) {
    case 'TRIALING':
      text = days === null ? 'Prueba gratuita' : `Prueba · ${formatDays(days)}`
      break
    case 'ACTIVE':
      text = days === null ? `Plan ${subscription.plan.name}` : `${subscription.plan.name} · ${formatDays(days)}`
      break
    case 'EXPIRED':
      text = 'Suscripción vencida'
      break
    default:
      text = 'Configuración pendiente'
  }
  return (
    <Link
      to={`/portal/${encodeURIComponent(storeId)}/plan`}
      aria-label={`${text}. Ver plan y suscripción`}
      className="inline-flex min-h-11 items-center rounded-full lg:min-h-10"
    >
      <StatusBadge tone={SUBSCRIPTION_STATUS_TONE[subscription.status]}>{text}</StatusBadge>
    </Link>
  )
}
