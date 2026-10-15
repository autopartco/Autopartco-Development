import { useEffect, useState } from 'react'

/** Estado de apertura de la paleta de comandos y su atajo Cmd/Ctrl+K. */
export function useCommandPalette() {
  const [open, setOpen] = useState(false)
  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        setOpen((current) => !current)
      }
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [])
  return { open, setOpen }
}
