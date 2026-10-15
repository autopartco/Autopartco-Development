import { Link } from 'react-router'
import { ClockIcon, LockKeyholeIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { Subscription } from '../api/types'
import { formatDate, formatDays } from '../lib/labels'

/**
 * Aviso persistente: suscripción vencida (EXPIRED) o prueba que termina en 7 días o menos.
 * Nada más: el texto no promete nada que el servidor no aplique.
 */
export function SubscriptionBanner({ subscription, plansPath, canManage }: { subscription: Subscription; plansPath: string; canManage: boolean }) {
  const expired = subscription.status === 'EXPIRED'
  const endingTrial =
    subscription.status === 'TRIALING' && subscription.daysRemaining !== null && subscription.daysRemaining <= 7
  if (!expired && !endingTrial) return null

  const days = subscription.daysRemaining ?? 0
  const Icon = expired ? LockKeyholeIcon : ClockIcon
  return (
    <section
      aria-label="Estado de tu suscripción"
      className={
        expired
          ? 'border-b border-destructive/25 bg-destructive/5 text-foreground'
          : 'border-b border-stock-low/25 bg-stock-low/10 text-foreground'
      }
    >
      <div className="mx-auto flex max-w-7xl flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between sm:px-6 lg:px-8">
        <div className="flex items-start gap-3">
          <Icon className={expired ? 'mt-0.5 size-5 shrink-0 text-destructive' : 'mt-0.5 size-5 shrink-0 text-stock-low'} aria-hidden />
          <p className="text-sm">
            {expired ? (
              <>
                <strong className="font-semibold">Tu suscripción venció{subscription.validThrough ? ` el ${formatDate(subscription.validThrough)}` : ''}.</strong>{' '}
                Puedes ver tu información y gestionar pedidos, pero no crear ni publicar productos.{' '}
                {canManage ? 'Renueva tu plan para volver a hacerlo.' : 'Pídele al propietario que renueve el plan.'}
              </>
            ) : (
              <>
                <strong className="font-semibold">
                  Tu prueba gratuita termina {days === 0 ? 'hoy' : `en ${formatDays(days)}`}.
                </strong>{' '}
                {canManage
                  ? 'Elige un plan antes de que termine para no perder la posibilidad de crear y publicar.'
                  : 'Pídele al propietario que elija un plan antes de que termine.'}
              </>
            )}
          </p>
        </div>
        {canManage ? (
          <Button className="h-11 shrink-0 px-4" nativeButton={false} render={<Link to={plansPath} />}>
            {expired ? 'Renovar plan' : 'Elegir plan'}
          </Button>
        ) : null}
      </div>
    </section>
  )
}
