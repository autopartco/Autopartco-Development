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
    <footer className="surface-asphalt mt-16 border-t-2 border-ap-red-on-dark sm:mt-20">
      <Container className="grid gap-x-10 gap-y-10 py-12 sm:grid-cols-2 lg:grid-cols-[1.6fr_1fr_1fr]">
        <div className="flex flex-col items-start gap-4 sm:col-span-2 lg:col-span-1">
          <Logo tone="dark" />
          <p className="max-w-sm text-sm text-ap-on-dark-muted">
            Repuestos de los almacenes de tu ciudad en un solo lugar, para conductores y talleres. Compara precio,
            existencias y distancia, y revisa que la pieza le sirva al vehículo antes de salir.
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
        <div className="flex flex-col gap-6">
          <div>
            <ColumnTitle>Para almacenes</ColumnTitle>
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
          </div>
          <div>
            <ColumnTitle>Comprar en Autopartco</ColumnTitle>
            <p className="max-w-xs text-sm text-ap-on-dark-muted">
              La compra en línea llegará pronto. Mientras tanto, consulta precios y existencias, y contacta a la tienda.
            </p>
          </div>
        </div>
      </Container>
      <div className="border-t border-ap-line-on-dark">
        <Container className="py-5 text-xs text-ap-on-dark-muted">
          © {CURRENT_YEAR} Autopartco. Precios y existencias informados por cada tienda.
        </Container>
      </div>
    </footer>
  )
}
