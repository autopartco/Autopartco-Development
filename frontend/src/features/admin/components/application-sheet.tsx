import { useState } from 'react'
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

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="label-mono text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 font-medium break-words">{children}</dd>
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
    <li className="flex items-center gap-3 rounded-lg bg-concrete p-3">
      <FileTextIcon className="size-5 shrink-0 text-muted-foreground" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="font-semibold">{DOCUMENT_TYPE_LABEL[document.type]}</p>
        <p className="truncate text-sm text-muted-foreground">
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

/** Detalle de una solicitud: datos, documentos descargables, relaciones y decisión (aprobar/rechazar). */
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
            <SheetHeader className="p-4 pr-16 sm:p-6 sm:pr-16">
              <SheetTitle className="text-xl font-extrabold tracking-tight">{current.businessName}</SheetTitle>
              <SheetDescription>Solicitud enviada el {formatDateTime(current.createdAt)}.</SheetDescription>
              <div className="mt-1">
                <StatusBadge tone="warning">En revisión</StatusBadge>
              </div>
            </SheetHeader>

            <div className="flex flex-col gap-6 px-4 pb-4 sm:px-6">
              <dl className="grid gap-4 sm:grid-cols-2">
                <Fact label="NIT">{current.nit}</Fact>
                <Fact label="Tipo">{LEGAL_TYPE_LABEL[current.legalType]}</Fact>
                <Fact label="Correo del almacén">{current.businessEmail}</Fact>
                <Fact label="Correo de quien solicita">{current.applicantEmail}</Fact>
                <Fact label="Plan">{current.selectedPlan?.name ?? current.plan?.name ?? 'Sin plan asignado'}</Fact>
              </dl>

              <section aria-labelledby="docs-title" className="flex flex-col gap-3">
                <h3 id="docs-title" className="text-base font-extrabold tracking-tight">
                  Documentos ({current.documents.length})
                </h3>
                {current.documents.length === 0 ? (
                  <p className="rounded-lg bg-concrete p-3 text-sm text-muted-foreground">
                    Esta solicitud no trae documentos. Rechazarla con el motivo es lo recomendable.
                  </p>
                ) : (
                  <ul className="flex flex-col gap-2">
                    {current.documents.map((document) => (
                      <DocumentRow key={document.id} application={current} document={document} />
                    ))}
                  </ul>
                )}
              </section>

              <section aria-labelledby="rel-title" className="flex flex-col gap-3">
                <h3 id="rel-title" className="text-base font-extrabold tracking-tight">
                  Relaciones
                </h3>
                <RelationsView key={current.applicationId} application={current} />
              </section>
            </div>

            <div className="sticky bottom-0 mt-auto flex flex-wrap justify-end gap-2 border-t bg-popover p-4 sm:px-6">
              <Button variant="outline" className="h-11 px-4 text-destructive" onClick={() => setDecision(false)}>
                <XIcon aria-hidden />
                Rechazar
              </Button>
              <Button className="h-11 px-5" onClick={() => setDecision(true)}>
                <CheckIcon aria-hidden />
                Aprobar
              </Button>
            </div>

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
