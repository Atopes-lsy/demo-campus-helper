import React, { useEffect, useState, useCallback } from 'react';
import { Card, Row, Col, Statistic, Table, Spin, Tag } from 'antd';
import {
  DollarOutlined,
  CheckCircleOutlined,
  StarOutlined,
  RiseOutlined,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { getRunnerEarnings } from '../../api/runner';
import { useAuth } from '../../contexts/AuthContext';

interface RecentOrder {
  id: number;
  orderNo: string;
  amount: number;
  completedAt: string;
}

interface EarningsData {
  runnerName: string;
  rating: number;
  totalOrders: number;
  totalEarnings: number;
  todayEarnings: number;
  recentOrders: RecentOrder[];
}

const EarningsDashboard: React.FC = () => {
  const { userId } = useAuth();
  const [data, setData] = useState<EarningsData | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getRunnerEarnings(userId);
      const earningsData = res.data || res;
      setData(earningsData);
    } catch {
      // error handled by interceptor
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading) {
    return <Spin size="large" style={{ display: 'block', margin: '80px auto' }} />;
  }

  if (!data) {
    return <div style={{ textAlign: 'center', padding: 80, color: '#999' }}>暂无数据</div>;
  }

  const recentColumns = [
    {
      title: '订单编号',
      dataIndex: 'orderNo',
      key: 'orderNo',
      width: 180,
    },
    {
      title: '金额',
      dataIndex: 'amount',
      key: 'amount',
      width: 120,
      render: (val: number) => <span style={{ fontWeight: 600, color: '#1677ff' }}>¥{val?.toFixed(2)}</span>,
    },
    {
      title: '完成时间',
      dataIndex: 'completedAt',
      key: 'completedAt',
      width: 180,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>
        {data.runnerName} — 收入统计
        <Tag color="gold" style={{ marginLeft: 12 }}>
          <StarOutlined /> {data.rating?.toFixed(1)}
        </Tag>
      </h2>

      <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="总完成单数"
              value={data.totalOrders}
              prefix={<CheckCircleOutlined />}
              suffix="单"
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="总收入"
              value={data.totalEarnings}
              precision={2}
              prefix={<DollarOutlined />}
              suffix="元"
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="今日收入"
              value={data.todayEarnings}
              precision={2}
              prefix={<RiseOutlined />}
              suffix="元"
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card>
            <Statistic
              title="评分"
              value={data.rating}
              precision={1}
              prefix={<StarOutlined />}
              suffix="/ 5.0"
            />
          </Card>
        </Col>
      </Row>

      <Card title="最近完成的订单">
        <Table
          rowKey="id"
          columns={recentColumns}
          dataSource={data.recentOrders || []}
          loading={loading}
          pagination={{ pageSize: 20, hideOnSinglePage: true }}
          locale={{ emptyText: '暂无已完成订单' }}
        />
      </Card>
    </div>
  );
};

export default EarningsDashboard;
