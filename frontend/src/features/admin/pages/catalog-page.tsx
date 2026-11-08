import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { CheckIcon, FactoryIcon, PencilIcon, PlusIcon, PowerOffIcon, SearchXIcon, TagsIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { TextField } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { ConfirmDialog } from '@/features/portal/components/confirm-dialog'
import { SelectField } from '@/features/portal/components/form-controls'
import { QueryBoundary } from '@/features/portal/components/query-boundary'
import { ResultCount, SearchField } from '@/features/portal/components/search-field'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { normalizeText } from '@/features/portal/lib/fuzzy'
import { getAdminOpsService } from '../api/admin-ops-service'
import type { AdminCategory, AdminManufacturer } from '../api/ops-types'
import { AdminPage, FormSheet, RowActions } from '../components/admin-kit'
import { reloadAdmin, useAdminCategories, useAdminManufacturers } from '../hooks'

type Tab = 'categorias' | 'fabricantes'
const CODE_PATTERN = /^[A-Za-z0-9_-]+$/

/** Panel de un solo campo para crear o renombrar una categoría o un fabricante. */
function NameSheet({
  title,
  description,
  label,
  hint,
  initial,
  submitLabel,
  what,
  validate,
  normalize = (value) => value.trim(),
  onSave,
  onClose,
}: {
  title: string
  description: string
  label: string
  hint?: string
  initial: string
  submitLabel: string
  what: string
  validate: (value: string) => string | undefined
  normalize?: (value: string) => string
  onSave: (value: string) => Promise<void>
  onClose: () => void
}) {
  const [value, setValue] = useState(initial)
  const [error, setError] = useState<string>()
  return (
    <FormSheet
      open
      onOpenChange={(next) => !next && onClose()}
      title={title}
      description={description}
      submitLabel={submitLabel}
      what={what}
      onSubmit={async () => {
        const clean = normalize(value)
        const problem = validate(clean)
        setError(problem)
        if (problem) {
          document.getElementById('catalogo-nombre')?.focus()
          return false
        }
        await onSave(clean)
      }}
    >
      <TextField id="catalogo-nombre" label={label} hint={hint} error={error} value={value} onChange={(event) => setValue(event.target.value)} autoComplete="off" required maxLength={100} autoFocus />
    </FormSheet>
  )
}

function CategoriesTab() {
  const categories = useAdminCategories()
  const [editing, setEditing] = useState<{ category?: AdminCategory }>()
  const [toDeactivate, setToDeactivate] = useState<AdminCategory>()
  const [q, setQ] = useState('')
  const rows = useMemo(() => {
    const term = normalizeText(q)
    return (categories.data ?? []).filter((item) => !term || normalizeText(item.code).includes(term))
  }, [categories.data, q])

  const columns: DataColumn<AdminCategory>[] = [
    { id: 'codigo', header: 'Código', rowHeader: true, sortValue: (row) => row.code, cell: (row) => <span className="font-mono font-semibold">{row.code}</span> },
    { id: 'id', header: 'ID', className: 'hidden md:table-cell', cell: (row) => <span className="font-mono text-sm text-muted-foreground">{row.id}</span> },
    {
      id: 'acciones',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => (
        <RowActions
          label={`Acciones para ${row.code}`}
          actions={[
            { label: 'Renombrar', icon: <PencilIcon aria-hidden />, onSelect: () => setEditing({ category: row }) },
            { label: 'Desactivar', icon: <PowerOffIcon aria-hidden />, destructive: true, onSelect: () => setToDeactivate(row) },
          ]}
        />
      ),
    },
  ]

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <SearchField value={q} onChange={setQ} placeholder="Código de categoría" className="sm:max-w-sm sm:flex-1" />
        <Button className="h-11 px-4" onClick={() => setEditing({})}>
          <PlusIcon aria-hidden />
          Nueva categoría
        </Button>
      </div>
      <QueryBoundary result={categories} what="las categorías" keepPrevious>
        {(all) =>
          all.length === 0 ? (
            <EmptyState icon={<TagsIcon />} title="Aún no hay categorías" description="Las categorías ordenan el catálogo del marketplace." />
          ) : (
            <>
              <ResultCount shown={rows.length} total={all.length} singular="categoría" plural="categorías" />
              {rows.length === 0 ? (
                <EmptyState icon={<SearchXIcon />} title="Ninguna categoría coincide" description="Prueba con otro código." className="py-8" />
              ) : (
                <DataTable caption="Categorías del catálogo" columns={columns} rows={rows} getRowId={(row) => row.id} defaultSort={{ columnId: 'codigo', direction: 'asc' }} pageSize={25} />
              )}
            </>
          )
        }
      </QueryBoundary>
      {editing ? (
        <NameSheet
          title={editing.category ? `Renombrar ${editing.category.code}` : 'Nueva categoría'}
          description="El código identifica la categoría en filtros y promociones."
          label="Código"
          hint="Letras, números, guion o guion bajo. Se guarda en mayúsculas."
          initial={editing.category?.code ?? ''}
          submitLabel={editing.category ? 'Guardar' : 'Crear categoría'}
          what="la categoría"
          normalize={(value) => value.trim().toUpperCase()}
          validate={(value) => (!value ? 'Escribe el código.' : !CODE_PATTERN.test(value) ? 'Solo letras, números, guion y guion bajo, sin espacios.' : undefined)}
          onClose={() => setEditing(undefined)}
          onSave={async (code) => {
            const service = await getAdminOpsService()
            if (editing.category) await service.updateCategory(editing.category.id, code)
            else await service.createCategory(code)
            reloadAdmin('catalog:')
            toast.success(editing.category ? `Renombraste la categoría a ${code}.` : `Creaste la categoría ${code}.`)
          }}
        />
      ) : null}
      <ConfirmDialog
        open={toDeactivate !== undefined}
        onOpenChange={(next) => !next && setToDeactivate(undefined)}
        title={`¿Desactivar ${toDeactivate?.code ?? 'la categoría'}?`}
        description="Deja de ofrecerse al crear productos y en los filtros del marketplace. Los productos existentes la conservan."
        confirmLabel="Desactivar categoría"
        destructive
        onConfirm={async () => {
          if (!toDeactivate) return
          await (await getAdminOpsService()).deactivateCategory(toDeactivate.id)
          reloadAdmin('catalog:')
          toast.success(`Desactivaste ${toDeactivate.code}.`)
        }}
      />
    </div>
  )
}

