import { Link } from 'react-router'
import {
  ArrowRightIcon,
  BadgeCheckIcon,
  BarChart3Icon,
  CalendarCheckIcon,
  ChevronRightIcon,
  EyeIcon,
  GiftIcon,
  MegaphoneIcon,
  MessageCircleIcon,
  ScaleIcon,
  SearchIcon,
  StoreIcon,
  UsersIcon,
  WrenchIcon,
  type LucideIcon,
} from 'lucide-react'
import { CountUp } from '@/components/common/count-up'
import { Container } from '@/components/common/page-header'
import { Reveal } from '@/components/common/reveal'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useSellerCta } from '@/features/seller/hooks'
import { useHome } from '../hooks'
import { categoryIcon } from '../home/category-icons'
import { categoryPath } from '../lib/paths'
import { humanizeCode } from '../lib/humanize-code'

/**
 * `/somos`: quiénes somos, en lenguaje comercial. Cada ventaja describe algo que el producto ya hace;
 * las cifras y categorías vienen del servidor (sin datos no se muestran).
 */
type Benefit = { icon: LucideIcon; title: string; body: string }

const DRIVER_BENEFITS: readonly Benefit[] = [
  {
    icon: SearchIcon,
    title: 'Encuentra lo que tu carro necesita',
    body: 'Busca el repuesto o el servicio y dinos la marca y el modelo de tu carro para ver lo que te sirve.',
  },
  {
    icon: ScaleIcon,
    title: 'Compara antes de pagar',
    body: 'Precios, disponibilidad y distancia de varios almacenes en una sola pantalla.',
  },
  {
    icon: MessageCircleIcon,
    title: 'Habla directo con la tienda',
    body: 'Escríbele por WhatsApp, resuelve tus dudas y mira cómo llegar. Sin intermediarios.',
  },
  {
    icon: CalendarCheckIcon,
    title: 'Compra o agenda en minutos',
    body: 'Paga tu repuesto en línea y sigue tu pedido, o reserva tu cita en el taller.',
  },
  {
    icon: BadgeCheckIcon,
    title: 'Elige con confianza',
    body: 'Cada almacén se revisa antes de publicar y las opiniones son de personas que ya compraron.',
  },
  {
    icon: WrenchIcon,
    title: 'Hecho para el taller también',
    body: 'Si eres mecánico, consigue rápido lo que tus clientes necesitan, cerca de tu taller.',
  },
]

const BUSINESS_BENEFITS: readonly Benefit[] = [
  {
    icon: EyeIcon,
    title: 'Clientes que ya te buscan',
    body: 'Apareces justo cuando un conductor o un mecánico busca lo que vendes, cerca de ti.',
  },
  {
    icon: StoreIcon,
    title: 'Tu vitrina abierta todo el día',
    body: 'Publica repuestos y servicios; recibe pedidos pagados en línea, citas y mensajes por WhatsApp.',
  },
  {
    icon: UsersIcon,
    title: 'Tu negocio ordenado',
    body: 'Productos, inventario por sucursal, pedidos, citas y tu equipo, cada uno con sus permisos.',
  },
  {
    icon: BarChart3Icon,
    title: 'Decide con información',
    body: 'Mira qué se vende, qué se compra junto, qué podría agotarse y quiénes son tus mejores clientes.',
  },
  {
    icon: MegaphoneIcon,
    title: 'Haz que te vean más',
    body: 'Crea promociones y campañas para destacar tus productos ante quienes están listos para comprar.',
  },
]

/** Cifras reales de la plataforma; en error no se muestra nada y mientras carga, un esqueleto. */
function LiveStats() {
  const home = useHome({})
  if (home.status === 'error') return null
  const stats = home.data?.stats
  const figures = stats
    ? [
        { label: stats.stores === 1 ? 'almacén verificado' : 'almacenes verificados', value: stats.stores },
        { label: stats.cities === 1 ? 'ciudad' : 'ciudades', value: stats.cities },
        { label: stats.products === 1 ? 'repuesto publicado' : 'repuestos publicados', value: stats.products },
      ]
    : undefined
  return (
    <dl className="grid grid-cols-3 border-t border-ap-line-on-dark lg:grid-cols-1">
      {figures
        ? figures.map((figure) => (
            <div key={figure.label} className="flex flex-col gap-1 border-b border-ap-line-on-dark py-4 pr-3 lg:py-5">
              <dd className="order-1 ap-price text-3xl tabular-nums text-ap-white sm:text-4xl lg:text-5xl">
                <CountUp value={figure.value} />
              </dd>
              <dt className="order-2 text-xs text-ap-on-dark-muted sm:text-sm">{figure.label}</dt>
            </div>
          ))
        : Array.from({ length: 3 }, (_, index) => (
            <div key={index} className="flex flex-col gap-2 border-b border-ap-line-on-dark py-5" aria-hidden>
              <Skeleton className="h-10 w-24 opacity-20" />
              <Skeleton className="h-3 w-32 opacity-20" />
            </div>
          ))}
    </dl>
  )
}

