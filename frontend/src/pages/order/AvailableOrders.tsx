import React, { useEffect, useState, useCallback } from 'react';
import { Table, Button, Tag, Space, Card, message, Popconfirm, Select } from 'antd';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { getOrders, updateOrderStatus } from '../../api/order';
import { ORDER_TYPE_MAP, ORDER_STATUS_MAP, ORDER_TYPE_OPTIONS } from '../../utils/dict';
import { useAuth } from '../../contexts/AuthContext';

interface OrderRecord {
  id: number;
  orderNo: string;
  orderType: number;
  title: string;
  amount: number;
  tip: number;
  pickupLocation: string;
  deliveryLocation: string;
  packages?: string;
  status: number;
  createdAt: string;
}

const AvailableOrders: React.FC = () => {
  const navigate = useNavigate();
  const [dataSource, setDataSource] = useState<OrderRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [orderTypeFilter, setOrderTypeFilter] = useState<number | undefined>(undefined);

  const fetchOrders = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: pagination.current,
        size: pagination.pageSize,
        status: 0, // only pending orders
      };
      if (orderTypeFilter !== undefined) params.orderType = orderTypeFilter;

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
  }, [pagination.current, pagination.pageSize, orderTypeFilter]);

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

  const handleAccept = async (orderId: number) => {
    try {
      await updateOrderStatus(orderId, {
        newStatus: 1,
      });
      message.success('接单成功！');
      fetchOrders();
    } catch {
      // error handled by interceptor
    }
  };

  // Parse packages JSON for display
  const renderPackages = (packagesJson?: string) => {
    if (!packagesJson) return '—';
    try {
      const pkgs = JSON.parse(packagesJson) as { size: string }[];
      const labels: Record<string, string> = { small: '小件', medium: '中件', large: '大件' };
      return pkgs.map((p, i) => (
        <Tag key={i} color="#87A8D0">
          {labels[p.size] || p.size}
        </Tag>
      ));
    } catch {
      return '—';
    }
  };

  const columns = [
    {
      title: '订单编号',
      dataIndex: 'orderNo',
      key: 'orderNo',
      width: 170,
    },
    {
      title: '类型',
      dataIndex: 'orderType',
      key: 'orderType',
      width: 110,
      render: (val: number) => {
        const item = ORDER_TYPE_MAP[val];
        return item ? `${item.icon} ${item.label}` : '—';
      },
    },
    {
      title: '包裹',
      dataIndex: 'packages',
      key: 'packages',
      width: 160,
      render: (_: unknown, record: OrderRecord) => renderPackages(record.packages),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 90,
      render: (val: number) => `¥${val?.toFixed(2)}`,
    },
    {
      title: '小费',
      dataIndex: 'tip',
      key: 'tip',
      width: 80,
      render: (val: number) => (val ? `¥${val.toFixed(2)}` : '—'),
    },
    {
      title: '取件地址',
      dataIndex: 'pickupLocation',
      key: 'pickupLocation',
      width: 140,
    },
    {
      title: '送达地址',
      dataIndex: 'deliveryLocation',
      key: 'deliveryLocation',
      width: 140,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (val: string) => (val ? dayjs(val).format('MM-DD HH:mm') : '—'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right' as const,
      render: (_: unknown, record: OrderRecord) => (
        <Space>
          <a onClick={() => navigate(`/runner/orders/${record.id}`)}>详情</a>
          <Popconfirm title="确认接单？" onConfirm={() => handleAccept(record.id)}>
            <Button type="primary" size="small" style={{ background: '#5B8C6B', borderColor: '#5B8C6B' }}>接单</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card title="可接订单">
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Space>
          <Select
            placeholder="订单类型"
            allowClear
            style={{ width: 160 }}
            options={ORDER_TYPE_OPTIONS}
            value={orderTypeFilter}
            onChange={(val) => setOrderTypeFilter(val)}
          />
          <Button type="primary" onClick={() => fetchOrders()}>
            刷新
          </Button>
        </Space>
        <Tag color="#5B8C6B">{pagination.total} 个待接订单</Tag>
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={dataSource}
        loading={loading}
        scroll={{ x: 1300 }}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 个可接订单`,
        }}
        onChange={handleTableChange}
      />
    </Card>
  );
};

export default AvailableOrders;
