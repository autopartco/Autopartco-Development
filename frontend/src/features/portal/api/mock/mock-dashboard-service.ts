import type { DashboardService } from '../dashboard-service'
import type { Dashboard } from '../types'
import { getStore, requirePermission, simulateNetwork, statusOf, subscriptionOf, usageOf } from './mock-state'

/** Resumen simulado calculado desde los mismos datos que las demás pantallas. */
export function createMockDashboardService(): DashboardService {
  return {
    async dashboard(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'STORE_READ')
      const usage = usageOf(store)
      const subscription = subscriptionOf(store)
      const month = new Date().getMonth()
      const inThisMonth = store.orders.filter((sale) => new Date(sale.createdAt).getMonth() === month && sale.status !== 'CANCELLED')
      const dashboard: Dashboard = {
        storeId,
        storeName: store.summary.businessName,
        storeStatus: store.profile.state,
        plan: store.plan,
        selectedPlan: store.selectedPlan,
        setupRequired: store.summary.applicationStatus === 'CONFIGURATION_PENDING',
        subscriptionExpired: statusOf(store) === 'EXPIRED',
        trialEndsAt: store.trialEndsAt,
        paidThrough: store.paidThrough,
        maxBranches: store.plan.maxBranches,
        maxProducts: store.plan.maxProducts,
        maxUsers: store.plan.maxUsers,
        capabilities: store.plan.capabilities,
        usage: { branches: usage.branches.used, products: usage.products.used, users: usage.users.used },
        sales: {
          ordersThisMonth: inThisMonth.length,
          revenueThisMonth: inThisMonth.reduce((sum, sale) => sum + sale.total, 0),
          pendingOrders: store.orders.filter((sale) => sale.status === 'PENDING').length,
          preparingOrders: store.orders.filter((sale) => sale.status === 'PREPARING').length,
        },
        subscriptionStatus: subscription.status,
        daysRemaining: subscription.daysRemaining,
        inventory: {
          listings: store.products.length,
          outOfStock: store.products.filter((row) => row.quantity === 0).length,
          lowStock: store.products.filter((row) => row.quantity > 0 && row.quantity <= 3).length,
        },
      }
      return dashboard
    },
  }
}
