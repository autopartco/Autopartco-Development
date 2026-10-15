import { useEffect, useState } from 'react'
import { getAuthService } from '@/features/auth/api/auth-service'

/**
 * `true` cuando el servicio de cuentas ya intentó recuperar la sesión. Sin esto, al recargar
 * en modo `mock` la primera pintura vería "sin sesión" y redirigiría a /ingresar de más.
 */
export function useSessionReady(): boolean {
  const [ready, setReady] = useState(false)
  useEffect(() => {
    let active = true
    void getAuthService().finally(() => {
      if (active) setReady(true)
    })
    return () => {
      active = false
    }
  }, [])
  return ready
}
