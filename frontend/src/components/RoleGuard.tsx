import React from 'react';
import { Navigate } from 'react-router-dom';
import { message } from 'antd';
import { useAuth, ViewerRole } from '../contexts/AuthContext';

interface RoleGuardProps {
  children: React.ReactNode;
  allowedRoles: ViewerRole[];
  fallbackMessage?: string;
}

/** Role-specific default pages — exported for use in redirects and navigation */
export const ROLE_DEFAULTS: Record<ViewerRole, string> = {
  STUDENT: '/student/orders',
  RUNNER: '/runner/available',
  ADMIN: '/admin/orders',
};

const RoleGuard: React.FC<RoleGuardProps> = ({
  children,
  allowedRoles,
  fallbackMessage = '无权限访问该页面',
}) => {
  const { role } = useAuth();

  if (!allowedRoles.includes(role)) {
    message.warning(fallbackMessage);
    return <Navigate to={ROLE_DEFAULTS[role]} replace />;
  }

  return <>{children}</>;
};

export default RoleGuard;
