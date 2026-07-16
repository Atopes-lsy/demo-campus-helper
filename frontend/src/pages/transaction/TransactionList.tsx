import React, { useEffect, useState, useCallback } from 'react';
import { Table, Tag, Space, Card, Select, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { getPayments } from '../../api/payment';
import { PAY_STATUS_MAP } from '../../utils/dict';

const { Search } = Input;

interface PaymentRecord {
  id: number;
  paymentNo: string;
  orderId: number;
  payerId: number;
  payeeId: number;
  amount: number;
  payStatus: number;
  payChannel: string;
  transactionId: string;
  paidAt: string;
  settledAt: string;
  createdAt: string;
}

const TransactionList: React.FC = () => {
  const navigate = useNavigate();
  const [dataSource, setDataSource] = useState<PaymentRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [payStatusFilter, setPayStatusFilter] = useState<number | undefined>(undefined);

  const payStatusOptions = Object.entries(PAY_STATUS_MAP)
    .map(([v, { label }]) => ({ value: Number(v), label }));

  const fetchPayments = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: pagination.current,
        size: pagination.pageSize,
      };
      if (payStatusFilter !== undefined) params.payStatus = payStatusFilter;

      const res = await getPayments(params as any);
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
  }, [pagination.current, pagination.pageSize, payStatusFilter]);

  useEffect(() => {
    fetchPayments();
  }, [fetchPayments]);

  const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
    setPagination((prev) => ({
      ...prev,
      current: pag.current || prev.current,
      pageSize: pag.pageSize || prev.pageSize,
    }));
  };

  const columns = [
    {
      title: '支付单号',
      dataIndex: 'paymentNo',
      key: 'paymentNo',
      width: 170,
    },
    {
      title: '关联订单',
      dataIndex: 'orderId',
      key: 'orderId',
      width: 120,
      render: (val: number) => (
        <a onClick={() => navigate(`/admin/orders/${val}`)}>#{val}</a>
      ),
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 100,
      render: (val: number) => `¥${val?.toFixed(2)}`,
    },
    {
      title: '付款人',
      dataIndex: 'payerId',
      key: 'payerId',
      width: 100,
      render: (val: number) => `ID:${val}`,
    },
    {
      title: '收款人',
      dataIndex: 'payeeId',
      key: 'payeeId',
      width: 100,
      render: (val: number) => `ID:${val}`,
    },
    {
      title: '支付状态',
      dataIndex: 'payStatus',
      key: 'payStatus',
      width: 100,
      render: (val: number) => {
        const item = PAY_STATUS_MAP[val];
        return item ? <Tag color={item.color}>{item.label}</Tag> : '—';
      },
    },
    {
      title: '支付渠道',
      dataIndex: 'payChannel',
      key: 'payChannel',
      width: 100,
      render: (val: string) => val || '—',
    },
    {
      title: '流水号',
      dataIndex: 'transactionId',
      key: 'transactionId',
      width: 160,
      ellipsis: true,
      render: (val: string) => val || '—',
    },
    {
      title: '支付时间',
      dataIndex: 'paidAt',
      key: 'paidAt',
      width: 160,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
  ];

  return (
    <Card title="后台流水">
      <div style={{ marginBottom: 16 }}>
        <Space wrap>
          <Select
            placeholder="支付状态"
            allowClear
            style={{ width: 140 }}
            options={payStatusOptions}
            value={payStatusFilter}
            onChange={(val) => setPayStatusFilter(val)}
          />
          <Search
            placeholder="搜索支付单号"
            allowClear
            style={{ width: 240 }}
            onSearch={(val) => {
              // Filtering by keyword on the server side would need API support
              // For now, this triggers a refresh
              fetchPayments();
            }}
          />
        </Space>
      </div>
      <Table
        rowKey="id"
        columns={columns}
        dataSource={dataSource}
        loading={loading}
        scroll={{ x: 1400 }}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条支付记录`,
        }}
        onChange={handleTableChange}
      />
    </Card>
  );
};

export default TransactionList;
