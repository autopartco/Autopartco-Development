import { Suspense } from 'react'
import { createBrowserRouter, Navigate, useParams } from 'react-router'
import NotFoundPage from '@/features/marketplace/pages/not-found-page'
import {
  AdminAdsPage,
  AdminApplicationsPage,
  AdminAuditPage,
  AdminCatalogPage,
  AdminCustomersPage,
  AdminOverviewPage,
  AdminPlansPage,
  AdminPromotionsPage,
  AdminSearchesPage,
  AdminStatsPage,
  AdminRoot,
  BenefitsPage,
  CartPage,
  CategoriesPage,
  CategoryPage,
  CheckoutPage,
  CustomerRegisterPage,
  HomePage,
  LoginPage,
  OrderDetailPage,
  OrderResultPage,
  OrdersPage,
  PasswordRecoveryPage,
  PortalAdsCampaignFormPage,
  PortalAdsCampaignsPage,
  PortalAdsCampaignStatsPage,
  PortalAdsPaymentResultPage,
  PortalApplicationPage,
  PortalBranchesPage,
  PortalInventoryMovementsPage,
  PortalInventoryPage,
  PortalAlertsPage,
  PortalIntelligenceAssociationsPage,
  PortalIntelligenceCustomersPage,
  PortalIntelligenceForecastPage,
  PortalIntelligenceVisibilityPage,
  PortalIntelligenceProductsPage,
  PortalIntelligenceReportsPage,
  PortalInventoryTransfersPage,
  PortalIndexPage,
  PortalLayout,
  PortalOrderDetailPage,
  PortalOrdersPage,
  PortalOverviewPage,
  PortalPaymentResultPage,
  PortalPlanPage,
  PortalProductFormPage,
  PortalProductsPage,
  PortalPromotionsPage,
  PortalSalesPage,
  PortalRoot,
  PortalStorePage,
  PortalTeamPage,
  PrivacyPage,
  ProductPage,
  RegisterPage,
  SearchPage,
  SellerRegisterPage,
  SellerSetupPage,
  SellPage,
  StorePage,
  StoresPage,
  VerifyEmailPage,
} from './lazy-pages'
import { RequireSession } from '@/features/auth/components/require-session'
import { GuestOnly } from '@/features/auth/components/guest-only'
import { RequirePermission } from '@/features/portal/components/require-permission'
import { LegacyRedirect } from './legacy-redirect'
import { PageFallback } from './page-fallback'
import { RootLayout } from './root-layout'
import { RouteError } from './route-error'

/** Ruta desconocida del portal: a Inicio con ruta absoluta (una relativa se anidaría en bucle). */
function PortalHomeRedirect() {
  const { storeId = '' } = useParams()
  return <Navigate to={`/portal/${encodeURIComponent(storeId)}/inicio`} replace />
}

