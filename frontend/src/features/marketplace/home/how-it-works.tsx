import { Container } from '@/components/common/page-header'
import { SellerCtaPanel } from '@/features/seller/components/seller-cta-panel'

const STEPS = [
  {
    title: 'Busca',
    body: 'Escribe el repuesto o la referencia. Si quieres, agrega la marca y el modelo de tu carro.',
  },
  {
    title: 'Compara',
    body: 'Mira el precio, las existencias y la distancia de cada almacén que lo publica.',
  },
  {
    title: 'Confirma compatibilidad',
    body: 'Revisa los vehículos para los que aplica la pieza y confirma la referencia con la tienda.',
  },
  {
    title: 'Compra o recoge',
    body: 'Agrega el repuesto al carrito y págalo en línea, o consulta con la tienda antes de ir al almacén.',
  },
] as const

/**
 * Cómo funciona: cuatro pasos numerados en una fila editorial (columna en móvil) y, al
 * lado, la invitación para almacenes. Sin iconos ni animación: el número ordena.
 */
export function HowItWorksSection() {
  return (
    <section aria-labelledby="como-funciona-titulo" className="border-t border-ap-line bg-ap-paper py-14 sm:py-20">
      <Container className="grid gap-12 xl:grid-cols-[minmax(0,8fr)_minmax(0,4fr)] xl:gap-14">
        <div className="flex flex-col gap-8">
          <div className="flex flex-col gap-3">
            <p className="ap-index">Cómo funciona / 4 pasos</p>
            <h2 id="como-funciona-titulo" className="text-section text-ap-ink">
              Del buscador al mostrador
            </h2>
            <p className="max-w-xl text-ap-muted">Sirve igual si arreglas tu carro o si atiendes un taller.</p>
          </div>
          <ol className="grid gap-x-6 sm:grid-cols-2 lg:grid-cols-4">
            {STEPS.map((step, index) => (
              <li key={step.title} className="flex flex-col gap-2 border-t-2 border-ap-graphite py-4">
                <span className="ap-ref text-ap-muted" aria-hidden>
                  {String(index + 1).padStart(2, '0')}
                </span>
                <h3 className="text-base font-semibold text-ap-ink">
                  <span className="sr-only">Paso {index + 1}: </span>
                  {step.title}
                </h3>
                <p className="text-sm text-ap-muted">{step.body}</p>
              </li>
            ))}
          </ol>
        </div>
        <SellerCtaPanel className="self-start" />
      </Container>
    </section>
  )
}
