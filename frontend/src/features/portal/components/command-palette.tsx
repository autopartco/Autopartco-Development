import { useEffect, useMemo, useState, type ComponentType, type KeyboardEvent } from 'react'
import { useNavigate } from 'react-router'
import { ClipboardListIcon, CornerDownLeftIcon, PackageIcon, PlusIcon, SearchIcon, ZapIcon, type LucideProps } from 'lucide-react'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { getOrderService } from '../api/order-service'
import { getProductService } from '../api/product-service'
import type { StorePermission } from '../api/types'
import { usePortal } from '../context'
import { fuzzyScore } from '../lib/fuzzy'
import { PORTAL_NAV_GROUPS } from '../lib/sections'

type Group = 'Acciones' | 'Secciones' | 'Productos' | 'Pedidos'

interface PaletteItem {
  id: string
  group: Group
  label: string
  hint?: string
  /** Ruta relativa a `/portal/:storeId/`. */
  to: string
  icon: ComponentType<LucideProps>
  /** Texto sobre el que busca la coincidencia difusa. */
  search: string
}

const GROUP_ORDER: Group[] = ['Acciones', 'Secciones', 'Productos', 'Pedidos']
const GROUP_LIMIT: Record<Group, number> = { Acciones: 5, Secciones: 8, Productos: 6, Pedidos: 6 }

const isMac = () => typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)

/** Botón visible de la cabecera que abre la paleta. */
export function CommandPaletteButton({ onClick }: { onClick: () => void }) {
  return (
    <Button
      type="button"
      variant="ghost"
      onClick={onClick}
      aria-label="Buscar o ir a… (Ctrl K)"
      aria-keyshortcuts="Control+K Meta+K"
      className="h-11 shrink-0 gap-2 px-3 text-white hover:bg-header-hover hover:text-white sm:border sm:border-white/15"
    >
      <SearchIcon className="size-[1.125rem]" aria-hidden />
      <span className="hidden sm:inline">Buscar o ir a…</span>
      <kbd aria-hidden className="hidden rounded border border-white/20 px-1.5 py-0.5 font-mono text-[0.6875rem] text-asphalt-muted md:inline">
        {isMac() ? '⌘K' : 'Ctrl K'}
      </kbd>
    </Button>
  )
}

function usePaletteItems(): { items: PaletteItem[]; loading: boolean; failed: boolean } {
  const { storeId, can } = usePortal()
  const products = useAsync(`portal:${storeId}:palette-products`, async () => (can('PRODUCT_READ') ? (await getProductService()).list(storeId) : []))
  const orders = useAsync(`portal:${storeId}:palette-orders`, async () => (can('ORDER_READ') ? (await getOrderService()).list(storeId) : []))

  const items = useMemo(() => {
    const list: PaletteItem[] = []
    const action = (id: string, label: string, to: string, permission: StorePermission, icon: ComponentType<LucideProps>, extra = '') => {
      if (can(permission)) list.push({ id, group: 'Acciones', label, to, icon, search: `${label} ${extra}` })
    }
    action('nuevo-producto', 'Nuevo producto', 'productos/nuevo', 'PRODUCT_CREATE', PlusIcon, 'agregar crear publicar')
    action('pedidos-pendientes', 'Ver pedidos pendientes', 'pedidos?estado=PENDING', 'ORDER_READ', ClipboardListIcon, 'atender confirmar')
    action('stock-bajo', 'Ver productos con stock bajo', 'inventario?stock=bajo', 'PRODUCT_READ', ZapIcon, 'reponer inventario')
    action('agotados', 'Ver productos agotados', 'inventario?stock=agotado', 'PRODUCT_READ', PackageIcon, 'reponer inventario sin stock')

    for (const group of PORTAL_NAV_GROUPS) {
      for (const section of group.items) {
        if (can(section.permission)) {
          list.push({ id: `seccion-${section.to}`, group: 'Secciones', label: section.label, hint: group.label, to: section.to, icon: section.icon, search: `${section.label} ${section.keywords ?? ''}` })
        }
      }
    }

    const seen = new Set<string>()
    for (const product of products.data ?? []) {
      if (seen.has(product.id)) continue
      seen.add(product.id)
      list.push({
        id: `producto-${product.id}`,
        group: 'Productos',
        label: product.name,
        hint: [product.sku, product.partNumber].filter(Boolean).join(' · ') || undefined,
        to: `productos/${encodeURIComponent(product.id)}`,
        icon: PackageIcon,
        search: `${product.name} ${product.sku ?? ''} ${product.partNumber ?? ''}`,
      })
    }
    for (const order of orders.data ?? []) {
      list.push({
        id: `pedido-${order.saleId}`,
        group: 'Pedidos',
        label: `Pedido #${order.orderNumber}`,
        hint: order.customerName ?? undefined,
        to: `pedidos/${encodeURIComponent(order.saleId)}`,
        icon: ClipboardListIcon,
        search: `pedido ${order.orderNumber} ${order.customerName ?? ''}`,
      })
    }
    return list
  }, [can, products.data, orders.data])

  return {
    items,
    loading: products.status === 'loading' || orders.status === 'loading',
    failed: products.status === 'error' || orders.status === 'error',
  }
}