/** Categorías reales con producto publicado: prueba de lo que hay hoy. */
function LiveCategories() {
  const home = useHome({})
  const categories = home.data?.categories?.filter((category) => category.productCount > 0).slice(0, 10)
  if (home.status === 'error' || (categories && categories.length === 0)) return null
  return (
    <section aria-labelledby="somos-categorias" className="border-y border-ap-line bg-ap-white py-10 sm:py-12">
      <Container className="flex flex-col gap-5">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="ap-index">Catálogo / hoy</p>
            <h2 id="somos-categorias" className="mt-1 text-block-title text-ap-ink">
              Lo que ya encuentras en Autopartco
            </h2>
          </div>
          <Link to="/categorias" className="inline-flex min-h-11 items-center gap-1 text-sm font-semibold text-ap-red hover:underline">
            Ver todas las categorías
            <ChevronRightIcon className="size-4" aria-hidden />
          </Link>
        </div>
        <ul className="flex flex-wrap gap-2">
          {categories
            ? categories.map((category) => {
                const Icon = categoryIcon(category.code)
                return (
                  <li key={category.id}>
                    <Link
                      to={categoryPath(category.code.toLowerCase())}
                      className="group inline-flex min-h-11 items-center gap-2 rounded-md border border-ap-line-strong bg-ap-white px-3 text-sm text-ap-ink transition-colors hover:border-ap-graphite hover:bg-ap-paper"
                    >
                      <Icon className="size-4 text-ap-steel group-hover:text-ap-red" aria-hidden />
                      <span className="font-medium">{humanizeCode(category.code)}</span>
                      <span className="ap-ref text-ap-muted">{category.productCount}</span>
                    </Link>
                  </li>
                )
              })
            : Array.from({ length: 6 }, (_, index) => (
                <li key={index} aria-hidden>
                  <Skeleton className="h-11 w-36" />
                </li>
              ))}
        </ul>
      </Container>
    </section>
  )
}

function AudienceLink({ href, kicker, title, body }: { href: string; kicker: string; title: string; body: string }) {
  return (
    <a
      href={href}
      className="group flex items-start justify-between gap-4 bg-ap-white p-5 transition-colors hover:bg-ap-paper sm:p-7"
    >
      <span className="flex flex-col gap-1.5">
        <span className="ap-index">{kicker}</span>
        <span className="text-block-title text-ap-ink group-hover:text-ap-red">{title}</span>
        <span className="text-sm text-ap-muted">{body}</span>
      </span>
      <span className="mt-1 grid size-10 shrink-0 place-items-center rounded-md border border-ap-line-strong text-ap-graphite transition-colors group-hover:border-ap-red group-hover:bg-ap-red group-hover:text-ap-white">
        <ArrowRightIcon className="ap-nudge size-5" aria-hidden />
      </span>
    </a>
  )
}

