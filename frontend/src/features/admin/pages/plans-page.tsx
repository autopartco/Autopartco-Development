import { useState } from 'react'
import { toast } from 'sonner'
import { CreditCardIcon, PencilIcon, PlusIcon, PowerIcon, PowerOffIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { TextField } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { formatCOP, formatNumber } from '@/lib/format'
import { ConfirmDialog } from '@/features/portal/components/confirm-dialog'
import { TextAreaField } from '@/features/portal/components/form-controls'
import { QueryBoundary } from '@/features/portal/components/query-boundary'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { CAPABILITY_LABEL } from '@/features/portal/lib/labels'
import { getAdminOpsService } from '../api/admin-ops-service'
import type { AdminPlan, PlanInput } from '../api/ops-types'
import { AdminPage, FormSheet, RowActions, SwitchField } from '../components/admin-kit'
import { reloadAdmin, useAdminPlans } from '../hooks'

const limit = (value: number | null) => (value === null ? '∞' : formatNumber(value))

interface Values {
  code: string
  name: string
  description: string
  price: string
  maxBranches: string
  maxProducts: string
  maxUsers: string
  features: string
  capabilities: string[]
  recommended: boolean
  enabled: boolean
}

const valuesOf = (plan?: AdminPlan): Values => ({
  code: plan?.code ?? '',
  name: plan?.name ?? '',
  description: plan?.description ?? '',
  price: plan ? String(plan.monthlyPriceCop) : '',
  maxBranches: plan?.maxBranches?.toString() ?? '',
  maxProducts: plan?.maxProducts?.toString() ?? '',
  maxUsers: plan?.maxUsers?.toString() ?? '',
  features: plan?.features.join('\n') ?? '',
  capabilities: plan?.capabilities ?? [],
  recommended: plan?.recommended ?? false,
  enabled: plan?.enabled ?? true,
})

type Errors = Partial<Record<keyof Values, string>>

/** Entero opcional (vacío = ilimitado) con mínimo. */
function optionalInt(raw: string, min: number): { value: number | null; error?: string } {
  if (!raw.trim()) return { value: null }
  const value = Number(raw)
  if (!Number.isInteger(value) || value < min) return { value: null, error: `Escribe un número entero desde ${min}, o déjalo vacío para ilimitado.` }
  return { value }
}

function toInput(values: Values): { input?: PlanInput; errors: Errors } {
  const errors: Errors = {}
  const code = values.code.trim().toUpperCase()
  if (!code) errors.code = 'Escribe el código del plan.'
  else if (code.length > 50) errors.code = 'Máximo 50 caracteres.'
  const name = values.name.trim()
  if (!name) errors.name = 'Escribe el nombre del plan.'
  else if (name.length > 120) errors.name = 'Máximo 120 caracteres.'
  if (values.description.length > 2000) errors.description = 'Máximo 2000 caracteres.'
  const price = Number(values.price)
  if (!values.price.trim() || !Number.isInteger(price) || price < 0) errors.price = 'Escribe el precio mensual en pesos, sin puntos (0 si es gratis).'
  const branches = optionalInt(values.maxBranches, 1)
  const products = optionalInt(values.maxProducts, 0)
  const users = optionalInt(values.maxUsers, 1)
  if (branches.error) errors.maxBranches = branches.error
  if (products.error) errors.maxProducts = products.error
  if (users.error) errors.maxUsers = users.error
  const features = values.features.split('\n').map((line) => line.trim()).filter(Boolean)
  if (features.length > 30) errors.features = 'Máximo 30 líneas.'
  else if (features.some((line) => line.length > 250)) errors.features = 'Cada línea admite hasta 250 caracteres.'
  if (Object.keys(errors).length > 0) return { errors }
  return {
    errors,
    input: {
      code,
      name,
      description: values.description.trim() || null,
      monthlyPriceCop: price,
      currency: 'COP',
      features,
      recommended: values.recommended,
      enabled: values.enabled,
      maxBranches: branches.value,
      maxProducts: products.value,
      maxUsers: users.value,
      capabilities: values.capabilities,
    },
  }
}

function PlanSheet({ open, onOpenChange, plan }: { open: boolean; onOpenChange: (open: boolean) => void; plan?: AdminPlan }) {
  const [values, setValues] = useState(() => valuesOf(plan))
  const [errors, setErrors] = useState<Errors>({})
  const set = <K extends keyof Values>(key: K, value: Values[K]) => setValues((current) => ({ ...current, [key]: value }))
  const text = (key: Exclude<keyof Values, 'capabilities' | 'recommended' | 'enabled'>) => ({
    id: `plan-${key}`,
    value: values[key],
    error: errors[key],
    onChange: (event: { target: { value: string } }) => set(key, event.target.value),
  })

  return (
    <FormSheet
      open={open}
      onOpenChange={onOpenChange}
      title={plan ? `Editar ${plan.name}` : 'Nuevo plan'}
      description="Los cambios se aplican a la página de planes y a los límites de los almacenes suscritos."
      submitLabel={plan ? 'Guardar cambios' : 'Crear plan'}
      what="el plan"
      onSubmit={async () => {
        const { input, errors: found } = toInput(values)
        setErrors(found)
        if (!input) {
          document.getElementById(`plan-${Object.keys(found)[0]}`)?.focus()
          return false
        }
        const service = await getAdminOpsService()
        if (plan) await service.updatePlan(plan.id, input)
        else await service.createPlan(input)
        reloadAdmin('plans')
        toast.success(plan ? `Actualizaste el plan ${input.name}.` : `Creaste el plan ${input.name}.`)
      }}
    >
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField {...text('code')} label="Código" maxLength={50} autoComplete="off" required hint="En mayúsculas. Ej.: PRO." className="uppercase" />
        <TextField {...text('name')} label="Nombre" maxLength={120} autoComplete="off" required />
      </div>
      <TextAreaField {...text('description')} label="Descripción" optional maxLength={2000} />
      <TextField {...text('price')} label="Precio mensual (COP)" inputMode="numeric" required hint={values.price && Number(values.price) >= 0 ? formatCOP(Number(values.price) || 0) : 'Sin puntos ni signo de pesos.'} />
      <fieldset className="grid gap-4 sm:grid-cols-3">
        <legend className="mb-2 text-sm font-semibold">Límites (vacío = ilimitado)</legend>
        <TextField {...text('maxBranches')} label="Sucursales" inputMode="numeric" />
        <TextField {...text('maxProducts')} label="Productos" inputMode="numeric" />
        <TextField {...text('maxUsers')} label="Usuarios" inputMode="numeric" />
      </fieldset>
      <TextAreaField {...text('features')} label="Beneficios visibles" optional hint="Uno por línea. Se muestran en la página de planes." />
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1 text-sm font-semibold">Funciones incluidas</legend>
        <div className="grid gap-2 sm:grid-cols-2">
          {Object.entries(CAPABILITY_LABEL).map(([key, label]) => {
            const id = `plan-cap-${key}`
            return (
              <label key={key} htmlFor={id} className="flex min-h-11 items-center gap-3 rounded-lg border border-border px-3 text-sm">
                <Checkbox
                  id={id}
                  checked={values.capabilities.includes(key)}
                  onCheckedChange={(checked) =>
                    set('capabilities', checked ? [...values.capabilities, key] : values.capabilities.filter((item) => item !== key))
                  }
                />
                {label}
              </label>
            )
          })}
        </div>
      </fieldset>
      <SwitchField id="plan-recommended" label="Recomendado" hint="Se destaca en la página de planes." checked={values.recommended} onCheckedChange={(checked) => set('recommended', checked)} />
      <SwitchField id="plan-enabled" label="Disponible" hint="Los almacenes pueden elegirlo o cambiarse a él." checked={values.enabled} onCheckedChange={(checked) => set('enabled', checked)} />
    </FormSheet>
  )
}

/** `/admin/planes`: planes de suscripción con alta, edición y deshabilitación. */
export default function AdminPlansPage() {
  const plans = useAdminPlans()
  const [sheet, setSheet] = useState<{ plan?: AdminPlan; key: number }>()
  const [toDisable, setToDisable] = useState<AdminPlan>()
  const open = (plan?: AdminPlan) => setSheet({ plan, key: Date.now() })

  async function enable(plan: AdminPlan) {
    try {
      const { id, ...rest } = plan
      await (await getAdminOpsService()).updatePlan(id, { ...rest, enabled: true })
      reloadAdmin('plans')
      toast.success(`${plan.name} vuelve a estar disponible.`)
    } catch {
      toast.error(`No pudimos habilitar ${plan.name}. Inténtalo de nuevo.`)
    }
  }

  const columns: DataColumn<AdminPlan>[] = [
    {
      id: 'plan',
      header: 'Plan',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div className="min-w-0">
          <p className="font-semibold">{row.name}</p>
          <p className="label-mono text-muted-foreground">{row.code}</p>
        </div>
      ),
    },
    { id: 'precio', header: 'Precio mensual', align: 'end', sortValue: (row) => row.monthlyPriceCop, cell: (row) => <span className="tabular-nums">{row.monthlyPriceCop === 0 ? 'Gratis' : formatCOP(row.monthlyPriceCop)}</span> },
    {
      id: 'limites',
      header: 'Sucursales · productos · usuarios',
      className: 'hidden md:table-cell',
      cell: (row) => <span className="tabular-nums">{`${limit(row.maxBranches)} · ${limit(row.maxProducts)} · ${limit(row.maxUsers)}`}</span>,
    },
    { id: 'funciones', header: 'Funciones', className: 'hidden lg:table-cell', sortValue: (row) => row.capabilities.length, cell: (row) => row.capabilities.length },
    {
      id: 'estado',
      header: 'Estado',
      sortValue: (row) => (row.enabled ? 0 : 1),
      cell: (row) => (
        <div className="flex flex-wrap gap-1.5">
          {row.enabled ? <StatusBadge tone="success">Disponible</StatusBadge> : <StatusBadge>Deshabilitado</StatusBadge>}
          {row.recommended ? <StatusBadge tone="info">Recomendado</StatusBadge> : null}
        </div>
      ),
    },
    {
      id: 'acciones',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => (
        <RowActions
          label={`Acciones para ${row.name}`}
          actions={[
            { label: 'Editar', icon: <PencilIcon aria-hidden />, onSelect: () => open(row) },
            row.enabled
              ? { label: 'Deshabilitar', icon: <PowerOffIcon aria-hidden />, destructive: true, onSelect: () => setToDisable(row) }
              : { label: 'Habilitar', icon: <PowerIcon aria-hidden />, onSelect: () => void enable(row) },
          ]}
        />
      ),
    },
  ]

  return (
    <AdminPage
      label="Comercial"
      title="Planes"
      description="Define precio, límites y funciones de cada plan de suscripción para almacenes."
      actions={
        <Button className="h-11 px-4" onClick={() => open()}>
          <PlusIcon aria-hidden />
          Nuevo plan
        </Button>
      }
    >
      <QueryBoundary result={plans} what="los planes" keepPrevious>
        {(rows) =>
          rows.length === 0 ? (
            <EmptyState icon={<CreditCardIcon />} title="Aún no hay planes" description="Crea el primero para que los almacenes puedan suscribirse.">
              <Button className="h-11 px-5" onClick={() => open()}>
                <PlusIcon aria-hidden />
                Nuevo plan
              </Button>
            </EmptyState>
          ) : (
            <DataTable caption="Planes de suscripción" columns={columns} rows={rows} getRowId={(row) => row.id} defaultSort={{ columnId: 'precio', direction: 'asc' }} />
          )
        }
      </QueryBoundary>
      {sheet ? <PlanSheet key={sheet.key} open onOpenChange={(next) => !next && setSheet(undefined)} plan={sheet.plan} /> : null}
      <ConfirmDialog
        open={toDisable !== undefined}
        onOpenChange={(next) => !next && setToDisable(undefined)}
        title={`¿Deshabilitar ${toDisable?.name ?? 'el plan'}?`}
        description="Dejará de ofrecerse a almacenes nuevos. Los almacenes que ya lo tienen conservan su suscripción. Puedes volver a habilitarlo."
        confirmLabel="Deshabilitar plan"
        destructive
        onConfirm={async () => {
          if (!toDisable) return
          await (await getAdminOpsService()).disablePlan(toDisable.id)
          reloadAdmin('plans')
          toast.success(`Deshabilitaste ${toDisable.name}.`)
        }}
      />
    </AdminPage>
  )
}
