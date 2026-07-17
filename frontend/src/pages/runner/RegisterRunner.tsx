import React from 'react';
import { Card, Form, Input, Button, message } from 'antd';
import { UserOutlined, PhoneOutlined, IdcardOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { createRunner } from '../../api/runner';

interface RegisterFormValues {
  name: string;
  phone: string;
  idCard: string;
  password: string;
}

const RegisterRunner: React.FC = () => {
  const navigate = useNavigate();
  const [form] = Form.useForm<RegisterFormValues>();
  const [submitting, setSubmitting] = React.useState(false);

  const handleSubmit = async (values: RegisterFormValues) => {
    setSubmitting(true);
    try {
      await createRunner({ ...values } as unknown as Record<string, unknown>);
      message.success('注册成功！请返回登录页面登录');
      navigate('/login');
    } catch {
      // error handled by interceptor
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{
      display: 'flex', justifyContent: 'center', alignItems: 'center',
      minHeight: '100vh', background: '#E9EEEA',
    }}>
      <Card
        title={<div style={{ textAlign: 'center' }}>
          <img
            src="/campus-helper-logo.png"
            alt="校园帮帮"
            style={{ height: 64, width: 64, objectFit: 'contain', marginBottom: -4 }}
          />
          <div style={{ fontSize: 22, fontWeight: 700, color: '#4A7C59', letterSpacing: 1 }}>注册成为跑腿员</div>
        </div>}
        style={{ width: 420, borderRadius: 16, border: '1px solid #E0E8E2',
          boxShadow: '0 8px 32px rgba(23, 24, 28, 0.08), 0 2px 8px rgba(23, 24, 28, 0.04)' }}
      >
        <Form form={form} layout="vertical" onFinish={handleSubmit}>
          <Form.Item name="name" label="姓名"
            rules={[{ required: true, message: '请输入姓名' }, { min: 2, max: 20, message: '2-20个字符' }]}>
            <Input prefix={<UserOutlined />} placeholder="真实姓名" size="large" />
          </Form.Item>

          <Form.Item name="phone" label="手机号"
            rules={[{ required: true, message: '请输入手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]}>
            <Input prefix={<PhoneOutlined />} placeholder="11位手机号" maxLength={11} size="large" />
          </Form.Item>

          <Form.Item name="idCard" label="身份证号"
            rules={[{ required: true, message: '请输入身份证号' }, { pattern: /^\d{17}[\dXx]$/, message: '18位身份证号' }]}>
            <Input prefix={<IdcardOutlined />} placeholder="18位身份证号" maxLength={18} size="large" />
          </Form.Item>

          <Form.Item name="password" label="登录密码"
            rules={[{ required: true, message: '请设置登录密码' }, { min: 6, message: '至少6位' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="设置登录密码（至少6位）" size="large" />
          </Form.Item>

          <Form.Item style={{ marginTop: 24 }}>
            <Button type="primary" htmlType="submit" loading={submitting} block size="large">
              提交注册
            </Button>
          </Form.Item>

          <div style={{ textAlign: 'center' }}>
            <a onClick={() => navigate('/login')} style={{ color: '#5B8C6B' }}>已有账号？返回登录</a>
          </div>
        </Form>
      </Card>
    </div>
  );
};

export default RegisterRunner;
