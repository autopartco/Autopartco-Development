import { createBrowserRouter, Navigate } from 'react-router'
import NotFoundPage from '@/features/marketplace/pages/not-found-page'
import { PortalLayout } from '@/features/portal/components/portal-layout'
import {
  CategoriesPage,
  CategoryPage,
  CustomerRegisterPage,
  HomePage,
  LoginPage,
  PasswordRecoveryPage,
  PortalIndexPage,
  PortalOverviewPage,
  PortalPlanPage,
  ProductPage,
  RegisterPage,
  SearchPage,
  SellerRegisterPage,
  SellPage,
  StoresPage,
  VerifyEmailPage,
} from './lazy-pages'
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
          // Portal del almacén: solo se muestran los datos que el servidor autoriza.
          { path: 'portal', element: <PortalIndexPage /> },
          {
            path: 'portal/:storeId',
            element: <PortalLayout />,
            children: [
              { index: true, element: <Navigate to="inicio" replace /> },
              { path: 'inicio', element: <PortalOverviewPage /> },
              { path: 'plan', element: <PortalPlanPage /> },
            ],
          },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
])
