import { useId, useMemo, useState, type ReactNode } from 'react'
import { ArrowDownIcon, ArrowUpDownIcon, ArrowUpIcon, ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

export interface DataColumn<T> {
  id: string
  header: string
  cell: (row: T) => ReactNode
  /** Celda que nombra la fila (`th scope="row"`). Una por tabla, normalmente la primera. */
  rowHeader?: boolean
  /** Con valor de orden, el encabezado es un botón que ordena en el cliente. */
  sortValue?: (row: T) => string | number
  align?: 'start' | 'end'
  /** Columna de acciones: el encabezado se oculta visualmente pero se lee. */
  actions?: boolean
  className?: string
}

interface DataTableProps<T> {
  /** Nombre de la tabla: lo leen los lectores de pantalla. */
  caption: string
  columns: DataColumn<T>[]
  rows: T[]
  getRowId: (row: T) => string
  defaultSort?: { columnId: string; direction: 'asc' | 'desc' }
  className?: string
  /** Con valor, la tabla pagina en el cliente (controles «Anterior / Siguiente» bajo la tabla). */
  pageSize?: number
  /**
   * Fila completa clicable (ratón/tacto). No sustituye al enlace de la fila: el destino accesible
   * por teclado debe seguir siendo un `<a>` dentro de la fila. Los clics en enlaces, botones y
   * campos de la fila no la activan.
   */
  onRowClick?: (row: T) => void
}

const INTERACTIVE = 'a,button,input,select,textarea,label,summary,[role="button"],[role="link"],[data-row-click-ignore]'

const collator = new Intl.Collator('es-CO', { numeric: true, sensitivity: 'base' })

/**
 * Tabla de datos accesible: `caption`, `th scope`, `aria-sort` y orden en el cliente.
 * Por debajo de 40rem de ancho (del contenedor, no de la pantalla) cada fila pasa a tarjeta y
 * cada celda muestra su encabezado; el scroll horizontal es alcanzable con teclado.
 */
export function DataTable<T>({ caption, columns, rows, getRowId, defaultSort, className, pageSize, onRowClick }: DataTableProps<T>) {
  const [sort, setSort] = useState(defaultSort)
  const [page, setPage] = useState(0)
  const captionId = useId()

  const sorted = useMemo(() => {
    const column = columns.find((candidate) => candidate.id === sort?.columnId)
    if (!column?.sortValue || !sort) return rows
    const value = column.sortValue
    const factor = sort.direction === 'asc' ? 1 : -1
    return rows.toSorted((a, b) => {
      const left = value(a)
      const right = value(b)
      if (typeof left === 'number' && typeof right === 'number') return (left - right) * factor
      return collator.compare(String(left), String(right)) * factor
    })
  }, [columns, rows, sort])

  const pages = pageSize ? Math.max(1, Math.ceil(sorted.length / pageSize)) : 1
  const current = Math.min(page, pages - 1)
  const visible = pageSize ? sorted.slice(current * pageSize, (current + 1) * pageSize) : sorted

  function toggle(columnId: string) {
    setPage(0)
    setSort((current) =>
      current?.columnId === columnId
        ? { columnId, direction: current.direction === 'asc' ? 'desc' : 'asc' }
        : { columnId, direction: 'asc' },
    )
  }

  const sortedColumn = columns.find((column) => column.id === sort?.columnId)

  return (
    <div className="flex flex-col gap-3">
    <div
      // Región enfocable para poder desplazar con teclado cuando la tabla no cabe.
      tabIndex={0}
      role="region"
      aria-labelledby={captionId}
      className={cn('@container overflow-x-auto rounded-xl border border-ap-line bg-ap-white focus-visible:outline-offset-0', className)}
    >
      <Table className="@max-[40rem]:block">
        <caption id={captionId} className="sr-only">
          {caption}
        </caption>
        <TableHeader className="@max-[40rem]:hidden">
          <TableRow>
            {columns.map((column) => {
              const active = sort?.columnId === column.id
              const ariaSort = active ? (sort?.direction === 'asc' ? 'ascending' : 'descending') : column.sortValue ? 'none' : undefined
              return (
                <TableHead key={column.id} aria-sort={ariaSort} numeric={column.align === 'end'} className={column.className}>
                  {column.actions ? (
                    <span className="sr-only">{column.header}</span>
                  ) : column.sortValue ? (
                    <button
                      type="button"
                      onClick={() => toggle(column.id)}
                      className={cn(
                        '-mx-2 inline-flex h-9 items-center gap-1.5 rounded-md px-2 font-semibold hover:bg-ap-white hover:text-ap-ink pointer-coarse:h-11',
                        column.align === 'end' && 'flex-row-reverse',
                      )}
                    >
                      {column.header}
                      {active ? (
                        sort?.direction === 'asc' ? <ArrowUpIcon className="size-3.5" aria-hidden /> : <ArrowDownIcon className="size-3.5" aria-hidden />
                      ) : (
                        <ArrowUpDownIcon className="size-3.5 opacity-60" aria-hidden />
                      )}
                    </button>
                  ) : (
                    column.header
                  )}
                </TableHead>
              )
            })}
          </TableRow>
        </TableHeader>
        <TableBody className="@max-[40rem]:block @max-[40rem]:space-y-3 @max-[40rem]:p-3">
          {visible.map((row) => (
            <TableRow
              key={getRowId(row)}
              onClick={
                onRowClick
                  ? (event) => {
                      if (event.defaultPrevented || (event.target as HTMLElement).closest(INTERACTIVE)) return
                      // Selección de texto en curso: no navegar.
                      if (window.getSelection()?.toString()) return
                      onRowClick(row)
                    }
                  : undefined
              }
              className={cn(
                onRowClick && 'cursor-pointer',
                'hover:bg-ap-paper/60 @max-[40rem]:mb-0 @max-[40rem]:block @max-[40rem]:overflow-hidden @max-[40rem]:rounded-lg @max-[40rem]:border @max-[40rem]:border-ap-line @max-[40rem]:hover:bg-transparent',
              )}
            >
              {columns.map((column) => {
                const content = column.cell(row)
                const common = cn(
                  column.align === 'end' && 'text-right tabular-nums @max-[40rem]:text-left',
                  // Tarjeta: etiqueta a la izquierda, valor a la derecha.
                  '@max-[40rem]:grid @max-[40rem]:grid-cols-[minmax(6rem,35%)_1fr] @max-[40rem]:items-center @max-[40rem]:gap-3 @max-[40rem]:border-b-0 @max-[40rem]:px-3 @max-[40rem]:py-2',
                  !column.actions && "@max-[40rem]:before:text-xs @max-[40rem]:before:font-semibold @max-[40rem]:before:text-ap-muted @max-[40rem]:before:content-[attr(data-label)]",
                  column.actions && '@max-[40rem]:grid-cols-1 @max-[40rem]:border-t @max-[40rem]:border-ap-line @max-[40rem]:bg-ap-paper',
                  column.className,
                )
                return column.rowHeader ? (
                  <TableHead
                    key={column.id}
                    scope="row"
                    data-label={column.header}
                    className={cn(
                      'h-auto border-b border-ap-line bg-transparent text-sm font-semibold whitespace-normal text-ap-ink',
                      common,
                      '@max-[40rem]:grid-cols-1 @max-[40rem]:bg-ap-paper @max-[40rem]:before:hidden',
                    )}
                  >
                    {content}
                  </TableHead>
                ) : (
                  <TableCell key={column.id} data-label={column.header} className={common}>
                    {content}
                  </TableCell>
                )
              })}
            </TableRow>
          ))}
        </TableBody>
      </Table>
      <p className="sr-only" aria-live="polite">
        {sortedColumn ? `Tabla ordenada por ${sortedColumn.header}, ${sort?.direction === 'asc' ? 'ascendente' : 'descendente'}.` : ''}
      </p>
    </div>
    {pageSize && pages > 1 ? (
      <nav aria-label={`Paginación: ${caption}`} className="flex flex-wrap items-center justify-between gap-3 text-sm">
        <p role="status" className="text-ap-muted tabular-nums">
          Mostrando {current * pageSize + 1} a {Math.min((current + 1) * pageSize, sorted.length)} de {sorted.length}
        </p>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setPage(current - 1)}
            disabled={current === 0}
            className="inline-flex h-11 items-center gap-1 rounded-md border border-input bg-ap-white px-3 font-semibold text-ap-graphite transition-colors hover:border-ap-graphite hover:bg-ap-paper disabled:pointer-events-none disabled:opacity-50"
          >
            <ChevronLeftIcon className="size-4" aria-hidden />
            Anterior
          </button>
          <span className="px-1 text-ap-muted tabular-nums">
            Página {current + 1} de {pages}
          </span>
          <button
            type="button"
            onClick={() => setPage(current + 1)}
            disabled={current >= pages - 1}
            className="inline-flex h-11 items-center gap-1 rounded-md border border-input bg-ap-white px-3 font-semibold text-ap-graphite transition-colors hover:border-ap-graphite hover:bg-ap-paper disabled:pointer-events-none disabled:opacity-50"
          >
            Siguiente
            <ChevronRightIcon className="size-4" aria-hidden />
          </button>
        </div>
      </nav>
    ) : null}
    </div>
  )
}
