import { Link } from 'react-router'
import { CheckCheckIcon, EyeIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { StoreAlert } from '../api/alert-types'
import { usePortal } from '../context'
import { alertDetail, alertTarget, deadStockProducts, SEVERITY_LABEL } from '../lib/alerts'
import { formatDateTime } from '../lib/labels'
import type { AlertActions } from '../lib/use-alert-actions'
import { StatusBadge } from './status-badge'

interface AlertCardProps {
  alert: StoreAlert
  actions: AlertActions
  /** Se llama al seguir el enlace (p. ej. para cerrar el panel). */
  onNavigate?: () => void
  compact?: boolean
}

/** Una alerta: gravedad, mensaje, detalle del `payload`, enlace al producto o cliente y acciones de leída y resuelta. */
export function AlertCard({ alert, actions, onNavigate, compact }: AlertCardProps) {
  const { storeId, can } = usePortal()
  const severity = SEVERITY_LABEL[alert.severity]
  const unread = alert.readAt === null && alert.resolvedAt === null
  const target = alertTarget(alert, storeId, can)
  const detail = alertDetail(alert)
  const products = alert.type === 'DEAD_STOCK' ? deadStockProducts(alert).slice(0, 5) : []
  const busy = actions.busyId === alert.id
  const base = `/portal/${encodeURIComponent(storeId)}`

  return (
    <article className={cn('flex flex-col gap-2.5 rounded-lg bg-card p-4 ring-1', unread ? 'ring-foreground/25' : 'ring-foreground/10')} aria-label={alert.title}>
      <div className="flex flex-wrap items-center gap-2">
        <StatusBadge tone={severity.tone}>{severity.label}</StatusBadge>
        {unread ? <StatusBadge tone="neutral">Sin leer</StatusBadge> : null}
        {alert.resolvedAt ? <StatusBadge tone="success">Resuelta</StatusBadge> : null}
        <time dateTime={alert.createdAt} className="ml-auto text-xs text-muted-foreground">
          {formatDateTime(alert.createdAt)}
        </time>
      </div>
      <div className="flex flex-col gap-1">
        <h3 className={cn('text-base tracking-tight', unread ? 'font-bold' : 'font-semibold')}>{alert.title}</h3>
        <p className="text-sm text-foreground/85">{alert.message}</p>
        {detail ? <p className="text-sm text-muted-foreground">{detail}</p> : null}
      </div>
      {products.length > 0 ? (
        <ul className="flex list-disc flex-col gap-0.5 pl-5 text-sm">
          {products.map((product) => (
            <li key={product.productId}>
              {can('PRODUCT_READ') ? (
                <Link to={`${base}/productos/${encodeURIComponent(product.productId)}`} onClick={onNavigate} className="font-medium underline-offset-4 hover:underline">
                  {product.name}
                </Link>
              ) : (
                product.name
              )}
              {product.stock !== undefined ? <span className="text-muted-foreground"> · {product.stock} en stock</span> : null}
            </li>
          ))}
        </ul>
      ) : null}
      <div className={cn('flex flex-wrap items-center gap-2', compact && 'gap-1')}>
        {target ? (
          <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={target.to} onClick={onNavigate} />}>
            {target.label}
          </Button>
        ) : null}
        {unread ? (
          <Button variant="ghost" className="h-11 px-3" disabled={busy} onClick={() => void actions.markRead(alert)}>
            <EyeIcon aria-hidden />
            Marcar como leída
          </Button>
        ) : null}
        {!alert.resolvedAt ? (
          <Button variant="ghost" className="h-11 px-3" disabled={busy} onClick={() => void actions.resolve(alert)}>
            <CheckCheckIcon aria-hidden />
            Resolver
          </Button>
        ) : null}
      </div>
    </article>
  )
}
