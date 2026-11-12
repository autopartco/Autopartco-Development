import { Link } from 'react-router'
import { Container } from '@/components/common/page-header'
import { Logo } from '@/components/common/logo'
import { useSellerCta } from '@/features/seller/hooks'

const CURRENT_YEAR = new Date().getFullYear()

const LINKS = [
  { to: '/buscar', label: 'Buscar repuestos' },
  { to: '/categorias', label: 'Categorías' },
  { to: '/tiendas', label: 'Tiendas' },
]

/** Compra en línea: carrito, pedidos con seguimiento, calificaciones y avisos (las de cuenta piden ingresar). */
const BUYER_LINKS = [
  { to: '/carrito', label: 'Carrito' },
  { to: '/mis-pedidos', label: 'Mis pedidos' },
  { to: '/cuenta/calificar', label: 'Calificar mis compras' },
  { to: '/cuenta/notificaciones', label: 'Notificaciones' },
]

const LINK_CLASS =
  'inline-flex min-h-11 items-center text-ap-on-dark-muted underline-offset-4 transition-colors duration-150 hover:text-ap-white hover:underline md:min-h-8'

function ColumnTitle({ id, children }: { id?: string; children: string }) {
  return (
    <h2 id={id} className="mb-2 ap-index text-ap-on-dark-muted md:mb-3">
      {children}
    </h2>
  )
}

/** Pie grafito: estructura firme, enlaces claros y una línea roja superior como cierre de marca. */
export function SiteFooter() {
  const cta = useSellerCta()
  return (
    <footer className="ap-surface-dark mt-16 border-t-2 border-ap-red-on-dark sm:mt-20">
      <Container className="grid grid-cols-2 gap-x-10 gap-y-10 py-12 sm:grid-cols-3 lg:grid-cols-[1.6fr_1fr_1fr_1fr]">
        <div className="col-span-2 flex flex-col items-start gap-4 sm:col-span-3 lg:col-span-1">
          <Logo tone="dark" />
          <p className="max-w-sm text-sm text-ap-on-dark-muted">
            Repuestos de los almacenes de tu ciudad en un solo lugar, para conductores y talleres. Compara precio,
            existencias y distancia, revisa que la pieza le sirva a tu vehículo y cómprala en línea con seguimiento
            del pedido.
          </p>
        </div>
        <nav aria-labelledby="pie-explorar">
          <ColumnTitle id="pie-explorar">Explorar</ColumnTitle>
          <ul className="flex flex-col text-sm md:gap-1">
            {LINKS.map((link) => (
              <li key={link.to}>
                <Link to={link.to} className={LINK_CLASS}>
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <nav aria-labelledby="pie-comprar">
          <ColumnTitle id="pie-comprar">Comprar</ColumnTitle>
          <ul className="flex flex-col text-sm md:gap-1">
            {BUYER_LINKS.map((link) => (
              <li key={link.to}>
                <Link to={link.to} className={LINK_CLASS}>
                  {link.label}
                </Link>
              </li>
            ))}
          </ul>
        </nav>
        <nav aria-labelledby="pie-almacenes">
          <ColumnTitle id="pie-almacenes">Para almacenes</ColumnTitle>
          <ul className="flex flex-col text-sm md:gap-1">
            <li>
              <Link to="/vender" className={LINK_CLASS}>
                Vender en Autopartco
              </Link>
            </li>
            <li>
              <Link to={cta.state === 'loading' ? '/vender/registro' : cta.to} className={LINK_CLASS}>
                {cta.state === 'loading' || cta.state === 'learn' ? 'Registra tu almacén' : cta.label}
              </Link>
            </li>
          </ul>
        </nav>
      </Container>
      <div className="border-t border-ap-line-on-dark">
        <Container className="flex flex-col gap-1 py-5 text-xs text-ap-on-dark-muted sm:flex-row sm:items-center sm:justify-between">
          <p>© {CURRENT_YEAR} Autopartco. Precios y existencias informados por cada tienda.</p>
          <Link to="/privacidad" className={LINK_CLASS}>
            Privacidad y cookies
          </Link>
        </Container>
      </div>
    </footer>
  )
}
