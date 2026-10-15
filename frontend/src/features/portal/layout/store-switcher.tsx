import { useLocation, useNavigate } from 'react-router'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { initials } from '@/lib/format'
import { usePortal } from '../context'
import { sectionOf } from '../lib/sections'

/** Tienda actual: nombre fijo con una sola tienda; selector si la persona tiene varias activas. */
export function StoreSwitcher() {
  const { storeId, stores, store } = usePortal()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const openable = stores.filter((candidate) => candidate.applicationStatus === 'ACTIVE')
  const name = store?.businessName ?? 'Tu almacén'

  const avatar = (
    <Avatar className="size-8 rounded-md text-xs">
      {store?.logoUrl ? <AvatarImage src={store.logoUrl} alt="" /> : null}
      <AvatarFallback aria-hidden>{initials(name) || name.slice(0, 2).toUpperCase()}</AvatarFallback>
    </Avatar>
  )

  if (openable.length < 2) {
    return (
      <div className="flex min-w-0 items-center gap-2.5">
        {avatar}
        <p className="truncate text-sm font-semibold">{name}</p>
      </div>
    )
  }

  const items = openable.map((candidate) => ({ value: candidate.storeId, label: candidate.businessName }))
  return (
    <div className="flex min-w-0 items-center gap-2.5">
      {avatar}
      <Label htmlFor="selector-tienda" className="sr-only">
        Almacén
      </Label>
      <Select
        items={items}
        value={storeId}
        onValueChange={(next) => {
          if (typeof next === 'string' && next !== storeId) navigate(`/portal/${encodeURIComponent(next)}/${sectionOf(pathname)}`)
        }}
      >
        <SelectTrigger id="selector-tienda" className="h-11 w-full min-w-0 max-w-[16rem] bg-card font-semibold sm:h-10">
          <SelectValue />
        </SelectTrigger>
        <SelectContent alignItemWithTrigger={false} align="start" className="min-w-64">
          {items.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
