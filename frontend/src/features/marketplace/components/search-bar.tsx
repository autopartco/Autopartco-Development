import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { SearchIcon, XIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { QUERY_PARAMS } from '../lib/product-query'
import { PhotoSearchButton } from './photo-search'

interface SearchBarProps {
  className?: string
  /** `dark`: dentro de la cabecera carbón (cápsula translúcida que se aclara al enfocar). */
  tone?: 'light' | 'dark'
  autoFocus?: boolean
  /** Activa el atajo "/" para enfocar el campo (solo una barra por página: la de la cabecera). */
  shortcut?: boolean
}

/** "/" no debe robar la tecla mientras se escribe en otro campo. */
function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
}

/**
 * Buscador de repuestos sincronizado con `/buscar?q=`.
 * Si ya estás en resultados, conserva los filtros y vuelve a la primera página.
 */
export function SearchBar({ className, tone = 'light', autoFocus, shortcut }: SearchBarProps) {
  const id = useId()
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const onResults = location.pathname === '/buscar'
  const currentQuery = onResults ? (searchParams.get(QUERY_PARAMS.q) ?? '') : ''
  const [value, setValue] = useState(currentQuery)
  // La búsqueda de la URL manda (atrás/adelante, filtros): el campo se reinicia cuando cambia.
  const [syncedQuery, setSyncedQuery] = useState(currentQuery)
  if (syncedQuery !== currentQuery) {
    setSyncedQuery(currentQuery)
    setValue(currentQuery)
  }

  useEffect(() => {
    if (!shortcut) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) return
      if (isTyping(event.target)) return
      event.preventDefault()
      inputRef.current?.focus()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [shortcut])

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const query = value.trim()
    const params = onResults ? new URLSearchParams(searchParams) : new URLSearchParams()
    params.delete(QUERY_PARAMS.page)
    if (query) params.set(QUERY_PARAMS.q, query)
    else params.delete(QUERY_PARAMS.q)
    const qs = params.toString()
    navigate(qs ? `/buscar?${qs}` : '/buscar')
  }

  const dark = tone === 'dark'
  return (
    <form role="search" onSubmit={handleSubmit} className={cn('w-full min-w-0', className)}>
      <div
        className={cn(
          'group flex h-11 w-full min-w-0 items-center overflow-hidden rounded-full border transition-[background-color,border-color,box-shadow] duration-200 ease-out-snap xl:h-10',
          dark
            ? 'border-white/15 bg-white/10 hover:bg-white/15 focus-within:border-transparent focus-within:bg-white focus-within:shadow-md focus-within:ring-2 focus-within:ring-brand-on-asphalt focus-within:hover:bg-white'
            : 'border-border bg-muted focus-within:border-brand focus-within:bg-card focus-within:shadow-sm focus-within:ring-2 focus-within:ring-brand/30',
        )}
      >
        <label htmlFor={id} className="sr-only">
          Buscar repuestos
        </label>
        <SearchIcon
          className={cn(
            'pointer-events-none ml-3.5 size-4 shrink-0 transition-colors',
            dark ? 'text-asphalt-muted group-focus-within:text-muted-foreground' : 'text-muted-foreground',
          )}
          aria-hidden
        />
        <input
          ref={inputRef}
          id={id}
          name="q"
          type="search"
          value={value}
          onChange={(event) => setValue(event.target.value)}
          autoFocus={autoFocus}
          autoComplete="off"
          enterKeyHint="search"
          placeholder="Busca repuestos, marca o referencia"
          className={cn(
            'h-full min-w-0 flex-1 appearance-none bg-transparent px-2.5 text-base text-ellipsis outline-none sm:text-sm [&::-webkit-search-cancel-button]:hidden [&::-webkit-search-decoration]:hidden',
            dark
              ? 'text-white placeholder:text-asphalt-muted focus:text-foreground focus:placeholder:text-muted-foreground'
              : 'text-foreground placeholder:text-muted-foreground',
          )}
        />
        {value ? (
          <button
            type="button"
            aria-label="Borrar búsqueda"
            onClick={() => {
              setValue('')
              inputRef.current?.focus()
            }}
            className={cn(
              'grid size-11 shrink-0 place-items-center rounded-full transition-colors [--focus:var(--color-brand)] focus-visible:-outline-offset-2! xl:size-10',
              dark
                ? 'text-asphalt-muted hover:text-white group-focus-within:text-muted-foreground group-focus-within:hover:text-foreground'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            <XIcon className="size-4" aria-hidden />
          </button>
        ) : shortcut ? (
          <kbd
            aria-hidden
            className="mr-2 hidden h-5 min-w-5 items-center justify-center rounded border border-white/20 px-1 font-mono text-xs text-asphalt-muted group-focus-within:hidden xl:inline-flex"
          >
            /
          </kbd>
        ) : null}
        <PhotoSearchButton
          className={cn(
            'rounded-full xl:size-10',
            dark &&
              'text-asphalt-muted hover:bg-white/10 hover:text-white group-focus-within:text-muted-foreground group-focus-within:hover:bg-concrete group-focus-within:hover:text-foreground',
          )}
        />
        <button
          type="submit"
          aria-label="Buscar"
          className="grid h-full w-12 shrink-0 place-items-center bg-primary text-primary-foreground transition-colors [--focus:var(--color-brand-deep)] hover:bg-brand-bright focus-visible:-outline-offset-2!"
        >
          <SearchIcon className="size-[18px]" aria-hidden />
        </button>
      </div>
    </form>
  )
}
