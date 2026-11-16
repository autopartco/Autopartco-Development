import { useSearchParams } from 'react-router'

/** Filtros de un listado en la URL (`?q=&estado=&pagina=`): compartibles y sobreviven al recargar. */
export function useUrlFilters() {
  const [params, setParams] = useSearchParams()
  const page = Math.max(1, Number(params.get('pagina')) || 1)
  function get(name: string): string {
    return params.get(name) ?? ''
  }
  function update(next: Record<string, string>, resetPage = true) {
    const merged = new URLSearchParams(params)
    for (const [name, value] of Object.entries(next)) {
      if (value) merged.set(name, value)
      else merged.delete(name)
    }
    if (resetPage) merged.delete('pagina')
    setParams(merged, { replace: true })
  }
  return { get, page, update }
}
