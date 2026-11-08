import { Link } from 'react-router'
import { Container, PageIntro } from '@/components/common/page-header'
import { Button } from '@/components/ui/button'
import { useSession } from '@/features/auth/hooks'
import { useSellerCta } from '@/features/seller/hooks'
import { DOCUMENT_LABELS, REQUIRED_DOCUMENTS } from '@/features/seller/api/types'

/**
 * `/vender`: landing para almacenes. Los pasos describen el flujo real del backend
 * (storesuite/onboarding y operations/storeverification): solicitud con documentos,
 * verificación de correo, revisión, enlace de configuración y plan después.
 */
const LATER_STEPS = [
  {
    title: 'Revisamos tus documentos',
    body: 'Un equipo de Autopartco revisa la identidad y la legalidad del almacén antes de aprobarlo.',
  },
  {
    title: 'Configura tu almacén',
    body: 'Al aprobarte, te llega un enlace para subir tu logo y tu banner. Empiezas con la prueba gratuita y eliges plan después.',
  },
] as const

/** Sin sesión, la solicitud crea la cuenta; con sesión de cliente, usa la suya. */
function stepsFor(signedIn: boolean) {
  return [
    signedIn
      ? {
          title: 'Envía tu solicitud',
          body: 'Datos del almacén y los documentos que pedimos según el tipo de persona. Usas tu cuenta actual.',
        }
      : {
          title: 'Envía tu solicitud',
          body: 'Datos del almacén, tus datos de acceso y los documentos que pedimos según el tipo de persona.',
        },
    signedIn
      ? { title: 'Ten tu correo verificado', body: 'Si aún no lo verificaste, abre el enlace que te enviamos al crear tu cuenta.' }
      : { title: 'Verifica tu correo', body: 'Te enviamos un enlace para activar tu cuenta.' },
    ...LATER_STEPS,
  ]
}

function DocumentList({ title, keys }: { title: string; keys: readonly (keyof typeof DOCUMENT_LABELS)[] }) {
  return (
    <div className="flex flex-col gap-2">
      <h3 className="text-base font-semibold text-ap-ink">{title}</h3>
      <ol className="flex flex-col divide-y divide-ap-line border-y border-ap-line text-sm">
        {keys.map((key, index) => (
          <li key={key} className="grid grid-cols-[2rem_minmax(0,1fr)] gap-2 py-2.5 text-ap-ink">
            <span className="ap-ref text-ap-muted" aria-hidden>
              {String(index + 1).padStart(2, '0')}
            </span>
            {DOCUMENT_LABELS[key]}
          </li>
        ))}
      </ol>
    </div>
  )
}

export default function SellPage() {
  const session = useSession()
  const steps = stepsFor(Boolean(session))
  // Registrar, ver la solicitud en curso o ir al portal: lo decide la cuenta (el servidor valida).
  const seller = useSellerCta()
  const cta =
    seller.state === 'loading' || seller.state === 'register'
      ? { to: '/vender/registro', label: session ? 'Solicitar mi almacén' : 'Registrar mi almacén' }
      : { to: seller.to, label: seller.label }
  return (
    <Container className="py-6 sm:py-8">
      <title>Vende en Autopartco | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Vender en Autopartco' }]}
        label="Almacenes / Registro"
        title="Vende en Autopartco"
        description={
          <p>
            Muestra tu inventario a los conductores y talleres que buscan repuestos cerca de tu almacén. Cada almacén
            se revisa antes de publicar.
          </p>
        }
        className="max-w-3xl"
      >
        <div className="mt-3 flex flex-wrap gap-2">
          <Button size="lg" nativeButton={false} render={<Link to={cta.to} />}>
            {cta.label}
          </Button>
          <Button variant="outline" size="lg" nativeButton={false} render={<Link to="/tiendas" />}>
            Ver almacenes que ya publican
          </Button>
        </div>
      </PageIntro>

      <section aria-labelledby="vender-pasos" className="mt-12 sm:mt-16">
        <p className="ap-index">Proceso / {steps.length} pasos</p>
        <h2 id="vender-pasos" className="mt-2 text-section text-ap-ink">
          Así entra tu almacén
        </h2>
        <ol className="mt-6 grid max-w-3xl border-t border-ap-graphite">
          {steps.map((step, index) => (
            <li key={step.title} className="grid grid-cols-[2.5rem_minmax(0,1fr)] gap-x-4 border-b border-ap-line py-4 sm:grid-cols-[2.5rem_14rem_minmax(0,1fr)]">
              <span className="ap-ref pt-0.5 text-ap-muted" aria-hidden>
                {String(index + 1).padStart(2, '0')}
              </span>
              <h3 className="font-semibold text-ap-ink">
                <span className="sr-only">Paso {index + 1}: </span>
                {step.title}
              </h3>
              <p className="col-start-2 text-sm text-ap-muted sm:col-start-3">{step.body}</p>
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="vender-documentos" className="mt-12 sm:mt-16">
        <p className="ap-index">Documentos / PDF, PNG o JPG de hasta 10 MB</p>
        <h2 id="vender-documentos" className="mt-2 text-section text-ap-ink">
          Documentos que vas a necesitar
        </h2>
        <p className="mt-2 max-w-2xl text-ap-muted">Pedimos documentos distintos según el tipo de persona que registra el almacén.</p>
        <div className="mt-6 grid gap-8 md:grid-cols-2 md:gap-10">
          <DocumentList title="Persona natural" keys={REQUIRED_DOCUMENTS.NATURAL_PERSON} />
          <DocumentList title="Empresa (persona jurídica)" keys={REQUIRED_DOCUMENTS.LEGAL_ENTITY} />
        </div>
        <Button size="lg" className="mt-8" nativeButton={false} render={<Link to={cta.to} />}>
          {cta.label}
        </Button>
      </section>
    </Container>
  )
}
