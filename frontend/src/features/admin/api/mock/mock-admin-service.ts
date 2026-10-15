import { ApiError, simulateLatency } from '@/lib/api'
import type { AdminApplicationService, ApplicationDocument, StoreApplication } from '../types'

/**
 * Revisión simulada: parte de 9 solicitudes pendientes en memoria. Aprobar o rechazar la saca
 * de la lista, como el servidor (`/pending`). Para ver un error al revisar, usa el almacén
 * "Frenos del Norte" (responde 500). Los documentos descargables son archivos de texto de ejemplo.
 */
const doc = (id: string, type: ApplicationDocument['type'], filename: string, sizeBytes: number): ApplicationDocument => ({
  id,
  type,
  filename,
  contentType: 'application/pdf',
  sizeBytes,
})

const NATURAL = (n: string): ApplicationDocument[] => [
  doc(`${n}-d1`, 'IDENTITY_DOCUMENT', 'cedula.pdf', 412_000),
  doc(`${n}-d2`, 'RUT', 'rut.pdf', 268_000),
]
const LEGAL = (n: string): ApplicationDocument[] => [
  doc(`${n}-d1`, 'RUT', 'rut-empresa.pdf', 301_000),
  doc(`${n}-d2`, 'LEGAL_REPRESENTATIVE_ID', 'cedula-representante.pdf', 388_000),
  doc(`${n}-d3`, 'CERTIFICATE_OF_EXISTENCE', 'camara-de-comercio.pdf', 1_840_000),
]

const seed = (
  n: number,
  businessName: string,
  nit: string,
  legalType: StoreApplication['legalType'],
  createdAt: string,
  domain: string,
): StoreApplication => ({
  applicationId: `app-${n}`,
  storeId: `store-${n}`,
  businessName,
  nit,
  businessEmail: `contacto@${domain}`,
  applicantEmail: `gerencia@${domain}`,
  legalType,
  status: 'PENDING_REVIEW',
  plan: { code: 'TRY', name: 'Prueba gratuita' },
  selectedPlan: null,
  documents: legalType === 'LEGAL_ENTITY' ? LEGAL(`app-${n}`) : NATURAL(`app-${n}`),
  reviewNotes: null,
  createdAt,
})

export function createMockAdminApplicationService(): AdminApplicationService {
  let pendingList: StoreApplication[] = [
    seed(1, 'Frenos del Norte', '900.123.456-7', 'LEGAL_ENTITY', '2026-09-02T09:14:00', 'frenosnorte.co'),
    seed(2, 'Repuestos La 80', '1.017.234.567-1', 'NATURAL_PERSON', '2026-09-09T15:40:00', 'la80.co'),
    seed(3, 'Autopartes Medellín SAS', '901.555.210-3', 'LEGAL_ENTITY', '2026-09-17T11:05:00', 'apmedellin.co'),
    seed(4, 'Lubricantes El Cóndor', '800.222.909-4', 'LEGAL_ENTITY', '2026-09-24T08:30:00', 'elcondor.co'),
    seed(5, 'Don Ramiro Repuestos', '79.456.123-9', 'NATURAL_PERSON', '2026-10-01T17:22:00', 'donramiro.co'),
    seed(6, 'Llantas y Rines Cali', '900.777.318-0', 'LEGAL_ENTITY', '2026-10-05T10:10:00', 'llantascali.co'),
    seed(7, 'Eléctricos Pereira', '1.088.100.200-5', 'NATURAL_PERSON', '2026-10-08T13:45:00', 'electricospereira.co'),
    seed(8, 'Suspensión Total Bogotá', '901.010.404-2', 'LEGAL_ENTITY', '2026-10-12T09:00:00', 'suspensiontotal.co'),
    seed(9, 'Motores Barranquilla', '72.345.678-6', 'NATURAL_PERSON', '2026-10-14T16:20:00', 'motoresbq.co'),
  ]

  return {
    simulated: true,
    async pending() {
      await simulateLatency(700)
      return pendingList.map((item) => ({ ...item }))
    },
    async review(applicationId, input) {
      await simulateLatency(800)
      const found = pendingList.find((item) => item.applicationId === applicationId)
      if (!found) throw new ApiError('La solicitud ya fue revisada o no existe.', { status: 404, code: 'NOT_FOUND' })
      if (found.businessName === 'Frenos del Norte') {
        throw new ApiError('Error simulado del servidor.', { status: 500, code: 'INTERNAL_ERROR' })
      }
      pendingList = pendingList.filter((item) => item.applicationId !== applicationId)
      return {
        ...found,
        status: input.approved ? 'CONFIGURATION_PENDING' : 'REJECTED',
        reviewNotes: input.notes?.trim() || null,
      }
    },
    async downloadDocument(_applicationId, document) {
      await simulateLatency(400)
      return new Blob([`Documento simulado: ${document.filename}\nNo es un archivo real.`], { type: 'text/plain' })
    },
  }
}
