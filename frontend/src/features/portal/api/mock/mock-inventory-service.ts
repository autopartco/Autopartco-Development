import { ApiError } from '@/lib/api'
import type { InventoryService } from '../inventory-service'
import type {
  CsvImportError,
  CsvImportResult,
  MovementReason,
  Page,
  ProductRow,
  StockMovement,
  StockTransfer,
  StockTransferItem,
  StockTransferSummary,
  TransferInput,
} from '../types'
import { csvFilename } from '../../lib/inventory'
import { type MockStore, getStore, newId, requirePermission, simulateNetwork } from './mock-state'

const REASONS: (MovementReason & { label: string })[] = [
  { code: 'COUNT_ADJUSTMENT', label: 'Conteo físico', direction: 'ANY' },
  { code: 'MANUAL_CORRECTION', label: 'Corrección manual', direction: 'ANY' },
  { code: 'DAMAGE', label: 'Producto dañado', direction: 'OUT' },
  { code: 'LOSS_THEFT', label: 'Pérdida o robo', direction: 'OUT' },
  { code: 'OBSOLESCENCE', label: 'Obsolescencia', direction: 'OUT' },
]

const REASON_LABEL: Record<string, string> = {
  ...Object.fromEntries(REASONS.map((reason) => [reason.code, reason.label])),
  TRANSFER_OUT: 'Transferencia (salida)',
  TRANSFER_IN: 'Transferencia (entrada)',
  CSV_IMPORT: 'Importación CSV',
}

const DEFAULT_MIN_STOCK = 3
const MAX_TRANSFER_ITEMS = 500
const MAX_ALL_ITEMS = 10_000
const CSV_MAX_BYTES = 2 * 1024 * 1024
const CSV_MAX_ROWS = 5_000

const movementsByStore = new Map<string, StockMovement[]>()
const transfersByStore = new Map<string, StockTransfer[]>()
/** `storeId:clave` -> id de la transferencia ya hecha (idempotencia). */
const transferKeys = new Map<string, string>()

const movementsOf = (storeId: string) => movementsByStore.get(storeId) ?? movementsByStore.set(storeId, []).get(storeId) ?? []
const transfersOf = (storeId: string) => transfersByStore.get(storeId) ?? transfersByStore.set(storeId, []).get(storeId) ?? []

function bad(code: string, message: string, details?: unknown): ApiError {
  return new ApiError(message, { status: 400, code, details })
}

function notFound(code: string, message: string, details?: unknown): ApiError {
  return new ApiError(message, { status: 404, code, details })
}

function conflict(code: string, message: string, details?: unknown): ApiError {
  return new ApiError(message, { status: 409, code, details })
}

const actorOf = (store: MockStore) => {
  const member = store.members[0]
  return member ? `${member.name} ${member.surname}` : null
}

function activeBranch(store: MockStore, branchId: string) {
  const branch = store.branches.find((candidate) => candidate.id === branchId && candidate.state === 'ACTIVE')
  if (!branch) throw notFound('BRANCH_NOT_FOUND', 'No encontramos esa sucursal activa en tu almacén.')
  return branch
}

function listing(store: MockStore, branchId: string, productId: string): ProductRow | undefined {
  return store.products.find((row) => row.branchId === branchId && row.id === productId)
}

function record(
  store: MockStore,
  row: ProductRow,
  previous: number,
  next: number,
  reasonCode: string,
  extra: { referenceType?: string; referenceId?: string; notes?: string | null } = {},
): StockMovement {
  const movement: StockMovement = {
    id: newId('mov'),
    occurredAt: new Date().toISOString(),
    branchId: row.branchId,
    branchName: row.branchName,
    productId: row.id,
    productName: row.name,
    sku: row.sku,
    movementType: next > previous ? 'IN' : 'OUT',
    quantity: Math.abs(next - previous),
    previousStock: previous,
    newStock: next,
    reasonCode,
    reasonLabel: REASON_LABEL[reasonCode] ?? reasonCode,
    actorName: actorOf(store),
    referenceType: extra.referenceType ?? null,
    referenceId: extra.referenceId ?? null,
    notes: extra.notes ?? null,
  }
  movementsOf(store.summary.storeId).push(movement)
  return movement
}

