import { useParams } from 'react-router'
import { SearchResultsView } from './search-results-view'

/** `/categoria/:slug`: resultados con la categoría fijada por la ruta. */
export default function CategoryPage() {
  const { slug = '' } = useParams()
  // La clave reinicia el estado local (p. ej. el panel móvil) al cambiar de categoría.
  return <SearchResultsView key={slug} fixedCategory={slug} />
}
