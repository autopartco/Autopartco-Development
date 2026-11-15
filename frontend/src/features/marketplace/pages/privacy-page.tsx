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
      'El área que elegiste en el mapa, redondeada a unos 1 km. Sirve para mostrar almacenes, servicios y repuestos cercanos.',
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
        description="Qué datos usamos cuando compras repuestos, agendas servicios o buscas almacenes cerca de ti, para qué, qué derechos tienes y qué cookies usa el sitio."
      >
        <p className="text-sm text-ap-muted">Última actualización: 15 de noviembre de 2026.</p>
      </PageIntro>

      <div className="flex flex-col gap-8">
        <Section id="responsable" title="Responsable del tratamiento">
          <p>
            El responsable de tus datos personales es Autopartco. Tratamos la información conforme a la Ley 1581 de 2012,
            el Decreto 1377 de 2013 y las demás normas de protección de datos de Colombia.
          </p>
          <p>
            Al crear tu cuenta, comprar, agendar un servicio o usar las funciones del sitio, nos autorizas para tratar
            tus datos como se explica aquí. Solo pedimos los datos que necesitamos para cada función.
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
              <strong>Citas de servicio:</strong> el servicio, la sede, la fecha y la hora, tu teléfono de contacto, tu
              vehículo (marca, modelo, año y, si quieres, la placa) y tus notas. Los usamos para gestionar tu cita y
              avisarte por correo y notificaciones cuando cambie.
            </li>
            <li>
              <strong>Ubicación:</strong> tu ubicación aproximada o la ciudad que elijas, para mostrarte almacenes,
              servicios y repuestos cerca de ti.
            </li>
            <li>
              <strong>Estadísticas anónimas:</strong> visitas a perfiles, productos y servicios, y clics en WhatsApp y en
              «Cómo llegar», sin identificar a la persona, para mejorar el sitio y mostrarle a cada almacén cómo le va.
            </li>
          </ul>
        </Section>

        <Section id="citas" title="Citas de servicio y almacenes">
          <p>
            Cuando agendas un servicio, compartimos los datos de la cita con el almacén que lo presta, para que pueda
            atenderte. Ese almacén solo ve tu teléfono y la placa de tu vehículo mientras la cita no esté cancelada,
            rechazada o vencida. Después deja de verlos.
          </p>
          <p>
            Autopartco no cobra los servicios en línea: el precio que ves es de referencia («desde») y lo confirma el
            almacén.
          </p>
        </Section>

        <Section id="whatsapp" title="WhatsApp">
          <p>
            Al tocar «Escribir por WhatsApp» pasas a WhatsApp. La conversación ocurre allí, fuera de Autopartco, y se rige
            por la política de privacidad de WhatsApp y Meta. Autopartco no ve ni guarda lo que escribas.
          </p>
          <p>
            Lo único que registramos es el clic, de forma anónima, para contarle al almacén cuántas personas quisieron
            escribirle.
          </p>
        </Section>

        <Section id="ubicacion" title="Tu ubicación">
          <p>
            Usamos tu ubicación aproximada o la ciudad que elijas para mostrarte lo que tienes cerca. Puedes cambiarla en
            cualquier momento desde el selector de ubicación.
          </p>
          <ul className="list-disc space-y-2 pl-5">
            <li>
              <strong>Zona elegida:</strong> se guarda en tu navegador, redondeada a unos 1 km, durante 30 días (cookie{' '}
              <span className="font-mono text-[0.8125rem]">ap_area</span>).
            </li>
            <li>
              <strong>Ubicación exacta:</strong> solo si tocas «Usar mi ubicación» y aceptas el permiso de tu navegador.
              Se usa en el momento, redondeada a unos 100 m, para ordenar los resultados por cercanía. No se guarda y no
              queda ligada a tu cuenta.
            </li>
            <li>
              <strong>Buscador de lugares:</strong> para sugerir direcciones y nombrar tu zona usamos un servicio externo
              de mapas (Photon). Recibe el texto que escribes o la zona redondeada, no tu cuenta.
            </li>
          </ul>
        </Section>

        <Section id="metricas" title="Estadísticas anónimas de visitas">
          <p>
            Medimos visitas a perfiles de almacén, productos y servicios, y los clics en WhatsApp y en «Cómo llegar».
            Para no contar dos veces a la misma persona usamos un identificador anónimo que cambia cada día. No guardamos
            tu IP ni lo relacionamos con tu cuenta. Cada almacén ve solo totales, nunca quién fue.
          </p>
        </Section>

        <Section id="derechos" title="Tus derechos como titular">
          <p>Según la Ley 1581 de 2012 y el Decreto 1377 de 2013 puedes, en cualquier momento:</p>
          <ul className="list-disc space-y-1 pl-5">
            <li>Conocer los datos que tenemos sobre ti.</li>
            <li>Actualizarlos y rectificarlos.</li>
            <li>Solicitar que los suprimamos.</li>
            <li>Revocar la autorización que nos diste.</li>
            <li>Pedir prueba de la autorización y saber cómo usamos tus datos.</li>
            <li>Presentar quejas ante la Superintendencia de Industria y Comercio.</li>
          </ul>
          <p>
            Desde Mi cuenta, en Privacidad, puedes descargar una copia de tus datos, que ya incluye tus citas de servicio,
            y solicitar la eliminación de tu cuenta.
          </p>
          <p>
            Para cualquier otra solicitud:{' '}
            {contact ? <>escríbenos a {contact}.</> : <>escríbenos desde el formulario de contacto.</>} Respondemos las
            consultas en máximo 10 días hábiles y los reclamos en máximo 15 días hábiles.
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
