import { useState } from 'react'
import { toast } from 'sonner'
import { invalidateAsync } from '@/lib/use-async'
import { getAlertService } from '../api/alert-service'
import type { AlertPatch, StoreAlert } from '../api/alert-types'
import { usePortal } from '../context'
import { actionErrorMessage } from './errors'

/** Marcar leída o resolver una alerta; descarta alertas y contador en memoria (los hooks montados recargan solos). */
export function useAlertActions() {
  const { storeId } = usePortal()
  const [busyId, setBusyId] = useState<string>()

  async function apply(alert: StoreAlert, patch: AlertPatch, success?: string) {
    setBusyId(alert.id)
    try {
      await (await getAlertService()).update(storeId, alert.id, patch)
      invalidateAsync(`portal:${storeId}:alerts`)
      if (success) toast.success(success)
    } catch (error) {
      toast.error(actionErrorMessage(error, 'No pudimos actualizar la alerta. Inténtalo de nuevo.'))
    } finally {
      setBusyId(undefined)
    }
  }

  return {
    busyId,
    markRead: (alert: StoreAlert) => apply(alert, { read: true }),
    resolve: (alert: StoreAlert) => apply(alert, { resolved: true }, 'Marcaste la alerta como resuelta.'),
  }
}

export type AlertActions = ReturnType<typeof useAlertActions>