function paginate<T>(items: T[], page: number, size: number): Page<T> {
  if (!Number.isInteger(page) || page < 1) throw bad('VALIDATION_ERROR', 'La página debe ser mayor o igual a 1.')
  if (!Number.isInteger(size) || size < 1 || size > 100) throw bad('VALIDATION_ERROR', 'El tamaño de página debe estar entre 1 y 100.')
  const start = (page - 1) * size
  return { items: items.slice(start, start + size), page, size, total: items.length, totalPages: Math.ceil(items.length / size) }
}

function summaryOf(transfer: StockTransfer): StockTransferSummary {
  const { items: _items, ...summary } = transfer
  void _items
  return summary
}

function runTransfer(store: MockStore, input: TransferInput): StockTransfer {
  const storeId = store.summary.storeId
  if (input.fromBranchId === input.toBranchId) throw bad('SAME_BRANCH', 'La sucursal de origen y la de destino deben ser distintas.')
  const from = activeBranch(store, input.fromBranchId)
  const to = activeBranch(store, input.toBranchId)

  let lines: { productId: string; quantity: number }[]
  if (input.mode === 'ALL') {
    lines = store.products
      .filter((row) => row.branchId === from.id && row.quantity > 0)
      .map((row) => ({ productId: row.id, quantity: row.quantity }))
    if (lines.length === 0) throw conflict('TRANSFER_EMPTY', 'La sucursal de origen no tiene existencias para transferir.')
    if (lines.length > MAX_ALL_ITEMS) throw conflict('TRANSFER_TOO_LARGE', 'La sucursal tiene demasiados productos para transferirlos de una vez.')
  } else {
    const items = input.items ?? []
    if (items.length === 0 || items.length > MAX_TRANSFER_ITEMS) {
      throw bad('VALIDATION_ERROR', `Indica entre 1 y ${MAX_TRANSFER_ITEMS} productos a transferir.`)
    }
    if (items.some((item) => !Number.isInteger(item.quantity) || item.quantity < 1)) {
      throw bad('INVALID_QUANTITY', 'Cada producto debe tener una cantidad mayor que 0.')
    }
    const seen = new Set<string>()
    const duplicated = items.filter((item) => (seen.has(item.productId) ? true : (seen.add(item.productId), false)))
    if (duplicated.length > 0) throw bad('DUPLICATE_PRODUCT', 'Un producto aparece más de una vez.', duplicated.map((item) => item.productId))
    if (items.some((item) => !listing(store, from.id, item.productId))) {
      throw notFound('LISTING_NOT_FOUND', 'Algún producto no está listado en la sucursal de origen.')
    }
    const short = items.flatMap((item) => {
      const available = listing(store, from.id, item.productId)?.quantity ?? 0
      return item.quantity > available ? [{ productId: item.productId, requested: item.quantity, available }] : []
    })
    if (short.length > 0) throw conflict('INSUFFICIENT_STOCK', 'No hay existencias suficientes en la sucursal de origen.', short)
    lines = items
  }

  const transferId = newId('trf')
  const items: StockTransferItem[] = lines.map((line) => {
    const source = listing(store, from.id, line.productId) as ProductRow
    let target = listing(store, to.id, line.productId)
    if (!target) {
      target = { ...source, branchId: to.id, branchName: to.name, quantity: 0, minStock: null }
      store.products.push(target)
    }
    const fromBefore = source.quantity
    const toBefore = target.quantity
    source.quantity = fromBefore - line.quantity
    target.quantity = toBefore + line.quantity
    record(store, source, fromBefore, source.quantity, 'TRANSFER_OUT', { referenceType: 'TRANSFER_ITEM', referenceId: transferId })
    record(store, target, toBefore, target.quantity, 'TRANSFER_IN', { referenceType: 'TRANSFER_ITEM', referenceId: transferId })
    return { productId: line.productId, productName: source.name, quantity: line.quantity, fromStock: source.quantity, toStock: target.quantity }
  })

  const transfer: StockTransfer = {
    id: transferId,
    fromBranchId: from.id,
    fromBranchName: from.name,
    toBranchId: to.id,
    toBranchName: to.name,
    mode: input.mode,
    totalUnits: items.reduce((sum, item) => sum + item.quantity, 0),
    itemCount: items.length,
    notes: input.notes?.trim() || null,
    actorName: actorOf(store),
    createdAt: new Date().toISOString(),
    items,
  }
  transfersOf(storeId).push(transfer)
  return transfer
}

// ------------------------------------------------------------------ CSV

const BOM = '﻿'

