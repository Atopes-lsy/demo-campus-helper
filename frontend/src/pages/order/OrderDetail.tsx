import React, { useEffect, useState } from 'react';
import { Card, Descriptions, Tag, Timeline, Button, Spin, Empty, Space, Steps, Popconfirm, message } from 'antd';
import { ArrowLeftOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { getOrder, getOrderTracking, updateOrderStatus } from '../../api/order';
import { ORDER_TYPE_MAP, ORDER_STATUS_MAP, PACKAGE_SIZE_MAP } from '../../utils/dict';
import { useAuth } from '../../contexts/AuthContext';

interface PackageItem {
  size: string;
}

interface OrderDetailData {
  id: number;
  orderNo: string;
  orderType: number;
  title: string;
  status: number;
  amount: number;
  tip: number;
  pickupLocation: string;
  deliveryLocation: string;
  description: string;
  packages?: string;
  pickupCode?: string;
  student?: { name: string; phone: string };
  runner?: { name: string; phone: string };
  createdAt: string;
}

interface TrackingRecord {
  id: number;
  description: string;
  statusFrom?: number;
  statusTo?: number;
  createdAt: string;
}

const STATUS_STEPS = [
  { status: 0, title: '待接单' },
  { status: 1, title: '已接单' },
  { status: 2, title: '取件中' },
  { status: 3, title: '配送中' },
  { status: 4, title: '已送达' },
  { status: 5, title: '已完成' },
];

const OrderDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { role, userId } = useAuth();
  const [order, setOrder] = useState<OrderDetailData | null>(null);
  const [tracking, setTracking] = useState<TrackingRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  const fetchData = async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [orderRes, trackingRes] = await Promise.all([
        getOrder(Number(id)),
        getOrderTracking(Number(id)),
      ]);
      const orderData = orderRes.data || orderRes;
      const trackingData = trackingRes.data || trackingRes;
      setOrder(orderData);
      setTracking(trackingData.records || trackingData.list || trackingData || []);
    } catch {
      // error handled by interceptor
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  // ---- Status action helpers ----
  const doAction = async (newStatus: number, operatorType: string, successMsg: string) => {
    if (!order) return;
    setActionLoading(true);
    try {
      await updateOrderStatus(order.id, {
        newStatus,
        operatorId: userId,
        operatorType,
      });
      message.success(successMsg);
      await fetchData();
    } catch {
      // error handled by interceptor
    } finally {
      setActionLoading(false);
    }
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" />
      </div>
    );
  }

  if (!order) {
    return <div>订单不存在</div>;
  }

  const typeItem = ORDER_TYPE_MAP[order.orderType];
  const statusItem = ORDER_STATUS_MAP[order.status];
  const rolePrefix = role === 'STUDENT' ? 'student' : role === 'RUNNER' ? 'runner' : 'admin';

  // ---- Visibility rules ----
  const canSeeTitle = role === 'ADMIN' || role === 'RUNNER';
  const canSeePickupCode =
    role === 'ADMIN' || role === 'STUDENT' || (role === 'RUNNER' && order.status >= 1);

  // Parse packages JSON
  let packages: PackageItem[] = [];
  try {
    if (order.packages) {
      packages = JSON.parse(order.packages);
    }
  } catch {
    packages = [];
  }

  // ---- Determine current step for Steps component ----
  const currentStepIndex = order.status === 6 ? -1 : STATUS_STEPS.findIndex((s) => s.status === order.status);

  // ---- Role-specific action buttons ----
  const renderActions = () => {
    const buttons: React.ReactNode[] = [];

    if (role === 'STUDENT') {
      if (order.status === 0) {
        buttons.push(
          <Popconfirm key="cancel" title="确定取消该订单？" onConfirm={() => doAction(6, 'STUDENT', '订单已取消')}>
            <Button danger loading={actionLoading}>取消订单</Button>
          </Popconfirm>
        );
      }
      if (order.status === 4) {
        buttons.push(
          <Popconfirm key="confirm" title="确认已收到包裹？" onConfirm={() => doAction(5, 'STUDENT', '已确认收货')}>
            <Button type="primary" icon={<CheckCircleOutlined />} loading={actionLoading}>确认收货</Button>
          </Popconfirm>
        );
      }
    }

    if (role === 'RUNNER') {
      if (order.status === 1) {
        buttons.push(
          <Button key="go" type="primary" loading={actionLoading} onClick={() => doAction(2, 'RUNNER', '已标记为前往取件')}>
            前往取件
          </Button>
        );
      }
      if (order.status === 2) {
        buttons.push(
          <Popconfirm key="picked" title="确认已取到包裹？" onConfirm={() => doAction(3, 'RUNNER', '已标记为配送中')}>
            <Button type="primary" loading={actionLoading}>已取件，开始配送</Button>
          </Popconfirm>
        );
      }
      if (order.status === 3) {
        buttons.push(
          <Popconfirm key="deliver" title="确认已送达？" onConfirm={() => doAction(4, 'RUNNER', '已标记为已送达')}>
            <Button type="primary" style={{ background: '#52c41a', borderColor: '#52c41a' }} loading={actionLoading}>
              确认送达
            </Button>
          </Popconfirm>
        );
      }
    }

    if (role === 'ADMIN') {
      if (order.status !== 5 && order.status !== 6) {
        buttons.push(
          <Popconfirm key="force" title="管理员强制取消该订单？" onConfirm={() => doAction(6, 'ADMIN', '已强制取消')}>
            <Button danger loading={actionLoading}>强制取消</Button>
          </Popconfirm>
        );
      }
    }

    return buttons;
  };

  const actionButtons = renderActions();

  return (
    <div>
      <Button
        icon={<ArrowLeftOutlined />}
        style={{ marginBottom: 16 }}
        onClick={() => navigate(`/${rolePrefix}/orders`)}
      >
        返回订单列表
      </Button>

      {/* ---- Status flow steps ---- */}
      {order.status !== 6 && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Steps
            size="small"
            current={currentStepIndex}
            status={order.status === 6 ? 'error' : 'process'}
            items={STATUS_STEPS.map((s) => ({ title: s.title }))}
          />
        </Card>
      )}

      {/* ---- Action buttons ---- */}
      {actionButtons.length > 0 && (
        <Card size="small" style={{ marginBottom: 16, background: '#f6ffed' }}>
          <Space size="middle">
            <span style={{ fontWeight: 600, fontSize: 14 }}>当前可操作：</span>
            {actionButtons}
          </Space>
        </Card>
      )}

      <Card title="订单详情" style={{ marginBottom: 16 }}>
        <Descriptions bordered column={{ xs: 1, sm: 2 }}>
          <Descriptions.Item label="订单编号">{order.orderNo}</Descriptions.Item>
          <Descriptions.Item label="订单类型">
            {typeItem ? `${typeItem.icon} ${typeItem.label}` : '—'}
          </Descriptions.Item>

          {canSeeTitle && (
            <Descriptions.Item label="标题" span={2}>
              {order.title || '—'}
            </Descriptions.Item>
          )}

          <Descriptions.Item label="状态">
            {statusItem ? <Tag color={statusItem.color}>{statusItem.label}</Tag> : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="下单人">
            {order.student?.name || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="跑腿员">
            {order.runner?.name || '—'}
          </Descriptions.Item>
          <Descriptions.Item label="取件地址">{order.pickupLocation}</Descriptions.Item>
          <Descriptions.Item label="送达地址">{order.deliveryLocation}</Descriptions.Item>
          <Descriptions.Item label="金额">¥{order.amount?.toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="小费">¥{(order.tip ?? 0)?.toFixed(2)}</Descriptions.Item>

          <Descriptions.Item label="包裹明细" span={2}>
            {packages.length > 0 ? (
              <Space wrap>
                {packages.map((pkg, idx) => {
                  const info = PACKAGE_SIZE_MAP[pkg.size];
                  return (
                    <Tag key={idx} color="blue">
                      包裹{idx + 1}：{info ? `${info.label}（${info.size}）¥${info.price}` : pkg.size}
                    </Tag>
                  );
                })}
              </Space>
            ) : (
              '—'
            )}
          </Descriptions.Item>

          <Descriptions.Item label="创建时间">
            {order.createdAt ? dayjs(order.createdAt).format('YYYY-MM-DD HH:mm:ss') : '—'}
          </Descriptions.Item>

          <Descriptions.Item label="取件码">
            {canSeePickupCode ? (order.pickupCode || '—') : (
              <span style={{ color: '#999' }}>接单后可见</span>
            )}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="订单追踪">
        {tracking.length > 0 ? (
          <Timeline
            items={tracking.map((record) => ({
              children: (
                <div>
                  <div>{record.description}</div>
                  <div style={{ color: '#999', fontSize: 12 }}>
                    {dayjs(record.createdAt).format('YYYY-MM-DD HH:mm:ss')}
                  </div>
                </div>
              ),
              color: record.statusTo === 5 ? 'green' : record.statusTo === 6 ? 'red' : 'blue',
            }))}
          />
        ) : (
          <Empty description="暂无追踪记录" />
        )}
      </Card>
    </div>
  );
};

export default OrderDetail;
