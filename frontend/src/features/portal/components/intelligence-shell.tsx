import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import { ArrowUpRightIcon, BrainCircuitIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import type { AsyncResult } from '@/lib/use-async'
import { usePortal } from '../context'
import { useActiveBranches } from '../hooks'
import { isPlanError } from '../lib/errors'
import { SelectField } from './form-controls'
import { PageHeader } from './page-header'
import { QueryBoundary } from './query-boundary'

/** Capability del plan Empresarial que habilita la inteligencia comercial. */
export const ANALYTICS_CAPABILITY = 'ADVANCED_REPORTS'

/** Subsecciones de Inteligencia. */
export function IntelligenceTabs() {
  const { storeId } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}/inteligencia`
  const tabs = [
    { to: base, label: 'Reportes', end: true },
    { to: `${base}/clientes`, label: 'Clientes', end: false },
    { to: `${base}/productos`, label: 'Productos', end: false },
    { to: `${base}/se-compran-juntos`, label: 'Se compran juntos', end: false },
    { to: `${base}/proyeccion`, label: 'Proyección', end: false },
  ]
  return (
    // Móvil: píldoras que bajan de línea y crecen hasta llenar cada fila (sin hueco al final). Desde `sm`: pestañas
    // subrayadas en una fila.
    <nav aria-label="Secciones de inteligencia" className="-mt-2 mb-6 flex flex-wrap gap-2 sm:gap-1 sm:border-b sm:border-border">
      {tabs.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.end}
          className={({ isActive }) =>
            cn(
              'inline-flex min-h-11 grow items-center justify-center rounded-full px-4 text-sm font-semibold whitespace-nowrap ring-1 transition-colors',
              'sm:-mb-px sm:grow-0 sm:rounded-none sm:border-b-2 sm:px-3 sm:ring-0',
              isActive
                ? 'bg-foreground text-background ring-foreground sm:border-foreground sm:bg-transparent sm:text-foreground'
                : 'bg-card text-muted-foreground ring-foreground/15 hover:text-foreground sm:border-transparent sm:bg-transparent',
            )
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </nav>
  )
}

/** Tarjeta que explica que la inteligencia comercial es del plan Empresarial. */
export function EnterpriseRequired() {
  const { storeId, can } = usePortal()
  const canSeePlan = can('STORE_READ')
  return (
    <div className="flex flex-col items-start gap-4 rounded-lg bg-concrete p-6 sm:p-8">
      <div className="flex items-center gap-2 font-semibold">
        <BrainCircuitIcon className="size-5" aria-hidden />
        <h2 className="text-xl tracking-tight">Inteligencia comercial es parte del plan Empresarial</h2>
      </div>
      <p className="max-w-prose text-foreground/85">
        Con el plan Empresarial ves cómo evolucionan tus ventas, qué clientes están por dejar de comprar, qué productos proteger o liquidar y cuánto
        podrías vender el próximo mes.
      </p>
      <ul className="max-w-prose list-disc space-y-1 pl-5 text-sm text-foreground/85">
        <li>Reportes por rango, sucursal y categoría, con exportación a CSV.</li>
        <li>Segmentos de clientes con una acción sugerida para cada uno.</li>
        <li>Clasificación ABC/XYZ de productos y stock mínimo sugerido.</li>
        <li>Proyección de ventas del próximo mes.</li>
      </ul>
      {canSeePlan ? (
        <Button className="h-11 px-5" nativeButton={false} render={<Link to={`/portal/${encodeURIComponent(storeId)}/plan`} />}>
          Ver el plan Empresarial
          <ArrowUpRightIcon aria-hidden />
        </Button>
      ) : (
        <p className="text-sm font-semibold">Pídele al propietario del almacén que active el plan Empresarial.</p>
      )}
    </div>
  )
}

function GateSkeleton() {
  return (
    <div role="status">
      <span className="sr-only">Cargando…</span>
      <Skeleton aria-hidden className="h-40 w-full" />
    </div>
  )
}

interface IntelligencePageProps {
  title: string
  description: string
  actions?: ReactNode
  children: ReactNode
}

/**
 * Carcasa de las pantallas de Inteligencia: encabezado, pestañas y la verificación del plan. Sin la capability
 * `ADVANCED_REPORTS` no se monta el contenido (y por tanto no se llama a la API). Es solo presentación: el servidor
 * vuelve a comprobar plan y permiso en cada petición.
 */
export function IntelligencePage({ title, description, actions, children }: IntelligencePageProps) {
  const { subscription, subscriptionError } = usePortal()
  let body: ReactNode = children
  if (!subscription && !subscriptionError) body = <GateSkeleton />
  else if (subscription && !subscription.capabilities.includes(ANALYTICS_CAPABILITY)) body = <EnterpriseRequired />
  return (
    <>
      <title>{`${title} | Inteligencia | Autopartco`}</title>
      <PageHeader title="Inteligencia" description={description} actions={body === children ? actions : undefined} />
      <IntelligenceTabs />
      <h2 className="sr-only">{title}</h2>
      {body}
    </>
  )
}

/** `QueryBoundary` que, si el servidor responde que el plan no incluye la función, muestra la tarjeta del plan. */
export function AnalyticsBoundary<T>(props: {
  result: AsyncResult<T>
  what: string
  skeleton?: ReactNode
  keepPrevious?: boolean
  children: (data: T) => ReactNode
}) {
  if (props.result.status === 'error' && isPlanError(props.result.error)) return <EnterpriseRequired />
  return <QueryBoundary {...props} />
}

interface BranchSelectProps {
  value: string
  onChange: (branchId: string) => void
  id?: string
}

/**
 * Selector de sucursal. Solo aparece si la persona puede ver las sucursales y el almacén tiene más de una activa;
 * con una sola, los reportes ya son de esa sucursal.
 */
export function BranchSelect({ value, onChange, id = 'inteligencia-sucursal' }: BranchSelectProps) {
  const { branches, known } = useActiveBranches()
  if (!known || branches.length < 2) return null
  return (
    <SelectField
      id={id}
      label="Sucursal"
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder="Todas las sucursales"
      options={branches.map((branch) => ({ value: branch.id, label: branch.name }))}
      containerClassName="w-full sm:w-56"
    />
  )
}
