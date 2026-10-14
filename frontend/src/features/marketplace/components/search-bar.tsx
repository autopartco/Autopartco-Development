import { useId, type FormEvent } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { SearchIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { QUERY_PARAMS } from '../lib/product-query'
import { PhotoSearchButton } from './photo-search'

interface SearchBarProps {
  className?: string
  /** `dark`: dentro de la cabecera carbón (campo blanco y botón rojo con hover más claro). */
  tone?: 'light' | 'dark'
  autoFocus?: boolean
}

/**
 * Buscador de repuestos sincronizado con `/buscar?q=`.
 * Si ya estás en resultados, conserva los filtros y vuelve a la primera página.
 */
export function SearchBar({ className, tone = 'light', autoFocus }: SearchBarProps) {
  const id = useId()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const onResults = location.pathname === '/buscar'
  const currentQuery = onResults ? (searchParams.get(QUERY_PARAMS.q) ?? '') : ''

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    const params = onResults ? new URLSearchParams(searchParams) : new URLSearchParams()
    params.delete(QUERY_PARAMS.page)
    if (value) params.set(QUERY_PARAMS.q, value)
    else params.delete(QUERY_PARAMS.q)
    const qs = params.toString()
    navigate(qs ? `/buscar?${qs}` : '/buscar')
  }

  const dark = tone === 'dark'
  return (
    <form role="search" onSubmit={handleSubmit} className={cn('flex w-full min-w-0 items-stretch', className)}>
      <label htmlFor={id} className="sr-only">
        Buscar repuestos
      </label>
      <div className="relative min-w-0 flex-1">
        <SearchIcon
          className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground"
          aria-hidden
        />
        <input
          // La clave reinicia el campo cuando cambia la búsqueda en la URL (atrás/adelante).
          key={currentQuery}
          id={id}
          name="q"
          type="search"
          defaultValue={currentQuery}
          autoFocus={autoFocus}
          autoComplete="off"
          enterKeyHint="search"
          placeholder="Repuesto o referencia"
          className={cn(
            'h-11 w-full min-w-0 rounded-l-lg border border-r-0 pr-12 pl-9 text-base text-foreground placeholder:text-muted-foreground sm:text-sm [&::-webkit-search-cancel-button]:cursor-pointer',
            dark ? 'border-white bg-white lg:h-10 lg:pr-11' : 'border-input bg-card',
          )}
        />
        {/* Dentro del campo (blanco también en la cabecera oscura): no le quita ancho en móvil. */}
        <PhotoSearchButton className={cn('absolute top-0 right-0', dark && 'lg:size-10')} />
      </div>
      <Button
        type="submit"
        className={cn('h-11 rounded-l-none px-4', dark && 'hover:bg-brand-bright lg:h-10')}
      >
        Buscar
      </Button>
    </form>
  )
}
