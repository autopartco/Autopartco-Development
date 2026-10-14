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