/** Neutraliza celdas que un programa de hojas de cálculo leería como fórmula. */
function cell(value: string | number | null | undefined): string {
  let text = value === null || value === undefined ? '' : String(value)
  if (/^[=+\-@]/.test(text)) text = `'${text}`
  return /[;"\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
}

function parseCsv(text: string): { line: number; cells: string[] }[] {
  const records: { line: number; cells: string[] }[] = []
  let cells: string[] = []
  let current = ''
  let quoted = false
  let line = 1
  let startLine = 1
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i]
    if (quoted) {
      if (char === '"' && text[i + 1] === '"') {
        current += '"'
        i += 1
      } else if (char === '"') quoted = false
      else {
        if (char === '\n') line += 1
        current += char
      }
    } else if (char === '"') quoted = true
    else if (char === ';') {
      cells.push(current)
      current = ''
    } else if (char === '\n' || char === '\r') {
      if (char === '\r' && text[i + 1] === '\n') i += 1
      cells.push(current)
      if (cells.some((value) => value.trim() !== '')) records.push({ line: startLine, cells })
      cells = []
      current = ''
      line += 1
      startLine = line
    } else current += char
  }
  if (quoted) throw bad('INVALID_CSV', 'El archivo tiene comillas sin cerrar.')
  cells.push(current)
  if (cells.some((value) => value.trim() !== '')) records.push({ line: startLine, cells })
  if (records.length > CSV_MAX_ROWS + 1) throw bad('TOO_MANY_ROWS', 'El archivo supera el máximo de filas permitido.')
  return records
}

const headerKey = (value: string) =>
  value.replace(BOM, '').trim().normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

function parseWhole(value: string): number | undefined {
  return /^\d{1,9}$/.test(value) ? Number(value) : undefined
}

function parsePrice(value: string): number | undefined {
  let text = value
  if (/^\d{1,3}([.,]\d{3})+$/.test(text)) text = text.replace(/[.,]/g, '')
  if (!/^\d{1,10}([.,]\d{1,2})?$/.test(text)) return undefined
  const price = Number(text.replace(',', '.'))
  return price > 0 ? price : undefined
}

