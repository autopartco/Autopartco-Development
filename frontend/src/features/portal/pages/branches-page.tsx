import { useState } from 'react'
import { toast } from 'sonner'
import { MapPinIcon, MoreHorizontalIcon, PencilIcon, PlusIcon, PowerOffIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { getBranchService } from '../api/branch-service'
import type { Branch } from '../api/types'
import { BranchSheet } from '../components/branch-sheet'
import { ConfirmDialog } from '../components/confirm-dialog'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { UsageMeter } from '../components/usage-meter'
import { usePortal } from '../context'
import { focusMain, useStoreQuery } from '../hooks'

function BranchesView({ branches }: { branches: Branch[] }) {
  const { storeId, can, subscription, invalidateStore } = usePortal()
  const [editing, setEditing] = useState<Branch>()
  const [sheetOpen, setSheetOpen] = useState(false)
  const [toDeactivate, setToDeactivate] = useState<Branch>()
  const usage = subscription?.usage.branches

  const columns: DataColumn<Branch>[] = [
    {
      id: 'nombre',
      header: 'Sucursal',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => <span className="font-semibold">{row.name}</span>,
    },
    {
      id: 'direccion',
      header: 'Dirección',
      cell: (row) =>
        row.location ? (
          <span>
            {row.location.addressLine}
            {row.location.addressComplement ? `, ${row.location.addressComplement}` : ''}
          </span>
        ) : (
          '—'
        ),
    },
    {
      id: 'ciudad',
      header: 'Ciudad',
      sortValue: (row) => row.location?.city ?? '',
      cell: (row) => (row.location ? `${row.location.city}, ${row.location.department}` : '—'),
    },
    { id: 'telefono', header: 'Teléfono', cell: (row) => <span className="tabular-nums">{row.numberPhone}</span> },
    {
      id: 'estado',
      header: 'Estado',
      sortValue: (row) => row.state,
      cell: (row) => (row.state === 'ACTIVE' ? <StatusBadge tone="success">Activa</StatusBadge> : <StatusBadge>Inactiva</StatusBadge>),
    },
    ...(can('BRANCH_UPDATE') || can('BRANCH_DELETE')
      ? [
          {
            id: 'acciones',
            header: 'Acciones',
            actions: true,
            align: 'end' as const,
            cell: (row: Branch) => (
              <DropdownMenu>
                <DropdownMenuTrigger
                  aria-label={`Acciones para ${row.name}`}
                  className="inline-flex size-10 items-center justify-center rounded-lg outline-none hover:bg-concrete data-popup-open:bg-concrete pointer-coarse:size-11"
                >
                  <MoreHorizontalIcon className="size-5" aria-hidden />
                </DropdownMenuTrigger>
                <DropdownMenuContent>
                  {can('BRANCH_UPDATE') ? (
                    <DropdownMenuItem
                      onClick={() => {
                        setEditing(row)
                        setSheetOpen(true)
                      }}
                    >
                      <PencilIcon aria-hidden />
                      Editar
                    </DropdownMenuItem>
                  ) : null}
                  {can('BRANCH_DELETE') && row.state === 'ACTIVE' ? (
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
      {usage ? (
        <UsageMeter
          capacity="BRANCHES"
          used={usage.used}
          limit={usage.limit}
          upgradeTo={`/portal/${encodeURIComponent(storeId)}/plan`}
          className="max-w-md rounded-lg bg-card p-4 ring-1 ring-foreground/10"
        />
      ) : null}
      <DataTable caption="Sucursales del almacén" columns={columns} rows={branches} getRowId={(row) => row.id} defaultSort={{ columnId: 'nombre', direction: 'asc' }} />

      <BranchSheet open={sheetOpen} onOpenChange={setSheetOpen} branch={editing} />
      <ConfirmDialog
        open={toDeactivate !== undefined}
        onOpenChange={(open) => !open && setToDeactivate(undefined)}
        title={`¿Desactivar ${toDeactivate?.name ?? 'la sucursal'}?`}
        description="Dejará de despachar pedidos y no podrás volver a activarla desde el portal. Sus productos y pedidos anteriores se conservan."
        confirmLabel="Desactivar sucursal"
        destructive
        onConfirm={async () => {
          if (!toDeactivate) return
          await (await getBranchService()).deactivate(storeId, toDeactivate.id)
          invalidateStore()
          toast.success(`Desactivaste ${toDeactivate.name}.`)
          focusMain()
        }}
      />
    </div>
  )
}

/** `/portal/:storeId/sucursales`: listado, alta y edición en panel lateral, desactivación con confirmación. */
export default function PortalBranchesPage() {
  const { can } = usePortal()
  const branches = useStoreQuery('branches', async (id) => (await getBranchService()).list(id))
  const [creating, setCreating] = useState(false)

  return (
    <>
      <title>Sucursales | Autopartco</title>
      <PageHeader
        title="Sucursales"
        description="Los puntos desde los que despachas. Cada producto tiene su precio y sus existencias por sucursal."
        actions={
          can('BRANCH_CREATE') ? (
            <Button className="h-11 px-4" onClick={() => setCreating(true)}>
              <PlusIcon aria-hidden />
              Agregar sucursal
            </Button>
          ) : null
        }
      />
      <QueryBoundary result={branches} what="tus sucursales">
        {(rows) =>
          rows.length === 0 ? (
            <EmptyState icon={<MapPinIcon />} title="Aún no tienes sucursales" description="Crea la primera para poder cargar productos con su precio y existencias.">
              {can('BRANCH_CREATE') ? (
                <Button className="h-11 px-5" onClick={() => setCreating(true)}>
                  <PlusIcon aria-hidden />
                  Agregar sucursal
                </Button>
              ) : null}
            </EmptyState>
          ) : (
            <BranchesView branches={rows} />
          )
        }
      </QueryBoundary>
      <BranchSheet open={creating} onOpenChange={setCreating} branch={undefined} />
    </>
  )
}
