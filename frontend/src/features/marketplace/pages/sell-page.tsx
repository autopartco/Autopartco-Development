import { Link } from 'react-router'
import { FileTextIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { Button } from '@/components/ui/button'
import { useSession } from '@/features/auth/hooks'
import { accountKindOf } from '@/features/auth/lib/account-menu-items'
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
    <div className="flex flex-col gap-3 rounded-xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07)]">
      <h3 className="text-lg leading-tight font-bold tracking-tight">{title}</h3>
      <ul className="flex flex-col gap-2 text-sm">
        {keys.map((key) => (
          <li key={key} className="flex items-start gap-2">
            <FileTextIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
            {DOCUMENT_LABELS[key]}
          </li>
        ))}
      </ul>
    </div>
  )
}

export default function SellPage() {
  const session = useSession()
  const kind = session ? accountKindOf(session.roles) : undefined
  const steps = stepsFor(Boolean(session))
  // El rol solo decide el atajo; `/vender/registro` y el servidor resuelven cada caso.
  const cta =
    kind === 'store'
      ? { to: '/portal', label: 'Ir al portal de mi almacén' }
      : { to: '/vender/registro', label: session ? 'Solicitar mi almacén' : 'Registrar mi almacén' }
  return (
    <Container className="py-6 sm:py-8">
      <title>Vende en Autopartco | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Vender en Autopartco' }]}
        label="Para almacenes"
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
          <Button className="h-11 px-5 text-base" nativeButton={false} render={<Link to={cta.to} />}>
            {cta.label}
          </Button>
          <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/tiendas" />}>
            Ver almacenes que ya publican
          </Button>
        </div>
      </PageIntro>

      <section aria-labelledby="vender-pasos" className="mt-14 sm:mt-20">
        <p className="label-mono text-muted-foreground">4 pasos</p>
        <h2 id="vender-pasos" className="mt-3 text-section">
          Así entra tu almacén
        </h2>
        <ol className="mt-8 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {steps.map((step, index) => (
            <li key={step.title} className="flex flex-col gap-3 rounded-xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07)]">
              <span className="grid size-8 place-items-center rounded-full bg-thread font-mono text-xs font-bold text-white" aria-hidden>
                {index + 1}
              </span>
              <h3 className="text-lg leading-tight font-bold tracking-tight">{step.title}</h3>
              <p className="text-sm text-muted-foreground">{step.body}</p>
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="vender-documentos" className="mt-14 sm:mt-20">
        <p className="label-mono text-muted-foreground">PDF, PNG o JPG de hasta 10 MB</p>
        <h2 id="vender-documentos" className="mt-3 text-section">
          Documentos que vas a necesitar
        </h2>
        <div className="mt-8 grid gap-3 md:grid-cols-2">
          <DocumentList title="Persona natural" keys={REQUIRED_DOCUMENTS.NATURAL_PERSON} />
          <DocumentList title="Empresa (persona jurídica)" keys={REQUIRED_DOCUMENTS.LEGAL_ENTITY} />
        </div>
        <Button className="mt-8 h-11 px-5 text-base" nativeButton={false} render={<Link to={cta.to} />}>
          {cta.label}
        </Button>
      </section>
    </Container>
  )
}
