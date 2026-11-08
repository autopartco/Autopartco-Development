import { Link } from 'react-router'
import { ChevronRightIcon, StoreIcon, UserRoundIcon, type LucideIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'

interface Choice {
  to: string
  icon: LucideIcon
  title: string
  body: string
}

const CHOICES: Choice[] = [
  {
    to: '/registro/cliente',
    icon: UserRoundIcon,
    title: 'Soy cliente',
    body: 'Conductor o taller que busca repuestos y compara almacenes.',
  },
  {
    to: '/vender/registro',
    icon: StoreIcon,
    title: 'Tengo un almacén',
    body: 'Solicita publicar tu inventario. Revisamos tus documentos antes de activarte.',
  },
]

/** `/registro`: elige el tipo de cuenta. Cada opción es un enlace (navegable con teclado). */
export default function RegisterPage() {
  return (
    <Container className="py-6 sm:py-8">
      <title>Crear cuenta | Autopartco</title>
      <div className="mx-auto w-full max-w-2xl">
        <PageIntro
          crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Crear cuenta' }]}
          label="Cuenta Autopartco"
          title="Crear cuenta"
          description={<p>¿Cómo vas a usar Autopartco?</p>}
          className="mb-6 sm:mb-8"
        />
        <ul className="divide-y divide-ap-line border-y border-ap-line">
          {CHOICES.map((choice) => (
            <li key={choice.to}>
              <Link
                to={choice.to}
                className="group flex min-h-24 items-start gap-4 px-2 py-5 transition-colors hover:bg-ap-paper sm:px-4"
              >
                <choice.icon className="mt-0.5 size-5 shrink-0 text-ap-muted group-hover:text-ap-red" aria-hidden />
                <span className="flex min-w-0 flex-1 flex-col gap-1">
                  <span className="text-lg leading-tight font-semibold text-ap-ink group-hover:text-ap-red">{choice.title}</span>
                  <span className="text-sm text-ap-muted">{choice.body}</span>
                </span>
                <ChevronRightIcon className="mt-0.5 size-5 shrink-0 text-ap-muted" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
        <p className="mt-6 text-sm text-ap-muted">
          ¿Ya tienes cuenta?{' '}
          <Link to="/ingresar" className="inline-flex min-h-11 items-center font-medium text-ap-red underline-offset-4 hover:text-ap-red-hover hover:underline">
            Ingresar
          </Link>
        </p>
      </div>
    </Container>
  )
}
