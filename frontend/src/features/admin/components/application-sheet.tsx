import { useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { CheckIcon, DownloadIcon, FileTextIcon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { actionErrorMessage } from '@/features/portal/lib/errors'
import { formatDateTime } from '@/features/portal/lib/labels'
import { getAdminApplicationService } from '../api/admin-service'
import type { ApplicationDocument, StoreApplication } from '../api/types'
import { reloadPendingApplications } from '../hooks'
import { DOCUMENT_TYPE_LABEL, formatBytes, LEGAL_TYPE_LABEL } from '../lib/labels'
import { RelationsView } from './relations-view'
import { ReviewDialog } from './review-dialog'

function Fact({ label, mono, children }: { label: string; mono?: boolean; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5 border-b border-ap-line pb-2">
      <dt className="text-sm text-ap-muted">{label}</dt>
      <dd className={mono ? 'ap-ref break-all text-ap-ink' : 'font-medium break-words text-ap-ink'}>{children}</dd>
    </div>
  )
}

function DocumentRow({ application, document }: { application: StoreApplication; document: ApplicationDocument }) {
  const [pending, setPending] = useState(false)

  async function download() {
    setPending(true)
    try {
      const blob = await (await getAdminApplicationService()).downloadDocument(application.applicationId, document)
      const url = URL.createObjectURL(blob)
      const link = window.document.createElement('a')
      link.href = url
      link.download = document.filename
      link.click()
      window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
    } catch (cause) {
      toast.error(actionErrorMessage(cause, 'No pudimos descargar el documento. Inténtalo de nuevo.'))
    } finally {
      setPending(false)
    }
  }

  return (
    <li className="flex items-center gap-3 py-3">
      <FileTextIcon className="size-5 shrink-0 text-ap-muted" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="font-semibold text-ap-ink">{DOCUMENT_TYPE_LABEL[document.type]}</p>
        <p className="ap-ref truncate text-ap-muted">
          {document.filename} · {formatBytes(document.sizeBytes)}
        </p>
      </div>
      <Button
        variant="outline"
        className="h-11 shrink-0 px-3"
        disabled={pending}
        aria-label={`Descargar ${DOCUMENT_TYPE_LABEL[document.type]}`}
        onClick={() => void download()}
      >
        <DownloadIcon aria-hidden />
        {pending ? 'Descargando…' : 'Descargar'}
      </Button>
    </li>
  )
}

interface ApplicationSheetProps {
  application: StoreApplication | undefined
  onClose: () => void
}

/**
 * Detalle de una solicitud (§7.4), en tres zonas separadas: lectura (datos, documentos, relaciones), y al pie
 * la decisión. Aprobar es la acción principal; rechazar va en rojo semántico. Ambas piden confirmación.
 */
export function ApplicationSheet({ application, onClose }: ApplicationSheetProps) {
  const [decision, setDecision] = useState<boolean>()
  // Se recuerda la última solicitud para que el panel no se vacíe mientras se anima el cierre.
  const [shown, setShown] = useState<StoreApplication>()
  if (application && application !== shown) setShown(application)
  const current = application ?? shown

  async function submit(notes: string) {
    if (!current || decision === undefined) return
    const approved = decision
    await (await getAdminApplicationService()).review(current.applicationId, { approved, notes: notes || undefined })
    toast.success(approved ? `Aprobaste a ${current.businessName}.` : `Rechazaste la solicitud de ${current.businessName}.`)
    reloadPendingApplications()
    onClose()
  }

  return (
    <Sheet open={application !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full gap-0 overflow-y-auto p-0 sm:max-w-2xl">
        {current ? (
          <>
            <SheetHeader className="border-b border-ap-line p-4 pr-16 sm:p-6 sm:pr-16">
              <p className="ap-index">Solicitud de almacén</p>
              <SheetTitle className="text-block-title text-ap-ink">{current.businessName}</SheetTitle>
              <SheetDescription>
                Enviada por {current.applicantEmail} el <time dateTime={current.createdAt} className="ap-ref">{formatDateTime(current.createdAt)}</time>.
              </SheetDescription>
              <div className="mt-1">
                <StatusBadge tone="warning">En revisión</StatusBadge>
              </div>
            </SheetHeader>

            <div className="flex flex-col gap-8 p-4 sm:p-6">
              <section aria-labelledby="datos-title" className="flex flex-col gap-3">
                <h3 id="datos-title" className="text-base font-semibold text-ap-ink">
                  Datos del almacén
                </h3>
                <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
                  <Fact label="NIT" mono>
                    {current.nit}
                  </Fact>
                  <Fact label="Tipo">{LEGAL_TYPE_LABEL[current.legalType]}</Fact>
                  <Fact label="Correo del almacén">{current.businessEmail}</Fact>
                  <Fact label="Correo de quien solicita">{current.applicantEmail}</Fact>
                  <Fact label="Plan elegido">{current.selectedPlan?.name ?? current.plan?.name ?? 'Sin plan asignado'}</Fact>
                  <Fact label="ID de la solicitud" mono>
                    {current.applicationId}
                  </Fact>
                </dl>
                {current.reviewNotes ? (
                  <div className="border-l-2 border-ap-line-strong py-1 pl-3">
                    <p className="text-sm text-ap-muted">Nota registrada</p>
                    <p className="text-sm whitespace-pre-line text-ap-ink">{current.reviewNotes}</p>
                  </div>
                ) : null}
              </section>

              <section aria-labelledby="docs-title" className="flex flex-col gap-1">
                <h3 id="docs-title" className="text-base font-semibold text-ap-ink">
                  Documentos <span className="ap-num font-normal text-ap-muted">({current.documents.length})</span>
                </h3>
                {current.documents.length === 0 ? (
                  <p className="mt-2 rounded-md border border-ap-warning/30 bg-ap-warning-soft p-3 text-sm text-ap-ink">
                    <strong className="font-semibold">Sin documentos.</strong> Sin respaldo no se puede verificar el almacén: lo recomendable es rechazarla indicando qué debe enviar.
                  </p>
                ) : (
                  <ul className="divide-y divide-ap-line border-y border-ap-line">
                    {current.documents.map((document) => (
                      <DocumentRow key={document.id} application={current} document={document} />
                    ))}
                  </ul>
                )}
              </section>

              <section aria-labelledby="rel-title" className="flex flex-col gap-3">
                <h3 id="rel-title" className="text-base font-semibold text-ap-ink">
                  Relaciones
                </h3>
                <RelationsView key={current.applicationId} application={current} />
              </section>
            </div>

            <section aria-labelledby="decision-title" className="sticky bottom-0 mt-auto flex flex-col gap-3 border-t border-ap-line-strong bg-ap-white p-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
              <p id="decision-title" className="text-sm text-ap-muted">
                <span className="font-semibold text-ap-ink">Decisión.</span> Antes de guardar te pedimos confirmarla.
              </p>
              <div className="flex flex-wrap justify-end gap-2">
                <Button variant="outline" className="h-11 border-ap-danger/50 px-4 text-ap-danger hover:border-ap-danger hover:bg-ap-danger-soft" onClick={() => setDecision(false)}>
                  <XIcon aria-hidden />
                  Rechazar
                </Button>
                <Button className="h-11 px-5" onClick={() => setDecision(true)}>
                  <CheckIcon aria-hidden />
                  Aprobar
                </Button>
              </div>
            </section>

            <ReviewDialog
              key={`${current.applicationId}-${decision}`}
              application={current}
              approved={decision === true}
              open={decision !== undefined}
              onOpenChange={(open) => !open && setDecision(undefined)}
              onSubmit={submit}
            />
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  )
}
