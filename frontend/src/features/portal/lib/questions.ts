import { invalidateAsync } from '@/lib/use-async'
import { getQuestionPortalService } from '../api/question-service'
import { useStoreQuery } from '../hooks'

/** Preguntas sin responder del almacén activo (en el alcance de quien consulta). Comparte caché entre pantallas. */
export function usePortalQuestionPending() {
  return useStoreQuery('questions:summary', async (storeId) => (await getQuestionPortalService()).pendingCount(storeId))
}

/** Tras responder o borrar una respuesta: descarta lista y contador de preguntas de este almacén. */
export function invalidatePortalQuestions(storeId: string): void {
  invalidateAsync(`portal:${storeId}:questions`)
}
