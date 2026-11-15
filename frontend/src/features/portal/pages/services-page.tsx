import { useState } from 'react'
import { toast } from 'sonner'
import { MoreHorizontalIcon, PencilIcon, PlusIcon, PowerOffIcon, WrenchIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button, buttonVariants } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { serviceCategoryLabel } from '@/features/services/lib/categories'
import { formatCOP } from '@/lib/format'
import { getStoreServicesService, type StoreServiceItem } from '../api/store-services-service'
import { ConfirmDialog } from '../components/confirm-dialog'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { ServiceSheet } from '../components/service-sheet'
import { StatusBadge } from '../components/status-badge'
import { usePortal } from '../context'
import { focusMain, useActiveBranches, useStoreQuery } from '../hooks'

function ServicesView({ services, onCreate }: { services: StoreServiceItem[]; onCreate: () => void }) {
  const { storeId, can, invalidateStore } = usePortal()
  const { branches, known } = useActiveBranches()
  const editable = can('STORE_UPDATE')
  const [editingId, setEditingId] = useState<string>()
  const [toDeactivate, setToDeactivate] = useState<StoreServiceItem>()
  const editing = services.find((service) => service.id === editingId)

  const columns: DataColumn<StoreServiceItem>[] = [
    {
      id: 'nombre',
      header: 'Servicio',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <span className="flex flex-col">
          <span className="font-semibold text-ap-ink">{row.name}</span>
          <span className="text-xs text-ap-muted">{serviceCategoryLabel(row.category)}</span>
        </span>
      ),
    },
    {
      id: 'precio',
      header: 'Precio desde',
      align: 'end',
      sortValue: (row) => row.priceFrom ?? -1,
      cell: (row) => (row.priceFrom != null ? <span className="ap-num">{formatCOP(row.priceFrom)}</span> : <span className="text-ap-muted">A cotizar</span>),
    },
    {
      id: 'agenda',
      header: 'Agenda en línea',
      sortValue: (row) => (row.bookingEnabled ? 1 : 0),
      cell: (row) => (row.bookingEnabled ? <StatusBadge tone="success">Activa · franjas de {row.slotMinutes} min</StatusBadge> : <StatusBadge>Desactivada</StatusBadge>),
    },
    {
      id: 'sucursales',
      header: 'Sucursales',
      align: 'end',
      sortValue: (row) => row.branchIds.length,
      cell: (row) => <span className="ap-num">{row.branchIds.length}</span>,
    },
    {
      id: 'estado',
      header: 'Estado',
      sortValue: (row) => (row.active ? 1 : 0),
      cell: (row) => (row.active ? <StatusBadge tone="success">Publicado</StatusBadge> : <StatusBadge>Desactivado</StatusBadge>),
    },
    ...(editable
      ? [
          {
            id: 'acciones',
            header: 'Acciones',
            actions: true,
            align: 'end' as const,
            cell: (row: StoreServiceItem) => (
              <DropdownMenu>
                <DropdownMenuTrigger aria-label={`Acciones para ${row.name}`} className={buttonVariants({ variant: 'ghost', size: 'icon' })}>
                  <MoreHorizontalIcon className="size-5" aria-hidden />
                </DropdownMenuTrigger>
                <DropdownMenuContent>
                  <DropdownMenuItem onClick={() => setEditingId(row.id)}>
                    <PencilIcon aria-hidden />
                    Editar
                  </DropdownMenuItem>
                  {row.active ? (
                    <DropdownMenuItem variant="destructive" onClick={() => setToDeactivate(row)}>
                      <PowerOffIcon aria-hidden />
                      Desactivar
                    </DropdownMenuItem>
                  ) : null}
                </DropdownMenuContent>
              </DropdownMenu>
            ),
          },
        ]
      : []),
  ]

  return (
    <div className="flex flex-col gap-5">
      {services.length === 0 ? (
        <EmptyState icon={<WrenchIcon />} title="Aún no tienes servicios" description="Publica el primero para que los compradores lo encuentren y puedan pedir cita.">
          {editable ? (
            <Button className="h-11 px-5" onClick={onCreate}>
              <PlusIcon aria-hidden />
              Agregar servicio
            </Button>
          ) : null}
        </EmptyState>
      ) : (
        <DataTable caption="Servicios del almacén" columns={columns} rows={services} getRowId={(row) => row.id} defaultSort={{ columnId: 'nombre', direction: 'asc' }} pageSize={10} />
      )}
      <ServiceSheet open={editing !== undefined} onOpenChange={(open) => !open && setEditingId(undefined)} service={editing} branches={branches} branchesKnown={known} />
      <ConfirmDialog
        open={toDeactivate !== undefined}
        onOpenChange={(open) => !open && setToDeactivate(undefined)}
        title={`¿Desactivar ${toDeactivate?.name ?? 'el servicio'}?`}
        description="Dejará de aparecer en el marketplace y no recibirá citas nuevas. Las citas ya pedidas se conservan."
        confirmLabel="Desactivar servicio"
        destructive
        onConfirm={async () => {
          if (!toDeactivate) return
          await (await getStoreServicesService()).deactivate(storeId, toDeactivate.id)
          invalidateStore()
          toast.success(`Desactivaste ${toDeactivate.name}.`)
          focusMain()
        }}
      />
    </div>
  )
}

/** `/portal/:storeId/servicios`: servicios del almacén con imagen, agenda en línea, franja y sucursales. */
export default function PortalServicesPage() {
  const { can } = usePortal()
  const services = useStoreQuery('services', async (id) => (await getStoreServicesService()).list(id))
  const { branches, known } = useActiveBranches()
  const [creating, setCreating] = useState(false)

  return (
    <>
      <title>Servicios | Autopartco</title>
      <PageHeader
        title="Servicios"
        description="Lo que haces en tu taller: precio de referencia, agenda en línea y las sucursales donde lo prestas."
        actions={
          can('STORE_UPDATE') ? (
            <Button className="h-11 px-4" onClick={() => setCreating(true)}>
              <PlusIcon aria-hidden />
              Agregar servicio
            </Button>
          ) : null
        }
      />
      <QueryBoundary result={services} what="tus servicios">
        {(rows) => <ServicesView services={rows} onCreate={() => setCreating(true)} />}
      </QueryBoundary>
      <ServiceSheet open={creating} onOpenChange={setCreating} service={undefined} branches={branches} branchesKnown={known} />
    </>
  )
}
