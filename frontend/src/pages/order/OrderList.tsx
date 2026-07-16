import React, { useEffect, useState, useCallback, useRef } from 'react';
import { Table, Button, Select, Input, Tag, Space, Card, message, Popconfirm, Modal, Rate } from 'antd';
import { PlusOutlined, SearchOutlined, StarOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { getOrders, updateOrderStatus } from '../../api/order';
import { submitReview } from '../../api/review';
import { ORDER_TYPE_MAP, ORDER_STATUS_MAP, ORDER_TYPE_OPTIONS, ORDER_STATUS_OPTIONS } from '../../utils/dict';
import { useAuth } from '../../contexts/AuthContext';

const { Search } = Input;
const { TextArea } = Input;

const TAG_OPTIONS = ['快速', '准时', '态度好', '细心', '沟通顺畅'];

export type OrderListMode = 'student-own' | 'runner-deliveries' | 'admin-all';

interface OrderRecord {
  id: number;
  orderNo: string;
  orderType: number;
  title: string;
  amount: number;
  status: number;
  runnerId?: number;
  studentId?: number;
  runner?: { name: string };
  student?: { name: string };
  createdAt: string;
}

interface Props {
  mode: OrderListMode;
}

const OrderList: React.FC<Props> = ({ mode }) => {
  const navigate = useNavigate();
  const { role, userId } = useAuth();
  const [dataSource, setDataSource] = useState<OrderRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [filters, setFilters] = useState<{ orderType?: number; status?: number; keyword?: string }>({});

  // Review modal state
  const [reviewModalOpen, setReviewModalOpen] = useState(false);
  const [reviewOrderId, setReviewOrderId] = useState<number | null>(null);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewTags, setReviewTags] = useState<string[]>([]);
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const reviewedIds = useRef(new Set<number>());

  // Determine which role-specific pages to navigate to
  const rolePrefix = role === 'STUDENT' ? 'student' : role === 'RUNNER' ? 'runner' : 'admin';

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: pagination.current,
        size: pagination.pageSize,
      };

      // Auto-filters based on mode
      if (mode === 'student-own') {
        params.studentId = userId;
      } else if (mode === 'runner-deliveries') {
        params.runnerId = userId;
      }
      // admin-all: no auto-filter

      if (filters.orderType !== undefined) params.orderType = filters.orderType;
      if (filters.status !== undefined) params.status = filters.status;
      if (filters.keyword) params.keyword = filters.keyword;

      const res = await getOrders(params as any);
      const data = res.data || res;
      setDataSource(data.records || data.list || []);
      setPagination((prev) => ({
        ...prev,
        total: data.total || 0,
      }));
    } catch {
      // error handled by interceptor
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize, filters, mode, userId]);

  useEffect(() => {
    fetchOrders();
  }, [fetchOrders]);

  // Auto-refresh for students & runners — use ref for stable polling
  const fetchOrdersRef = useRef(fetchOrders);
  fetchOrdersRef.current = fetchOrders;

  const needsPolling = mode === 'student-own' || mode === 'runner-deliveries';
  useEffect(() => {
    if (!needsPolling) return;
    // Immediate first poll after 1s (in case initial fetch missed data)
    const id1 = setTimeout(() => { fetchOrdersRef.current(); }, 1000);
    const timer = setInterval(() => { fetchOrdersRef.current(); }, 2000);
    return () => { clearTimeout(id1); clearInterval(timer); };
  }, [needsPolling]);

  const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
    setPagination((prev) => ({
      ...prev,
      current: pag.current || prev.current,
      pageSize: pag.pageSize || prev.pageSize,
    }));
  };

  // ---- Student actions ----
  const handleCancel = async (id: number) => {
    try {
      await updateOrderStatus(id, { newStatus: 6, operatorId: userId, operatorType: 'STUDENT' });
      message.success('订单已取消');
      fetchOrders();
    } catch {
      // error handled by interceptor
    }
  };

  const handleConfirmReceipt = async (id: number) => {
    try {
      await updateOrderStatus(id, { newStatus: 5, operatorId: userId, operatorType: 'STUDENT' });
      message.success('已确认收货');
      fetchOrders();
    } catch {
      // error handled by interceptor
    }
  };

  // ---- Runner actions ----
  const handlePickedUp = async (id: number) => {
    try {
      await updateOrderStatus(id, { newStatus: 3, operatorId: userId, operatorType: 'RUNNER' });
      message.success('已标记为配送中');
      fetchOrders();
    } catch {
      // error handled by interceptor
    }
  };

  const handleDelivered = async (id: number) => {
    try {
      await updateOrderStatus(id, { newStatus: 4, operatorId: userId, operatorType: 'RUNNER' });
      message.success('已标记为已送达');
      fetchOrders();
    } catch {
      // error handled by interceptor
    }
  };

  // ---- Review actions ----
  const handleOpenReview = (orderId: number) => {
    setReviewOrderId(orderId);
    setReviewRating(5);
    setReviewContent('');
    setReviewTags([]);
    setReviewModalOpen(true);
  };

  const handleSubmitReview = async () => {
    if (!reviewOrderId) return;
    setReviewSubmitting(true);
    try {
      await submitReview({
        orderId: reviewOrderId, reviewerType: 1, rating: reviewRating,
        content: reviewContent || '', tags: reviewTags.join(','),
      });
      message.success('评分成功！');
      reviewedIds.current.add(reviewOrderId);
      setReviewModalOpen(false);
      fetchOrders();
    } catch { /* duplicate or error — already handled */ }
    finally { setReviewSubmitting(false); }
  };

  // ---- Admin actions ----
  const handleForceCancel = async (id: number) => {
    try {
      await updateOrderStatus(id, { newStatus: 6, operatorId: userId, operatorType: 'ADMIN' });
      message.success('订单已被管理员强制取消');
      fetchOrders();
    } catch {
      // error handled by interceptor
    }
  };

  const columns = [
    {
      title: '订单编号',
      dataIndex: 'orderNo',
      key: 'orderNo',
      width: 160,
    },
    {
      title: '订单类型',
      dataIndex: 'orderType',
      key: 'orderType',
      width: 120,
      render: (val: number) => {
        const item = ORDER_TYPE_MAP[val];
        return item ? `${item.icon} ${item.label}` : '—';
      },
    },
    // Title: hidden for student-own, visible for runner-deliveries and admin-all
    ...(mode !== 'student-own'
      ? [
          {
            title: '标题',
            dataIndex: 'title' as const,
            key: 'title',
            ellipsis: true,
          },
        ]
      : []),
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 100,
      render: (val: number) => `¥${val?.toFixed(2)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (val: number) => {
        const item = ORDER_STATUS_MAP[val];
        return item ? <Tag color={item.color}>{item.label}</Tag> : '—';
      },
    },
    // Student name column — admin only
    ...(mode === 'admin-all'
      ? [
          {
            title: '下单人',
            key: 'student' as const,
            width: 100,
            render: (_: unknown, record: OrderRecord) =>
              record.student?.name || `ID:${record.studentId}` || '—',
          },
        ]
      : []),
    {
      title: '跑腿员',
      key: 'runner',
      width: 100,
      render: (_: unknown, record: OrderRecord) =>
        record.runner?.name || '—',
    },
    {
      title: '预估送达',
      key: 'estimated',
      width: 110,
      render: (_: unknown, record: OrderRecord) => {
        if (!record.createdAt) return '—';
        const estimated = dayjs(record.createdAt).add(30, 'minute');
        const isLate = record.status < 5 && dayjs().isAfter(estimated);
        return <span style={{ color: isLate ? 'red' : undefined, fontWeight: isLate ? 600 : undefined }}>
          {estimated.format('MM-DD HH:mm')}{isLate ? ' ⚠' : ''}
        </span>;
      },
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (val: string) => (val ? dayjs(val).format('MM-DD HH:mm') : '—'),
    },
    {
      title: '操作',
      key: 'actions',
      width: mode === 'runner-deliveries' ? 200 : 160,
      fixed: 'right' as const,
      render: (_: unknown, record: OrderRecord) => (
        <Space>
          <a onClick={() => navigate(`/${rolePrefix}/orders/${record.id}`)}>详情</a>

          {/* Student actions */}
          {mode === 'student-own' && record.status === 0 && (
            <Popconfirm title="确定取消该订单？" onConfirm={() => handleCancel(record.id)}>
              <a style={{ color: 'red' }}>取消</a>
            </Popconfirm>
          )}
          {mode === 'student-own' && record.status === 4 && (
            <Popconfirm title="确认收货？" onConfirm={() => handleConfirmReceipt(record.id)}>
              <a style={{ color: 'green' }}>确认收货</a>
            </Popconfirm>
          )}
          {mode === 'student-own' && record.status === 5 && !reviewedIds.current.has(record.id) && (
            <a style={{ color: '#faad14', fontWeight: 600 }}
               onClick={() => handleOpenReview(record.id)}>
              <StarOutlined /> 评分
            </a>
          )}

          {/* Runner actions */}
          {mode === 'runner-deliveries' && record.status === 1 && (
            <Popconfirm title="确认已取件？" onConfirm={() => handlePickedUp(record.id)}>
              <a style={{ color: '#1677ff' }}>待取件</a>
            </Popconfirm>
          )}
          {mode === 'runner-deliveries' && record.status === 3 && (
            <Popconfirm title="确认已送达？" onConfirm={() => handleDelivered(record.id)}>
              <a style={{ color: 'green' }}>已送达</a>
            </Popconfirm>
          )}

          {/* Admin actions */}
          {mode === 'admin-all' && record.status !== 5 && record.status !== 6 && (
            <Popconfirm title="强制取消该订单？" onConfirm={() => handleForceCancel(record.id)}>
              <a style={{ color: 'red' }}>强制取消</a>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const cardTitle =
    mode === 'student-own'
      ? '我的订单'
      : mode === 'runner-deliveries'
        ? '我的配送'
        : '全部订单管理';

  return (
    <Card title={cardTitle}>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Space wrap>
          <Select
            placeholder="订单类型"
            allowClear
            style={{ width: 160 }}
            options={ORDER_TYPE_OPTIONS}
            value={filters.orderType}
            onChange={(val) => setFilters((prev) => ({ ...prev, orderType: val }))}
          />
          <Select
            placeholder="订单状态"
            allowClear
            style={{ width: 140 }}
            options={ORDER_STATUS_OPTIONS}
            value={filters.status}
            onChange={(val) => setFilters((prev) => ({ ...prev, status: val }))}
          />
          <Search
            placeholder="搜索关键词"
            allowClear
            style={{ width: 240 }}
            enterButton={<SearchOutlined />}
            value={filters.keyword}
            onChange={(e) => setFilters((prev) => ({ ...prev, keyword: e.target.value }))}
            onSearch={(val) => setFilters((prev) => ({ ...prev, keyword: val }))}
          />
          <Button type="primary" icon={<SearchOutlined />} onClick={() => fetchOrders()}>
            查询
          </Button>
        </Space>
        {mode === 'student-own' && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/student/orders/new')}>
            新建订单
          </Button>
        )}
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={dataSource}
        loading={loading}
        scroll={{ x: 1200 }}
        rowClassName={(record: OrderRecord) =>
          mode === 'student-own' && record.status === 4 ? 'row-delivered' : ''
        }
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={handleTableChange}
      />

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
              <Tag.CheckableTag key={t} checked={reviewTags.includes(t)}
                onChange={(c) => setReviewTags(c ? [...reviewTags, t] : reviewTags.filter((x) => x !== t))}>
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
    </Card>
  );
};

export default OrderList;
