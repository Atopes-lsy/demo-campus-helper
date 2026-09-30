import React, { Suspense } from 'react';
import { Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Space, Tag, Button, FloatButton, Spin, notification } from 'antd';
import { LogoutOutlined, RobotOutlined } from '@ant-design/icons';
const OrderList = React.lazy(() => import('./pages/order/OrderList'));
const OrderForm = React.lazy(() => import('./pages/order/OrderForm'));
const OrderDetail = React.lazy(() => import('./pages/order/OrderDetail'));
const AvailableOrders = React.lazy(() => import('./pages/order/AvailableOrders'));
const TransactionList = React.lazy(() => import('./pages/transaction/TransactionList'));
const RegisterRunner = React.lazy(() => import('./pages/runner/RegisterRunner'));
const EarningsDashboard = React.lazy(() => import('./pages/runner/EarningsDashboard'));
const RunnerManagement = React.lazy(() => import('./pages/admin/RunnerManagement'));
const StudentManagement = React.lazy(() => import('./pages/admin/StudentManagement'));
const LoginPage = React.lazy(() => import('./pages/auth/LoginPage'));
const AIChat = React.lazy(() => import('./pages/ai/AIChat'));
const AnomalyList = React.lazy(() => import('./pages/ai/AnomalyList'));
const Dashboard = React.lazy(() => import('./pages/dashboard/Dashboard'));
import RoleGuard, { ROLE_DEFAULTS } from './components/RoleGuard';
import { ROLE_MENUS } from './config/menus';
import { useAuth, ViewerRole } from './contexts/AuthContext';
import { useWebSocket } from './hooks/useWebSocket';

const { Header, Sider, Content } = Layout;

const ROLE_COLOR: Record<ViewerRole, string> = {
  STUDENT: '#5B8C6B',
  RUNNER: '#E8A648',
  ADMIN: '#D96C6C',
};

const ROLE_OPTIONS: { value: ViewerRole; label: string }[] = [
  { value: 'STUDENT', label: '学生' },
  { value: 'RUNNER', label: '跑腿员' },
  { value: 'ADMIN', label: '管理员' },
];

const PageFallback = (
  <div style={{ minHeight: 240, display: 'grid', placeItems: 'center' }}>
    <Spin size="large" />
  </div>
);

/** Wraps authenticated pages — redirects to /login if not logged in */
const AuthGuard: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { isLoggedIn } = useAuth();
  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