function IconMark({ icon: Icon, tone = 'light' }: { icon: LucideIcon; tone?: 'light' | 'red' }) {
  return (
    <span
      className={
        tone === 'red'
          ? 'grid size-11 shrink-0 place-items-center rounded-md bg-ap-red text-ap-white'
          : 'grid size-11 shrink-0 place-items-center rounded-md border border-ap-line-strong bg-ap-white text-ap-red'
      }
      aria-hidden
    >
      <Icon className="size-5" />
    </span>
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
      <title>Somos Autopartco | Autopartco</title>
      <meta
        name="description"
        content="Autopartco reúne repuestos, talleres y almacenes de tu ciudad en un solo lugar. Compara, compra y agenda con confianza."
      />

      {/* 1. Presentación */}
      <section aria-labelledby="somos-titulo" className="ap-surface-dark">
        <Container className="grid gap-10 pt-10 pb-24 sm:pt-14 sm:pb-28 lg:grid-cols-[minmax(0,8fr)_minmax(0,4fr)] lg:gap-16 lg:pt-20 lg:pb-32">
          <div className="flex flex-col gap-6">
            <nav aria-label="Ruta" className="text-sm text-ap-on-dark-muted">
              <Link to="/" className="underline-offset-4 hover:text-ap-white hover:underline">
                Inicio
              </Link>
              <span aria-hidden> / </span>
              <span aria-current="page" className="text-ap-white">
                Somos
              </span>
            </nav>
            <p className="ap-index">Autopartco / Quiénes somos</p>
            <h1
              id="somos-titulo"
              className="max-w-3xl text-[clamp(2.5rem,1.6rem+3.6vw,4.25rem)] leading-[1.02] font-bold tracking-[-0.03em] text-ap-white"
            >
              Todo lo que tu carro necesita,{' '}
              <span className="relative whitespace-nowrap">
                en un solo lugar
                <span aria-hidden className="absolute inset-x-0 -bottom-1 h-1.5 bg-ap-red sm:-bottom-2 sm:h-2" />
              </span>
              .
            </h1>
            <p className="max-w-2xl text-lg text-ap-on-dark-muted sm:text-xl">
              Autopartco conecta a quienes cuidan su carro con los almacenes y talleres de Colombia. Encuentra, compara y
              compra sin perder tiempo. Y si tienes un negocio, llega a más clientes y vende más.
            </p>
            <div className="mt-2 flex flex-wrap gap-3">
              <Button size="lg" nativeButton={false} render={<Link to="/buscar" />}>
                Buscar repuestos y servicios
                <ArrowRightIcon data-icon="inline-end" aria-hidden />
              </Button>
              <Button variant="outline" size="lg" nativeButton={false} render={<Link to={sellerCta.to} />}>
                <StoreIcon data-icon="inline-start" aria-hidden />
                Soy almacén o taller
              </Button>
            </div>
          </div>
          <div className="flex flex-col justify-end">
            <p className="ap-index mb-2">Autopartco / en cifras</p>
            <LiveStats />
          </div>
        </Container>
      </section>

      {/* 2. Accesos por público, montados sobre el borde de la presentación */}
      <Container className="-mt-14 sm:-mt-16">
        <div className="grid gap-px overflow-hidden rounded-lg border border-ap-line bg-ap-line shadow-[0_12px_32px_-16px_rgb(0_0_0/0.35)] md:grid-cols-2">
          <AudienceLink
            href="#somos-conductores"
            kicker="Para ti / 01"
            title="Busco repuestos o un taller"
            body="Conductores y mecánicos que quieren resolver rápido y pagar lo justo."
          />
          <AudienceLink
            href="#somos-empresas"
            kicker="Para tu negocio / 02"
            title="Tengo un almacén o un taller"
            body="Negocios que quieren más clientes y una forma más simple de vender."
          />
        </div>
      </Container>

      {/* 3. Propósito */}
      <section aria-labelledby="somos-proposito" className="py-16 sm:py-24">
        <Container className="grid gap-8 lg:grid-cols-[minmax(0,4fr)_minmax(0,8fr)] lg:gap-16">
          <div>
            <p className="ap-index">Propósito</p>
            <h2 id="somos-proposito" className="mt-2 text-section text-ap-ink">
              Por qué existimos
            </h2>
          </div>
          <div className="flex flex-col gap-6">
            <p className="text-[clamp(1.375rem,1.05rem+1.3vw,2rem)] leading-snug font-semibold tracking-[-0.015em] text-ap-ink">
              Conseguir un repuesto no debería significar recorrer media ciudad, hacer diez llamadas o comprar sin saber
              si le sirve a tu carro. <span className="text-ap-red">Y un buen almacén no debería depender solo de quien pasa
              por la puerta.</span>
            </p>
            <p className="max-w-2xl text-lg text-ap-muted">
              Por eso creamos Autopartco: una vitrina confiable donde la oferta de cada ciudad se ve completa, los precios
              se comparan con claridad y comprar o reservar es tan fácil como buscar.
            </p>
          </div>
        </Container>
      </section>

      <LiveCategories />

      {/* 4. Conductores y mecánicos */}
      <section id="somos-conductores" aria-labelledby="somos-conductores-titulo" className="scroll-mt-24 py-16 sm:py-24">
        <Container className="flex flex-col gap-10">
          <div className="flex max-w-2xl flex-col gap-3">
            <p className="ap-index">Para conductores y mecánicos</p>
            <h2 id="somos-conductores-titulo" className="text-[clamp(1.75rem,1.3rem+1.8vw,2.5rem)] leading-tight font-bold tracking-[-0.02em] text-ap-ink">
              Menos vueltas. Mejores decisiones.
            </h2>
            <p className="text-lg text-ap-muted">Para quien arregla su propio carro y para quien atiende un taller todos los días.</p>
          </div>
          <ul className="grid gap-x-8 sm:grid-cols-2 lg:grid-cols-3">
            {DRIVER_BENEFITS.map((benefit) => (
              <Reveal as="li" key={benefit.title} className="flex gap-4 border-t-2 border-ap-graphite py-6">
                <IconMark icon={benefit.icon} />
                <div className="flex flex-col gap-1.5">
                  <h3 className="text-base font-semibold text-ap-ink">{benefit.title}</h3>
                  <p className="text-sm text-ap-muted">{benefit.body}</p>
                </div>
              </Reveal>
            ))}
          </ul>
          <div>
            <Button size="lg" nativeButton={false} render={<Link to="/buscar" />}>
              Empezar a buscar
              <ArrowRightIcon data-icon="inline-end" aria-hidden />
            </Button>
          </div>
        </Container>
      </section>

      {/* 5. Almacenes y talleres */}
      <section id="somos-empresas" aria-labelledby="somos-empresas-titulo" className="scroll-mt-24 border-t border-ap-line bg-ap-paper py-16 sm:py-24">
        <Container className="grid gap-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-16">
          <div className="flex flex-col gap-5 lg:sticky lg:top-[calc(var(--header-h)+2rem)] lg:self-start">
            <p className="ap-index">Para almacenes y talleres</p>
            <h2 id="somos-empresas-titulo" className="text-[clamp(1.75rem,1.3rem+1.8vw,2.5rem)] leading-tight font-bold tracking-[-0.02em] text-ap-ink">
              Más clientes, más ventas, menos complicaciones.
            </h2>
            <p className="text-lg text-ap-muted">
              Autopartco es tu vitrina en línea y también la herramienta para manejar tu negocio día a día.
            </p>
            <div className="flex items-start gap-4 rounded-lg border border-ap-line border-l-4 border-l-ap-red bg-ap-white p-5">
              <IconMark icon={GiftIcon} tone="red" />
              <div className="flex flex-col gap-1">
                <p className="font-semibold text-ap-ink">Tu primer mes es gratis</p>
                <p className="text-sm text-ap-muted">Configura tu almacén, publica y elige plan después. Sin compromiso.</p>
              </div>
            </div>
            <div>
              <Button size="lg" nativeButton={false} render={<Link to={sellerCta.to} />}>
                {sellerCta.label}
                <ArrowRightIcon data-icon="inline-end" aria-hidden />
              </Button>
            </div>
          </div>
          <ul className="grid gap-px overflow-hidden rounded-lg border border-ap-line bg-ap-line sm:grid-cols-2">
            {BUSINESS_BENEFITS.map((benefit, index) => (
              <Reveal
                as="li"
                key={benefit.title}
                className={`flex flex-col gap-4 bg-ap-white p-6 ${index === BUSINESS_BENEFITS.length - 1 ? 'sm:col-span-2' : ''}`}
              >
                <IconMark icon={benefit.icon} />
                <div className="flex flex-col gap-1.5">
                  <h3 className="text-base font-semibold text-ap-ink">{benefit.title}</h3>
                  <p className="text-sm text-ap-muted">{benefit.body}</p>
                </div>
              </Reveal>
            ))}
          </ul>
        </Container>
      </section>

      {/* 6. Invitación (pegada al pie) */}
      <section aria-labelledby="somos-invitacion" className="ap-surface-dark -mb-16 sm:-mb-20">
        <Container className="flex flex-col gap-12 py-16 sm:py-24">
          <div className="flex max-w-3xl flex-col gap-4">
            <p className="ap-index">Únete</p>
            <h2
              id="somos-invitacion"
              className="text-[clamp(2rem,1.4rem+2.6vw,3.25rem)] leading-[1.05] font-bold tracking-[-0.025em] text-ap-white"
            >
              Bienvenido a <span className="text-ap-red-on-dark">autopartco.co</span>
            </h2>
            <p className="text-lg text-ap-on-dark-muted">
              Seas quien busca o quien vende, aquí hay un lugar para ti. Únete a la comunidad que está cambiando la forma de
              comprar y vender repuestos en Colombia.
            </p>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div className="flex flex-col items-start gap-4 rounded-lg border border-ap-line-on-dark p-6 sm:p-8">
              <IconMark icon={SearchIcon} tone="red" />
              <h3 className="text-block-title text-ap-white">Si buscas repuestos o servicios</h3>
              <p className="text-ap-on-dark-muted">
                Crea tu cuenta gratis, encuentra lo que tu carro necesita y compra o agenda con la tranquilidad de elegir
                bien.
              </p>
              <div className="mt-auto flex flex-wrap gap-2 pt-2">
                <Button size="lg" nativeButton={false} render={<Link to="/registro/cliente" />}>
                  Crear mi cuenta gratis
                </Button>
                <Button variant="outline" size="lg" nativeButton={false} render={<Link to="/buscar" />}>
                  Empezar a buscar
                </Button>
              </div>
            </div>
            <div className="flex flex-col items-start gap-4 rounded-lg border border-ap-line-on-dark p-6 sm:p-8">
              <IconMark icon={StoreIcon} tone="red" />
              <h3 className="text-block-title text-ap-white">Si tienes un almacén o un taller</h3>
              <p className="text-ap-on-dark-muted">
                Registra tu negocio, publica tus productos y servicios y empieza a recibir clientes. El primer mes es gratis.
              </p>
              <div className="mt-auto flex flex-wrap gap-2 pt-2">
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
