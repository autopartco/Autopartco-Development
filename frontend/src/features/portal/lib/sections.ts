/** Secciones del portal (segmento de ruta tras `/portal/:storeId/`). */
export const PORTAL_SECTIONS = ['inicio', 'productos', 'pedidos', 'sucursales', 'equipo', 'tienda', 'plan'] as const

/** Sección actual a partir de la URL, para conservarla al cambiar de tienda. */
export function sectionOf(pathname: string): string {
  const section = pathname.split('/').filter(Boolean)[2]
  return PORTAL_SECTIONS.find((candidate) => candidate === section) ?? 'inicio'
}
