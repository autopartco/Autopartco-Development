import { Link } from 'react-router'
import { Container } from '@/components/common/page-header'
import { Logo } from '@/components/common/logo'

const CURRENT_YEAR = new Date().getFullYear()

const LINKS = [
  { to: '/buscar', label: 'Buscar repuestos' },
  { to: '/categorias', label: 'Categorías' },
  { to: '/tiendas', label: 'Tiendas' },
]

const LINK_CLASS =
  'inline-flex min-h-11 items-center text-asphalt-muted underline-offset-4 transition-colors hover:text-white hover:underline md:min-h-8'

/**
 * Borde cosido: el mismo hilo rojo del mapa, en puntada doble. Donde el navegador
 * soporta animaciones ligadas al scroll, una tapa se retira y la costura aparece
 * al entrar el pie; si no, la costura se ve completa.
 */
function StitchedEdge() {
  return (
    <div className="relative overflow-hidden px-4 pt-4" aria-hidden>
      <div className="stitch-line" />
      <div className="stitch-cover absolute inset-0 bg-asphalt" />
    </div>
  )
}

function ColumnTitle({ children }: { children: string }) {
  return <h2 className="mb-2 label-mono text-asphalt-muted md:mb-3">{children}</h2>
}

export function SiteFooter() {
  return (
    <footer className="surface-asphalt stitch-scope mt-20 sm:mt-24">
      <StitchedEdge />
      <Container className="grid gap-x-10 gap-y-10 py-12 sm:grid-cols-2 sm:py-14 lg:grid-cols-[1.6fr_1fr_1fr]">
        <div className="flex flex-col items-start gap-4 sm:col-span-2 lg:col-span-1">
          <Logo tone="dark" />
          <p className="max-w-sm text-sm text-asphalt-muted">
            Repuestos de los almacenes de tu ciudad en un solo lugar, para conductores y talleres. Compara precio,
            existencias y distancia, y revisa que la pieza le sirva al vehículo antes de salir.
          </p>
        </div>
        <nav aria-labelledby="pie-explorar">
          <h2 id="pie-explorar" className="mb-2 label-mono text-asphalt-muted md:mb-3">
            Explorar
          </h2>
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
                <Link to="/vender/registro" className={LINK_CLASS}>
                  Registrar mi almacén
                </Link>
              </li>
            </ul>
          </div>
          <div>
            <ColumnTitle>Comprar en Autopartco</ColumnTitle>
            <p className="max-w-xs text-sm text-asphalt-muted">
              La compra en línea llegará pronto. Mientras tanto, consulta precios y existencias, y contacta a la tienda.
            </p>
          </div>
        </div>
      </Container>
      <div className="border-t border-asphalt-line">
        <Container className="py-5 text-xs text-asphalt-muted">
          © {CURRENT_YEAR} Autopartco. Precios y existencias informados por cada tienda.
        </Container>
      </div>
    </footer>
  )
}
