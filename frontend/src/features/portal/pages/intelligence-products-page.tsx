import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PackageSearchIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP, formatNumber, pluralize } from '@/lib/format'
import { getAnalyticsService } from '../api/analytics-service'
import { getInventoryService } from '../api/inventory-service'
import type { ProductAction, ProductInsight, ProductReport } from '../api/analytics-types'
import { SelectField } from '../components/form-controls'
import { AnalyticsBoundary, BranchSelect, IntelligencePage } from '../components/intelligence-shell'
import { usePortal } from '../context'
import { branchParamValue, useActiveBranches, useBranchSearchParam, useStoreQuery } from '../hooks'
import { ABC_INFO, ACTION_INFO, ACTION_ORDER, XYZ_INFO } from '../lib/analytics'
import { inventoryErrorMessage } from '../lib/inventory'

const ABC_OPTIONS = Object.entries(ABC_INFO).map(([value, info]) => ({ value, label: `Clase ${info.label}` }))
const XYZ_OPTIONS = [...Object.entries(XYZ_INFO).map(([value, info]) => ({ value, label: `Clase ${info.label}` })), { value: 'none', label: 'Sin clasificar' }]
const ACTION_OPTIONS = ACTION_ORDER.map((value) => ({ value, label: ACTION_INFO[value].label }))

function ProductsSkeleton() {
  return (
    <div role="status">
      <span className="sr-only">Cargando los productos…</span>
      <div aria-hidden className="flex flex-col gap-3">
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-14" />
        ))}
      </div>
    </div>
  )
}

