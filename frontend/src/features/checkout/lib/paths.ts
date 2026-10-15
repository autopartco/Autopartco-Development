export function checkoutPath(): string {
  return '/checkout'
}

export function orderResultPath(orderId: string): string {
  return `/pedido/${encodeURIComponent(orderId)}/resultado`
}
