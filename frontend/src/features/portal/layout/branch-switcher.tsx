import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { usePortal } from '../context'

const ALL = 'todas'

/**
 * Sucursal activa en la barra de contexto (§7.3), junto al almacén: filtro por defecto de resumen, inventario,
 * traslados y analítica. Solo aparece con dos o más sucursales activas; el plan y los cupos siguen siendo del almacén.
 */
export function BranchSwitcher() {
  const { activeBranches, branchId, setBranchId, branchReady } = usePortal()
  if (!branchReady || activeBranches.length < 2) return null

  const items = [{ value: ALL, label: 'Todas las sucursales' }, ...activeBranches.map((branch) => ({ value: branch.id, label: branch.name }))]
  return (
    <>
      <span aria-hidden className="hidden h-5 w-px shrink-0 bg-ap-line sm:block" />
      <div className="flex min-w-0 items-center">
        <Label htmlFor="selector-sucursal" className="sr-only">
          Sucursal
        </Label>
        <Select
          items={items}
          value={branchId ?? ALL}
          onValueChange={(next) => {
            if (typeof next === 'string') setBranchId(next === ALL ? undefined : next)
          }}
        >
          <SelectTrigger id="selector-sucursal" className="h-11 w-full min-w-0 max-w-[14rem] bg-ap-white">
            <SelectValue />
          </SelectTrigger>
          <SelectContent alignItemWithTrigger={false} align="start" className="min-w-56">
            {items.map((item) => (
              <SelectItem key={item.value} value={item.value}>
                {item.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>
    </>
  )
}
