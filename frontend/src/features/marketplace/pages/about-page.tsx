import { Link } from 'react-router'
import { Container, PageIntro } from '@/components/common/page-header'
import { Button } from '@/components/ui/button'
import { useSellerCta } from '@/features/seller/hooks'

/**
 * `/somos`: quiénes somos, en lenguaje comercial. Cada ventaja describe algo que el producto ya hace;
 * sin cifras: los conteos reales viven en el inicio.
 */
const DRIVER_BENEFITS = [
  {
    title: 'Encuentra lo que tu carro necesita',
    body: 'Busca por nombre del repuesto o del servicio y dinos la marca y el modelo de tu carro para ver lo que te sirve.',
  },
  {
    title: 'Compara antes de pagar',
    body: 'Precios, disponibilidad y distancia de varios almacenes en una sola pantalla. Tú decides con toda la información.',
  },
  {
    title: 'Habla directo con la tienda',
    body: 'Escríbele por WhatsApp, haz tus preguntas y mira cómo llegar. Sin intermediarios.',
  },
  {
    title: 'Compra o agenda en minutos',
    body: 'Paga tu repuesto en línea y sigue tu pedido, o reserva tu cita en el taller cuando te quede mejor.',
  },
  {
    title: 'Elige con confianza',
    body: 'Cada almacén se revisa antes de publicar y las opiniones vienen de personas que ya compraron.',
  },
] as const

const BUSINESS_BENEFITS = [
  {
    label: 'Visibilidad',
    title: 'Clientes que ya te están buscando',
    body: 'Tu almacén o taller aparece justo cuando un conductor o un mecánico busca lo que vendes, cerca de ti.',
  },
  {
    label: 'Ventas',
    title: 'Una vitrina abierta todo el día',
    body: 'Publica repuestos y servicios, recibe pedidos pagados en línea, citas y mensajes por WhatsApp.',
  },
  {
    label: 'Gestión',
    title: 'Tu negocio ordenado en un solo lugar',
    body: 'Productos, inventario por sucursal, pedidos, citas y tu equipo de trabajo, cada uno con sus permisos.',
  },
  {
    label: 'Crecimiento',
    title: 'Decide con información, no con intuición',
    body: 'Mira qué se vende, qué se compra junto, qué podría agotarse y quiénes son tus mejores clientes.',
  },
  {
    label: 'Promoción',
    title: 'Haz que te vean más',
    body: 'Crea promociones y campañas para destacar tus productos frente a quienes están listos para comprar.',
  },
  {
    label: 'Sin riesgo',
    title: 'Empieza gratis',
    body: 'Tu primer mes es de prueba. Configuras tu almacén, publicas y eliges plan después.',
  },
] as const

function Benefit({ index, title, body }: { index: number; title: string; body: string }) {
  return (
    <li className="grid grid-cols-[2.5rem_minmax(0,1fr)] gap-x-4 border-b border-ap-line py-4 sm:grid-cols-[2.5rem_18rem_minmax(0,1fr)]">
      <span className="ap-ref pt-0.5 text-ap-muted" aria-hidden>
        {String(index + 1).padStart(2, '0')}
      </span>
      <h3 className="font-semibold text-ap-ink">{title}</h3>
      <p className="col-start-2 text-sm text-ap-muted sm:col-start-3">{body}</p>
    </li>
  )
}

