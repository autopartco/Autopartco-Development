import { HomeHero } from '../home/home-hero'
import { AboutSection, FeaturedSection, QuickAccessSection, SellersSection } from '../home/home-sections'
import { HowItWorksSection } from '../home/how-it-works'
import { useHomeData } from '../home/use-home-query'

/**
 * Inicio v2: hero, acceso directo, cómo funciona, destacados, qué es Autopartco y vendedores.
 * Una sola llamada (`/marketplace/home`) alimenta las secciones. La comparación de precios se retiró:
 * repetía la búsqueda y solo aportaba cuando dos almacenes publican la misma referencia.
 */
export default function HomePage() {
  const home = useHomeData()
  return (
    <>
      <title>Autopartco | Repuestos en los almacenes más cerca de ti</title>
      <HomeHero home={home} />
      <QuickAccessSection home={home} />
      <HowItWorksSection />
      <FeaturedSection home={home} />
      <AboutSection home={home} />
      <SellersSection home={home} />
    </>
  )
}
