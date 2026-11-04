import type { VehicleRule, VehicleRuleInput } from '../api/types'

/** Máximo de reglas por producto que acepta el servidor. */
export const MAX_VEHICLE_RULES = 100

/** Texto legible: «Toyota · todos los modelos», «Renault · Duster · todas las versiones», «Renault · Duster · 2.0 4x4». */
export function ruleLabel(rule: VehicleRule): string {
  const model = rule.modelName ?? 'todos los modelos'
  const version = rule.modelName ? (rule.versionLabel ?? 'todas las versiones') : undefined
  return [rule.makeName, model, version].filter(Boolean).join(' · ')
}

/** Lo que viaja al servidor: ids, con el nivel «todos» ausente. */
export function toRuleInputs(rules: VehicleRule[]): VehicleRuleInput[] {
  return rules.map((rule) => ({
    makeId: rule.makeId,
    ...(rule.modelId ? { modelId: rule.modelId } : {}),
    ...(rule.versionId ? { versionId: rule.versionId } : {}),
  }))
}

/** `true` si `outer` cubre todo lo que cubre `inner` (un nivel nulo cubre todo lo que hay debajo). */
export function ruleCovers(outer: VehicleRule, inner: VehicleRule): boolean {
  return (
    outer.makeId === inner.makeId &&
    (outer.modelId === null || outer.modelId === inner.modelId) &&
    (outer.versionId === null || outer.versionId === inner.versionId)
  )
}

export type AddRuleResult =
  | { status: 'added'; rules: VehicleRule[]; replaced: VehicleRule[] }
  /** Ya cubierta por `by` (duplicada o contenida): no cambia nada. */
  | { status: 'covered'; rules: VehicleRule[]; by: VehicleRule }

/**
 * Agrega una regla evitando duplicados visuales. Si ya la cubre otra, no se agrega; si ella cubre
 * a otras (p. ej. «Renault · todos los modelos» sobre «Renault · Duster»), las reemplaza.
 * El servidor normaliza igual: esto solo evita que el usuario vea reglas redundantes.
 */
export function addRule(rules: VehicleRule[], rule: VehicleRule): AddRuleResult {
  const by = rules.find((existing) => ruleCovers(existing, rule))
  if (by) return { status: 'covered', rules, by }
  const replaced = rules.filter((existing) => ruleCovers(rule, existing))
  return { status: 'added', rules: [...rules.filter((existing) => !replaced.includes(existing)), rule], replaced }
}
