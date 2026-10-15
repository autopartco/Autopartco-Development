import { ApiError } from '@/lib/api'
import type { SubscriptionService } from '../subscription-service'
import type { SimulatedPaymentResult, SubscriptionPayment } from '../types'
import { getStore, MOCK_PLANS, requireOwner, requirePermission, simulateNetwork, statusOf, subscriptionOf } from './mock-state'

function addCalendarMonths(from: Date, months: number): Date {
  const date = new Date(from)
  date.setMonth(date.getMonth() + months)
  return date
}

function toLocalIso(date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`
}

/** Resultados por `clientRequestId` para la idempotencia del cobro simulado. */
const processed = new Map<string, SimulatedPaymentResult>()

/** Suscripción y cobro simulados con las mismas reglas del plan §4.3 (402, 409, idempotencia). */
export function createMockSubscriptionService(): SubscriptionService {
  return {
    async subscription(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'STORE_READ')
      return subscriptionOf(store)
    },

    async plans() {
      await simulateNetwork(300)
      return MOCK_PLANS.filter((plan) => plan.enabled)
    },

    async payments(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'STORE_READ')
      return [...store.payments].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    },

    async paySimulated(storeId, input) {
      await simulateNetwork(900)
      const store = getStore(storeId)
      requireOwner(store)
      const repeated = processed.get(input.clientRequestId)
      if (repeated) return repeated

      const plan = MOCK_PLANS.find((candidate) => candidate.id === input.planId)
      if (!plan) throw new ApiError('No encontramos ese plan.', { status: 404, code: 'SELLER_PLAN_NOT_FOUND' })
      if (plan.monthlyPriceCop <= 0) {
        throw new ApiError('Ese plan no se paga: es la prueba gratuita.', { status: 400, code: 'SELLER_PLAN_NOT_PAYABLE' })
      }
      if (!Number.isInteger(input.months) || input.months < 1 || input.months > 12) {
        throw new ApiError('Elige entre 1 y 12 meses.', { status: 400, code: 'INVALID_PREPAID_MONTHS' })
      }
      if (statusOf(store) === 'ACTIVE' && store.plan.code !== plan.code) {
        throw new ApiError('Ya tienes un periodo pagado con otro plan. Podrás cambiar de plan cuando termine.', {
          status: 409,
          code: 'SUBSCRIPTION_PLAN_CHANGE_LOCKED',
          details: { currentPlanCode: store.plan.code, paidThrough: store.paidThrough },
        })
      }

      const amountCop = plan.monthlyPriceCop * input.months
      if (input.simulatedOutcome === 'REJECTED') {
        throw new ApiError('El pago de prueba fue rechazado. No se hizo ningún cobro.', {
          status: 402,
          code: 'PAYMENT_REJECTED',
          details: { amountCop, currency: 'COP' },
        })
      }

      const now = new Date()
      const candidates = [now, store.trialEndsAt, store.paidThrough]
        .filter((value): value is Date | string => value !== null)
        .map((value) => new Date(value))
      const start = new Date(Math.max(...candidates.map((date) => date.getTime())))
      const end = addCalendarMonths(start, input.months)

      const payment: SubscriptionPayment = {
        id: crypto.randomUUID(),
        reference: `SIM-${input.clientRequestId}`,
        provider: 'SIMULATED',
        planCode: plan.code,
        planName: plan.name,
        months: input.months,
        amountCop,
        currency: 'COP',
        status: 'APPROVED',
        createdAt: toLocalIso(now),
        paidAt: toLocalIso(now),
        periodStartsAt: toLocalIso(start),
        periodEndsAt: toLocalIso(end),
      }
      store.payments.push(payment)
      store.plan = plan
      store.selectedPlan = plan
      store.paidThrough = toLocalIso(end)
      const result = { payment, subscription: subscriptionOf(store) }
      processed.set(input.clientRequestId, result)
      return result
    },

    async checkout(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requireOwner(store)
      throw new ApiError('El cobro con Wompi no está disponible en la simulación.', {
        status: 400,
        code: 'PAYMENT_METHOD_NOT_SUPPORTED',
      })
    },
  }
}
