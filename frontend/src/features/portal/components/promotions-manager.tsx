import { useState } from 'react'
import { toast } from 'sonner'
import { BarChart3Icon, MoreHorizontalIcon, PencilIcon, PlusIcon, TagIcon, Trash2Icon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button, buttonVariants } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useAsync } from '@/lib/use-async'
import type { Promotion, PromotionAudience } from '../api/promotion-types'
import { formatDate } from '../lib/labels'
import type { PromotionConfig } from '../lib/promotion-config'
import { AUDIENCE_LABEL, discountText, phaseOf, PHASE_LABEL, TARGET_LABEL } from '../lib/promotions'
import { ConfirmDialog } from './confirm-dialog'
import { PromotionResultsSheet } from './promotion-results-sheet'
import { PromotionSheet } from './promotion-sheet'
import { QueryBoundary } from './query-boundary'
import { StatusBadge } from './status-badge'

interface PromotionsManagerProps {
  config: PromotionConfig
  /** Abre el formulario de una promoción nueva al montar (enlace desde un segmento de clientes). */
  startCreating?: boolean
  initialAudience?: PromotionAudience
  /** Se llama cuando el formulario inicial se cierra, para limpiar la URL. */
  onStartHandled?: () => void
}

/**
 * Lista de promociones con estado, audiencia, resultados, alta, edición y eliminación. La usan el portal del almacén
 * y la administración con su propio servicio (`PromotionConfig`). Solo muestra lo que devuelve el servidor.
 */
export function PromotionsManager({ config, startCreating = false, initialAudience, onStartHandled }: PromotionsManagerProps) {
  const promotions = useAsync(`${config.cacheKey}:list`, () => config.service.list(config.storeId))
  const [creating, setCreating] = useState(startCreating)
  const [editing, setEditing] = useState<Promotion>()
  const [results, setResults] = useState<Promotion>()
  const [toDelete, setToDelete] = useState<Promotion>()

  function closeCreate(open: boolean) {
    setCreating(open)
    if (!open) onStartHandled?.()
  }

  const create = config.canCreate ? (
    <Button className="h-11 px-4" onClick={() => setCreating(true)}>
      <PlusIcon aria-hidden />
      Nueva promoción
    </Button>
  ) : null

  const columns: DataColumn<Promotion>[] = [
    {
      id: 'nombre',
      header: 'Promoción',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div>
          <p className="font-semibold text-ap-ink">{row.name}</p>
          <p className="text-xs text-ap-muted">{TARGET_LABEL[row.targetType]}</p>
        </div>
      ),
    },
    { id: 'descuento', header: 'Descuento', align: 'end', sortValue: (row) => row.discountValue, cell: (row) => <span className="ap-price">{discountText(row)}</span> },
    {
      id: 'audiencia',
      header: 'Audiencia',
      sortValue: (row) => AUDIENCE_LABEL[row.audience],
      cell: (row) => (row.audience === 'ALL' ? AUDIENCE_LABEL.ALL : <StatusBadge tone="info">{AUDIENCE_LABEL[row.audience]}</StatusBadge>),
    },
    {
      id: 'vigencia',
      header: 'Vigencia',
      sortValue: (row) => row.startsAt,
      cell: (row) => (
        <span className="ap-ref whitespace-nowrap">
          {formatDate(row.startsAt)} al {formatDate(row.endsAt)}
        </span>
      ),
    },
    {
      id: 'estado',
      header: 'Estado',
      sortValue: (row) => PHASE_LABEL[phaseOf(row)].label,
      cell: (row) => {
        const phase = PHASE_LABEL[phaseOf(row)]
        return <StatusBadge tone={phase.tone}>{phase.label}</StatusBadge>
      },
    },
    {
      id: 'acciones',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => (
        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label={`Acciones para ${row.name}`}
            className={buttonVariants({ variant: 'ghost', size: 'icon' })}
          >
            <MoreHorizontalIcon className="size-5" aria-hidden />
          </DropdownMenuTrigger>
          <DropdownMenuContent>
            <DropdownMenuItem onClick={() => setResults(row)}>
              <BarChart3Icon aria-hidden />
              Ver resultados
            </DropdownMenuItem>
            {config.canUpdate ? (
              <DropdownMenuItem onClick={() => setEditing(row)}>
                <PencilIcon aria-hidden />
                Editar
              </DropdownMenuItem>
            ) : null}
            {config.canDelete ? (
              <DropdownMenuItem variant="destructive" onClick={() => setToDelete(row)}>
                <Trash2Icon aria-hidden />
                Eliminar
              </DropdownMenuItem>
            ) : null}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  return (
    <>
      {create && (promotions.data?.length ?? 0) > 0 ? <div className="mb-5 flex justify-end">{create}</div> : null}
      <QueryBoundary result={promotions} what="las promociones">
        {(rows) =>
          rows.length === 0 ? (
            <EmptyState
              icon={<TagIcon />}
              title="Aún no tienes promociones"
              description="Crea un descuento para tus productos y elige quién lo ve: todos tus compradores o un grupo de clientes. Después podrás ver cuánto vendió."
            >
              {create}
            </EmptyState>
          ) : (
            <DataTable
              caption="Promociones"
              columns={columns}
              rows={rows}
              getRowId={(row) => row.id}
              defaultSort={{ columnId: 'vigencia', direction: 'desc' }}
              pageSize={10}
            />
          )
        }
      </QueryBoundary>

      <PromotionSheet config={config} open={creating} onOpenChange={closeCreate} promotion={undefined} initialAudience={initialAudience} />
      <PromotionSheet
        config={config}
        open={editing !== undefined}
        onOpenChange={(open) => !open && setEditing(undefined)}
        promotion={editing}
      />
      <PromotionResultsSheet config={config} promotion={results} onClose={() => setResults(undefined)} />
      <ConfirmDialog
        open={toDelete !== undefined}
        onOpenChange={(open) => !open && setToDelete(undefined)}
        title={`¿Eliminar “${toDelete?.name ?? 'la promoción'}”?`}
        description="Dejará de aplicarse de inmediato y también se perderán sus resultados. Si solo quieres detenerla un tiempo, edítala y desactívala."
        confirmLabel="Eliminar promoción"
        destructive
        onConfirm={async () => {
          if (!toDelete) return
          await config.service.remove(config.storeId, toDelete.id)
          config.invalidate()
          toast.success(`Eliminaste “${toDelete.name}”.`)
        }}
      />
    </>
  )
}
