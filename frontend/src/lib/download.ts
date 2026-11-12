/** Guarda un `Blob` con el nombre dado (enlace temporal; se libera enseguida). */
export function saveBlob(blob: Blob, fileName: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.append(link)
  link.click()
  link.remove()
  setTimeout(() => URL.revokeObjectURL(url), 30_000)
}

/**
 * Abre un documento HTML (p. ej. un comprobante) en otra pestaña. La pestaña se abre antes de la petición para que el
 * navegador no la bloquee; si la petición falla, se cierra.
 */
export async function openHtmlInNewTab(load: () => Promise<Blob>): Promise<void> {
  const tab = window.open('', '_blank')
  try {
    const blob = await load()
    const url = URL.createObjectURL(new Blob([await blob.text()], { type: 'text/html' }))
    if (tab) tab.location.href = url
    else window.location.assign(url)
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (error) {
    tab?.close()
    throw error
  }
}