function Guide() {
  return (
    <details className="border-y border-ap-line py-1 text-sm">
      <summary className="inline-flex min-h-11 cursor-pointer items-center font-semibold text-ap-ink">¿Qué significan las clases y las acciones?</summary>
      <div className="mt-2 grid gap-5 pb-3 md:grid-cols-3">
        <div>
          <h3 className="font-semibold text-ap-ink">Clase ABC (por ingresos)</h3>
          <dl className="mt-2 flex flex-col gap-1.5">
            {Object.entries(ABC_INFO).map(([code, info]) => (
              <div key={code}>
                <dt className="inline font-semibold">{info.label}: </dt>
                <dd className="inline text-ap-ink">{info.description}</dd>
              </div>
            ))}
          </dl>
        </div>
        <div>
          <h3 className="font-semibold text-ap-ink">Clase XYZ (por regularidad)</h3>
          <dl className="mt-2 flex flex-col gap-1.5">
            {Object.entries(XYZ_INFO).map(([code, info]) => (
              <div key={code}>
                <dt className="inline font-semibold">{info.label}: </dt>
                <dd className="inline text-ap-ink">{info.description}</dd>
              </div>
            ))}
          </dl>
          <p className="mt-1.5 text-ap-ink">Sin clasificar: aún no tiene ventas en el periodo.</p>
        </div>
        <div>
          <h3 className="font-semibold text-ap-ink">Acción sugerida</h3>
          <dl className="mt-2 flex flex-col gap-1.5">
            {ACTION_ORDER.map((code) => (
              <div key={code}>
                <dt className="inline font-semibold">{ACTION_INFO[code].label}: </dt>
                <dd className="inline text-ap-ink">{ACTION_INFO[code].description}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>
      <p className="mt-1 pb-3 text-ap-ink">
        La cobertura son los días que duran tus existencias al ritmo de venta actual. El stock mínimo sugerido cubre 7 días de reposición más un stock de seguridad según qué tanto varían las ventas (mayor en los productos de clase A).
      </p>
    </details>
  )
}

interface ApplyProps {
  branchId: string
  busyId: string | undefined
  canUpdate: boolean
  onApply: (item: ProductInsight) => void
}

function columns({ branchId, busyId, canUpdate, onApply }: ApplyProps): DataColumn<ProductInsight>[] {
  return [
    {
      id: 'producto',
      header: 'Producto',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div className="min-w-0">
          <p className="font-semibold text-ap-ink">{row.name}</p>
          <p className="ap-ref text-ap-muted">{row.sku ?? 'Sin SKU'}</p>
        </div>
      ),
    },
    {
      id: 'clase',
      header: 'Clase',
      sortValue: (row) => `${row.abcClass}${row.xyzClass ?? 'Ñ'}`,
      cell: (row) => (
        <span title={`${ABC_INFO[row.abcClass].description}${row.xyzClass ? ` ${XYZ_INFO[row.xyzClass].description}` : ''}`}>
          <strong>{row.abcClass}</strong>
          {row.xyzClass ? ` · ${row.xyzClass}` : <span className="text-ap-muted"> · sin clasificar</span>}
        </span>
      ),
    },
    {
      id: 'ventas',
      header: 'Ventas',
      align: 'end',
      sortValue: (row) => row.revenue,
      cell: (row) => (
        <div>
          <p>{formatCOP(row.revenue)}</p>
          <p className="text-xs text-ap-muted">{pluralize(row.units, 'unidad', 'unidades')}</p>
        </div>
      ),
    },
    { id: 'stock', header: 'Existencias', align: 'end', sortValue: (row) => row.stock, cell: (row) => formatNumber(row.stock) },
    {
      id: 'cobertura',
      header: 'Cobertura',
      align: 'end',
      sortValue: (row) => row.coverageDays ?? Number.MAX_SAFE_INTEGER,
      cell: (row) => (row.coverageDays === null ? <span className="text-ap-muted">Sin ventas</span> : pluralize(row.coverageDays, 'día', 'días')),
    },
    {
      id: 'minimo',
      header: 'Stock mínimo',
      align: 'end',
      sortValue: (row) => row.currentMinStock,
      cell: (row) => (
        <div>
          <p>Actual: {formatNumber(row.currentMinStock)}</p>
          <p className="text-xs text-ap-muted">{row.suggestedMinStock === null ? 'Sugerido: sin ventas' : `Sugerido: ${formatNumber(row.suggestedMinStock)}`}</p>
        </div>
      ),
    },
    {
      id: 'accion',
      header: 'Acción sugerida',
      sortValue: (row) => ACTION_ORDER.indexOf(row.action),
      cell: (row) => (
        <div className="min-w-0 max-w-64">
          <p className="font-semibold">{ACTION_INFO[row.action].label}</p>
          <p className="text-xs text-ap-muted">{ACTION_INFO[row.action].description}</p>
        </div>
      ),
    },
    ...(canUpdate
      ? [
          {
            id: 'aplicar',
            header: 'Aplicar',
            actions: true,
            align: 'end' as const,
            cell: (row: ProductInsight) =>
              row.suggestedMinStock === null || (branchId !== '' && row.suggestedMinStock === row.currentMinStock) ? null : (
                <Button variant="outline" className="h-11 px-3" disabled={busyId === row.productId} onClick={() => onApply(row)}>
                  Aplicar mínimo sugerido
                  <span className="sr-only"> de {row.name}: {row.suggestedMinStock}</span>
                </Button>
              ),
          },
        ]
      : []),
  ]
}

function ProductsView({ report, filters, apply }: { report: ProductReport; filters: { abc: string; xyz: string; action: string }; apply: ApplyProps }) {
  const shown = useMemo(
    () =>
      report.products.filter(
        (item) =>
          (!filters.abc || item.abcClass === filters.abc) &&
          (!filters.xyz || (filters.xyz === 'none' ? item.xyzClass === null : item.xyzClass === filters.xyz)) &&
          (!filters.action || item.action === (filters.action as ProductAction)),
      ),
    [report.products, filters.abc, filters.xyz, filters.action],
  )
  const cols = columns(apply)

  if (report.products.length === 0) {
    return (
      <EmptyState
        icon={<PackageSearchIcon />}
        title="Aún no hay productos para analizar"
        description="Publica productos con existencias y, cuando tengan ventas, los clasificaremos y te diremos qué hacer con cada uno."
      />
    )
  }
  if (shown.length === 0) {
    return <EmptyState icon={<SearchXIcon />} title="Ningún producto coincide con los filtros" description="Prueba con otra clase o quita algún filtro." />
  }
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-ap-muted" role="status">
        {pluralize(shown.length, 'producto', 'productos')}. Ventas de los últimos {formatNumber(report.windowDays)} días.
      </p>
      <DataTable
        caption="Clasificación y acciones sugeridas por producto"
        columns={cols}
        rows={shown}
        getRowId={(row) => row.productId}
        defaultSort={{ columnId: 'ventas', direction: 'desc' }}
        pageSize={15}
      />
    </div>
  )
}

/** `/portal/:storeId/inteligencia/productos`: clases ABC/XYZ, cobertura y stock mínimo sugerido. */
export default function IntelligenceProductsPage() {
  const { storeId, can, invalidateStore } = usePortal()
  const { branches, known } = useActiveBranches()
  const [params, setParams] = useSearchParams()
  const [busyId, setBusyId] = useState<string>()
  const [needBranch, setNeedBranch] = useState(false)

  const filters = { abc: params.get('abc') ?? '', xyz: params.get('xyz') ?? '', action: params.get('accion') ?? '' }
  const branchId = useBranchSearchParam()
  // Con una sola sucursal activa no hay nada que elegir: es esa.
  const targetBranch = branchId || (known && branches.length === 1 ? (branches[0]?.id ?? '') : '')

  const result = useStoreQuery(`analytics:products:${branchId}`, async (id) => (await getAnalyticsService()).products(id, branchId || undefined))

  function set(name: string, value: string) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        if (value) next.set(name, value)
        else next.delete(name)
        return next
      },
      { replace: true },
    )
    if (name === 'sucursal') setNeedBranch(false)
  }

  async function applySuggested(item: ProductInsight) {
    if (item.suggestedMinStock === null) return
    if (!targetBranch) {
      setNeedBranch(true)
      document.getElementById('inteligencia-sucursal')?.focus()
      return
    }
    setBusyId(item.productId)
    try {
      await (await getInventoryService()).setMinStock(storeId, targetBranch, item.productId, item.suggestedMinStock)
      invalidateStore()
      toast.success(`Stock mínimo de “${item.name}”: ${formatNumber(item.suggestedMinStock)}. Ya se usa en las alertas de stock bajo.`)
    } catch (error) {
      toast.error(inventoryErrorMessage(error, 'No pudimos aplicar el stock mínimo. Inténtalo de nuevo.'))
    } finally {
      setBusyId(undefined)
    }
  }

  const apply: ApplyProps = { branchId, busyId, canUpdate: can('PRODUCT_UPDATE'), onApply: (item) => void applySuggested(item) }

  return (
    <IntelligencePage title="Productos" description="Qué productos sostienen tus ventas, cuánto te duran las existencias y qué hacer con cada uno.">
      <div className="flex flex-col gap-5">
        <Guide />
        <form role="search" aria-label="Filtros de productos" className="flex flex-col flex-wrap gap-3 sm:flex-row sm:items-end" onSubmit={(event) => event.preventDefault()}>
          <SelectField id="prod-abc" label="Clase ABC" value={filters.abc} onChange={(event) => set('abc', event.target.value)} placeholder="Todas" options={ABC_OPTIONS} containerClassName="w-full sm:w-40" />
          <SelectField id="prod-xyz" label="Clase XYZ" value={filters.xyz} onChange={(event) => set('xyz', event.target.value)} placeholder="Todas" options={XYZ_OPTIONS} containerClassName="w-full sm:w-44" />
          <SelectField id="prod-accion" label="Acción sugerida" value={filters.action} onChange={(event) => set('accion', event.target.value)} placeholder="Todas" options={ACTION_OPTIONS} containerClassName="w-full sm:w-52" />
          <BranchSelect value={branchId} onChange={(value) => set('sucursal', branchParamValue(value))} />
        </form>
        {needBranch ? (
          <p role="alert" className="rounded-md border border-ap-warning/40 bg-ap-warning-soft p-4 text-sm font-medium text-ap-ink">
            {known
              ? 'Elige una sucursal en el filtro «Sucursal» para aplicar el stock mínimo: cada sucursal tiene su propio mínimo.'
              : 'Para aplicar el stock mínimo necesitas poder ver las sucursales del almacén. Pídele al propietario que revise tus permisos.'}
          </p>
        ) : null}
        <AnalyticsBoundary result={result} what="los productos" keepPrevious skeleton={<ProductsSkeleton />}>
          {(report) => <ProductsView report={report} filters={filters} apply={apply} />}
        </AnalyticsBoundary>
      </div>
    </IntelligencePage>
  )
}