async function processCsv(store: MockStore, text: string, dryRun: boolean): Promise<CsvImportResult> {
  const records = parseCsv(text)
  const [head, ...data] = records
  if (!head) throw bad('EMPTY_FILE', 'El archivo no tiene datos.')
  const columns = new Map<string, number>()
  head.cells.forEach((name, index) => {
    if (!columns.has(headerKey(name))) columns.set(headerKey(name), index)
  })
  if (!columns.has('sucursal') || !(columns.has('sku') || columns.has('referencia'))) {
    throw bad('INVALID_HEADER', 'La cabecera debe incluir sucursal y sku o referencia.')
  }
  if (!(columns.has('stock') || columns.has('precio') || columns.has('stock_minimo'))) {
    throw bad('INVALID_HEADER', 'La cabecera debe incluir al menos una de: stock, precio, stock_minimo.')
  }
  const value = (cells: string[], name: string) => {
    const index = columns.get(name)
    return index === undefined ? '' : (cells[index] ?? '').trim()
  }

  const errors: CsvImportError[] = []
  const seen = new Set<ProductRow>()
  const changes: { row: ProductRow; stock?: number; price?: number; minStock?: number }[] = []
  for (const { line, cells } of data) {
    const fail = (code: string, message: string) => errors.push({ line, code, message })
    const before = errors.length
    const branchName = value(cells, 'sucursal')
    const sku = value(cells, 'sku')
    const key = sku || value(cells, 'referencia')
    if (!branchName) fail('MISSING_BRANCH', 'Falta la sucursal.')
    if (!key) fail('MISSING_PRODUCT_KEY', 'Falta el SKU o la referencia.')
    const stockText = value(cells, 'stock')
    const minText = value(cells, 'stock_minimo')
    const priceText = value(cells, 'precio')
    const stock = stockText ? parseWhole(stockText) : undefined
    const baseText = value(cells, 'stock_base')
    const base = baseText ? parseWhole(baseText) : undefined
    const minStock = minText ? parseWhole(minText) : undefined
    const price = priceText ? parsePrice(priceText) : undefined
    if (stockText && stock === undefined) fail('INVALID_STOCK', 'El stock debe ser un entero mayor o igual a 0.')
    if (baseText && base === undefined) fail('INVALID_STOCK', 'El stock base debe ser un entero mayor o igual a 0.')
    if (minText && minStock === undefined) fail('INVALID_MIN_STOCK', 'El stock mínimo debe ser un entero mayor o igual a 0.')
    if (priceText && price === undefined) fail('INVALID_PRICE', 'El precio debe ser un valor en pesos mayor que 0.')
    if (errors.length > before) continue

    const matches = store.products.filter(
      (row) =>
        row.branchName.toLowerCase() === branchName.toLowerCase() &&
        (sku ? row.sku?.toLowerCase() === key.toLowerCase() : row.partNumber?.toLowerCase() === key.toLowerCase()),
    )
    if (matches.length === 0) {
      fail('PRODUCT_NOT_FOUND', `No existe un producto con SKU o referencia ${key} en la sucursal ${branchName}.`)
      continue
    }
    if (matches.length > 1) {
      fail('AMBIGUOUS_PRODUCT', `Hay más de un producto con ${key} en la sucursal ${branchName}.`)
      continue
    }
    const row = matches[0] as ProductRow
    if (seen.has(row)) {
      fail('DUPLICATE_ROW', 'El producto ya aparece en otra fila para esa sucursal.')
      continue
    }
    seen.add(row)
    // Con stock_base se aplica la diferencia (stock - stock_base) sobre el stock actual.
    let target = stock
    if (stock !== undefined && base !== undefined) {
      target = row.quantity + (stock - base)
      if (target < 0) {
        fail('STOCK_CHANGED', 'El stock cambió desde la exportación y no alcanza; exporta de nuevo.')
        continue
      }
    }
    const changed =
      (target !== undefined && target !== row.quantity) ||
      (price !== undefined && price !== row.price) ||
      (minStock !== undefined && minStock !== (row.minStock ?? DEFAULT_MIN_STOCK))
    if (changed) changes.push({ row, stock: target, price, minStock })
  }

  if (!dryRun && errors.length === 0) {
    const jobId = newId('imp')
    for (const { row, stock, price, minStock } of changes) {
      if (price !== undefined) {
        row.price = price
        row.effectivePrice = Math.max(0, price - row.discountAmount)
      }
      if (minStock !== undefined) row.minStock = minStock
      if (stock !== undefined && stock !== row.quantity) {
        const previous = row.quantity
        row.quantity = stock
        record(store, row, previous, stock, 'CSV_IMPORT', { referenceType: 'IMPORT_JOB', referenceId: jobId })
      }
    }
  }
  return { rows: data.length, updated: !dryRun && errors.length > 0 ? 0 : changes.length, errors }
}

