import { Suspense } from 'react'
import { createBrowserRouter, Navigate } from 'react-router'
import NotFoundPage from '@/features/marketplace/pages/not-found-page'
import {
  CartPage,
  CategoriesPage,
  CategoryPage,
  CheckoutPage,
  CustomerRegisterPage,
  HomePage,
  LoginPage,
  OrderResultPage,
  PasswordRecoveryPage,
  PortalBranchesPage,
  PortalIndexPage,
  PortalLayout,
  PortalOrderDetailPage,
  PortalOrdersPage,
  PortalOverviewPage,
  PortalPaymentResultPage,
  PortalPlanPage,
  PortalProductFormPage,
  PortalProductsPage,
  PortalRoot,
  PortalStorePage,
  PortalTeamPage,
  ProductPage,
  RegisterPage,
  SearchPage,
  SellerRegisterPage,
  SellerSetupPage,
  SellPage,
  StoresPage,
  VerifyEmailPage,
} from './lazy-pages'
import { GuestOnly } from '@/features/auth/components/guest-only'
import { RequirePermission } from '@/features/portal/components/require-permission'
import { LegacyRedirect } from './legacy-redirect'
import { PageFallback } from './page-fallback'
import { RootLayout } from './root-layout'
import { RouteError } from './route-error'

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    children: [
      {
        // Ruta sin path: los errores se muestran dentro del layout (con cabecera y pie).
        errorElement: <RouteError />,
        children: [
          { index: true, element: <HomePage /> },
          { path: 'buscar', element: <SearchPage /> },
          { path: 'categorias', element: <CategoriesPage /> },
          { path: 'categoria/:slug', element: <CategoryPage /> },
          { path: 'producto/:id', element: <ProductPage /> },
          { path: 'tiendas', element: <StoresPage /> },
          // Carrito (localStorage) y pago simulado (POST /marketplace/orders).
          { path: 'carrito', element: <CartPage /> },
          { path: 'checkout', element: <CheckoutPage /> },
          { path: 'pedido/:id/resultado', element: <OrderResultPage /> },
          // Almacenes: landing y solicitud (POST /store-applications).
          { path: 'vender', element: <SellPage /> },
          { path: 'vender/registro', element: <SellerRegisterPage /> },
          // Enlace del correo de aprobación: `/seller/setup?token=` (ver ReviewStoreApplication).
          { path: 'seller/setup', element: <SellerSetupPage /> },
          // Cuentas (core/identity/web/AuthController).
          { path: 'ingresar', element: <LoginPage /> },
          // Solo invitados: con sesión activa se redirige al inicio.
          { path: 'registro', element: <GuestOnly><RegisterPage /></GuestOnly> },
          { path: 'registro/cliente', element: <GuestOnly><CustomerRegisterPage /></GuestOnly> },
          { path: 'recuperar-contrasena', element: <GuestOnly><PasswordRecoveryPage /></GuestOnly> },
          // El correo de verificación del backend enlaza a `{APP_FRONTEND_BASE_URL}/verify-email?token=`.
          { path: 'verify-email', element: <VerifyEmailPage /> },
          // Alias antiguo: conserva el `?token=` del enlace.
          { path: 'verificar-correo', element: <LegacyRedirect to="/verify-email" /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
  {
    // Portal del almacén: su propia carcasa (sin cabecera ni pie del marketplace) y sesión obligatoria.
    // El cliente solo oculta navegación; cada petición la autoriza el servidor.
    path: 'portal',
    element: (
      <Suspense fallback={<PageFallback />}>
        <PortalRoot />
      </Suspense>
    ),
    errorElement: <RouteError />,
    children: [
      { index: true, element: <PortalIndexPage /> },
      // Retorno de Wompi tras pagar la suscripción (`?id=&env=`): consulta el estado al servidor.
      { path: 'pago/resultado', element: <PortalPaymentResultPage /> },
      {
        path: ':storeId',
        element: <PortalLayout />,
        children: [
          // La sección por defecto no es fija: si no hay acceso a Inicio, RequirePermission redirige.
          { index: true, element: <Navigate to="inicio" replace /> },
          { path: 'inicio', element: <RequirePermission permission="STORE_READ"><PortalOverviewPage /></RequirePermission> },
          { path: 'productos', element: <RequirePermission permission="PRODUCT_READ"><PortalProductsPage /></RequirePermission> },
          { path: 'productos/nuevo', element: <RequirePermission permission="PRODUCT_CREATE"><PortalProductFormPage /></RequirePermission> },
          { path: 'productos/:productId', element: <RequirePermission permission="PRODUCT_READ"><PortalProductFormPage /></RequirePermission> },
          { path: 'pedidos', element: <RequirePermission permission="ORDER_READ"><PortalOrdersPage /></RequirePermission> },
          { path: 'pedidos/:saleId', element: <RequirePermission permission="ORDER_READ"><PortalOrderDetailPage /></RequirePermission> },
          { path: 'sucursales', element: <RequirePermission permission="BRANCH_READ"><PortalBranchesPage /></RequirePermission> },
          { path: 'equipo', element: <RequirePermission permission="COLLABORATOR_READ"><PortalTeamPage /></RequirePermission> },
          { path: 'tienda', element: <RequirePermission permission="STORE_READ"><PortalStorePage /></RequirePermission> },
          { path: 'plan', element: <RequirePermission permission="STORE_READ"><PortalPlanPage /></RequirePermission> },
          { path: '*', element: <Navigate to="inicio" replace /> },
        ],
      },
    ],
  },
])
