import type { ReactNode } from 'react'
import { Container, PageIntro } from '@/components/common/page-header'

/** Correo público de soporte (`VITE_SUPPORT_EMAIL`); sin él no se inventa ningún destino. */
const SUPPORT_EMAIL = (import.meta.env.VITE_SUPPORT_EMAIL as string | undefined)?.trim()

const COOKIES: { name: string; purpose: ReactNode; duration: string }[] = [
  {
    name: 'ap_aid',
    purpose:
      'Identificador anónimo aleatorio (HttpOnly). Sirve para estadísticas agregadas de visitas y anuncios, y para atribuir compras a anuncios sin identificarte. Se guarda como hash con sal.',
    duration: '1 año',
  },
  {
    name: 'ap_area',
    purpose:
      'El área que elegiste en el mapa, redondeada a unos 1 km. Sirve para mostrar almacenes y repuestos cercanos.',
    duration: '30 días',
  },
  {
    name: 'autopartco_refresh',
    purpose: 'Cookie de sesión (HttpOnly). Mantiene tu inicio de sesión y se borra al cerrar sesión.',
    duration: 'Mientras la sesión esté vigente',
  },
]

function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="border-t border-ap-line pt-6">
      <h2 id={id} className="mb-3 text-xl font-semibold text-ap-graphite">
        {title}
      </h2>
      <div className="flex max-w-prose flex-col gap-3 text-ap-ink">{children}</div>
    </section>
  )
}

const LINK_CLASS = 'font-medium text-ap-red underline underline-offset-4 hover:text-ap-red-hover'

export default function PrivacyPage() {
  const contact = SUPPORT_EMAIL ? (
    <a href={`mailto:${SUPPORT_EMAIL}`} className={LINK_CLASS}>
      {SUPPORT_EMAIL}
    </a>
  ) : null

  return (
    <Container className="pb-4 pt-6 sm:pt-8">
      <title>Privacidad y cookies | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Privacidad y cookies' }]}
        label="LEGAL / PRIVACIDAD"
        title="Privacidad y cookies"
        description="Qué datos tratamos, para qué, qué derechos tienes y qué cookies usa el sitio."
      >
        <p className="text-sm text-ap-muted">Actualizado el 11 de noviembre de 2026.</p>
      </PageIntro>

      <div className="flex flex-col gap-8">
        <Section id="responsable" title="Responsable del tratamiento">
          <p>
            El responsable de tus datos personales es Autopartco. Tratamos la información conforme a la Ley 1581 de 2012
            y sus normas reglamentarias en Colombia.
          </p>
          <p>
            {contact ? (
              <>Para cualquier consulta sobre tus datos, escríbenos a {contact}.</>
            ) : (
              <>Para cualquier consulta sobre tus datos, escríbenos desde el formulario de contacto.</>
            )}
          </p>
        </Section>

        <Section id="datos" title="Qué datos tratamos y para qué">
          <ul className="list-disc space-y-2 pl-5">
            <li>
              <strong>Cuenta:</strong> los datos con los que te registras, para que puedas ingresar y gestionar tu perfil.
            </li>
            <li>
              <strong>Pedidos:</strong> lo que compras y los datos de entrega, para procesar y dar seguimiento a tu pedido.
            </li>
            <li>
              <strong>Pagos:</strong> los procesa Wompi. Autopartco no guarda datos de tarjetas; solo recibe el estado del
              pago.
            </li>
            <li>
              <strong>Estadísticas agregadas:</strong> visitas y anuncios medidos sin identificar a la persona, para
              mejorar el sitio.
            </li>
          </ul>
        </Section>

        <Section id="derechos" title="Tus derechos como titular">
          <p>Según la Ley 1581 de 2012 puedes, en cualquier momento:</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>Conocer los datos que tenemos sobre ti.</li>
            <li>Actualizarlos y rectificarlos.</li>
            <li>Solicitar que los suprimamos.</li>
            <li>Revocar la autorización que nos diste.</li>
          </ul>
          <p>
            Puedes ejercerlos desde tu cuenta o por contacto:{' '}
            {contact ? <>escríbenos a {contact}.</> : <>escríbenos desde el formulario de contacto.</>}
          </p>
        </Section>

        <Section id="cookies" title="Cookies y almacenamiento local">
          <p>
            Usamos solo cookies funcionales y necesarias. No requieren aceptación porque el sitio no funcionaría bien sin
            ellas.
          </p>
          <div className="max-w-4xl overflow-x-auto rounded-md border border-ap-line">
            <table className="w-full min-w-[34rem] border-collapse text-left text-sm">
              <caption className="sr-only">Cookies funcionales y necesarias que usa Autopartco</caption>
              <thead className="bg-ap-paper text-ap-graphite">
                <tr>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    Nombre
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    Para qué sirve
                  </th>
                  <th scope="col" className="px-3 py-2 font-semibold">
                    Duración
                  </th>
                </tr>
              </thead>
              <tbody>
                {COOKIES.map((cookie) => (
                  <tr key={cookie.name} className="border-t border-ap-line align-top">
                    <th scope="row" className="px-3 py-2 font-mono text-[0.8125rem] font-medium text-ap-ink">
                      {cookie.name}
                    </th>
                    <td className="px-3 py-2">{cookie.purpose}</td>
                    <td className="px-3 py-2 whitespace-nowrap">{cookie.duration}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p>
            Además, guardamos en el almacenamiento local de tu navegador el carrito, la vista del área elegida y un
            identificador de sesión anónimo; no se envían a terceros.
          </p>
          <p>
            No usamos cookies de marketing ni analíticas de terceros. Si algún día las agregamos, te pediremos tu
            consentimiento antes.
          </p>
        </Section>
      </div>
    </Container>
  )
}
