import { lazy } from 'react'

// Cada pantalla se descarga solo cuando se visita.
export const HomePage = lazy(() => import('@/features/marketplace/pages/home-page'))
export const SearchPage = lazy(() => import('@/features/marketplace/pages/search-page'))
export const CategoriesPage = lazy(() => import('@/features/marketplace/pages/categories-page'))
export const CategoryPage = lazy(() => import('@/features/marketplace/pages/category-page'))
export const ProductPage = lazy(() => import('@/features/marketplace/pages/product-page'))
export const StoresPage = lazy(() => import('@/features/marketplace/pages/stores-page'))
export const SellPage = lazy(() => import('@/features/marketplace/pages/sell-page'))

// Cuentas y solicitud de almacén.
export const LoginPage = lazy(() => import('@/features/auth/pages/login-page'))
export const RegisterPage = lazy(() => import('@/features/auth/pages/register-page'))
export const CustomerRegisterPage = lazy(() => import('@/features/auth/pages/customer-register-page'))
export const PasswordRecoveryPage = lazy(() => import('@/features/auth/pages/password-recovery-page'))
export const VerifyEmailPage = lazy(() => import('@/features/auth/pages/verify-email-page'))
export const SellerRegisterPage = lazy(() => import('@/features/seller/pages/seller-register-page'))
export const SellerSetupPage = lazy(() => import('@/features/seller/pages/seller-setup-page'))

// Carrito, pago y confirmación del pedido.
export const CartPage = lazy(() => import('@/features/cart/pages/cart-page'))
export const CheckoutPage = lazy(() => import('@/features/checkout/pages/checkout-page'))
export const OrderResultPage = lazy(() => import('@/features/checkout/pages/order-result-page'))

// Beneficios del comprador (promociones para su segmento).
export const BenefitsPage = lazy(() => import('@/features/benefits/pages/benefits-page'))

// Mis pedidos y seguimiento del comprador.
export const OrdersPage = lazy(() => import('@/features/orders/pages/orders-page'))
export const OrderDetailPage = lazy(() => import('@/features/orders/pages/order-detail-page'))

// Portal del almacén (SaaS vendedores): cada pantalla se descarga al visitarla.
export const PortalRoot = lazy(() => import('@/features/portal/layout/portal-root').then((module) => ({ default: module.PortalRoot })))
export const PortalLayout = lazy(() =>
  import('@/features/portal/components/portal-layout').then((module) => ({ default: module.PortalLayout })),
)
export const PortalIndexPage = lazy(() => import('@/features/portal/pages/portal-index-page'))
export const PortalOverviewPage = lazy(() => import('@/features/portal/pages/portal-overview-page'))
export const PortalPlanPage = lazy(() => import('@/features/portal/pages/portal-plan-page'))
export const PortalPaymentResultPage = lazy(() => import('@/features/portal/pages/payment-result-page'))
export const PortalProductsPage = lazy(() => import('@/features/portal/pages/products-page'))
export const PortalProductFormPage = lazy(() => import('@/features/portal/pages/product-form-page'))
export const PortalOrdersPage = lazy(() => import('@/features/portal/pages/orders-page'))
export const PortalOrderDetailPage = lazy(() => import('@/features/portal/pages/order-detail-page'))
export const PortalSalesPage = lazy(() => import('@/features/portal/pages/sales-page'))
export const PortalInventoryPage = lazy(() => import('@/features/portal/pages/inventory-page'))
export const PortalInventoryMovementsPage = lazy(() => import('@/features/portal/pages/inventory-movements-page'))
export const PortalInventoryTransfersPage = lazy(() => import('@/features/portal/pages/inventory-transfers-page'))
export const PortalIntelligenceReportsPage = lazy(() => import('@/features/portal/pages/intelligence-reports-page'))
export const PortalIntelligenceCustomersPage = lazy(() => import('@/features/portal/pages/intelligence-customers-page'))
export const PortalIntelligenceProductsPage = lazy(() => import('@/features/portal/pages/intelligence-products-page'))
export const PortalIntelligenceForecastPage = lazy(() => import('@/features/portal/pages/intelligence-forecast-page'))
export const PortalIntelligenceAssociationsPage = lazy(() => import('@/features/portal/pages/intelligence-associations-page'))
export const PortalPromotionsPage = lazy(() => import('@/features/portal/pages/promotions-page'))
export const PortalAlertsPage = lazy(() => import('@/features/portal/pages/alerts-page'))
export const PortalBranchesPage = lazy(() => import('@/features/portal/pages/branches-page'))
export const PortalTeamPage = lazy(() => import('@/features/portal/pages/team-page'))
export const PortalStorePage = lazy(() => import('@/features/portal/pages/store-profile-page'))

// Administración (ROOT): revisión de solicitudes de almacenes.
export const AdminRoot = lazy(() => import('@/features/admin/layout/admin-root').then((module) => ({ default: module.AdminRoot })))
export const AdminApplicationsPage = lazy(() => import('@/features/admin/pages/applications-page'))
export const AdminCustomersPage = lazy(() => import('@/features/admin/pages/customers-page'))
export const AdminPromotionsPage = lazy(() => import('@/features/admin/pages/promotions-page'))
export const AdminSearchesPage = lazy(() => import('@/features/admin/pages/searches-page'))
export const PortalApplicationPage = lazy(() => import('@/features/portal/pages/application-page'))