/** Inventario simulado: aplica las mismas reglas y códigos de error que el servidor. */
export function createMockInventoryService(): InventoryService {
  return {
    async listReasons(storeId) {
      await simulateNetwork(250)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_READ')
      return REASONS.map((reason) => ({ ...reason }))
    },

    async adjust(storeId, input) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'INVENTORY_ADJUST')
      const reason = REASONS.find((candidate) => candidate.code === input.reasonCode)
      if (!reason) throw bad('INVALID_REASON', 'El motivo de ajuste no es válido.')
      if (!Number.isInteger(input.quantity) || (input.mode === 'SET' ? input.quantity < 0 : input.quantity === 0)) {
        throw bad('INVALID_QUANTITY', input.mode === 'SET' ? 'La cantidad debe ser 0 o más.' : 'La variación no puede ser 0.')
      }
      const row = listing(store, input.branchId, input.productId)
      if (!row) throw notFound('LISTING_NOT_FOUND', 'El producto no está listado en esa sucursal.')
      const next = input.mode === 'SET' ? input.quantity : row.quantity + input.quantity
      if (next > 1_000_000) throw bad('INVALID_QUANTITY', 'La existencia resultante es demasiado grande.')
      if (next === row.quantity) throw bad('NO_CHANGES', `La existencia ya es ${next}.`)
      const direction = next > row.quantity ? 'IN' : 'OUT'
      if (reason.direction !== 'ANY' && reason.direction !== direction) {
        throw bad('REASON_DIRECTION', `El motivo no corresponde a ${direction === 'IN' ? 'una entrada' : 'una salida'} de existencias.`)
      }
      if (next < 0) throw conflict('INSUFFICIENT_STOCK', 'La existencia no puede quedar en negativo.')
      const previous = row.quantity
      row.quantity = next
      return { ...record(store, row, previous, next, reason.code, { notes: input.notes?.trim() || null }) }
    },

    async listMovements(storeId, query) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_READ')
      const from = query.from ? Date.parse(query.from) : undefined
      const to = query.to ? Date.parse(query.to) : undefined
      if (from !== undefined && to !== undefined && from > to) {
        throw bad('INVALID_DATE_RANGE', 'La fecha inicial debe ser anterior a la final.')
      }
      const rows = movementsOf(storeId)
        .filter(
          (movement) =>
            (!query.branchId || movement.branchId === query.branchId) &&
            (!query.productId || movement.productId === query.productId) &&
            (!query.reasonCode || movement.reasonCode === query.reasonCode) &&
            (from === undefined || Date.parse(movement.occurredAt) >= from) &&
            (to === undefined || Date.parse(movement.occurredAt) <= to),
        )
        .toSorted((a, b) => b.occurredAt.localeCompare(a.occurredAt))
      return structuredClone(paginate(rows, query.page, query.size))
    },

    async listLowStock(storeId, branchId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_READ')
      if (branchId) activeBranch(store, branchId)
      return store.products
        .filter((row) => (!branchId || row.branchId === branchId) && row.quantity <= (row.minStock ?? DEFAULT_MIN_STOCK))
        .map((row) => ({
          productId: row.id,
          productName: row.name,
          sku: row.sku,
          branchId: row.branchId,
          branchName: row.branchName,
          stock: row.quantity,
          minStock: row.minStock ?? DEFAULT_MIN_STOCK,
        }))
        .toSorted((a, b) => a.stock - b.stock || a.productName.localeCompare(b.productName, 'es'))
    },

    async setMinStock(storeId, branchId, productId, minStock) {
      await simulateNetwork(400)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_UPDATE')
      if (minStock !== null && (!Number.isInteger(minStock) || minStock < 0 || minStock > 1_000_000)) {
        throw bad('VALIDATION_ERROR', 'El stock mínimo debe ser un entero entre 0 y 1.000.000.')
      }
      const row = listing(store, branchId, productId)
      if (!row) throw notFound('LISTING_NOT_FOUND', 'El producto no está listado en esa sucursal.')
      row.minStock = minStock
    },

    async transfer(storeId, input) {
      await simulateNetwork(800)
      const store = getStore(storeId)
      requirePermission(store, 'INVENTORY_TRANSFER')
      const known = input.idempotencyKey ? transferKeys.get(`${storeId}:${input.idempotencyKey}`) : undefined
      const existing = known ? transfersOf(storeId).find((transfer) => transfer.id === known) : undefined
      if (existing) return structuredClone(existing)
      const transfer = runTransfer(store, input)
      if (input.idempotencyKey) transferKeys.set(`${storeId}:${input.idempotencyKey}`, transfer.id)
      return structuredClone(transfer)
    },

    async listTransfers(storeId, query) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_READ')
      const rows = transfersOf(storeId)
        .filter((transfer) => !query.branchId || transfer.fromBranchId === query.branchId || transfer.toBranchId === query.branchId)
        .toSorted((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map(summaryOf)
      return structuredClone(paginate(rows, query.page, query.size))
    },

    async getTransfer(storeId, transferId) {
      await simulateNetwork(300)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_READ')
      const transfer = transfersOf(storeId).find((candidate) => candidate.id === transferId)
      if (!transfer) throw notFound('TRANSFER_NOT_FOUND', 'No se encontró la transferencia.')
      return structuredClone(transfer)
    },

    async exportCsv(storeId, branchId) {
      await simulateNetwork(500)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_READ')
      if (branchId) activeBranch(store, branchId)
      const lines = store.products
        .filter((row) => !branchId || row.branchId === branchId)
        .map((row) =>
          [row.sku, row.partNumber, row.name, row.branchName, row.quantity, row.quantity, row.price, row.minStock, row.state].map(cell).join(';'),
        )
      const text = `${BOM}sku;referencia;nombre;sucursal;stock;stock_base;precio;stock_minimo;estado\r\n${lines.map((line) => `${line}\r\n`).join('')}`
      return { blob: new Blob([text], { type: 'text/csv;charset=UTF-8' }), filename: csvFilename() }
    },

    async importCsv(storeId, file, dryRun) {
      await simulateNetwork(700)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_UPDATE')
      requirePermission(store, 'INVENTORY_ADJUST')
      if (file.size > CSV_MAX_BYTES) throw bad('FILE_TOO_LARGE', 'El archivo supera el máximo de 2 MB.')
      return processCsv(store, await file.text(), dryRun)
    },
  }
}