function ManufacturersTab() {
  const manufacturers = useAdminManufacturers()
  const [editing, setEditing] = useState<{ manufacturer?: AdminManufacturer }>()
  const [toDeactivate, setToDeactivate] = useState<AdminManufacturer>()
  const [q, setQ] = useState('')
  const [state, setState] = useState('')
  const rows = useMemo(() => {
    const term = normalizeText(q)
    return (manufacturers.data ?? []).filter(
      (item) => (!state || (state === 'PENDING' ? !item.approved : item.approved)) && (!term || normalizeText(item.name).includes(term)),
    )
  }, [manufacturers.data, q, state])

  async function approve(item: AdminManufacturer) {
    try {
      await (await getAdminOpsService()).updateManufacturer(item.id, { approved: true })
      reloadAdmin('catalog:')
      toast.success(`Aprobaste ${item.name}.`)
    } catch {
      toast.error(`No pudimos aprobar ${item.name}. Inténtalo de nuevo.`)
    }
  }

  const columns: DataColumn<AdminManufacturer>[] = [
    { id: 'nombre', header: 'Fabricante', rowHeader: true, sortValue: (row) => row.name, cell: (row) => <span className="font-semibold">{row.name}</span> },
    {
      id: 'origen',
      header: 'Origen',
      className: 'hidden md:table-cell',
      cell: (row) => (row.suggestedByStoreId ? <span className="text-sm">Sugerido por almacén <span className="font-mono text-muted-foreground">{row.suggestedByStoreId}</span></span> : 'Catálogo oficial'),
    },
    {
      id: 'estado',
      header: 'Estado',
      sortValue: (row) => (row.approved ? 1 : 0),
      cell: (row) => (row.approved ? <StatusBadge tone="success">Aprobado</StatusBadge> : <StatusBadge tone="warning">Por aprobar</StatusBadge>),
    },
    {
      id: 'acciones',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => (
        <div className="flex items-center justify-end gap-1">
          {!row.approved ? (
            <Button variant="outline" className="h-10 px-3" aria-label={`Aprobar ${row.name}`} onClick={() => void approve(row)}>
              <CheckIcon aria-hidden />
              Aprobar
            </Button>
          ) : null}
          <RowActions
            label={`Acciones para ${row.name}`}
            actions={[
              { label: 'Renombrar', icon: <PencilIcon aria-hidden />, onSelect: () => setEditing({ manufacturer: row }) },
              { label: 'Desactivar', icon: <PowerOffIcon aria-hidden />, destructive: true, onSelect: () => setToDeactivate(row) },
            ]}
          />
        </div>
      ),
    },
  ]

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <form role="search" aria-label="Filtrar fabricantes" onSubmit={(event) => event.preventDefault()} className="grid flex-1 gap-3 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:max-w-2xl">
          <SearchField value={q} onChange={setQ} placeholder="Nombre del fabricante" />
          <SelectField id="filtro-fabricante" label="Estado" placeholder="Todos" options={[{ value: 'PENDING', label: 'Por aprobar' }, { value: 'APPROVED', label: 'Aprobados' }]} value={state} onChange={(event) => setState(event.target.value)} />
        </form>
        <Button className="h-11 px-4" onClick={() => setEditing({})}>
          <PlusIcon aria-hidden />
          Nuevo fabricante
        </Button>
      </div>
      <QueryBoundary result={manufacturers} what="los fabricantes" keepPrevious>
        {(all) =>
          all.length === 0 ? (
            <EmptyState icon={<FactoryIcon />} title="Aún no hay fabricantes" description="Los almacenes eligen el fabricante de cada repuesto de esta lista." />
          ) : (
            <>
              <ResultCount shown={rows.length} total={all.length} singular="fabricante" plural="fabricantes" />
              {rows.length === 0 ? (
                <EmptyState icon={<SearchXIcon />} title="Ningún fabricante coincide" description="Prueba con otro nombre o estado." className="py-8" />
              ) : (
                <DataTable caption="Fabricantes del catálogo" columns={columns} rows={rows} getRowId={(row) => row.id} defaultSort={{ columnId: 'estado', direction: 'asc' }} pageSize={25} />
              )}
            </>
          )
        }
      </QueryBoundary>
      {editing ? (
        <NameSheet
          title={editing.manufacturer ? `Renombrar ${editing.manufacturer.name}` : 'Nuevo fabricante'}
          description="Los fabricantes creados aquí quedan aprobados para todos los almacenes."
          label="Nombre"
          initial={editing.manufacturer?.name ?? ''}
          submitLabel={editing.manufacturer ? 'Guardar' : 'Crear fabricante'}
          what="el fabricante"
          validate={(value) => (!value ? 'Escribe el nombre.' : undefined)}
          onClose={() => setEditing(undefined)}
          onSave={async (name) => {
            const service = await getAdminOpsService()
            if (editing.manufacturer) await service.updateManufacturer(editing.manufacturer.id, { name })
            else await service.createManufacturer(name)
            reloadAdmin('catalog:')
            toast.success(editing.manufacturer ? `Renombraste el fabricante a ${name}.` : `Creaste el fabricante ${name}.`)
          }}
        />
      ) : null}
      <ConfirmDialog
        open={toDeactivate !== undefined}
        onOpenChange={(next) => !next && setToDeactivate(undefined)}
        title={`¿Desactivar ${toDeactivate?.name ?? 'el fabricante'}?`}
        description="Deja de ofrecerse al crear productos. Los productos existentes lo conservan."
        confirmLabel="Desactivar fabricante"
        destructive
        onConfirm={async () => {
          if (!toDeactivate) return
          await (await getAdminOpsService()).deactivateManufacturer(toDeactivate.id)
          reloadAdmin('catalog:')
          toast.success(`Desactivaste ${toDeactivate.name}.`)
        }}
      />
    </div>
  )
}

