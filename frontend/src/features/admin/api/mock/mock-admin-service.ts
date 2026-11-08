import { ApiError, simulateLatency } from '@/lib/api'
import type { AdminApplicationService, ApplicationDocument, ApplicationReviewer, StoreApplication } from '../types'

/**
 * Revisión simulada: 9 solicitudes pendientes y 6 ya revisadas en memoria. Aprobar o rechazar saca la
 * solicitud de `/pending` y le pone fecha y revisor, como el servidor. Entre las revisadas hay una sin fecha
 * registrada, una con el revisor eliminado y una con el solicitante eliminado. Para ver un error al revisar,
 * usa el almacén "Frenos del Norte" (responde 500). Los documentos descargables son archivos de texto de ejemplo.
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
  reviewedAt: null,
  reviewer: null,
})

const LAURA: ApplicationReviewer = { accountId: 'root-laura', name: 'Laura Gómez', email: 'root.laura@autopartco.co' }
const ANDRES: ApplicationReviewer = { accountId: 'root-andres', name: 'Andrés Rincón', email: 'root.andres@autopartco.co' }
/** Revisor de las decisiones tomadas en la sesión simulada. */
const SESSION_REVIEWER: ApplicationReviewer = { accountId: 'root-session', name: 'Administración Autopartco', email: 'root@autopartco.co' }

const reviewed = (
  base: StoreApplication,
  status: StoreApplication['status'],
  reviewedAt: string | null,
  reviewer: ApplicationReviewer | null,
  reviewNotes: string | null,
  extra: Partial<StoreApplication> = {},
): StoreApplication => ({ ...base, status, reviewedAt, reviewer, reviewNotes, ...extra })

/** Orden del servidor: pendientes más antiguas primero; luego revisadas por fecha de revisión descendente (sin fecha al final). */
function serverOrder(a: StoreApplication, b: StoreApplication): number {
  const aPending = a.status === 'PENDING_REVIEW'
  const bPending = b.status === 'PENDING_REVIEW'
  if (aPending !== bPending) return aPending ? -1 : 1
  if (aPending) return a.createdAt.localeCompare(b.createdAt)
  if (a.reviewedAt === b.reviewedAt) return 0
  if (a.reviewedAt === null) return 1
  if (b.reviewedAt === null) return -1
  return b.reviewedAt.localeCompare(a.reviewedAt)
}

const STATUSES: readonly StoreApplication['status'][] = ['PENDING_REVIEW', 'CONFIGURATION_PENDING', 'REJECTED', 'ACTIVE']

/** Fecha y hora local sin zona, como `LocalDateTime`. */
function localNow(): string {
  const now = new Date()
  return new Date(now.getTime() - now.getTimezoneOffset() * 60_000).toISOString().slice(0, 19)
}

