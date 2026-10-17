import { useCallback, useEffect, useRef, useState } from 'react'

export type SubmitResult<R> =
  | { status: 'ok'; value: R }
  | { status: 'error'; error: unknown }
  /** Ya había un envío en curso: esta llamada se ignoró y no tocó el servidor. */
  | { status: 'busy' }

export interface SubmitOnce<A extends unknown[], R> {
  /** Ejecuta la acción si no hay otra en curso. Nunca lanza: devuelve el resultado. */
  run: (...args: A) => Promise<SubmitResult<R>>
  pending: boolean
  /** Último error (se limpia al volver a enviar o con `clearError`). */
  error: unknown
  clearError: () => void
}

/**
 * Bloquea el doble envío de una acción asíncrona (la única escritura de un formulario).
 * El bloqueo usa una referencia, no estado: un segundo clic o Enter en el mismo tick ya ve el
 * candado, aunque React aún no haya repintado `pending`.
 */
export function useSubmitOnce<A extends unknown[], R>(action: (...args: A) => Promise<R>): SubmitOnce<A, R> {
  const locked = useRef(false)
  const actionRef = useRef(action)
  useEffect(() => {
    actionRef.current = action
  })
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<unknown>(undefined)

  const run = useCallback(async (...args: A): Promise<SubmitResult<R>> => {
    if (locked.current) return { status: 'busy' }
    locked.current = true
    setPending(true)
    setError(undefined)
    try {
      return { status: 'ok', value: await actionRef.current(...args) }
    } catch (cause) {
      setError(cause)
      return { status: 'error', error: cause }
    } finally {
      locked.current = false
      setPending(false)
    }
  }, [])

  const clearError = useCallback(() => setError(undefined), [])
  return { run, pending, error, clearError }
}
