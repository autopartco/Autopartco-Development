import { useState } from 'react'
import { toast } from 'sonner'
import { CheckCircle2Icon } from 'lucide-react'
import { FormAlert } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { formatNumber, pluralize } from '@/lib/format'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getInventoryService } from '../api/inventory-service'
import type { CsvImportResult } from '../api/types'
import { usePortal } from '../context'
import { csvRowErrorText, inventoryErrorMessage } from '../lib/inventory'

const MAX_BYTES = 2 * 1024 * 1024
const SHOWN_ERRORS = 50

function ImportForm({ onClose }: { onClose: () => void }) {
  const { storeId, invalidateStore } = usePortal()
  const [file, setFile] = useState<File>()
  const [preview, setPreview] = useState<CsvImportResult>()
  const [problem, setProblem] = useState<string>()
  const check = useSubmitOnce(async (selected: File) => (await getInventoryService()).importCsv(storeId, selected, true))
  const apply = useSubmitOnce(async (selected: File) => (await getInventoryService()).importCsv(storeId, selected, false))
  const busy = check.pending || apply.pending

  function choose(selected: File | undefined) {
    setPreview(undefined)
    setProblem(undefined)
    if (selected && selected.size > MAX_BYTES) {
      setFile(undefined)
      setProblem('El archivo supera el máximo de 2 MB. Divídelo en partes más pequeñas.')
      return
    }
    setFile(selected)
  }

  async function review() {
    if (!file) return
    setProblem(undefined)
    const result = await check.run(file)
    if (result.status === 'ok') setPreview(result.value)
    else if (result.status === 'error') setProblem(inventoryErrorMessage(result.error, 'No pudimos revisar el archivo. Inténtalo de nuevo.'))
  }

  async function confirm() {
    if (!file) return
    setProblem(undefined)
    const result = await apply.run(file)
    if (result.status === 'error') return setProblem(inventoryErrorMessage(result.error, 'No pudimos importar el archivo. No se cambió nada.'))
    if (result.status !== 'ok') return
    if (result.value.errors.length > 0) {
      setPreview(result.value)
      return setProblem('Mientras tanto cambió algo en tu inventario y ya no se puede aplicar el archivo. No se cambió nada: revisa las filas con error.')
    }
    invalidateStore()
    toast.success(`Importaste el archivo: ${pluralize(result.value.updated, 'fila actualizada', 'filas actualizadas')}.`)
    onClose()
  }

  const errors = preview?.errors ?? []
  const canConfirm = preview !== undefined && errors.length === 0 && preview.updated > 0

  return (
    <div className="flex flex-col gap-4" aria-busy={busy || undefined}>
      <DialogHeader>
        <DialogTitle>Importar inventario desde CSV</DialogTitle>
        <DialogDescription>
          Primero revisamos el archivo y te mostramos qué cambiaría; solo se aplica cuando lo confirmas. Descarga el inventario actual para usarlo como modelo.
        </DialogDescription>
      </DialogHeader>
      <ul className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">
        <li>Separado por punto y coma (;), hasta 2 MB y 5.000 filas.</li>
        <li>Columnas: sucursal, sku o referencia, y al menos una de stock, precio o stock_minimo.</li>
        <li>Si el archivo trae stock_base, no la edites: sirve para conservar las ventas hechas después de exportar.</li>
        <li>Una celda vacía deja el dato como está. Solo se actualizan productos que ya existen en esa sucursal.</li>
      </ul>
      <div className="flex flex-col gap-1.5">
        <label htmlFor="importar-archivo" className="text-sm font-semibold">
          Archivo CSV
        </label>
        <input
          id="importar-archivo"
          type="file"
          accept=".csv,text/csv"
          disabled={busy}
          onChange={(event) => choose(event.target.files?.[0])}
          className="block w-full min-w-0 rounded-lg border border-input bg-card p-2 text-sm file:mr-3 file:min-h-9 file:rounded-md file:border-0 file:bg-concrete file:px-3 file:text-sm file:font-semibold"
        />
      </div>
      {problem ? (
        <FormAlert title="No se cambió nada">
          {problem}
        </FormAlert>
      ) : null}
      {preview ? (
        <section aria-label="Resultado de la revisión" className="flex flex-col gap-3">
          <p role="status" className="flex items-start gap-2 rounded-lg bg-concrete p-3 text-sm">
            <CheckCircle2Icon className="mt-0.5 size-4 shrink-0" aria-hidden />
            <span>
              {pluralize(preview.rows, 'fila revisada', 'filas revisadas')}.{' '}
              {errors.length === 0
                ? preview.updated > 0
                  ? `${pluralize(preview.updated, 'fila cambiará', 'filas cambiarán')} tu inventario.`
                  : 'El archivo no cambia nada: los valores ya coinciden con tu inventario.'
                : `${pluralize(errors.length, 'fila tiene un error', 'filas tienen errores')}. Corrígelas y vuelve a subir el archivo; no se aplicará nada hasta que no haya errores.`}
            </span>
          </p>
          {errors.length > 0 ? (
            <div className="max-h-56 overflow-auto rounded-lg border border-border" tabIndex={0} role="region" aria-label="Filas con error">
              <table className="w-full text-left text-sm">
                <caption className="sr-only">Filas con error del archivo</caption>
                <thead className="sticky top-0 bg-card text-xs text-muted-foreground">
                  <tr>
                    <th scope="col" className="px-3 py-2 font-semibold">Línea</th>
                    <th scope="col" className="px-3 py-2 font-semibold">Qué corregir</th>
                  </tr>
                </thead>
                <tbody>
                  {errors.slice(0, SHOWN_ERRORS).map((error, index) => (
                    <tr key={`${error.line}-${index}`} className="border-t border-border">
                      <td className="px-3 py-2 tabular-nums">{formatNumber(error.line)}</td>
                      <td className="px-3 py-2">{csvRowErrorText(error)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}
          {errors.length > SHOWN_ERRORS ? <p className="text-xs text-muted-foreground">Mostramos las primeras {SHOWN_ERRORS} de {formatNumber(errors.length)} filas con error.</p> : null}
        </section>
      ) : null}
      <DialogFooter>
        <Button type="button" variant="outline" className="h-11 px-4" disabled={busy} onClick={onClose}>
          Cancelar
        </Button>
        {canConfirm ? (
          <Button type="button" className="h-11 px-4" disabled={busy} onClick={() => void confirm()}>
            {apply.pending ? 'Importando…' : `Confirmar importación (${pluralize(preview.updated, 'fila', 'filas')})`}
          </Button>
        ) : (
          <Button type="button" className="h-11 px-4" disabled={busy || !file} onClick={() => void review()}>
            {check.pending ? 'Revisando…' : preview ? 'Revisar de nuevo' : 'Revisar archivo'}
          </Button>
        )}
      </DialogFooter>
    </div>
  )
}

/** Importación CSV de inventario con vista previa (`dryRun=true`) y confirmación (`dryRun=false`). */
export function CsvImportDialog({ open, onOpenChange }: { open: boolean; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-xl">{open ? <ImportForm onClose={() => onOpenChange(false)} /> : null}</DialogContent>
    </Dialog>
  )
}
