import React from 'react';
import { Routes, Route, Navigate, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Select, Space, Tag } from 'antd';
import OrderList from './pages/order/OrderList';
import OrderForm from './pages/order/OrderForm';
import OrderDetail from './pages/order/OrderDetail';
import AvailableOrders from './pages/order/AvailableOrders';
import TransactionList from './pages/transaction/TransactionList';
import AIChat from './pages/ai/AIChat';
import AnomalyList from './pages/ai/AnomalyList';
import Dashboard from './pages/dashboard/Dashboard';
import RoleGuard, { ROLE_DEFAULTS } from './components/RoleGuard';
import { ROLE_MENUS } from './config/menus';
import { useAuth, ViewerRole } from './contexts/AuthContext';

const { Header, Sider, Content } = Layout;

const ROLE_COLOR: Record<ViewerRole, string> = {
  STUDENT: 'blue',
  RUNNER: 'orange',
  ADMIN: 'red',
};

const ROLE_OPTIONS: { value: ViewerRole; label: string }[] = [
  { value: 'STUDENT', label: '学生' },
  { value: 'RUNNER', label: '跑腿员' },
  { value: 'ADMIN', label: '管理员' },
];

const App: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { role, setRole } = useAuth();

  const menuItems = ROLE_MENUS[role];
  const selectedKey: string =
    menuItems
      ?.find((item) => item != null && location.pathname.startsWith(String(item.key)))
      ?.key?.toString() ||
    menuItems?.[0]?.key?.toString() ||
    '/';

  const handleRoleChange = (newRole: ViewerRole) => {
    setRole(newRole);
    navigate(ROLE_DEFAULTS[newRole]);
  };

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider collapsible>
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontSize: 18,
            fontWeight: 'bold',
          }}
        >
          校园帮帮
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(String(key))}
        />
      </Sider>
      <Layout>
        <Header
          style={{
            background: '#fff',
            padding: '0 24px',
            fontSize: 16,
            fontWeight: 500,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <span>
            校园帮帮 — {ROLE_OPTIONS.find((o) => o.value === role)?.label}端
          </span>
          <Space>
            <span style={{ fontSize: 13, fontWeight: 400, color: '#666' }}>切换角色：</span>
            <Select
              size="small"
              value={role}
              onChange={(v) => handleRoleChange(v)}
              options={ROLE_OPTIONS}
              style={{ width: 100 }}
            />
            <Tag color={ROLE_COLOR[role]}>
              {ROLE_OPTIONS.find((o) => o.value === role)?.label}
            </Tag>
          </Space>
        </Header>
        <Content style={{ margin: 16, padding: 24, background: '#fff', borderRadius: 8, minHeight: 360 }}>
          <Routes>
            {/* ======== Root: role-aware redirect ======== */}
            <Route path="/" element={<Navigate to={ROLE_DEFAULTS[role]} replace />} />

            {/* ======== Student Routes ======== */}
            <Route
              path="/student/orders"
              element={
                <RoleGuard allowedRoles={['STUDENT']}>
                  <OrderList mode="student-own" />
                </RoleGuard>
              }
            />
            <Route
              path="/student/orders/new"
              element={
                <RoleGuard allowedRoles={['STUDENT']}>
                  <OrderForm />
                </RoleGuard>
              }
            />
            <Route
              path="/student/orders/:id"
              element={
                <RoleGuard allowedRoles={['STUDENT']}>
                  <OrderDetail />
                </RoleGuard>
              }
            />

            {/* ======== Runner Routes ======== */}
            <Route
              path="/runner/available"
              element={
                <RoleGuard allowedRoles={['RUNNER']}>
                  <AvailableOrders />
                </RoleGuard>
              }
            />
            <Route
              path="/runner/deliveries"
              element={
                <RoleGuard allowedRoles={['RUNNER']}>
                  <OrderList mode="runner-deliveries" />
                </RoleGuard>
              }
            />
            <Route
              path="/runner/orders/:id"
              element={
                <RoleGuard allowedRoles={['RUNNER']}>
                  <OrderDetail />
                </RoleGuard>
              }
            />

            {/* ======== Admin Routes ======== */}
            <Route
              path="/admin/orders"
              element={
                <RoleGuard allowedRoles={['ADMIN']}>
                  <OrderList mode="admin-all" />
                </RoleGuard>
              }
            />
            <Route
              path="/admin/orders/:id"
              element={
                <RoleGuard allowedRoles={['ADMIN']}>
                  <OrderDetail />
                </RoleGuard>
              }
            />
            <Route
              path="/admin/runners"
              element={
                <RoleGuard allowedRoles={['ADMIN']}>
                  <div style={{ fontSize: 16, color: '#999', padding: 40, textAlign: 'center' }}>
                    跑腿员管理（开发中）
                  </div>
                </RoleGuard>
              }
            />
            <Route
              path="/admin/transactions"
              element={
                <RoleGuard allowedRoles={['ADMIN']}>
                  <TransactionList />
                </RoleGuard>
              }
            />

            {/* ======== Shared Routes ======== */}
            <Route path="/ai/chat" element={<AIChat />} />

            {/* Admin-only: Dashboard & Anomalies */}
            <Route
              path="/dashboard"
              element={
                <RoleGuard allowedRoles={['ADMIN']}>
                  <Dashboard />
                </RoleGuard>
              }
            />
            <Route
              path="/ai/anomalies"
              element={
                <RoleGuard allowedRoles={['ADMIN']}>
                  <AnomalyList />
                </RoleGuard>
              }
            />

            {/* ======== Legacy route redirects ======== */}
            <Route path="/orders" element={<Navigate to={ROLE_DEFAULTS[role]} replace />} />
            <Route path="/orders/new" element={<Navigate to="/student/orders/new" replace />} />
            <Route path="/orders/:id" element={<Navigate to={`/${role.toLowerCase()}/orders/${location.pathname.split('/').pop()}`} replace />} />

            {/* ======== Catch-all: redirect to role home ======== */}
            <Route path="*" element={<Navigate to={ROLE_DEFAULTS[role]} replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
};

export default App;