export function createMockAdminApplicationService(): AdminApplicationService {
  let applications: StoreApplication[] = [
    seed(1, 'Frenos del Norte', '900.123.456-7', 'LEGAL_ENTITY', '2026-09-02T09:14:00', 'frenosnorte.co'),
    seed(2, 'Repuestos La 80', '1.017.234.567-1', 'NATURAL_PERSON', '2026-09-09T15:40:00', 'la80.co'),
    seed(3, 'Autopartes Medellín SAS', '901.555.210-3', 'LEGAL_ENTITY', '2026-09-17T11:05:00', 'apmedellin.co'),
    seed(4, 'Lubricantes El Cóndor', '800.222.909-4', 'LEGAL_ENTITY', '2026-09-24T08:30:00', 'elcondor.co'),
    seed(5, 'Don Ramiro Repuestos', '79.456.123-9', 'NATURAL_PERSON', '2026-10-01T17:22:00', 'donramiro.co'),
    seed(6, 'Llantas y Rines Cali', '900.777.318-0', 'LEGAL_ENTITY', '2026-10-05T10:10:00', 'llantascali.co'),
    seed(7, 'Eléctricos Pereira', '1.088.100.200-5', 'NATURAL_PERSON', '2026-10-08T13:45:00', 'electricospereira.co'),
    seed(8, 'Suspensión Total Bogotá', '901.010.404-2', 'LEGAL_ENTITY', '2026-10-12T09:00:00', 'suspensiontotal.co'),
    seed(9, 'Motores Barranquilla', '72.345.678-6', 'NATURAL_PERSON', '2026-10-14T16:20:00', 'motoresbq.co'),
    reviewed(seed(10, 'Repuestos El Paisa', '901.300.112-8', 'LEGAL_ENTITY', '2026-08-03T10:00:00', 'elpaisa.co'), 'ACTIVE', '2026-08-05T11:32:00', LAURA, null, {
      selectedPlan: { code: 'PRO', name: 'Profesional' },
    }),
    reviewed(
      seed(11, 'Bujías y Filtros Tunja', '1.052.400.778-2', 'NATURAL_PERSON', '2026-08-20T08:15:00', 'bujiastunja.co'),
      'CONFIGURATION_PENDING',
      '2026-08-22T16:05:00',
      ANDRES,
      'Documentos completos. Falta subir logo y banner.',
    ),
    reviewed(
      seed(12, 'Autolujos Cartagena', '900.840.221-5', 'LEGAL_ENTITY', '2026-08-25T14:40:00', 'autolujosctg.co'),
      'REJECTED',
      '2026-08-27T09:12:00',
      LAURA,
      'El certificado de existencia tiene más de 30 días. Envía uno reciente.',
    ),
    // Revisada antes de que se registrara la fecha de revisión.
    reviewed(seed(13, 'Repuestos Villavicencio', '86.071.333-1', 'NATURAL_PERSON', '2026-07-10T09:30:00', 'repuestosvillavo.co'), 'ACTIVE', null, LAURA, null),
    // La cuenta revisora ya no existe.
    reviewed(seed(14, 'Transmisiones del Valle', '901.112.987-4', 'LEGAL_ENTITY', '2026-07-28T11:50:00', 'transvalle.co'), 'REJECTED', '2026-07-30T15:45:00', null, 'El RUT no corresponde al NIT registrado.'),
    // La cuenta solicitante ya no existe.
    reviewed(seed(15, 'Frenos y Embragues Neiva', '1.075.210.654-9', 'NATURAL_PERSON', '2026-08-12T13:00:00', 'frenosneiva.co'), 'CONFIGURATION_PENDING', '2026-08-14T10:20:00', ANDRES, null, {
      applicantEmail: null,
    }),
  ]

  return {
    simulated: true,
    async pending() {
      await simulateLatency(700)
      return applications
        .filter((item) => item.status === 'PENDING_REVIEW')
        .sort(serverOrder)
        .map((item) => ({ ...item }))
    },
    async listApplications({ status, page, size }) {
      await simulateLatency(600)
      if (status && !STATUSES.includes(status)) {
        throw new ApiError('El estado de solicitud no es válido.', { status: 400, code: 'INVALID_APPLICATION_STATUS' })
      }
      const pageSize = Math.min(Math.max(size, 1), 100)
      const matching = applications.filter((item) => !status || item.status === status).sort(serverOrder)
      const current = Math.max(1, page)
      return {
        items: matching.slice((current - 1) * pageSize, current * pageSize).map((item) => ({ ...item })),
        page: current,
        size: pageSize,
        total: matching.length,
        totalPages: Math.max(1, Math.ceil(matching.length / pageSize)),
      }
    },
    async review(applicationId, input) {
      await simulateLatency(800)
      const found = applications.find((item) => item.applicationId === applicationId && item.status === 'PENDING_REVIEW')
      if (!found) throw new ApiError('La solicitud ya fue revisada o no existe.', { status: 404, code: 'NOT_FOUND' })
      if (found.businessName === 'Frenos del Norte') {
        throw new ApiError('Error simulado del servidor.', { status: 500, code: 'INTERNAL_ERROR' })
      }
      const updated: StoreApplication = {
        ...found,
        status: input.approved ? 'CONFIGURATION_PENDING' : 'REJECTED',
        reviewNotes: input.notes?.trim() || null,
        reviewedAt: localNow(),
        reviewer: SESSION_REVIEWER,
      }
      applications = applications.map((item) => (item.applicationId === applicationId ? updated : item))
      return { ...updated }
    },
    async downloadDocument(_applicationId, document) {
      await simulateLatency(400)
      return new Blob([`Documento simulado: ${document.filename}\nNo es un archivo real.`], { type: 'text/plain' })
    },
  }
}
