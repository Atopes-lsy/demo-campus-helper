import type { MenuProps } from 'antd';
import {
  OrderedListOutlined,
  PlusOutlined,
  UnorderedListOutlined,
  CarryOutOutlined,
  RobotOutlined,
  DashboardOutlined,
  TeamOutlined,
  DollarOutlined,
  AlertOutlined,
  UserAddOutlined,
  BarChartOutlined,
} from '@ant-design/icons';
import type { ViewerRole } from '../contexts/AuthContext';

type MenuItem = Required<MenuProps>['items'][number];

export const ROLE_MENUS: Record<ViewerRole, MenuItem[]> = {
  STUDENT: [
    { key: '/student/orders', icon: <OrderedListOutlined />, label: '我的订单' },
    { key: '/student/orders/new', icon: <PlusOutlined />, label: '新建订单' },
    { key: '/ai/chat', icon: <RobotOutlined />, label: 'AI 助手' },
  ],
  RUNNER: [
    { key: '/runner/available', icon: <UnorderedListOutlined />, label: '可接订单' },
    { key: '/runner/deliveries', icon: <CarryOutOutlined />, label: '我的配送' },
    { key: '/runner/earnings', icon: <BarChartOutlined />, label: '收入统计' },
    { key: '/runner/register', icon: <UserAddOutlined />, label: '跑腿员注册' },
    { key: '/ai/chat', icon: <RobotOutlined />, label: 'AI 助手' },
  ],
  ADMIN: [
    { key: '/admin/orders', icon: <OrderedListOutlined />, label: '全部订单' },
    { key: '/admin/runners', icon: <TeamOutlined />, label: '跑腿员管理' },
    { key: '/dashboard', icon: <DashboardOutlined />, label: '数据看板' },
    { key: '/admin/transactions', icon: <DollarOutlined />, label: '后台流水' },
    { key: '/ai/anomalies', icon: <AlertOutlined />, label: '异常预警' },
    { key: '/ai/chat', icon: <RobotOutlined />, label: 'AI 助手' },
  ],
};

export { ROLE_DEFAULTS } from '../components/RoleGuard';
