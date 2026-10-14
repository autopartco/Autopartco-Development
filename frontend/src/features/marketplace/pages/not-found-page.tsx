import { Link } from 'react-router'
import { Container } from '@/components/common/page-header'
import { Button } from '@/components/ui/button'
import { SearchBar } from '../components/search-bar'

interface NotFoundContentProps {
  title?: string
  description?: string
}

/** Contenido 404 reutilizable (ruta inexistente, producto o categoría que no existe). */
export function NotFoundContent({
  title = 'No encontramos esta página',
  description = 'El enlace puede estar mal escrito o la página ya no existe. Busca el repuesto que necesitas o vuelve al inicio.',
}: NotFoundContentProps) {
  return (
    <Container className="flex flex-col items-start gap-5 py-14 sm:gap-6 sm:py-24">
      <title>{`${title} | Autopartco`}</title>
      <p className="label-mono text-brand tabular-nums">Error 404</p>
      <h1 className="max-w-3xl text-page-title">{title}</h1>
      <p className="max-w-prose text-muted-foreground sm:text-lg">{description}</p>
      <SearchBar className="max-w-xl" />
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
          Ir al inicio
        </Button>
        <Button variant="ghost" className="h-11 px-5" nativeButton={false} render={<Link to="/categorias" />}>
          Ver categorías
        </Button>
      </div>
    </Container>
  )
}

export default function NotFoundPage() {
  return <NotFoundContent />
}