export default function AboutPage() {
  const seller = useSellerCta()
  const sellerCta =
    seller.state === 'loading' || seller.state === 'learn' || seller.state === 'register'
      ? { to: '/vender', label: 'Registrar mi negocio' }
      : { to: seller.to, label: seller.label }

  return (
    <>
      <Container className="py-6 sm:py-8">
        <title>Somos Autopartco | Autopartco</title>
        <meta
          name="description"
          content="Autopartco reúne repuestos, talleres y almacenes de tu ciudad en un solo lugar. Compara, compra y agenda con confianza."
        />
        <PageIntro
          crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Somos' }]}
          label="Autopartco / Quiénes somos"
          title="Todo lo que tu carro necesita, en un solo lugar"
          description={
            <p>
              Autopartco es el punto de encuentro entre quienes cuidan su carro y los almacenes y talleres de Colombia.
              Reunimos repuestos y servicios de tu ciudad para que encuentres, compares y compres sin perder tiempo, y
              para que cada negocio llegue a más clientes y venda más.
            </p>
          }
          className="max-w-3xl"
        >
          <div className="mt-3 flex flex-wrap gap-2">
            <Button size="lg" nativeButton={false} render={<Link to="/buscar" />}>
              Buscar repuestos y servicios
            </Button>
            <Button variant="outline" size="lg" nativeButton={false} render={<Link to={sellerCta.to} />}>
              Soy almacén o taller
            </Button>
          </div>
        </PageIntro>

        <section aria-labelledby="somos-proposito" className="mt-12 grid gap-6 border-t-2 border-ap-graphite pt-6 sm:mt-16 lg:grid-cols-[1fr_2fr] lg:gap-10">
          <div>
            <p className="ap-index">Propósito / 01</p>
            <h2 id="somos-proposito" className="mt-2 text-section text-ap-ink">
              Por qué existimos
            </h2>
          </div>
          <div className="flex max-w-2xl flex-col gap-4 text-ap-ink">
            <p>
              Conseguir un repuesto no debería significar recorrer media ciudad, hacer diez llamadas o comprar sin saber
              si le sirve a tu carro. Y un buen almacén no debería depender solo de quien pasa por la puerta.
            </p>
            <p className="text-ap-muted">
              Por eso creamos Autopartco: una vitrina confiable donde la oferta de cada ciudad se ve completa, los precios
              se comparan con claridad y comprar o reservar es tan fácil como buscar.
            </p>
          </div>
        </section>

        <section aria-labelledby="somos-conductores" className="mt-12 sm:mt-16">
          <p className="ap-index">Para conductores y mecánicos / 02</p>
          <h2 id="somos-conductores" className="mt-2 text-section text-ap-ink">
            Menos vueltas, mejores decisiones
          </h2>
          <p className="mt-2 max-w-2xl text-ap-muted">
            Para quien arregla su propio carro y para quien atiende un taller todos los días.
          </p>
          <ol className="mt-6 grid max-w-4xl border-t border-ap-graphite">
            {DRIVER_BENEFITS.map((benefit, index) => (
              <Benefit key={benefit.title} index={index} title={benefit.title} body={benefit.body} />
            ))}
          </ol>
        </section>

        <section aria-labelledby="somos-empresas" className="mt-12 sm:mt-16">
          <p className="ap-index">Para almacenes y talleres / 03</p>
          <h2 id="somos-empresas" className="mt-2 text-section text-ap-ink">
            Más clientes, más ventas, menos complicaciones
          </h2>
          <p className="mt-2 max-w-2xl text-ap-muted">
            Autopartco es tu vitrina en línea y también la herramienta para manejar tu negocio día a día.
          </p>
          <dl className="mt-6 grid gap-x-10 sm:grid-cols-2 lg:grid-cols-3">
            {BUSINESS_BENEFITS.map((benefit) => (
              <div key={benefit.title} className="flex flex-col gap-1.5 border-t border-ap-line py-5">
                <p className="ap-index">{benefit.label}</p>
                <dt className="font-semibold text-ap-ink">{benefit.title}</dt>
                <dd className="text-sm text-ap-muted">{benefit.body}</dd>
              </div>
            ))}
          </dl>
        </section>
      </Container>

      <section aria-labelledby="somos-invitacion" className="ap-surface-dark mt-12 border-t-2 border-ap-red-on-dark sm:mt-16">
        <Container className="flex flex-col gap-10 py-12 sm:py-16">
          <div className="flex max-w-2xl flex-col gap-3">
            <p className="ap-index">Únete / 04</p>
            <h2 id="somos-invitacion" className="text-section">
              Bienvenido a autopartco.co
            </h2>
            <p className="text-ap-on-dark-muted">
              Seas quien busca o quien vende, aquí hay un lugar para ti. Te invitamos a ser parte de la comunidad
              automotriz que está cambiando la forma de comprar y vender repuestos en Colombia.
            </p>
          </div>
          <div className="grid gap-8 md:grid-cols-2 md:gap-0 md:divide-x md:divide-ap-line-on-dark">
            <div className="flex flex-col items-start gap-3 md:pr-10">
              <h3 className="text-block-title">Si buscas repuestos o servicios</h3>
              <p className="text-sm text-ap-on-dark-muted">
                Crea tu cuenta gratis, encuentra lo que tu carro necesita y compra o agenda con la tranquilidad de
                elegir bien.
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                <Button size="lg" nativeButton={false} render={<Link to="/registro/cliente" />}>
                  Crear mi cuenta
                </Button>
                <Button variant="outline" size="lg" nativeButton={false} render={<Link to="/buscar" />}>
                  Empezar a buscar
                </Button>
              </div>
            </div>
            <div className="flex flex-col items-start gap-3 border-t border-ap-line-on-dark pt-8 md:border-t-0 md:pt-0 md:pl-10">
              <h3 className="text-block-title">Si tienes un almacén o un taller</h3>
              <p className="text-sm text-ap-on-dark-muted">
                Registra tu negocio, publica tus productos y servicios y empieza a recibir clientes. El primer mes es
                gratis.
              </p>
              <div className="mt-2 flex flex-wrap gap-2">
                <Button size="lg" nativeButton={false} render={<Link to={sellerCta.to} />}>
                  {sellerCta.label}
                </Button>
                <Button variant="outline" size="lg" nativeButton={false} render={<Link to="/tiendas" />}>
                  Ver quiénes ya están
                </Button>
              </div>
            </div>
          </div>
        </Container>
      </section>
    </>
  )
}
