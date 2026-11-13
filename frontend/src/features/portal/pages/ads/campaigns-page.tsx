import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { BarChart3Icon, CreditCardIcon, MegaphoneIcon, MoreHorizontalIcon, PauseIcon, PencilIcon, PlayIcon, PlusIcon, Trash2Icon, XIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button, buttonVariants } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { formatCOP, pluralize } from '@/lib/format'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { adsApi, type AdCampaign } from '../../api/ads-api'
import { startAdCheckout } from '../../components/ads/start-ad-checkout'
import { ConfirmDialog } from '../../components/confirm-dialog'
import { SelectField } from '../../components/form-controls'
import { PageHeader } from '../../components/page-header'
import { QueryBoundary } from '../../components/query-boundary'
import { StatusBadge } from '../../components/status-badge'
import { usePortal } from '../../context'
import { adErrorMessage, campaignStatusView, formatDay, NO_REFUND_NOTICE, placementLabel, STATUS_FILTERS, STATUS_LABEL } from '../../lib/ads'
import { actionErrorMessage } from '../../lib/errors'

type Confirm = { kind: 'delete' | 'cancel'; campaign: AdCampaign }

/** `/portal/:storeId/publicidad`: campañas del almacén con su estado y las acciones que permite cada estado. */
export default function PortalAdsCampaignsPage() {
  const { storeId, can } = usePortal()
  const [params, setParams] = useSearchParams()
  const statusParam = params.get('estado')
  const status = STATUS_FILTERS.find((candidate) => candidate === statusParam)
  const cachePrefix = `portal:${storeId}:ads:`
  const campaigns = useAsync(`${cachePrefix}list:${status ?? 'all'}`, () => adsApi.list(storeId, status))
  const [busyId, setBusyId] = useState<string>()
  const [confirm, setConfirm] = useState<Confirm>()
  const canWrite = can('STORE_UPDATE')
  const canStats = can('ANALYTICS_READ')
  const base = `/portal/${encodeURIComponent(storeId)}/publicidad`

  async function run(campaign: AdCampaign, action: () => Promise<unknown>, success: string) {
    setBusyId(campaign.id)
    try {
      await action()
      invalidateAsync(cachePrefix)
      toast.success(success)
    } catch (error) {
      toast.error(adErrorMessage(error, actionErrorMessage(error, 'No pudimos completar la acción. Inténtalo de nuevo.')))
    } finally {
      setBusyId(undefined)
    }
  }

  const create = canWrite ? (
    <Button className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/nueva`} />}>
      <PlusIcon aria-hidden />
      Nueva campaña
    </Button>
  ) : null

  const columns: DataColumn<AdCampaign>[] = [
    {
      id: 'nombre',
      header: 'Campaña',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div>
          <p className="font-semibold text-ap-ink">{row.name}</p>
          <p className="text-xs text-ap-muted">{row.productIds.length > 0 ? pluralize(row.productIds.length, 'producto', 'productos') : 'Tu almacén en el mapa'}</p>
        </div>
      ),
    },
    {
      id: 'ubicaciones',
      header: 'Ubicaciones',
      cell: (row) => <span className="text-sm">{row.placements.map(placementLabel).join(', ')}</span>,
    },
    {
      id: 'vigencia',
      header: 'Vigencia',
      sortValue: (row) => row.startsOn,
      cell: (row) => (
        <span className="ap-ref whitespace-nowrap">
          {formatDay(row.startsOn)} al {formatDay(row.endsOn)}
        </span>
      ),
    },
    { id: 'total', header: 'Total', align: 'end', sortValue: (row) => row.totalPrice, cell: (row) => <span className="ap-price">{formatCOP(row.totalPrice)}</span> },
    {
      id: 'estado',
      header: 'Estado',
      sortValue: (row) => campaignStatusView(row).label,
      cell: (row) => {
        const view = campaignStatusView(row)
        return (
          <div className="flex flex-col items-start gap-1">
            <StatusBadge tone={view.tone}>{view.label}</StatusBadge>
            {row.status === 'REJECTED' && row.rejectedReason ? <span className="max-w-56 text-xs text-ap-muted">Motivo: {row.rejectedReason}</span> : null}
            {row.requiresManualRefund ? <span className="max-w-56 text-xs text-ap-muted">Reembolso pendiente: lo gestionamos manualmente.</span> : null}
          </div>
        )
      },
    },
    {
      id: 'acciones',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => {
        const isDraft = row.status === 'DRAFT'
        const stopped = row.status === 'PAUSED'
        const adminPause = stopped && row.pausedBy === 'ADMIN'
        const live = row.status === 'ACTIVE' || row.status === 'SCHEDULED'
        const showStats = canStats && row.paidAt !== null
        const hasWrite = canWrite && (isDraft || live || (stopped && !adminPause) || stopped)
        if (!showStats && !hasWrite) return null
        return (
          <DropdownMenu>
            <DropdownMenuTrigger
              aria-label={`Acciones para ${row.name}`}
              disabled={busyId === row.id}
              className={buttonVariants({ variant: 'ghost', size: 'icon' })}
            >
              <MoreHorizontalIcon className="size-5" aria-hidden />
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              {showStats ? (
                <DropdownMenuItem render={<Link to={`${base}/${encodeURIComponent(row.id)}/estadisticas`} />}>
                  <BarChart3Icon aria-hidden />
                  Estadísticas
                </DropdownMenuItem>
              ) : null}
              {canWrite && isDraft ? (
                <>
                  <DropdownMenuItem
                    onClick={() =>
                      void run(row, () => startAdCheckout(storeId, row), 'Te llevamos a Wompi para pagar.')
                    }
                  >
                    <CreditCardIcon aria-hidden />
                    Pagar
                  </DropdownMenuItem>
                  <DropdownMenuItem render={<Link to={`${base}/${encodeURIComponent(row.id)}/editar`} />}>
                    <PencilIcon aria-hidden />
                    Editar
                  </DropdownMenuItem>
                </>
              ) : null}
              {canWrite && row.status === 'ACTIVE' ? (
                <DropdownMenuItem onClick={() => void run(row, () => adsApi.pause(storeId, row.id), 'Pausaste la campaña.')}>
                  <PauseIcon aria-hidden />
                  Pausar
                </DropdownMenuItem>
              ) : null}
              {canWrite && stopped && !adminPause ? (
                <DropdownMenuItem onClick={() => void run(row, () => adsApi.resume(storeId, row.id), 'Reanudaste la campaña.')}>
                  <PlayIcon aria-hidden />
                  Reanudar
                </DropdownMenuItem>
              ) : null}
              {canWrite && (live || stopped) ? (
                <>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem variant="destructive" onClick={() => setConfirm({ kind: 'cancel', campaign: row })}>
                    <XIcon aria-hidden />
                    Cancelar campaña
                  </DropdownMenuItem>
                </>
              ) : null}
              {canWrite && isDraft ? (
                <>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem variant="destructive" onClick={() => setConfirm({ kind: 'delete', campaign: row })}>
                    <Trash2Icon aria-hidden />
                    Eliminar
                  </DropdownMenuItem>
                </>
              ) : null}
            </DropdownMenuContent>
          </DropdownMenu>
        )
      },
    },
  ]

  return (
    <>
      <title>Publicidad | Autopartco</title>
      <PageHeader
        label="Publicidad"
        title="Campañas"
        description="Muestra tus productos con la etiqueta «Patrocinado» en búsqueda, inicio, categorías y detalle de producto. Pagas por los días que publicas, no por clic."
        actions={create}
      />

      <div className="mb-5 max-w-xs">
        <SelectField
          id="ads-status"
          label="Estado"
          value={status ?? ''}
          placeholder="Todas"
          options={STATUS_FILTERS.map((value) => ({ value, label: STATUS_LABEL[value].label }))}
          onChange={(event) =>
            setParams(
              (current) => {
                const next = new URLSearchParams(current)
                if (event.target.value) next.set('estado', event.target.value)
                else next.delete('estado')
                return next
              },
              { replace: true },
            )
          }
        />
      </div>

      <QueryBoundary result={campaigns} what="tus campañas" keepPrevious>
        {(rows) =>
          rows.length === 0 ? (
            <EmptyState
              icon={<MegaphoneIcon />}
              title={status ? 'No hay campañas con este estado' : 'Aún no tienes campañas'}
              description={
                status
                  ? 'Cambia el filtro para ver las demás campañas.'
                  : 'Crea una campaña, elige productos publicados, ubicaciones y fechas, y paga por Wompi. Se activa cuando el pago se aprueba.'
              }
            >
              {status ? (
                <Button variant="outline" className="h-11 px-4" onClick={() => setParams({}, { replace: true })}>
                  Quitar filtro
                </Button>
              ) : (
                create
              )}
            </EmptyState>
          ) : (
            <>
              <DataTable
                caption="Campañas de publicidad"
                columns={columns}
                rows={rows}
                getRowId={(row) => row.id}
                defaultSort={{ columnId: 'vigencia', direction: 'desc' }}
                pageSize={10}
              />
              <p className="mt-4 max-w-prose text-xs text-ap-muted">{NO_REFUND_NOTICE}</p>
            </>
          )
        }
      </QueryBoundary>

      <ConfirmDialog
        open={confirm !== undefined}
        onOpenChange={(open) => !open && setConfirm(undefined)}
        title={confirm?.kind === 'delete' ? `¿Eliminar “${confirm.campaign.name}”?` : `¿Cancelar “${confirm?.campaign.name ?? 'la campaña'}”?`}
        description={
          confirm?.kind === 'delete'
            ? 'Es un borrador sin pagar: se elimina y no se puede recuperar.'
            : 'Dejará de mostrarse y no se puede reanudar. No hay reembolso automático de lo pagado.'
        }
        confirmLabel={confirm?.kind === 'delete' ? 'Eliminar campaña' : 'Cancelar campaña'}
        destructive
        onConfirm={async () => {
          if (!confirm) return
          const { kind, campaign } = confirm
          if (kind === 'delete') await adsApi.remove(storeId, campaign.id)
          else await adsApi.cancel(storeId, campaign.id)
          invalidateAsync(cachePrefix)
          toast.success(kind === 'delete' ? 'Eliminaste el borrador.' : 'Cancelaste la campaña.')
        }}
      />
    </>
  )
}
