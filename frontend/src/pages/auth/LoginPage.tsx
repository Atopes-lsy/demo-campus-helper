import React from 'react';
import { Card, Form, Input, Button, Tabs, message } from 'antd';
import { UserOutlined, LockOutlined, PhoneOutlined, IdcardOutlined, NumberOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { studentLogin, studentRegister, runnerLogin, adminLogin } from '../../api/auth';
import { useAuth, ViewerRole } from '../../contexts/AuthContext';
import { ROLE_DEFAULTS } from '../../components/RoleGuard';

interface LoginResult {
  userId: number;
  name: string;
  role: ViewerRole;
}

const LoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();
  const [loading, setLoading] = React.useState(false);
  const [activeTab, setActiveTab] = React.useState<string>('student');

  const handleLoginSuccess = (data: LoginResult) => {
    login(data.role, data.userId, data.name);
    message.success(`欢迎，${data.name}！`);
    navigate(ROLE_DEFAULTS[data.role]);
  };

  // ====== Student Login ======
  const handleStudentLogin = async (values: { studentNo: string; password: string }) => {
    setLoading(true);
    try {
      const res = await studentLogin(values as unknown as Record<string, unknown>);
      const data = (res.data || res) as LoginResult;
      handleLoginSuccess(data);
    } catch {
      // error handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  // ====== Student Register ======
  const handleStudentRegister = async (values: {
    studentNo: string; name: string; phone: string; dormitory: string; password: string;
  }) => {
    setLoading(true);
    try {
      const res = await studentRegister(values as unknown as Record<string, unknown>);
      const data = (res.data || res) as LoginResult;
      message.success('注册成功！');
      handleLoginSuccess(data);
    } catch {
      // error handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  // ====== Runner Login ======
  const handleRunnerLogin = async (values: { phone: string; password: string }) => {
    setLoading(true);
    try {
      const res = await runnerLogin(values as unknown as Record<string, unknown>);
      const data = (res.data || res) as LoginResult;
      handleLoginSuccess(data);
    } catch {
      // error handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  // ====== Admin Login ======
  const handleAdminLogin = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await adminLogin(values as unknown as Record<string, unknown>);
      const data = (res.data || res) as LoginResult;
      handleLoginSuccess(data);
    } catch {
      // error handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  const tabItems = [
    {
      key: 'student',
      label: '学生',
      children: (
        <Tabs
          size="small"
          items={[
            {
              key: 'login',
              label: '登录',
              children: (
                <Form onFinish={handleStudentLogin} layout="vertical" style={{ marginTop: 8 }}>
                  <Form.Item name="studentNo" rules={[{ required: true, message: '请输入学号' }]}>
                    <Input prefix={<IdcardOutlined />} placeholder="学号" size="large" />
                  </Form.Item>
                  <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
                    <Input.Password prefix={<LockOutlined />} placeholder="密码（默认123456）" size="large" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" loading={loading} block size="large">
                    登录
                  </Button>
                </Form>
              ),
            },
            {
              key: 'register',
              label: '注册',
              children: (
                <Form onFinish={handleStudentRegister} layout="vertical" style={{ marginTop: 8 }}>
                  <Form.Item name="studentNo" rules={[{ required: true, message: '请输入学号' }]}>
                    <Input prefix={<IdcardOutlined />} placeholder="学号" />
                  </Form.Item>
                  <Form.Item name="name" rules={[{ required: true, message: '请输入姓名' }]}>
                    <Input prefix={<UserOutlined />} placeholder="姓名" />
                  </Form.Item>
                  <Form.Item name="phone" rules={[
                    { required: true, message: '请输入手机号' },
                    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
                  ]}>
                    <Input prefix={<PhoneOutlined />} placeholder="手机号" />
                  </Form.Item>
                  <Form.Item name="dormitory" rules={[{ required: true, message: '请输入宿舍楼' }]}>
                    <Input prefix={<NumberOutlined />} placeholder="宿舍楼（如：5号宿舍楼）" />
                  </Form.Item>
                  <Form.Item name="password" rules={[{ required: true, message: '请设置密码' }]}>
                    <Input.Password prefix={<LockOutlined />} placeholder="设置密码" />
                  </Form.Item>
                  <Button type="primary" htmlType="submit" loading={loading} block>
                    注册
                  </Button>
                </Form>
              ),
            },
          ]}
        />
      ),
    },
    {
      key: 'runner',
      label: '跑腿员',
      children: (
        <div style={{ marginTop: 8 }}>
          <Form onFinish={handleRunnerLogin} layout="vertical">
            <Form.Item name="phone" rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
            ]}>
              <Input prefix={<PhoneOutlined />} placeholder="注册手机号" size="large" />
            </Form.Item>
            <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
              <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block size="large">
              登录
            </Button>
          </Form>
          <div style={{ textAlign: 'center', marginTop: 16 }}>
            <a onClick={() => navigate('/runner/register')}>没有账号？去注册跑腿员</a>
          </div>
        </div>
      ),
    },
    {
      key: 'admin',
      label: '管理员',
      children: (
        <Form onFinish={handleAdminLogin} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名（admin）" size="large" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码（admin123）" size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block size="large">
            登录
          </Button>
        </Form>
      ),
    },
  ];

  return (
    <div style={{
      display: 'flex', justifyContent: 'center', alignItems: 'center',
      minHeight: '100vh', background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <Card
        style={{ width: 420, boxShadow: '0 8px 24px rgba(0,0,0,0.15)' }}
        title={
          <div style={{ textAlign: 'center', fontSize: 24, fontWeight: 700, color: '#1677ff' }}>
            校园帮帮
          </div>
        }
      >
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          centered
          size="large"
          items={tabItems}
        />
      </Card>
    </div>
  );
};

export default LoginPage;
