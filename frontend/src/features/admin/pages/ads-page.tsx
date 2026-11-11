import { useState } from 'react'
import { toast } from 'sonner'
import { MegaphoneIcon, MoreHorizontalIcon, PauseIcon, PlayIcon, XCircleIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { TextField } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button, buttonVariants } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { formatCOP, pluralize } from '@/lib/format'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { SelectField, TextAreaField } from '@/features/portal/components/form-controls'
import type { AdCampaign, AdCampaignStatus, AdPlacementPrice } from '@/features/portal/api/ads-api'
import { adErrorMessage, campaignStatusView, formatDay, PLACEMENT_LABEL, PLACEMENT_ORDER, placementLabel, STATUS_FILTERS, STATUS_LABEL } from '@/features/portal/lib/ads'
import { actionErrorMessage } from '@/features/portal/lib/errors'
import { adminAdsApi } from '../api/admin-ads-api'
import { AdminBoundary, AdminPage, AdminSection, FormSheet, SwitchField } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const PREFIX = `${ADMIN_CACHE_PREFIX}ads:`
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

function RejectSheet({ campaign, onClose }: { campaign: AdCampaign | undefined; onClose: () => void }) {
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string>()
  return (
    <FormSheet
      open={campaign !== undefined}
      onOpenChange={(open) => {
        if (!open) {
          setReason('')
          setError(undefined)
          onClose()
        }
      }}
      title={`Rechazar “${campaign?.name ?? 'la campaña'}”`}
      description="El almacén verá el motivo. Si la campaña ya estaba pagada y no había empezado, queda marcada como «reembolso pendiente»: el reembolso es manual."
      submitLabel="Rechazar campaña"
      what="el rechazo"
      onSubmit={async () => {
        if (!campaign) return false
        const text = reason.trim()
        if (!text) {
          setError('Escribe el motivo del rechazo.')
          return false
        }
        if (text.length > 300) {
          setError('Usa máximo 300 caracteres.')
          return false
        }
        await adminAdsApi.reject(campaign.id, text)
        invalidateAsync(PREFIX)
        toast.success('Rechazaste la campaña.')
        setReason('')
        setError(undefined)
      }}
    >
      <TextAreaField id="ad-reject-reason" label="Motivo" maxLength={300} value={reason} error={error} onChange={(event) => setReason(event.target.value)} />
    </FormSheet>
  )
}

function PricesSheet({ open, onOpenChange, prices }: { open: boolean; onOpenChange: (open: boolean) => void; prices: AdPlacementPrice[] }) {
  const [rows, setRows] = useState(() => PLACEMENT_ORDER.map((placement) => {
    const current = prices.find((price) => price.placement === placement)
    return { placement, dailyPrice: current ? String(current.dailyPrice) : '', active: current?.active ?? true }
  }))
  const [errors, setErrors] = useState<Record<string, string>>({})
  return (
    <FormSheet
      open={open}
      onOpenChange={onOpenChange}
      title="Tarifas diarias"
      description="Precio en COP por día y ubicación, con IVA incluido. Los cambios aplican a las campañas que se creen o editen desde ahora."
      submitLabel="Guardar tarifas"
      what="las tarifas"
      onSubmit={async () => {
        const next: Record<string, string> = {}
        const payload: AdPlacementPrice[] = []
        for (const row of rows) {
          const value = Number(row.dailyPrice.replace(',', '.'))
          if (!Number.isFinite(value) || value <= 0) next[row.placement] = 'Escribe un valor mayor que 0.'
          else payload.push({ placement: row.placement, dailyPrice: value, active: row.active })
        }
        setErrors(next)
        if (Object.keys(next).length > 0) return false
        await adminAdsApi.savePrices(payload)
        invalidateAsync(PREFIX)
        toast.success('Actualizaste las tarifas.')
      }}
    >
      {rows.map((row, index) => (
        <div key={row.placement} className="flex flex-col gap-1 border-b border-ap-line pb-4">
          <TextField
            id={`ad-price-${row.placement}`}
            label={PLACEMENT_LABEL[row.placement]}
            type="number"
            inputMode="decimal"
            min={1}
            step="any"
            value={row.dailyPrice}
            error={errors[row.placement]}
            hint="COP por día"
            onChange={(event) => setRows((current) => current.map((item, i) => (i === index ? { ...item, dailyPrice: event.target.value } : item)))}
          />
          <SwitchField
            id={`ad-active-${row.placement}`}
            label="Disponible para vender"
            hint="Si la apagas, los almacenes no pueden elegirla en campañas nuevas."
            checked={row.active}
            onCheckedChange={(checked) => setRows((current) => current.map((item, i) => (i === index ? { ...item, active: checked } : item)))}
          />
        </div>
      ))}
    </FormSheet>
  )
}

