import { Link, NavLink } from 'react-router'
import { ArrowLeftRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { usePortal } from '../context'
import { useActiveBranches } from '../hooks'

/** Mínimo de sucursales activas para poder transferir inventario. */
export const MIN_BRANCHES_TO_TRANSFER = 2

/** Subsecciones de Inventario. «Transferencias» solo aparece con dos o más sucursales activas. */
export function InventoryTabs() {
  const { storeId } = usePortal()
  const { branches, known } = useActiveBranches()
  const base = `/portal/${encodeURIComponent(storeId)}/inventario`
  const tabs = [
    { to: base, label: 'Existencias', end: true },
    { to: `${base}/movimientos`, label: 'Movimientos', end: false },
    ...(known && branches.length >= MIN_BRANCHES_TO_TRANSFER ? [{ to: `${base}/transferencias`, label: 'Transferencias', end: false }] : []),
  ]
  return (
    <nav aria-label="Secciones de inventario" className="-mt-2 mb-6 flex gap-1 overflow-x-auto overflow-y-hidden border-b border-border">
      {tabs.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end={tab.end}
          className={({ isActive }) =>
            cn(
              '-mb-px inline-flex min-h-11 items-center border-b-2 px-3 text-sm font-semibold whitespace-nowrap transition-colors',
              isActive ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
            )
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </nav>
  )
}

/**
 * Aviso cuando el almacén no puede transferir: con una sola sucursal activa, mover inventario entre
 * sucursales es una función del plan Empresarial.
 */
export function TransfersRequirement({ className }: { className?: string }) {
  const { storeId, subscription } = usePortal()
  const enterprise = subscription?.plan.code === 'ENTERPRISE'
  const to = `/portal/${encodeURIComponent(storeId)}/${enterprise ? 'sucursales' : 'plan'}`
  return (
    <div className={cn('flex flex-col items-start gap-3 rounded-lg bg-concrete p-5', className)}>
      <div className="flex items-center gap-2 font-semibold">
        <ArrowLeftRightIcon className="size-5" aria-hidden />
        <p>Transferir inventario entre sucursales</p>
      </div>
      <p className="max-w-prose text-sm text-foreground/85">
        {enterprise
          ? 'Para mover existencias necesitas al menos dos sucursales activas. Agrega otra sucursal y podrás transferir todo el stock o solo los productos que elijas.'
          : 'Para mover existencias necesitas al menos dos sucursales activas, y varias sucursales hacen parte del plan Empresarial. Con él puedes transferir todo el stock o solo los productos que elijas.'}
      </p>
      <Link to={to} className="inline-flex min-h-11 items-center text-sm font-semibold underline underline-offset-4">
        {enterprise ? 'Ir a sucursales' : 'Ver el plan Empresarial'}
      </Link>
    </div>
  )
}