function PaletteBody({ onClose }: { onClose: () => void }) {
  const { storeId } = usePortal()
  const navigate = useNavigate()
  const { items, loading, failed } = usePaletteItems()
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)

  const results = useMemo(() => {
    const term = query.trim()
    const pool = term ? items : items.filter((item) => item.group === 'Acciones' || item.group === 'Secciones')
    const scored = pool
      .map((item) => {
        // El título manda: una coincidencia en él supera siempre a una en palabras clave o datos extra.
        const inLabel = fuzzyScore(term, item.label)
        const inExtra = inLabel === null ? fuzzyScore(term, item.search) : null
        const score = inLabel !== null ? inLabel + 300 : inExtra !== null ? inExtra / 2 : null
        return { item, score }
      })
      .filter((entry): entry is { item: PaletteItem; score: number } => entry.score !== null)
    const grouped = GROUP_ORDER.map((group) =>
      scored
        .filter((entry) => entry.item.group === group)
        .toSorted((a, b) => b.score - a.score)
        .slice(0, GROUP_LIMIT[group]),
    ).filter((entries) => entries.length > 0)
    // Con texto escrito, el grupo con la mejor coincidencia va primero.
    if (term) grouped.sort((a, b) => (b[0]?.score ?? 0) - (a[0]?.score ?? 0))
    return grouped.map((entries) => entries.map((entry) => entry.item))
  }, [items, query])

  const flat = results.flat()
  const current = flat.length === 0 ? -1 : Math.min(active, flat.length - 1)
  const activeId = current >= 0 ? `cmd-${flat[current]?.id}` : undefined

  useEffect(() => {
    if (activeId) document.getElementById(activeId)?.scrollIntoView({ block: 'nearest' })
  }, [activeId])

  function run(item: PaletteItem | undefined) {
    if (!item) return
    onClose()
    void navigate(`/portal/${encodeURIComponent(storeId)}/${item.to}`)
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (flat.length === 0) return
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      setActive((current + 1) % flat.length)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      setActive((current - 1 + flat.length) % flat.length)
    } else if (event.key === 'Enter') {
      event.preventDefault()
      run(flat[current])
    }
  }

  return (
    <>
      <div className="flex items-center gap-3 border-b border-border px-4">
        <SearchIcon className="size-5 shrink-0 text-muted-foreground" aria-hidden />
        <input
          autoFocus
          role="combobox"
          aria-label="Buscar secciones, productos y pedidos"
          aria-expanded
          aria-controls="cmd-listbox"
          aria-autocomplete="list"
          aria-activedescendant={activeId}
          autoComplete="off"
          spellCheck={false}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setActive(0)
          }}
          onKeyDown={onKeyDown}
          placeholder="Busca una sección, un producto o un pedido…"
          className="h-14 min-w-0 flex-1 bg-transparent text-base outline-none placeholder:text-muted-foreground"
        />
      </div>

      <div id="cmd-listbox" role="listbox" aria-label="Resultados" className="max-h-[min(24rem,50svh)] overflow-y-auto p-2">
        {results.map((entries) => {
          const group = entries[0]?.group
          return (
            <div key={group} role="group" aria-labelledby={`cmd-group-${group}`} className="pb-1">
              <p id={`cmd-group-${group}`} className="label-mono px-2 pt-2 pb-1 text-muted-foreground">
                {group}
              </p>
              {entries.map((item) => {
                const selected = flat[current]?.id === item.id
                return (
                  <div
                    key={item.id}
                    id={`cmd-${item.id}`}
                    role="option"
                    aria-selected={selected}
                    onPointerMove={() => setActive(flat.findIndex((entry) => entry.id === item.id))}
                    onClick={() => run(item)}
                    className={cn('flex min-h-11 cursor-pointer items-center gap-3 rounded-lg px-2.5 py-2', selected && 'bg-muted')}
                  >
                    <item.icon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                    <span className="min-w-0 flex-1 truncate text-sm font-medium">{item.label}</span>
                    {item.hint ? <span className="truncate text-xs text-muted-foreground">{item.hint}</span> : null}
                    {selected ? <CornerDownLeftIcon className="size-3.5 shrink-0 text-muted-foreground" aria-hidden /> : null}
                  </div>
                )
              })}
            </div>
          )
        })}
        {flat.length === 0 ? (
          <p className="px-3 py-8 text-center text-sm text-muted-foreground">
            {loading && query ? 'Cargando productos y pedidos…' : `Nada coincide con “${query}”. Prueba con otra palabra o el número de pedido.`}
          </p>
        ) : null}
      </div>

      <p role="status" className="sr-only">
        {flat.length === 0 ? 'Sin resultados' : `${flat.length} resultados`}
      </p>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-border px-4 py-2.5 text-xs text-muted-foreground">
        <span>↑↓ para moverte</span>
        <span>Enter para abrir</span>
        <span>Esc para cerrar</span>
        {loading ? <span className="ml-auto">Cargando productos y pedidos…</span> : null}
        {failed ? <span className="ml-auto text-stock-low">No pudimos cargar productos o pedidos; las secciones sí funcionan.</span> : null}
      </div>
    </>
  )
}

/** Paleta de comandos del portal: salta a secciones, productos y pedidos o lanza acciones rápidas. */
export function CommandPalette({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent showCloseButton={false} className="top-[10svh] max-w-xl translate-y-0 gap-0 p-0 sm:p-0">
        <DialogTitle className="sr-only">Buscar o ir a…</DialogTitle>
        <DialogDescription className="sr-only">Escribe para buscar secciones, productos y pedidos. Usa las flechas y Enter.</DialogDescription>
        <PaletteBody onClose={() => onOpenChange(false)} />
      </DialogContent>
    </Dialog>
  )
}
