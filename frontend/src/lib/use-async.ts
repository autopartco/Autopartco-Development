import { useCallback, useEffect, useEffectEvent, useState, useSyncExternalStore } from 'react'

/** Resultados ya resueltos por clave, para no repetir peticiones al volver a una pantalla. */
const resolved = new Map<string, unknown>()
/** Peticiones en curso por clave, para deduplicar llamadas simultáneas. */
const inflight = new Map<string, Promise<unknown>>()

function load<T>(key: string, fetcher: () => Promise<T>): Promise<T> {
  const pending = inflight.get(key)
  if (pending) return pending as Promise<T>
  const promise = fetcher()
    .then((data) => {
      resolved.set(key, data)
      return data
    })
    .finally(() => inflight.delete(key))
  inflight.set(key, promise)
  return promise
}

/** Contador de invalidaciones: los hooks montados vuelven a pedir sus datos cuando cambia. */
let invalidations = 0
const invalidationListeners = new Set<() => void>()

function subscribeInvalidations(listener: () => void): () => void {
  invalidationListeners.add(listener)
  return () => invalidationListeners.delete(listener)
}

/**
 * Descarta los resultados en caché cuyas claves empiezan por `prefix` (tras una mutación o al
 * cambiar de sesión). Las pantallas montadas con esas claves recargan solas; mientras llega la
 * respuesta conservan los últimos datos que ya tenían.
 */
export function invalidateAsync(prefix: string): void {
  for (const key of [...resolved.keys()]) {
    if (key.startsWith(prefix)) resolved.delete(key)
  }
  invalidations += 1
  for (const listener of invalidationListeners) listener()
}

type Settled<T> =
  | { key: string; status: 'loading'; data?: undefined; error?: undefined }
  | { key: string; status: 'success'; data: T; error?: undefined }
  | { key: string; status: 'error'; data?: undefined; error: unknown }

export interface AsyncResult<T> {
  status: 'loading' | 'success' | 'error'
  /** Datos de la clave actual, o los de la última clave resuelta mientras carga la nueva. */
  data: T | undefined
  error: unknown
  /** `true` si `data` no corresponde todavía a la clave actual (p. ej. al cambiar de página). */
  isStale: boolean
  retry: () => void
}

/**
 * Carga datos por clave con caché en memoria, deduplicación y reintento.
 * Conserva los últimos datos mientras llega la nueva respuesta para evitar parpadeos.
 */
export function useAsync<T>(key: string, fetcher: () => Promise<T>): AsyncResult<T> {
  const [settled, setSettled] = useState<Settled<T>>({ key, status: 'loading' })
  const [lastData, setLastData] = useState<T | undefined>(undefined)
  const [attempt, setAttempt] = useState(0)
  const runFetcher = useEffectEvent(() => load(key, fetcher))
  const invalidation = useSyncExternalStore(
    subscribeInvalidations,
    () => invalidations,
    () => 0,
  )

  useEffect(() => {
    if (resolved.has(key)) return
    let active = true
    runFetcher().then(
      (data) => {
        if (!active) return
        setSettled({ key, status: 'success', data })
        setLastData(data)
      },
      (error: unknown) => {
        if (active) setSettled({ key, status: 'error', error })
      },
    )
    return () => {
      active = false
    }
  }, [key, attempt, invalidation])

  const retry = useCallback(() => {
    resolved.delete(key)
    setSettled({ key, status: 'loading' })
    setAttempt((value) => value + 1)
  }, [key])

  // Caché resuelta: se usa directamente, sin esperar al efecto.
  if (resolved.has(key) && !(settled.key === key && settled.status === 'error')) {
    return { status: 'success', data: resolved.get(key) as T, error: undefined, isStale: false, retry }
  }
  if (settled.key === key && settled.status !== 'loading') {
    return settled.status === 'success'
      ? { status: 'success', data: settled.data, error: undefined, isStale: false, retry }
      : { status: 'error', data: undefined, error: settled.error, isStale: false, retry }
  }
  return { status: 'loading', data: lastData, error: undefined, isStale: true, retry }
}
