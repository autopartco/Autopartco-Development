import { useMemo, useState } from 'react'
import { BranchMap } from '@/features/marketplace/components/store/branch-map'
import type { StoreBranch } from '@/features/marketplace/api/types'
import type { ServiceSummary } from '../api/types'

/** Una sede por punto: varios servicios de la misma sede comparten marcador. */
function toBranches(items: ServiceSummary[]): StoreBranch[] {
  const byId = new Map<string, StoreBranch>()
  for (const item of items) {
    const { branch, store } = item
    if (!branch?.point || byId.has(branch.id)) continue
    byId.set(branch.id, {
      id: branch.id,
      name: store ? `${store.name} · ${branch.name}` : branch.name,
      address: branch.address ?? '',
      city: branch.city,
      point: branch.point,
      serviceIds: [],
    })
  }
  return [...byId.values()]
}

interface ServiceMapProps {
  items: ServiceSummary[]
  activeBranchId?: string
  onSelect: (branchId: string) => void
  className?: string
}

/**
 * Mapa de las sedes de los resultados (MapLibre de `features/location`, vía `BranchMap`): no se descarga hasta que el
 * mapa entra en pantalla. Es un complemento de la lista: si no carga o ninguna sede tiene coordenadas, lo dice y la
 * lista sigue completa.
 */
export function ServiceMap({ items, activeBranchId, onSelect, className }: ServiceMapProps) {
  const branches = useMemo(() => toBranches(items), [items])
  const [failed, setFailed] = useState(false)

  if (branches.length === 0 || failed) {
    return (
      <p role="status" className="rounded-lg border border-ap-line bg-ap-paper px-4 py-3 text-sm text-ap-muted">
        {failed
          ? 'No pudimos cargar el mapa. La lista tiene la dirección y la ruta de cada sede.'
          : 'Las sedes de estos resultados aún no tienen ubicación en el mapa. Usa la lista para ver la dirección de cada una.'}
      </p>
    )
  }
  return (
    <div className="flex flex-col gap-2">
      <BranchMap branches={branches} activeId={activeBranchId} onSelect={onSelect} onFail={() => setFailed(true)} className={className} />
      <p className="text-xs text-ap-muted tabular-nums">
        {branches.length === 1 ? '1 sede' : `${branches.length} sedes`} con ubicación en esta página de resultados. Guíate por la dirección de cada sede.
      </p>
    </div>
  )
}