/** `/admin/publicidad`: moderación de campañas (rechazar, pausar, reanudar) y tarifas por ubicación. Solo ROOT. */
export default function AdminAdsPage() {
  const [filters, setFilters] = useState<{ status?: AdCampaignStatus; storeId: string }>({ storeId: '' })
  const storeId = UUID.test(filters.storeId.trim()) ? filters.storeId.trim() : undefined
  const storeIdInvalid = filters.storeId.trim() !== '' && !storeId
  const campaigns = useAsync(`${PREFIX}list:${filters.status ?? ''}:${storeId ?? ''}`, () => adminAdsApi.list(filters.status, storeId))
  const prices = useAsync(`${PREFIX}prices`, () => adminAdsApi.prices())
  const [rejecting, setRejecting] = useState<AdCampaign>()
  const [editingPrices, setEditingPrices] = useState(false)
  const [busyId, setBusyId] = useState<string>()

  async function run(campaign: AdCampaign, action: () => Promise<unknown>, success: string) {
    setBusyId(campaign.id)
    try {
      await action()
      invalidateAsync(PREFIX)
      toast.success(success)
    } catch (error) {
      toast.error(adErrorMessage(error, actionErrorMessage(error, 'No pudimos completar la acción. Inténtalo de nuevo.')))
    } finally {
      setBusyId(undefined)
    }
  }

  const columns: DataColumn<AdCampaign>[] = [
    {
      id: 'nombre',
      header: 'Campaña',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div>
          <p className="font-semibold text-ap-ink">{row.name}</p>
          <p className="text-xs text-ap-muted">
            {pluralize(row.productIds.length, 'producto', 'productos')} · almacén <span className="ap-ref">{row.storeId.slice(0, 8)}</span>
          </p>
        </div>
      ),
    },
    { id: 'ubicaciones', header: 'Ubicaciones', cell: (row) => <span className="text-sm">{row.placements.map(placementLabel).join(', ')}</span> },
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
            {row.rejectedReason ? <span className="max-w-56 text-xs text-ap-muted">Motivo: {row.rejectedReason}</span> : null}
            {row.requiresManualRefund ? <span className="max-w-56 text-xs font-medium text-ap-warning">Reembolso manual pendiente</span> : null}
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
        const canReject = ['PENDING_PAYMENT', 'SCHEDULED', 'ACTIVE', 'PAUSED'].includes(row.status)
        const canPause = row.status === 'ACTIVE' || row.status === 'SCHEDULED'
        const canResume = row.status === 'PAUSED'
        if (!canReject && !canPause && !canResume) return null
        return (
          <DropdownMenu>
            <DropdownMenuTrigger aria-label={`Acciones para ${row.name}`} disabled={busyId === row.id} className={buttonVariants({ variant: 'ghost', size: 'icon' })}>
              <MoreHorizontalIcon className="size-5" aria-hidden />
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              {canPause ? (
                <DropdownMenuItem onClick={() => void run(row, () => adminAdsApi.pause(row.id), 'Pausaste la campaña.')}>
                  <PauseIcon aria-hidden />
                  Pausar
                </DropdownMenuItem>
              ) : null}
              {canResume ? (
                <DropdownMenuItem onClick={() => void run(row, () => adminAdsApi.resume(row.id), 'Reanudaste la campaña.')}>
                  <PlayIcon aria-hidden />
                  Reanudar
                </DropdownMenuItem>
              ) : null}
              {canReject ? (
                <DropdownMenuItem variant="destructive" onClick={() => setRejecting(row)}>
                  <XCircleIcon aria-hidden />
                  Rechazar
                </DropdownMenuItem>
              ) : null}
            </DropdownMenuContent>
          </DropdownMenu>
        )
      },
    },
  ]

  return (
    <AdminPage title="Publicidad" label="Comercial" description="Campañas de anuncios de todos los almacenes y tarifas por ubicación. Pausa o rechaza lo que incumpla las reglas.">
      <div className="flex flex-col gap-10">
        <AdminSection
          id="ads-campaigns"
          title="Campañas"
          actions={undefined}
        >
          <div className="mb-5 flex flex-wrap items-start gap-3">
            <SelectField
              id="admin-ads-status"
              label="Estado"
              placeholder="Todos"
              containerClassName="w-52"
              value={filters.status ?? ''}
              options={STATUS_FILTERS.map((value) => ({ value, label: STATUS_LABEL[value].label }))}
              onChange={(event) => setFilters((current) => ({ ...current, status: (event.target.value || undefined) as AdCampaignStatus | undefined }))}
            />
            <TextField
              id="admin-ads-store"
              label="Almacén (id)"
              containerClassName="w-80 max-w-full"
              value={filters.storeId}
              autoComplete="off"
              spellCheck={false}
              hint="Pega el identificador del almacén para filtrar."
              error={storeIdInvalid ? 'No es un identificador válido.' : undefined}
              onChange={(event) => setFilters((current) => ({ ...current, storeId: event.target.value }))}
            />
          </div>
          <AdminBoundary result={campaigns} what="las campañas" keepPrevious>
            {(rows) =>
              rows.length === 0 ? (
                <EmptyState icon={<MegaphoneIcon />} title="No hay campañas con estos filtros" description="Cambia el estado o el almacén para ver otras campañas." />
              ) : (
                <DataTable caption="Campañas de publicidad de todos los almacenes" columns={columns} rows={rows} getRowId={(row) => row.id} defaultSort={{ columnId: 'vigencia', direction: 'desc' }} pageSize={15} />
              )
            }
          </AdminBoundary>
        </AdminSection>

        <AdminSection
          id="ads-prices"
          title="Tarifas por ubicación"
          actions={
            prices.data ? (
              <Button variant="outline" className="h-11 px-4" onClick={() => setEditingPrices(true)}>
                Editar tarifas
              </Button>
            ) : undefined
          }
        >
          <AdminBoundary result={prices} what="las tarifas" keepPrevious>
            {(rows) => (
              <>
                <DataTable
                  caption="Tarifa diaria por ubicación"
                  columns={[
                    { id: 'placement', header: 'Ubicación', rowHeader: true, cell: (row: AdPlacementPrice) => PLACEMENT_LABEL[row.placement] ?? row.placement },
                    { id: 'price', header: 'Tarifa por día', align: 'end', cell: (row: AdPlacementPrice) => <span className="ap-price">{formatCOP(row.dailyPrice)}</span> },
                    { id: 'active', header: 'Estado', cell: (row: AdPlacementPrice) => <StatusBadge tone={row.active ? 'success' : 'neutral'}>{row.active ? 'Disponible' : 'No disponible'}</StatusBadge> },
                  ]}
                  rows={rows}
                  getRowId={(row) => row.placement}
                />
                {editingPrices ? <PricesSheet key={JSON.stringify(rows)} open onOpenChange={setEditingPrices} prices={rows} /> : null}
              </>
            )}
          </AdminBoundary>
        </AdminSection>
      </div>
      <RejectSheet campaign={rejecting} onClose={() => setRejecting(undefined)} />
    </AdminPage>
  )
}
