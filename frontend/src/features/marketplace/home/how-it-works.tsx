import { Container } from '@/components/common/page-header'

const STEPS = [
  {
    title: 'Busca',
    body: 'Escribe el repuesto o el servicio que necesitas. Si quieres, dinos la marca y el modelo de tu carro.',
  },
  {
    title: 'Compara',
    body: 'Mira precios, qué hay disponible y qué tan cerca queda cada almacén o taller.',
  },
  {
    title: 'Elige con seguridad',
    body: 'Revisa que sirva para tu carro y escríbele por WhatsApp a la tienda si tienes dudas.',
  },
  {
    title: 'Compra fácil',
    body: 'Compra tu repuesto en línea o agenda tu cita en el taller.',
  },
] as const

/**
 * Cómo funciona: cuatro pasos numerados en una fila editorial (columna en móvil).
 * Sin iconos ni animación: el número ordena.
 */
export function HowItWorksSection() {
  return (
    <section aria-labelledby="como-funciona-titulo" className="border-t border-ap-line bg-ap-paper py-14 sm:py-20">
      <Container className="flex flex-col gap-8">
        <div className="flex flex-col gap-3">
          <p className="ap-index">Cómo funciona / 4 pasos</p>
          <h2 id="como-funciona-titulo" className="text-section text-ap-ink">
            Del buscador a tu taller
          </h2>
          <p className="max-w-xl text-ap-muted">Para quien arregla su carro y para quien atiende un taller.</p>
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
      </Container>
    </section>
  )
}