/** `/admin/catalogo?vista=fabricantes`: datos maestros del catálogo. La pestaña vive en la URL. */
export default function AdminCatalogPage() {
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('vista') === 'fabricantes' ? 'fabricantes' : 'categorias'
  const pending = useAdminManufacturers().data?.filter((item) => !item.approved).length ?? 0
  const tabs: { id: Tab; label: string; badge?: number }[] = [
    { id: 'categorias', label: 'Categorías' },
    { id: 'fabricantes', label: 'Fabricantes', badge: pending },
  ]

  return (
    <AdminPage label="Comercial" title="Catálogo" description="Categorías y fabricantes oficiales que usan todos los almacenes al publicar repuestos.">
      <div role="tablist" aria-label="Datos del catálogo" className="-mt-2 mb-6 flex gap-1 overflow-x-auto overflow-y-hidden border-b border-border">
        {tabs.map((item) => (
          <button
            key={item.id}
            type="button"
            role="tab"
            id={`tab-${item.id}`}
            aria-selected={tab === item.id}
            aria-controls={`panel-${item.id}`}
            onClick={() => setParams(item.id === 'categorias' ? {} : { vista: item.id }, { replace: true })}
            className={cn(
              '-mb-px inline-flex min-h-11 items-center gap-2 border-b-2 px-3 text-sm font-semibold whitespace-nowrap transition-colors',
              tab === item.id ? 'border-foreground text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {item.label}
            {item.badge ? (
              <span className="inline-flex h-5 min-w-5 items-center justify-center rounded-full bg-stock-low/15 px-1.5 text-xs text-stock-low tabular-nums">
                <span className="sr-only">, por aprobar: </span>
                {item.badge}
              </span>
            ) : null}
          </button>
        ))}
      </div>
      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        {tab === 'categorias' ? <CategoriesTab /> : <ManufacturersTab />}
      </div>
    </AdminPage>
  )
}
