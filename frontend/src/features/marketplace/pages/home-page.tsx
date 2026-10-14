import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { ZoneHero } from '../home/zone-hero'

// Lo que está bajo el pliegue se descarga cuando el usuario se acerca (o al quedar el navegador libre).
const HomeSections = lazy(() => import('../home/home-sections'))

const PLACEHOLDER = <div className="min-h-[120rem]" aria-hidden />

function useNearViewport<T extends Element>() {
  const ref = useRef<T>(null)
  const [near, setNear] = useState(false)
  useEffect(() => {
    const node = ref.current
    if (!node || near) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) setNear(true)
      },
      { rootMargin: '800px 0px' },
    )
    observer.observe(node)
    const idle = 'requestIdleCallback' in window ? window.requestIdleCallback(() => setNear(true), { timeout: 4000 }) : undefined
    return () => {
      observer.disconnect()
      if (idle !== undefined) window.cancelIdleCallback(idle)
    }
  }, [near])
  return [ref, near] as const
}

export default function HomePage() {
  const [sentinelRef, near] = useNearViewport<HTMLDivElement>()
  return (
    <>
      <title>Autopartco | Repuestos en los almacenes más cerca de ti</title>
      <ZoneHero />
      <div ref={sentinelRef}>
        {near ? (
          <Suspense fallback={PLACEHOLDER}>
            <HomeSections />
          </Suspense>
        ) : (
          PLACEHOLDER
        )}
      </div>
    </>
  )
}