export const router = createBrowserRouter([
  {
    element: <RootLayout />,
    // Red de seguridad: si falla la propia carcasa (cabecera o pie), se muestra la página
    // de error de Autopartco en lugar de la pantalla por defecto de React Router.
    errorElement: <RouteError />,
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
          // Perfil público del almacén (GET /marketplace/stores/{id}).
          { path: 'tiendas/:storeId', element: <StorePage /> },
          // Carrito (localStorage) y pago (POST /marketplace/orders; Wompi o SIMULATED en desarrollo).
          { path: 'carrito', element: <CartPage /> },
          { path: 'checkout', element: <CheckoutPage /> },
          // Vuelta de Wompi (`?id=&env=`): exige sesión y consulta el estado al servidor; sin sesión vuelve aquí con los parámetros.
          { path: 'pedido/:id/resultado', element: <RequireSession fallback={<PageFallback />}><OrderResultPage /></RequireSession> },
          // Mis pedidos (GET /me/orders): exigen sesión; el servidor solo devuelve los del titular.
          { path: 'mis-pedidos', element: <RequireSession fallback={<PageFallback />}><OrdersPage /></RequireSession> },
          // Beneficios del comprador (GET /marketplace/me/benefits): exigen sesión.
          { path: 'mis-beneficios', element: <RequireSession fallback={<PageFallback />}><BenefitsPage /></RequireSession> },
          { path: 'mis-pedidos/:id', element: <RequireSession fallback={<PageFallback />}><OrderDetailPage /></RequireSession> },
          // Almacenes: landing y solicitud (POST /store-applications).
          { path: 'privacidad', element: <PrivacyPage /> },
          { path: 'vender', element: <SellPage /> },
          { path: 'vender/registro', element: <SellerRegisterPage /> },
          // Enlace del correo de aprobación (sin token): la página exige sesión y redirige a /ingresar si no hay.
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
      // Retorno de Wompi tras pagar una campaña (`?id=`): sincroniza y consulta el estado al servidor.
      { path: 'publicidad/pago', element: <PortalAdsPaymentResultPage /> },
      // Estado de la solicitud de almacén (`GET /me/store-applications`).
      { path: 'solicitud', element: <PortalApplicationPage /> },
      {
        path: ':storeId',
        element: <PortalLayout />,
        children: [
          // La sección por defecto no es fija: si no hay acceso a Inicio, RequirePermission redirige.
          { index: true, element: <Navigate to="inicio" replace /> },
          { path: 'inicio', element: <RequirePermission permission="STORE_READ"><PortalOverviewPage /></RequirePermission> },
          { path: 'ventas', element: <RequirePermission permission="ORDER_READ"><PortalSalesPage /></RequirePermission> },
          { path: 'inventario', element: <RequirePermission permission="PRODUCT_READ"><PortalInventoryPage /></RequirePermission> },
          { path: 'inventario/movimientos', element: <RequirePermission permission="PRODUCT_READ"><PortalInventoryMovementsPage /></RequirePermission> },
          { path: 'inventario/transferencias', element: <RequirePermission permission="PRODUCT_READ"><PortalInventoryTransfersPage /></RequirePermission> },
          { path: 'inteligencia', element: <RequirePermission permission="ANALYTICS_READ"><PortalIntelligenceReportsPage /></RequirePermission> },
          { path: 'inteligencia/clientes', element: <RequirePermission permission="ANALYTICS_READ"><PortalIntelligenceCustomersPage /></RequirePermission> },
          { path: 'inteligencia/productos', element: <RequirePermission permission="ANALYTICS_READ"><PortalIntelligenceProductsPage /></RequirePermission> },
          { path: 'inteligencia/se-compran-juntos', element: <RequirePermission permission="ANALYTICS_READ"><PortalIntelligenceAssociationsPage /></RequirePermission> },
          { path: 'inteligencia/visibilidad', element: <RequirePermission permission="ANALYTICS_READ"><PortalIntelligenceVisibilityPage /></RequirePermission> },
          { path: 'inteligencia/proyeccion', element: <RequirePermission permission="ANALYTICS_READ"><PortalIntelligenceForecastPage /></RequirePermission> },
          { path: 'productos', element: <RequirePermission permission="PRODUCT_READ"><PortalProductsPage /></RequirePermission> },
          { path: 'productos/nuevo', element: <RequirePermission permission="PRODUCT_CREATE"><PortalProductFormPage /></RequirePermission> },
          { path: 'productos/:productId', element: <RequirePermission permission="PRODUCT_READ"><PortalProductFormPage /></RequirePermission> },
          { path: 'pedidos', element: <RequirePermission permission="ORDER_READ"><PortalOrdersPage /></RequirePermission> },
          { path: 'pedidos/:saleId', element: <RequirePermission permission="ORDER_READ"><PortalOrderDetailPage /></RequirePermission> },
          { path: 'promociones', element: <RequirePermission permission="PROMOTION_READ"><PortalPromotionsPage /></RequirePermission> },
          { path: 'publicidad', element: <RequirePermission permission="STORE_READ"><PortalAdsCampaignsPage /></RequirePermission> },
          { path: 'publicidad/nueva', element: <RequirePermission permission="STORE_UPDATE"><PortalAdsCampaignFormPage /></RequirePermission> },
          { path: 'publicidad/:campaignId/editar', element: <RequirePermission permission="STORE_UPDATE"><PortalAdsCampaignFormPage /></RequirePermission> },
          { path: 'publicidad/:campaignId/estadisticas', element: <RequirePermission permission="ANALYTICS_READ"><PortalAdsCampaignStatsPage /></RequirePermission> },
          // El correo de alertas enlaza aquí: /portal/{storeId}/alertas
          { path: 'alertas', element: <RequirePermission permission="STORE_READ"><PortalAlertsPage /></RequirePermission> },
          { path: 'sucursales', element: <RequirePermission permission="BRANCH_READ"><PortalBranchesPage /></RequirePermission> },
          { path: 'equipo', element: <RequirePermission permission="COLLABORATOR_READ"><PortalTeamPage /></RequirePermission> },
          { path: 'tienda', element: <RequirePermission permission="STORE_READ"><PortalStorePage /></RequirePermission> },
          { path: 'plan', element: <RequirePermission permission="STORE_READ"><PortalPlanPage /></RequirePermission> },
          { path: '*', element: <PortalHomeRedirect /> },
        ],
      },
    ],
  },
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
      { index: true, element: <Navigate to="resumen" replace /> },
      { path: 'resumen', element: <AdminOverviewPage /> },
      { path: 'solicitudes', element: <AdminApplicationsPage /> },
      { path: 'auditoria', element: <AdminAuditPage /> },
      { path: 'estadisticas', element: <AdminStatsPage /> },
      { path: 'planes', element: <AdminPlansPage /> },
      { path: 'promociones', element: <AdminPromotionsPage /> },
      { path: 'publicidad', element: <AdminAdsPage /> },
      { path: 'catalogo', element: <AdminCatalogPage /> },
      { path: 'clientes', element: <AdminCustomersPage /> },
      { path: 'busquedas', element: <AdminSearchesPage /> },
      { path: '*', element: <Navigate to="resumen" replace /> },
    ],
  },
])
