import { Link, useSearchParams } from 'react-router'
import { BellOffIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { useAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { getAlertService } from '../api/alert-service'
import type { AlertStatusFilter } from '../api/alert-types'
import { AlertCard } from '../components/alert-card'
import { AlertPreferences } from '../components/alert-preferences'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { usePortal } from '../context'
import { useAlertActions } from '../lib/use-alert-actions'

const FILTERS: { param: string; status: AlertStatusFilter; label: string; empty: string }[] = [
  { param: 'abiertas', status: 'open', label: 'Abiertas', empty: 'No tienes alertas abiertas. Te avisaremos aquí cuando algo necesite tu atención.' },
  { param: 'no-leidas', status: 'unread', label: 'No leídas', empty: 'Ya leíste todas tus alertas.' },
  { param: 'todas', status: 'all', label: 'Todas', empty: 'Todavía no hay alertas en tu almacén.' },
]

function AlertsList({ filter }: { filter: (typeof FILTERS)[number] }) {
  const { storeId } = usePortal()
  const alerts = useAsync(`portal:${storeId}:alerts:list:${filter.status}`, async () => (await getAlertService()).list(storeId, filter.status))
  const actions = useAlertActions()
  return (
    <QueryBoundary result={alerts} what="las alertas" keepPrevious>
      {(rows) =>
        rows.length === 0 ? (
          <EmptyState icon={<BellOffIcon />} title="Sin alertas por ahora" description={filter.empty} />
        ) : (
          <ul className="flex flex-col gap-3">
            {rows.map((alert) => (
              <li key={alert.id}>
                <AlertCard alert={alert} actions={actions} />
              </li>
            ))}
          </ul>
        )
      }
    </QueryBoundary>
  )
}

/** `/portal/:storeId/alertas`: alertas del almacén con filtros y, con `ALERT_MANAGE`, la pestaña de preferencias. */
export default function PortalAlertsPage() {
  const { storeId, can } = usePortal()
  const [params] = useSearchParams()
  const canManage = can('ALERT_MANAGE')
  const preferences = canManage && params.get('vista') === 'preferencias'
  const filter = FILTERS.find((item) => item.param === params.get('estado')) ?? FILTERS[0]!
  const base = `/portal/${encodeURIComponent(storeId)}/alertas`

  const tabClass = (active: boolean) =>
    cn(
      '-mb-px inline-flex min-h-11 items-center border-b-2 px-3 text-sm font-semibold whitespace-nowrap transition-colors',
      active ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
    )

  return (
    <>
      <title>Alertas | Autopartco</title>
      <PageHeader title="Alertas" description="Avisos sobre ventas, existencias y clientes que conviene atender a tiempo." />
      {canManage ? (
        <nav aria-label="Secciones de alertas" className="mb-6 flex gap-1 overflow-x-auto border-b border-border">
          <Link to={base} className={tabClass(!preferences)} aria-current={!preferences ? 'page' : undefined}>
            Alertas
          </Link>
          <Link to={`${base}?vista=preferencias`} className={tabClass(preferences)} aria-current={preferences ? 'page' : undefined}>
            Preferencias
          </Link>
        </nav>
      ) : null}
      {preferences ? (
        <AlertPreferences />
      ) : (
        <div className="flex flex-col gap-5">
          <nav aria-label="Filtrar alertas" className="flex flex-wrap gap-2">
            {FILTERS.map((item) => (
              <Link
                key={item.param}
                to={`${base}?estado=${item.param}`}
                replace
                aria-current={item === filter ? 'true' : undefined}
                className={cn(
                    'inline-flex min-h-11 items-center rounded-full px-4 text-sm font-semibold ring-1 transition-colors',
                    item === filter ? 'bg-foreground text-background ring-foreground' : 'bg-card ring-foreground/15 hover:ring-foreground/40',
                )}
              >
                {item.label}
              </Link>
            ))}
          </nav>
          <AlertsList filter={filter} />
        </div>
      )}
    </>
  )
}
