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
      <div className="mx-auto w-full max-w-3xl">
        <PageIntro
          crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Crear cuenta' }]}
          label="Cuenta Autopartco"
          title="Crear cuenta"
          description={<p>¿Cómo vas a usar Autopartco?</p>}
          className="mb-6 sm:mb-8"
        />
        <ul className="grid gap-3 sm:grid-cols-2">
          {CHOICES.map((choice) => (
            <li key={choice.to}>
              <Link
                to={choice.to}
                className="group flex h-full min-h-28 items-start gap-4 rounded-2xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.08)] transition-shadow hover:shadow-[0_0_0_2px_var(--color-brand)] sm:p-6"
              >
                <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-concrete text-brand transition-colors group-hover:bg-brand group-hover:text-primary-foreground">
                  <choice.icon className="size-5" aria-hidden />
                </span>
                <span className="flex min-w-0 flex-1 flex-col gap-1">
                  <span className="text-lg leading-tight font-bold tracking-tight">{choice.title}</span>
                  <span className="text-sm text-muted-foreground">{choice.body}</span>
                </span>
                <ChevronRightIcon className="mt-3 size-5 shrink-0 text-muted-foreground" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
        <p className="mt-6 text-sm text-muted-foreground">
          ¿Ya tienes cuenta?{' '}
          <Link to="/ingresar" className="font-medium text-brand underline-offset-4 hover:underline">
            Ingresar
          </Link>
        </p>
      </div>
    </Container>
  )
}