const App: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { role, isLoggedIn, userName, logout } = useAuth();
  const [notificationApi, notificationContext] = notification.useNotification();
  useWebSocket(isLoggedIn, (payload) => {
    notificationApi.info({
      message: payload.title || '订单通知',
      description: payload.content || '订单状态已更新',
      placement: 'topRight',
    });
  });

  const menuItems = ROLE_MENUS[role];
  const selectedKey: string =
    menuItems
      ?.find((item) => item != null && location.pathname.startsWith(String(item.key)))
      ?.key?.toString() ||
    menuItems?.[0]?.key?.toString() ||
    '/';

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  // Public routes (no login required)
  const isPublicRoute =
    location.pathname === '/login' ||
    location.pathname === '/runner/register';

  if (!isLoggedIn && !isPublicRoute) {
    return <Navigate to="/login" replace />;
  }

  // For login/register pages, render without sidebar
  if (isPublicRoute && !isLoggedIn) {
    return (
      <>
        {notificationContext}
        <Suspense fallback={PageFallback}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/runner/register" element={<RegisterRunner />} />
            <Route path="*" element={<Navigate to="/login" replace />} />
          </Routes>
        </Suspense>
      </>
    );
  }

  return (
    <>
    {notificationContext}
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        style={{
          borderRight: '1px solid #E0E8E2',
          boxShadow: '1px 0 8px rgba(23, 24, 28, 0.03)',
        }}
      >
        <div
          style={{
            height: 56,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 8,
            color: '#17181C',
            fontSize: 17,
            fontWeight: 700,
            letterSpacing: 1,
            borderBottom: '1px solid #E8EDE9',
          }}
        >
          校园帮帮
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(String(key))}
          style={{ borderInlineEnd: 'none' }}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#FFFFFF',
            padding: '0 28px',
            fontSize: 15,
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid #E8EDE9',
            height: 56,
          }}
        >
          <span style={{ color: '#3D4F42' }}>
            校园帮帮 — {ROLE_OPTIONS.find((o) => o.value === role)?.label}端
          </span>
          <Space size={12}>
            <Tag
              style={{
                borderRadius: 20,
                padding: '0 12px',
                fontWeight: 500,
                border: 'none',
              }}
              color={ROLE_COLOR[role]}
            >
              {userName || ROLE_OPTIONS.find((o) => o.value === role)?.label}
            </Tag>
            <Button
              type="text"
              icon={<LogoutOutlined />}
              onClick={handleLogout}
              style={{ color: '#768E7D' }}
            >
              退出
            </Button>
          </Space>
        </Header>
        <Content
          style={{
            margin: 20,
            padding: 24,
            background: '#FFFFFF',
            borderRadius: 12,
            minHeight: 360,
            boxShadow:
              '0 1px 3px rgba(23, 24, 28, 0.04), 0 1px 2px rgba(23, 24, 28, 0.03)',
          }}
        >
          <Suspense fallback={PageFallback}>
            <Routes>
            <Route path="/" element={<Navigate to={ROLE_DEFAULTS[role]} replace />} />
            <Route path="/login" element={<Navigate to={ROLE_DEFAULTS[role]} replace />} />

            {/* ======== Student Routes ======== */}
            <Route path="/student/orders" element={<RoleGuard allowedRoles={['STUDENT']}><OrderList mode="student-own" /></RoleGuard>} />
            <Route path="/student/orders/new" element={<RoleGuard allowedRoles={['STUDENT']}><OrderForm /></RoleGuard>} />
            <Route path="/student/orders/:id" element={<RoleGuard allowedRoles={['STUDENT']}><OrderDetail /></RoleGuard>} />

            {/* ======== Runner Routes ======== */}
            <Route path="/runner/available" element={<RoleGuard allowedRoles={['RUNNER']}><AvailableOrders /></RoleGuard>} />
            <Route path="/runner/deliveries" element={<RoleGuard allowedRoles={['RUNNER']}><OrderList mode="runner-deliveries" /></RoleGuard>} />
            <Route path="/runner/orders/:id" element={<RoleGuard allowedRoles={['RUNNER']}><OrderDetail /></RoleGuard>} />
            <Route path="/runner/earnings" element={<RoleGuard allowedRoles={['RUNNER']}><EarningsDashboard /></RoleGuard>} />
            <Route path="/runner/register" element={<RegisterRunner />} />

            {/* ======== Admin Routes ======== */}
            <Route path="/admin/orders" element={<RoleGuard allowedRoles={['ADMIN']}><OrderList mode="admin-all" /></RoleGuard>} />
            <Route path="/admin/orders/:id" element={<RoleGuard allowedRoles={['ADMIN']}><OrderDetail /></RoleGuard>} />
            <Route path="/admin/runners" element={<RoleGuard allowedRoles={['ADMIN']}><RunnerManagement /></RoleGuard>} />
            <Route path="/admin/students" element={<RoleGuard allowedRoles={['ADMIN']}><StudentManagement /></RoleGuard>} />
            <Route path="/admin/transactions" element={<RoleGuard allowedRoles={['ADMIN']}><TransactionList /></RoleGuard>} />
            <Route path="/admin/ai" element={<RoleGuard allowedRoles={['ADMIN']}><AIChat /></RoleGuard>} />
            <Route path="/dashboard" element={<RoleGuard allowedRoles={['ADMIN']}><Dashboard /></RoleGuard>} />
            <Route path="/ai/anomalies" element={<RoleGuard allowedRoles={['ADMIN']}><AnomalyList /></RoleGuard>} />

            {/* ======== Legacy redirects ======== */}
            <Route path="/ai/chat" element={<Navigate to="/admin/ai" replace />} />
            <Route path="/orders" element={<Navigate to={ROLE_DEFAULTS[role]} replace />} />
            <Route path="/orders/new" element={<Navigate to="/student/orders/new" replace />} />
            <Route path="*" element={<Navigate to={ROLE_DEFAULTS[role]} replace />} />
            </Routes>
          </Suspense>
        </Content>
        {/* AI Assistant floating button — ADMIN only */}
        {role === 'ADMIN' && (
          <FloatButton
            icon={<RobotOutlined />}
            type="primary"
            tooltip="AI 助手"
            onClick={() => navigate('/admin/ai')}
            style={{ right: 32, bottom: 32 }}
          />
        )}
      </Layout>
    </Layout>
    </>
  );
};

export default App;
