import { useCallback, useMemo, useState } from 'react'

export type FieldErrors = Record<string, string>

/** Comparación estructural para detectar cambios (primitivos, arreglos y objetos planos; el resto por referencia). */
export function isEqual(a: unknown, b: unknown): boolean {
  if (Object.is(a, b)) return true
  if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((item, index) => isEqual(item, b[index]))
  const plain = (value: unknown): value is Record<string, unknown> =>
    typeof value === 'object' && value !== null && Object.getPrototypeOf(value) === Object.prototype
  if (plain(a) && plain(b)) {
    const keys = Object.keys(a)
    return keys.length === Object.keys(b).length && keys.every((key) => key in b && isEqual(a[key], b[key]))
  }
  return false
}

export interface DraftFormOptions<V extends object> {
  /** Valores iniciales (se leen una sola vez; usa `reset` para cargar otros). */
  initial: V
  /** Reglas del cliente. Los campos sin error no aparecen en el mapa. El servidor valida otra vez. */
  validate?: (values: V) => FieldErrors
}

export interface DraftForm<V extends object> {
  values: V
  set: <K extends keyof V>(field: K, value: V[K]) => void
  /** Cambia varios campos a la vez. */
  patch: (changes: Partial<V>) => void
  /** Marca el campo como visitado (llamar en `onBlur`) para mostrar su error. */
  touch: (field: keyof V) => void
  /** Errores de todas las reglas del cliente, estén o no a la vista. */
  errors: FieldErrors
  /** Error a mostrar: el del servidor, o el del cliente si el campo se visitó o ya se intentó enviar. */
  errorFor: (field: keyof V & string) => string | undefined
  /** Marca el formulario como «intentado», calcula los errores y devuelve el primer campo inválido. */
  validateAll: () => { valid: boolean; errors: FieldErrors; firstInvalid: string | undefined }
  /** Errores que devolvió el servidor por campo; se limpian al editar el campo. */
  setServerErrors: (errors: FieldErrors) => void
  isDirty: boolean
  /** `true` tras el primer intento de envío. */
  submitted: boolean
  /** Vuelve a los valores guardados, o carga `next` como nuevo punto de partida. */
  reset: (next?: V) => void
  /** Toma los valores actuales como guardados (tras un envío exitoso): `isDirty` vuelve a `false`. */
  markSaved: () => void
}

/**
 * Borrador local de un formulario: todo vive en memoria hasta el envío final. El hook no hace
 * peticiones ni conoce el servidor; solo guarda valores, cambios, validación y errores por campo.
 * Las escrituras las hace quien lo usa, una sola vez, dentro de `useSubmitOnce`.
 */
export function useDraftForm<V extends object>({ initial, validate }: DraftFormOptions<V>): DraftForm<V> {
  const [values, setValues] = useState<V>(initial)
  const [baseline, setBaseline] = useState<V>(initial)
  const [touched, setTouched] = useState<Partial<Record<keyof V, boolean>>>({})
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrorsState] = useState<FieldErrors>({})

  const errors = useMemo<FieldErrors>(() => validate?.(values) ?? {}, [validate, values])

  const clearServerError = useCallback((fields: string[]) => {
    setServerErrorsState((current) => {
      if (!fields.some((field) => field in current)) return current
      return Object.fromEntries(Object.entries(current).filter(([key]) => !fields.includes(key)))
    })
  }, [])

  const set = useCallback(
    <K extends keyof V>(field: K, value: V[K]) => {
      setValues((current) => ({ ...current, [field]: value }))
      clearServerError([String(field)])
    },
    [clearServerError],
  )

  const patch = useCallback(
    (changes: Partial<V>) => {
      setValues((current) => ({ ...current, ...changes }))
      clearServerError(Object.keys(changes))
    },
    [clearServerError],
  )

  const touch = useCallback((field: keyof V) => setTouched((current) => (current[field] ? current : { ...current, [field]: true })), [])

  const errorFor = useCallback(
    (field: keyof V & string) => serverErrors[field] ?? (submitted || touched[field] ? errors[field] : undefined),
    [serverErrors, submitted, touched, errors],
  )

  const validateAll = useCallback(() => {
    setSubmitted(true)
    const found = validate?.(values) ?? {}
    const firstInvalid = Object.keys(found).find((field) => found[field])
    return { valid: firstInvalid === undefined, errors: found, firstInvalid }
  }, [validate, values])

  const reset = useCallback(
    (next?: V) => {
      const target = next ?? baseline
      setValues(target)
      setBaseline(target)
      setTouched({})
      setSubmitted(false)
      setServerErrorsState({})
    },
    [baseline],
  )

  const markSaved = useCallback(() => {
    setBaseline(values)
    setServerErrorsState({})
  }, [values])

  const isDirty = useMemo(() => !isEqual(values, baseline), [values, baseline])

  return {
    values,
    set,
    patch,
    touch,
    errors,
    errorFor,
    validateAll,
    setServerErrors: setServerErrorsState,
    isDirty,
    submitted,
    reset,
    markSaved,
  }
}
