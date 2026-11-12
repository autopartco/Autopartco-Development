import { useId } from 'react'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { cn } from '@/lib/utils'
import type { StaffRole, StaffRoleCode } from '../api/types'
import { AREA_ACCESS_LABEL, areaAccess, permissionLabel } from '../lib/staff-roles'

const OPTION_CLASS =
  'flex min-h-14 cursor-pointer items-start gap-3 rounded-md border border-ap-line-strong bg-ap-white p-3 transition-colors hover:bg-ap-paper has-data-checked:border-ap-red has-data-checked:bg-ap-red-tint has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-(--focus)'

const ACCESS_CLASS = {
  manage: 'font-semibold text-ap-ink',
  read: 'text-ap-ink',
  none: 'text-ap-muted',
} as const

/** Qué puede hacer un rol, por área, con el detalle de permisos plegado. */
export function RoleAccessSummary({ permissionCodes, className }: { permissionCodes: readonly string[]; className?: string }) {
  const areas = areaAccess(permissionCodes)
  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <dl className="divide-y divide-ap-line rounded-md border border-ap-line text-sm">
        {areas.map((area) => (
          <div key={area.id} className="flex items-center justify-between gap-3 px-3 py-2">
            <dt className="text-ap-muted">{area.label}</dt>
            <dd className={ACCESS_CLASS[area.access]}>{AREA_ACCESS_LABEL[area.access]}</dd>
          </div>
        ))}
      </dl>
      <details className="group text-sm">
        <summary className="inline-flex min-h-11 cursor-pointer items-center font-semibold text-ap-red underline-offset-4 hover:underline">
          Ver permisos detallados
        </summary>
        <ul className="mt-1 grid gap-x-4 gap-y-1 pl-1 text-ap-ink sm:grid-cols-2">
          {areas
            .flatMap((area) => area.codes)
            .map((code) => (
              <li key={code}>{permissionLabel(code)}</li>
            ))}
        </ul>
      </details>
    </div>
  )
}

interface RolePickerProps {
  id?: string
  roles: readonly StaffRole[]
  value: StaffRoleCode | undefined
  onChange: (code: StaffRoleCode) => void
  disabled?: boolean
  error?: string
}

/** Elección entre los roles predefinidos, con el resumen por áreas del rol marcado. */
export function RolePicker({ id, roles, value, onChange, disabled, error }: RolePickerProps) {
  const labelId = useId()
  const errorId = useId()
  const selected = roles.find((role) => role.code === value)
  return (
    <div className="flex flex-col gap-3">
      <div id={id} tabIndex={-1} className="flex flex-col gap-1.5 outline-none">
        <p id={labelId} className="text-sm font-semibold">
          Rol
        </p>
        <RadioGroup
          aria-labelledby={labelId}
          aria-describedby={error ? errorId : undefined}
          aria-invalid={error ? true : undefined}
          value={value ?? ''}
          disabled={disabled}
          onValueChange={(next) => onChange(next as StaffRoleCode)}
        >
          {roles.map((role) => (
            <label key={role.code} className={OPTION_CLASS}>
              <RadioGroupItem value={role.code} className="mt-0.5" />
              <span className="flex min-w-0 flex-col">
                <span className="font-medium text-ap-ink">{role.name}</span>
                <span className="text-sm text-ap-muted">{role.description}</span>
              </span>
            </label>
          ))}
        </RadioGroup>
        {error ? (
          <p id={errorId} className="text-sm font-medium text-ap-danger">
            {error}
          </p>
        ) : null}
      </div>
      {selected ? (
        <section aria-label={`Acceso del rol ${selected.name}`} className="flex flex-col gap-1.5">
          <p className="text-sm font-semibold">Qué podrá hacer</p>
          <RoleAccessSummary permissionCodes={selected.permissionCodes} />
        </section>
      ) : null}
    </div>
  )
}
