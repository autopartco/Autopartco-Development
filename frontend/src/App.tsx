import { LazyMotion, MotionConfig, domAnimation } from 'motion/react'
import { RouterProvider } from 'react-router'
import { TooltipProvider } from '@/components/ui/tooltip'
import { router } from '@/app/router'

export default function App() {
  return (
    <LazyMotion features={domAnimation} strict>
      {/* Respeta "reducir movimiento" del sistema: conserva opacidad, elimina desplazamientos. */}
      <MotionConfig reducedMotion="user">
        <TooltipProvider delay={300}>
          <RouterProvider router={router} />
        </TooltipProvider>
      </MotionConfig>
    </LazyMotion>
  )
}
