import React, { useMemo } from 'react';
import { Form, Input, InputNumber, Radio, Button, Card, Select, message, Space, Divider } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { createOrder } from '../../api/order';
import { ORDER_TYPE_OPTIONS, PACKAGE_SIZE_MAP, PACKAGE_SIZE_OPTIONS, PICKUP_LOCATION_OPTIONS } from '../../utils/dict';
import { useAuth } from '../../contexts/AuthContext';

const { TextArea } = Input;

interface PackageItem {
  size: string;
}

interface OrderFormValues {
  orderType: number;
  pickupLocation: string;
  deliveryLocation: string;
  packages: PackageItem[];
  pickupCode: string;
  tip?: number;
}

/** Calculate total price from package list */
function calcTotal(packages: PackageItem[] | undefined): number {
  if (!packages) return 0;
  return packages.reduce((sum, p) => sum + (PACKAGE_SIZE_MAP[p.size]?.price || 0), 0);
}

const OrderForm: React.FC = () => {
  const navigate = useNavigate();
  const { role, userId } = useAuth();
  const [form] = Form.useForm<OrderFormValues>();
  const [submitting, setSubmitting] = React.useState(false);

  // Reactive watch on packages — triggers re-render on every change
  const watchedPackages: PackageItem[] | undefined = Form.useWatch('packages', form);

  // Compute total with useMemo to avoid recalculation on every render
  const totalAmount = useMemo(() => calcTotal(watchedPackages), [watchedPackages]);

  // Only students can create orders
  if (role !== 'STUDENT') {
    return (
      <Card title="提示" style={{ maxWidth: 700, margin: '0 auto', textAlign: 'center' }}>
        <p style={{ fontSize: 16, color: '#768E7D', marginTop: 40, marginBottom: 40 }}>
          仅学生用户可以新建订单。
        </p>
        <Button type="primary" onClick={() => navigate('/student/orders')}>
          返回我的订单
        </Button>
      </Card>
    );
  }

  const handleSubmit = async (values: OrderFormValues) => {
    setSubmitting(true);
    try {
      const payload = {
        studentId: userId,
        orderType: values.orderType,
        pickupLocation: values.pickupLocation,
        deliveryLocation: values.deliveryLocation,
        packages: values.packages,
        pickupCode: values.pickupCode || '',
        tip: values.tip || 0,
      };
      await createOrder(payload as unknown as Record<string, unknown>);
      message.success('订单创建成功');
      navigate('/student/orders');
    } catch {
      // error handled by interceptor
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Card title="新建订单" style={{ maxWidth: 700, margin: '0 auto' }}>
      <Form
        form={form}
        layout="vertical"
        onFinish={handleSubmit}
        initialValues={{
          orderType: 1,
          tip: 0,
          packages: [{ size: 'small' }],
        }}
      >
        {/* ---- Order type ---- */}
        <Form.Item
          name="orderType"
          label="订单类型"
          rules={[{ required: true, message: '请选择订单类型' }]}
        >
          <Radio.Group options={ORDER_TYPE_OPTIONS} />
        </Form.Item>

        {/* ---- Pickup location (select) ---- */}
        <Form.Item
          name="pickupLocation"
          label="取件地址"
          rules={[{ required: true, message: '请选择取件地址' }]}
        >
          <Select
            placeholder="请选择取件地址"
            options={PICKUP_LOCATION_OPTIONS}
          />
        </Form.Item>

        {/* ---- Delivery location ---- */}
        <Form.Item
          name="deliveryLocation"
          label="送达地址"
          rules={[{ required: true, message: '请输入送达地址' }]}
        >
          <Input placeholder="例如：5号宿舍楼" />
        </Form.Item>

        {/* ---- Package list ---- */}
        <Form.Item label="包裹列表（每个包裹选择大小）" required>
          <Form.List name="packages">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Space
                    key={key}
                    align="baseline"
                    style={{ display: 'flex', marginBottom: 8 }}
                  >
                    <Form.Item
                      {...restField}
                      name={[name, 'size']}
                      rules={[{ required: true, message: '请选择大小' }]}
                      style={{ marginBottom: 0 }}
                    >
                      <Select
                        placeholder="选择包裹大小"
                        options={PACKAGE_SIZE_OPTIONS}
                        style={{ width: 260 }}
                      />
                    </Form.Item>

                    {fields.length > 1 && (
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        onClick={() => remove(name)}
                      />
                    )}
                  </Space>
                ))}

                <Button
                  type="dashed"
                  onClick={() => add({ size: 'small' })}
                  block
                  icon={<PlusOutlined />}
                >
                  添加包裹
                </Button>

                {/* ---- Auto-calculated total ---- */}
                <Divider />
                <div style={{ fontSize: 16, fontWeight: 600, color: '#5B8C6B' }}>
                  订单金额（自动计算）：¥{totalAmount.toFixed(2)}
                </div>
                <div style={{ fontSize: 12, color: '#768E7D', marginTop: 4 }}>
                  小件（20×20cm）= ¥4 &nbsp;|&nbsp;
                  中件（40×40cm）= ¥5 &nbsp;|&nbsp;
                  大件（60×60cm）= ¥8
                </div>
              </>
            )}
          </Form.List>
        </Form.Item>

        {/* ---- Pickup code ---- */}
        <Form.Item name="pickupCode" label="取件码">
          <TextArea
            rows={2}
            placeholder="请输入取件码（取件员接单后才可见）"
          />
        </Form.Item>

        {/* ---- Tip ---- */}
        <Form.Item name="tip" label="小费（选填）">
          <InputNumber min={0} step={0.5} precision={2} style={{ width: '100%' }} placeholder="0.00" />
        </Form.Item>

        {/* ---- Submit ---- */}
        <Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>
            提交订单
          </Button>
        </Form.Item>
      </Form>
    </Card>
  );
};

export default OrderForm;
