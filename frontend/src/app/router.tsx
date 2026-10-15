import { Suspense } from 'react'
import { createBrowserRouter, Navigate } from 'react-router'
import NotFoundPage from '@/features/marketplace/pages/not-found-page'
import {
  AdminApplicationsPage,
  AdminRoot,
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
  SellPage,
  StoresPage,
  VerifyEmailPage,
} from './lazy-pages'
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
          // Cuentas (core/identity/web/AuthController).
          { path: 'ingresar', element: <LoginPage /> },
          { path: 'registro', element: <RegisterPage /> },
          { path: 'registro/cliente', element: <CustomerRegisterPage /> },
          { path: 'recuperar-contrasena', element: <PasswordRecoveryPage /> },
          { path: 'verificar-correo', element: <VerifyEmailPage /> },
          // El correo de verificación del backend enlaza a `{APP_FRONTEND_BASE_URL}/verify-email?token=`.
          { path: 'verify-email', element: <VerifyEmailPage /> },
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
          { index: true, element: <Navigate to="inicio" replace /> },
          { path: 'inicio', element: <PortalOverviewPage /> },
          { path: 'productos', element: <PortalProductsPage /> },
          { path: 'productos/nuevo', element: <PortalProductFormPage /> },
          { path: 'productos/:productId', element: <PortalProductFormPage /> },
          { path: 'pedidos', element: <PortalOrdersPage /> },
          { path: 'pedidos/:saleId', element: <PortalOrderDetailPage /> },
          { path: 'sucursales', element: <PortalBranchesPage /> },
          { path: 'equipo', element: <PortalTeamPage /> },
          { path: 'tienda', element: <PortalStorePage /> },
          { path: 'plan', element: <PortalPlanPage /> },
          { path: '*', element: <Navigate to="inicio" replace /> },
        ],
      },
    ],
  },
])
  {
    // Administración: solo ROLE_ROOT (la interfaz oculta; el servidor responde 403 a cualquier otro rol).
    path: 'admin',
    element: (
      <Suspense fallback={<PageFallback />}>
        <AdminRoot />
      </Suspense>
    ),
    errorElement: <RouteError />,
    children: [
      { index: true, element: <Navigate to="solicitudes" replace /> },
      { path: 'solicitudes', element: <AdminApplicationsPage /> },
      { path: '*', element: <Navigate to="solicitudes" replace /> },
    ],
  },
