import type { ReactNode } from 'react'
import { Link, NavLink } from 'react-router'
import { ArrowUpRightIcon } from 'lucide-react'
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
    // Pestañas subrayadas en una fila; en móvil la fila se desplaza en horizontal (sin píldoras). La activa lleva
    // la línea de acento roja (§3.5).
    <nav aria-label="Secciones de inteligencia" className="-mt-2 mb-6 overflow-x-auto border-b border-ap-line">
      <ul className="flex w-max gap-1">
        {tabs.map((tab) => (
          <li key={tab.to}>
            <NavLink
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                cn(
                  '-mb-px inline-flex min-h-11 items-center border-b-2 px-3 text-sm font-semibold whitespace-nowrap transition-colors duration-150',
                  isActive ? 'border-ap-red text-ap-ink' : 'border-transparent text-ap-muted hover:border-ap-line-strong hover:text-ap-ink',
                )
              }
            >
              {tab.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

/** Explica que la inteligencia comercial es del plan Empresarial (sección abierta, sin tarjeta). */
export function EnterpriseRequired() {
  const { storeId, can } = usePortal()
  const canSeePlan = can('STORE_READ')
  return (
    <section className="flex flex-col items-start gap-4 border-l-2 border-ap-graphite py-1 pl-5">
      <p className="ap-index">Plan Empresarial</p>
      <h2 className="text-block-title text-ap-ink">Inteligencia comercial es parte del plan Empresarial</h2>
      <p className="max-w-prose text-ap-ink">
        Con el plan Empresarial ves cómo evolucionan tus ventas, qué clientes están por dejar de comprar, qué productos proteger o liquidar y cuánto
        podrías vender el próximo mes.
      </p>
      <ul className="max-w-prose list-disc space-y-1 pl-5 text-sm text-ap-ink">
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
        <p className="text-sm font-semibold text-ap-ink">Pídele al propietario del almacén que active el plan Empresarial.</p>
      )}
    </section>
  )
}

function GateSkeleton() {
  return (
    <div role="status" className="flex flex-col gap-3">
      <span className="sr-only">Comprobando tu plan…</span>
      <Skeleton aria-hidden className="h-6 w-64" />
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
