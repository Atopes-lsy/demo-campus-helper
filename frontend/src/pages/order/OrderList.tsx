import React, { useEffect, useState, useCallback } from 'react';
import { Table, Button, Select, Input, Tag, Space, Card, message, Popconfirm } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { getOrders, updateOrderStatus } from '../../api/order';
import { ORDER_TYPE_MAP, ORDER_STATUS_MAP, ORDER_TYPE_OPTIONS, ORDER_STATUS_OPTIONS } from '../../utils/dict';
import { useAuth } from '../../contexts/AuthContext';

const { Search } = Input;

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
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '—'),
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
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={handleTableChange}
      />
    </Card>
  );
};

export default OrderList;
