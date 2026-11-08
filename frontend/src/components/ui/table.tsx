import * as React from "react"
import { cn } from "cn"

/**
 * Primitivas de tabla semántica (§8.5). El contenedor con scroll es focusable para que el
 * teclado pueda desplazarlo; debe llevar `aria-label` (lo pone `DataTable`).
 *
 * - Números y precios: `numeric` en `TableHead`/`TableCell` (o la utilidad `ap-num`)
 *   alinea a la derecha con cifras tabulares.
 * - Fila seleccionada: `data-state="selected"` o `aria-selected` en `TableRow` pinta el
 *   fondo `ap-red-tint` y una línea de acento roja a la izquierda.
 */
function Table({ className, ...props }: React.ComponentProps<"table">) {
  return <table data-slot="table" className={cn("w-full caption-bottom border-separate border-spacing-0 text-sm text-ap-ink", className)} {...props} />
}

function TableHeader({ className, ...props }: React.ComponentProps<"thead">) {
  return <thead data-slot="table-header" className={cn(className)} {...props} />
}

function TableBody({ className, ...props }: React.ComponentProps<"tbody">) {
  return <tbody data-slot="table-body" className={cn(className)} {...props} />
}

function TableRow({ className, ...props }: React.ComponentProps<"tr">) {
  return (
    <tr
      data-slot="table-row"
      className={cn(
        "group/row data-[state=selected]:bg-ap-red-tint aria-selected:bg-ap-red-tint data-[state=selected]:*:first:shadow-[inset_3px_0_0_var(--ap-red)] aria-selected:*:first:shadow-[inset_3px_0_0_var(--ap-red)]",
        className
      )}
      {...props}
    />
  )
}

/** Clase compartida para celdas numéricas (cantidades, precios). */
const TABLE_NUMERIC_CLASS = "text-right tabular-nums"

function TableHead({ className, scope = "col", numeric, ...props }: React.ComponentProps<"th"> & { numeric?: boolean }) {
  return (
    <th
      data-slot="table-head"
      scope={scope}
      className={cn(
        "h-10 border-b border-ap-line-strong bg-ap-paper px-3 text-left align-middle text-xs font-semibold whitespace-nowrap text-ap-graphite",
        numeric && TABLE_NUMERIC_CLASS,
        className
      )}
      {...props}
    />
  )
}

function TableCell({ className, numeric, ...props }: React.ComponentProps<"td"> & { numeric?: boolean }) {
  return (
    <td
      data-slot="table-cell"
      className={cn("border-b border-ap-line px-3 py-3 align-middle", numeric && TABLE_NUMERIC_CLASS, className)}
      {...props}
    />
  )
}

function TableCaption({ className, ...props }: React.ComponentProps<"caption">) {
  return <caption data-slot="table-caption" className={cn("py-3 text-left text-sm text-ap-muted", className)} {...props} />
}

export { Table, TableHeader, TableBody, TableRow, TableHead, TableCell, TableCaption, TABLE_NUMERIC_CLASS }
