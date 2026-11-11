import { useEffect, useId, useMemo, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router'
import { LoaderCircleIcon, SearchIcon, XIcon } from 'lucide-react'
import { useMapArea } from '@/features/location/hooks'
import { formatCOP } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { SuggestionKind } from '../../api/types'
import { useSearchSuggestions } from '../../hooks'
import { productPath } from '../../lib/paths'
import { SEARCH_PARAMS } from '../../lib/search-query'
import { PhotoSearchButton } from '../photo-search'

interface SearchBoxProps {
  className?: string
  /** `dark`: dentro de la cabecera carbón (cápsula translúcida que se aclara al enfocar). */
  tone?: 'light' | 'dark'
  autoFocus?: boolean
  /** Activa el atajo "/" para enfocar el campo (solo una caja por página: la de la cabecera). */
  shortcut?: boolean
  /** `lg`: caja grande del inicio (alto 56 px). */
  size?: 'md' | 'lg'
  /** Vehículo elegido al lado de la caja (inicio): se añade a la URL de `/buscar`. */
  vehicle?: { makeId?: string; modelId?: string; versionId?: string }
}

const KIND_LABEL: Record<SuggestionKind, string> = {
  CATEGORY: 'Categoría',
  MANUFACTURER: 'Fabricante',
  VEHICLE: 'Vehículo',
  QUERY: 'Búsqueda frecuente',
}

type Option =
  | { type: 'term'; id: string; term: string; kind: SuggestionKind }
  | { type: 'product'; id: string; productId: string; name: string; price: number; partNumber?: string }

/** "/" no debe robar la tecla mientras se escribe en otro campo. */
function isTyping(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
}

/**
 * Caja de búsqueda con sugerencias (patrón combobox con lista): `aria-activedescendant` mueve la opción
 * activa sin sacar el foco del campo. Flechas para recorrer, Enter para elegir, Esc para cerrar.
 * Sincronizada con `/buscar?q=`: en resultados conserva los filtros y vuelve a la primera página.
 */
export function SearchBox({ className, tone = 'light', autoFocus, shortcut, size = 'md', vehicle }: SearchBoxProps) {
  const uid = useId()
  const listId = `${uid}-list`
  const optionId = (index: number) => `${uid}-opt-${index}`
  const containerRef = useRef<HTMLFormElement>(null)
  const inputRef = useRef<HTMLInputElement>(null)
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  const area = useMapArea()

  const onResults = location.pathname === '/buscar'
  const currentQuery = onResults ? (searchParams.get(SEARCH_PARAMS.q) ?? '') : ''
  const [value, setValue] = useState(currentQuery)
  // La búsqueda de la URL manda (atrás/adelante, filtros): el campo se reinicia cuando cambia.
  const [syncedQuery, setSyncedQuery] = useState(currentQuery)
  if (syncedQuery !== currentQuery) {
    setSyncedQuery(currentQuery)
    setValue(currentQuery)
  }

  const [open, setOpen] = useState(false)
  const [active, setActive] = useState(-1)
  const near = area.precision === 'exact' ? area.user : undefined
  const suggestions = useSearchSuggestions(value, near, open)

  const options = useMemo<Option[]>(() => {
    const data = suggestions.data
    if (!data) return []
    return [
      ...data.suggestions.map((item, index): Option => ({ type: 'term', id: `t${index}`, term: item.term, kind: item.kind })),
      ...data.products.map(
        (product): Option => ({
          type: 'product',
          id: `p${product.listingKey}`,
          productId: product.id,
          name: product.name,
          price: product.price,
          partNumber: product.partNumber,
        }),
      ),
    ]
  }, [suggestions.data])

  const expanded = open && value.trim().length >= 2
  const showList = expanded && options.length > 0
  const activeIndex = showList && active < options.length ? active : -1

  useEffect(() => {
    if (!shortcut) return
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key !== '/' || event.ctrlKey || event.metaKey || event.altKey || event.defaultPrevented) return
      if (isTyping(event.target)) return
      event.preventDefault()
      inputRef.current?.focus()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [shortcut])

  useEffect(() => {
    if (!open) return
    const onPointerDown = (event: PointerEvent) => {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  function search(text: string) {
    const query = text.trim()
    const params = onResults ? new URLSearchParams(searchParams) : new URLSearchParams()
    params.delete(SEARCH_PARAMS.page)
    // Una búsqueda nueva vuelve a interpretar el texto.
    params.delete(SEARCH_PARAMS.interpret)
    if (query) params.set(SEARCH_PARAMS.q, query)
    else params.delete(SEARCH_PARAMS.q)
    if (vehicle?.makeId) {
      params.set(SEARCH_PARAMS.makeId, vehicle.makeId)
      if (vehicle.modelId) params.set(SEARCH_PARAMS.modelId, vehicle.modelId)
      if (vehicle.modelId && vehicle.versionId) params.set(SEARCH_PARAMS.versionId, vehicle.versionId)
    }
    const qs = params.toString()
    setOpen(false)
    navigate(qs ? `/buscar?${qs}` : '/buscar')
  }

  function choose(option: Option) {
    setOpen(false)
    if (option.type === 'product') {
      navigate(productPath(option.productId))
      return
    }
    setValue(option.term)
    search(option.term)
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const option = activeIndex >= 0 ? options[activeIndex] : undefined
    if (option) choose(option)
    else search(value)
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      if (!expanded) {
        setOpen(true)
        return
      }
      if (options.length === 0) return
      event.preventDefault()
      const step = event.key === 'ArrowDown' ? 1 : -1
      setActive((current) => {
        const from = current >= options.length ? -1 : current
        // Posiciones -1 (ninguna) … n-1, de forma circular.
        return ((from + 1 + step + options.length + 1) % (options.length + 1)) - 1
      })
    } else if (event.key === 'Escape') {
      if (open) {
        event.preventDefault()
        setOpen(false)
        setActive(-1)
      }
    } else if (event.key === 'Tab') {
      setOpen(false)
    } else if (event.key === 'Home' && showList && activeIndex >= 0) {
      event.preventDefault()
      setActive(0)
    } else if (event.key === 'End' && showList && activeIndex >= 0) {
      event.preventDefault()
      setActive(options.length - 1)
    }
  }

  const dark = tone === 'dark'
  const termOptions = options.filter((option) => option.type === 'term')
  const productOptions = options.filter((option) => option.type === 'product')
  const optionClass = (index: number) =>
    cn(
      'flex min-h-11 cursor-pointer items-center gap-3 px-4 py-2 text-sm text-ap-ink',
      index === activeIndex ? 'bg-ap-paper outline-2 -outline-offset-2 outline-ap-graphite' : 'hover:bg-ap-paper',
    )

  let status = ''
  if (expanded) {
    if (suggestions.loading) status = 'Buscando sugerencias…'
    else if (options.length > 0) status = `${options.length} ${options.length === 1 ? 'sugerencia' : 'sugerencias'}. Usa las flechas para recorrerlas.`
  }

  return (
    <form ref={containerRef} role="search" onSubmit={handleSubmit} className={cn('relative w-full min-w-0', className)}>
      <div
        className={cn(
          'group flex w-full min-w-0 items-center overflow-hidden rounded-md border transition-[background-color,border-color] duration-150',
          size === 'lg' ? 'h-14' : 'h-11 xl:h-10',
          dark
            ? 'border-white/15 bg-white/10 hover:bg-white/15 focus-within:border-transparent focus-within:bg-white focus-within:ring-2 focus-within:ring-ap-red-on-dark focus-within:hover:bg-white'
            : 'border-input bg-ap-white focus-within:border-ap-graphite focus-within:ring-2 focus-within:ring-ap-red',
        )}
      >
        <label htmlFor={`${uid}-input`} className="sr-only">
          Buscar repuestos
        </label>
        <SearchIcon
          className={cn(
            'pointer-events-none ml-3.5 size-4 shrink-0 transition-colors',
            dark ? 'text-ap-on-dark-muted group-focus-within:text-muted-foreground' : 'text-muted-foreground',
          )}
          aria-hidden
        />
        <input
          ref={inputRef}
          id={`${uid}-input`}
          name="q"
          type="text"
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={activeIndex >= 0 ? optionId(activeIndex) : undefined}
          value={value}
          onChange={(event) => {
            setValue(event.target.value)
            setActive(-1)
            setOpen(true)
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={handleKeyDown}
          autoFocus={autoFocus}
          autoComplete="off"
          autoCapitalize="off"
          spellCheck={false}
          enterKeyHint="search"
          placeholder="Busca repuestos, marca o referencia"
          className={cn(
            'h-full min-w-0 flex-1 appearance-none bg-transparent px-2.5 text-base text-ellipsis outline-none sm:text-sm',
            dark
              ? 'text-white placeholder:text-ap-on-dark-muted focus:text-foreground focus:placeholder:text-muted-foreground'
              : 'text-foreground placeholder:text-muted-foreground',
          )}
        />
        {suggestions.loading && expanded ? (
          <LoaderCircleIcon className="mr-2 size-4 shrink-0 animate-spin text-muted-foreground motion-reduce:animate-none" aria-hidden />
        ) : null}
        {value ? (
          <button
            type="button"
            aria-label="Borrar búsqueda"
            onClick={() => {
              setValue('')
              setActive(-1)
              inputRef.current?.focus()
            }}
            className={cn(
              'grid size-11 shrink-0 place-items-center rounded-md transition-colors [--focus:var(--color-ap-red)] focus-visible:-outline-offset-2! xl:size-10',
              dark
                ? 'text-ap-on-dark-muted hover:text-white group-focus-within:text-muted-foreground group-focus-within:hover:text-foreground'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            <XIcon className="size-4" aria-hidden />
          </button>
        ) : shortcut ? (
          <kbd
            aria-hidden
            className="mr-2 hidden h-5 min-w-5 items-center justify-center rounded border border-white/20 px-1 font-mono text-xs text-ap-on-dark-muted group-focus-within:hidden xl:inline-flex"
          >
            /
          </kbd>
        ) : null}
        <PhotoSearchButton
          className={cn(
            'rounded-md xl:size-10',
            dark &&
              'text-ap-on-dark-muted hover:bg-white/10 hover:text-white group-focus-within:text-muted-foreground group-focus-within:hover:bg-ap-paper group-focus-within:hover:text-foreground',
          )}
        />
        <button
          type="submit"
          aria-label="Buscar"
          className="grid h-full w-12 shrink-0 place-items-center bg-primary text-primary-foreground transition-colors [--focus:var(--color-ap-red-hover)] hover:bg-ap-red-hover focus-visible:-outline-offset-2!"
        >
          <SearchIcon className="size-[18px]" aria-hidden />
        </button>
      </div>

      <p role="status" className="sr-only">
        {status}
      </p>

      <div
        id={listId}
        role="listbox"
        aria-label="Sugerencias de búsqueda"
        hidden={!showList}
        className="absolute inset-x-0 top-full z-50 mt-1 max-h-[min(24rem,70svh)] overflow-y-auto overscroll-contain rounded-md border border-ap-line-strong bg-ap-white py-1 text-ap-ink shadow-overlay"
      >
        {termOptions.length > 0 ? (
          <div role="group" aria-label="Búsquedas sugeridas">
            {termOptions.map((option) => {
              const index = options.indexOf(option)
              return (
                <div
                  key={option.id}
                  id={optionId(index)}
                  role="option"
                  aria-selected={index === activeIndex}
                  className={optionClass(index)}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => choose(option)}
                >
                  <SearchIcon className="size-4 shrink-0 text-ap-muted" aria-hidden />
                  <span className="min-w-0 flex-1 truncate">{option.type === 'term' ? option.term : ''}</span>
                  {option.type === 'term' ? <span className="shrink-0 text-xs text-ap-muted">{KIND_LABEL[option.kind]}</span> : null}
                </div>
              )
            })}
          </div>
        ) : null}
        {productOptions.length > 0 ? (
          <div role="group" aria-label="Repuestos">
            {productOptions.map((option) => {
              const index = options.indexOf(option)
              if (option.type !== 'product') return null
              return (
                <div
                  key={option.id}
                  id={optionId(index)}
                  role="option"
                  aria-selected={index === activeIndex}
                  className={optionClass(index)}
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => choose(option)}
                >
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{option.name}</span>
                    {option.partNumber ? <span className="ap-ref block truncate text-xs text-ap-muted">REF. {option.partNumber}</span> : null}
                  </span>
                  <span className="shrink-0 text-sm font-semibold tabular-nums">{formatCOP(option.price)}</span>
                </div>
              )
            })}
          </div>
        ) : null}
      </div>
    </form>
  )
}
