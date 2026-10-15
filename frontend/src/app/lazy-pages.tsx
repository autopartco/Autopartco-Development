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

// Carrito, pago y confirmación del pedido.
export const CartPage = lazy(() => import('@/features/cart/pages/cart-page'))
export const CheckoutPage = lazy(() => import('@/features/checkout/pages/checkout-page'))
export const OrderResultPage = lazy(() => import('@/features/checkout/pages/order-result-page'))

// Portal del almacén (SaaS vendedores): cada pantalla se descarga al visitarla.
export const PortalRoot = lazy(() => import('@/features/portal/layout/portal-root').then((module) => ({ default: module.PortalRoot })))
export const PortalLayout = lazy(() =>
  import('@/features/portal/components/portal-layout').then((module) => ({ default: module.PortalLayout })),
)
export const PortalIndexPage = lazy(() => import('@/features/portal/pages/portal-index-page'))
export const PortalOverviewPage = lazy(() => import('@/features/portal/pages/portal-overview-page'))
export const PortalPlanPage = lazy(() => import('@/features/portal/pages/portal-plan-page'))
export const PortalProductsPage = lazy(() => import('@/features/portal/pages/products-page'))
export const PortalProductFormPage = lazy(() => import('@/features/portal/pages/product-form-page'))
export const PortalOrdersPage = lazy(() => import('@/features/portal/pages/orders-page'))
export const PortalOrderDetailPage = lazy(() => import('@/features/portal/pages/order-detail-page'))
export const PortalBranchesPage = lazy(() => import('@/features/portal/pages/branches-page'))
export const PortalTeamPage = lazy(() => import('@/features/portal/pages/team-page'))
export const PortalStorePage = lazy(() => import('@/features/portal/pages/store-profile-page'))
