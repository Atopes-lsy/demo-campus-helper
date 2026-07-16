import React, { useEffect, useState } from 'react';
import {
  Card, Descriptions, Tag, Timeline, Button, Spin, Empty, Space,
  Steps, Popconfirm, message, Modal, Rate, Input,
} from 'antd';
import { ArrowLeftOutlined, CheckCircleOutlined, StarOutlined } from '@ant-design/icons';
import { useParams, useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { getOrder, getOrderTracking, updateOrderStatus } from '../../api/order';
import { submitReview, getOrderReview } from '../../api/review';
import { ORDER_TYPE_MAP, ORDER_STATUS_MAP, PACKAGE_SIZE_MAP } from '../../utils/dict';
import { useAuth } from '../../contexts/AuthContext';

const { TextArea } = Input;

const TAG_OPTIONS = ['快速', '准时', '态度好', '细心', '沟通顺畅'];

interface PackageItem { size: string; }

interface OrderDetailData {
  id: number; orderNo: string; orderType: number; title: string;
  status: number; amount: number; tip: number;
  pickupLocation: string; deliveryLocation: string; description: string;
  packages?: string; pickupCode?: string;
  student?: { name: string; phone: string };
  runner?: { name: string; phone: string };
  createdAt: string;
}

interface TrackingRecord {
  id: number; description: string; statusFrom?: number; statusTo?: number; createdAt: string;
}

const STATUS_STEPS = [
  { status: 0, title: '待接单' }, { status: 1, title: '已接单' },
  { status: 2, title: '取件中' }, { status: 3, title: '配送中' },
  { status: 4, title: '已送达' }, { status: 5, title: '已完成' },
];

const OrderDetail: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { role, userId } = useAuth();
  const [order, setOrder] = useState<OrderDetailData | null>(null);
  const [tracking, setTracking] = useState<TrackingRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [actionLoading, setActionLoading] = useState(false);

  // Review state
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewTags, setReviewTags] = useState<string[]>([]);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [alreadyReviewed, setAlreadyReviewed] = useState(false);

  const fetchData = async () => {
    if (!id) return;
    setLoading(true);
    try {
      const [orderRes, trackingRes] = await Promise.all([
        getOrder(Number(id)), getOrderTracking(Number(id)),
      ]);
      const orderData = orderRes.data || orderRes;
      const trackingData = trackingRes.data || trackingRes;
      setOrder(orderData);
      setTracking(trackingData.records || trackingData.list || trackingData || []);
    } catch { /* error handled by interceptor */ }
    finally { setLoading(false); }
  };

  const checkReviewStatus = async () => {
    if (!id) return;
    try {
      const res = await getOrderReview(Number(id), 1);
      const data = res.data || res;
      if (data) setAlreadyReviewed(true);
    } catch { /* ignore */ }
  };

  useEffect(() => { fetchData(); checkReviewStatus(); }, [id]);

  const doAction = async (newStatus: number, operatorType: string, successMsg: string) => {
    if (!order) return;
    setActionLoading(true);
    try {
      await updateOrderStatus(order.id, { newStatus, operatorId: userId, operatorType });
      message.success(successMsg);
      await fetchData();
      // If student just confirmed receipt (5), check review status
      if (newStatus === 5 && operatorType === 'STUDENT') {
        setAlreadyReviewed(false);
      }
    } catch { /* error handled by interceptor */ }
    finally { setActionLoading(false); }
  };

  // ---- Submit review ----
  const handleSubmitReview = async () => {
    if (!order) return;
    setReviewSubmitting(true);
    try {
      await submitReview({
        orderId: order.id,
        reviewerType: 1,
        rating: reviewRating,
        content: reviewContent || '',
        tags: reviewTags.join(','),
      });
      message.success('评分提交成功！感谢您的评价');
      setReviewModalOpen(false);
      setAlreadyReviewed(true);
    } catch { /* error handled by interceptor */ }
    finally { setReviewSubmitting(false); }
  };

  if (loading) return <div style={{ textAlign: 'center', padding: 100 }}><Spin size="large" /></div>;
  if (!order) return <div>订单不存在</div>;

  const typeItem = ORDER_TYPE_MAP[order.orderType];
  const statusItem = ORDER_STATUS_MAP[order.status];
  const rolePrefix = role === 'STUDENT' ? 'student' : role === 'RUNNER' ? 'runner' : 'admin';
  const canSeeTitle = role === 'ADMIN' || role === 'RUNNER';
  const canSeePickupCode = role === 'ADMIN' || role === 'STUDENT' || (role === 'RUNNER' && order.status >= 1);
  const currentStepIndex = order.status === 6 ? -1 : STATUS_STEPS.findIndex((s) => s.status === order.status);

  let packages: PackageItem[] = [];
  try { if (order.packages) packages = JSON.parse(order.packages); } catch { packages = []; }

  const renderActions = () => {
    const buttons: React.ReactNode[] = [];
    if (role === 'STUDENT') {
      if (order.status === 0) {
        buttons.push(
          <Popconfirm key="cancel" title="确定取消？" onConfirm={() => doAction(6, 'STUDENT', '已取消')}>
            <Button danger loading={actionLoading}>取消订单</Button>
          </Popconfirm>
        );
      }
      if (order.status === 4) {
        buttons.push(
          <Popconfirm key="confirm" title="确认已收到？" onConfirm={() => doAction(5, 'STUDENT', '已确认收货')}>
            <Button type="primary" icon={<CheckCircleOutlined />} loading={actionLoading}>确认收货</Button>
          </Popconfirm>
        );
      }
      if (order.status === 5 && !alreadyReviewed) {
        buttons.push(
          <Button key="review" type="primary" icon={<StarOutlined />} onClick={() => setReviewModalOpen(true)}>
            评价跑腿员
          </Button>
        );
      }
      if (order.status === 5 && alreadyReviewed) {
        buttons.push(<Tag key="done" color="green">已评价 ✓</Tag>);
      }
    }
    if (role === 'RUNNER') {
      if (order.status === 1) buttons.push(<Button key="go" type="primary" loading={actionLoading} onClick={() => doAction(2, 'RUNNER', '前往取件')}>前往取件</Button>);
      if (order.status === 2) buttons.push(<Popconfirm key="picked" title="已取到？" onConfirm={() => doAction(3, 'RUNNER', '配送中')}><Button type="primary" loading={actionLoading}>已取件，配送</Button></Popconfirm>);
      if (order.status === 3) buttons.push(<Popconfirm key="deliver" title="已送达？" onConfirm={() => doAction(4, 'RUNNER', '已送达')}><Button type="primary" style={{ background: '#52c41a', borderColor: '#52c41a' }} loading={actionLoading}>确认送达</Button></Popconfirm>);
    }
    if (role === 'ADMIN' && order.status !== 5 && order.status !== 6) {
      buttons.push(<Popconfirm key="force" title="强制取消？" onConfirm={() => doAction(6, 'ADMIN', '已强制取消')}><Button danger loading={actionLoading}>强制取消</Button></Popconfirm>);
    }
    return buttons;
  };

  const actionButtons = renderActions();

  return (
    <div>
      <Button icon={<ArrowLeftOutlined />} style={{ marginBottom: 16 }} onClick={() => navigate(`/${rolePrefix}/orders`)}>
        返回订单列表
      </Button>

      {order.status !== 6 && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Steps size="small" current={currentStepIndex} status="process"
            items={STATUS_STEPS.map((s) => ({ title: s.title }))} />
        </Card>
      )}

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
          <Descriptions.Item label="订单类型">{typeItem ? `${typeItem.icon} ${typeItem.label}` : '—'}</Descriptions.Item>
          {canSeeTitle && <Descriptions.Item label="标题" span={2}>{order.title || '—'}</Descriptions.Item>}
          <Descriptions.Item label="状态">{statusItem ? <Tag color={statusItem.color}>{statusItem.label}</Tag> : '—'}</Descriptions.Item>
          <Descriptions.Item label="下单人">{order.student?.name || '—'}</Descriptions.Item>
          <Descriptions.Item label="跑腿员">{order.runner?.name || '—'}</Descriptions.Item>
          <Descriptions.Item label="取件地址">{order.pickupLocation}</Descriptions.Item>
          <Descriptions.Item label="送达地址">{order.deliveryLocation}</Descriptions.Item>
          <Descriptions.Item label="金额">¥{order.amount?.toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="小费">¥{(order.tip ?? 0)?.toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="包裹明细" span={2}>
            {packages.length > 0 ? (
              <Space wrap>{packages.map((pkg, idx) => {
                const info = PACKAGE_SIZE_MAP[pkg.size];
                return <Tag key={idx} color="blue">包裹{idx + 1}：{info ? `${info.label}（${info.size}）¥${info.price}` : pkg.size}</Tag>;
              })}</Space>
            ) : '—'}
          </Descriptions.Item>
          <Descriptions.Item label="创建时间">{order.createdAt ? dayjs(order.createdAt).format('YYYY-MM-DD HH:mm:ss') : '—'}</Descriptions.Item>
          <Descriptions.Item label="取件码">
            {canSeePickupCode ? (order.pickupCode || '—') : <span style={{ color: '#999' }}>接单后可见</span>}
          </Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="订单追踪">
        {tracking.length > 0 ? (
          <Timeline items={tracking.map((r) => ({
            children: <div><div>{r.description}</div><div style={{ color: '#999', fontSize: 12 }}>{dayjs(r.createdAt).format('MM-DD HH:mm')}</div></div>,
            color: r.statusTo === 5 ? 'green' : r.statusTo === 6 ? 'red' : 'blue',
          }))} />
        ) : <Empty description="暂无追踪记录" />}
      </Card>

      {/* ---- Review Modal ---- */}
      <Modal
        title="评价跑腿员"
        open={reviewModalOpen}
        onOk={handleSubmitReview}
        onCancel={() => setReviewModalOpen(false)}
        confirmLoading={reviewSubmitting}
        destroyOnClose
      >
        <div style={{ textAlign: 'center', marginBottom: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 600 }}>评分</div>
          <Rate value={reviewRating} onChange={setReviewRating} style={{ fontSize: 32 }} />
          <div style={{ color: '#999', marginTop: 4 }}>{['', '很差', '较差', '一般', '较好', '非常好'][reviewRating]}</div>
        </div>
        <div style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 600 }}>快捷标签</div>
          <Space wrap>
            {TAG_OPTIONS.map((t) => (
              <Tag.CheckableTag
                key={t}
                checked={reviewTags.includes(t)}
                onChange={(checked) => setReviewTags(checked ? [...reviewTags, t] : reviewTags.filter((x) => x !== t))}
              >
                {t}
              </Tag.CheckableTag>
            ))}
          </Space>
        </div>
        <div>
          <div style={{ marginBottom: 8, fontWeight: 600 }}>评价内容（选填）</div>
          <TextArea rows={3} value={reviewContent} onChange={(e) => setReviewContent(e.target.value)} placeholder="说说这次服务的感受吧..." />
        </div>
      </Modal>
    </div>
  );
};

export default OrderDetail;
