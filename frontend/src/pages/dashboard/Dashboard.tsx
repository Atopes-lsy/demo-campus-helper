import React, { useEffect, useState, useCallback } from 'react';
import { Card, Row, Col, Statistic, Spin, Radio, Empty } from 'antd';
import {
  ShoppingCartOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  DollarOutlined,
} from '@ant-design/icons';
import { Line } from '@ant-design/charts';
import { getOverview, getRevenueTrend } from '../../api/statistics';

interface OverviewData {
  todayOrderCount?: number;
  onlineRunnerCount?: number;
  completionRate?: number;
  todayRevenue?: number;
}

interface TrendPoint {
  date: string;
  revenue: number;
  orderCount: number;
}

const PERIOD_OPTIONS = [
  { label: '今日', value: 'day' },
  { label: '近7天', value: 'week' },
  { label: '近30天', value: 'month' },
];

const Dashboard: React.FC = () => {
  const [overview, setOverview] = useState<OverviewData>({});
  const [loading, setLoading] = useState(false);
  const [period, setPeriod] = useState('week');
  const [trendData, setTrendData] = useState<TrendPoint[]>([]);
  const [trendLoading, setTrendLoading] = useState(false);

  useEffect(() => {
    const fetchOverview = async () => {
      setLoading(true);
      try {
        const res = await getOverview();
        const data = res.data || res;
        setOverview(data);
      } finally {
        setLoading(false);
      }
    };
    fetchOverview();
  }, []);

  const fetchTrend = useCallback(async (p: string) => {
    setTrendLoading(true);
    try {
      const res = await getRevenueTrend(p);
      const list = (res.data || res) as TrendPoint[];
      // Convert revenue from string/decimal to number if needed
      setTrendData(
        list.map((item) => ({
          ...item,
          revenue: typeof item.revenue === 'string' ? parseFloat(item.revenue) : item.revenue,
        }))
      );
    } finally {
      setTrendLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchTrend(period);
  }, [period, fetchTrend]);

  const hasChartData = trendData.some((d) => d.revenue > 0 || d.orderCount > 0);

  const lineConfig = {
    data: trendData,
    xField: 'date',
    yField: 'revenue',
    smooth: true,
    point: { size: 3, shape: 'circle' },
    lineStyle: { lineWidth: 2 },
    color: '#5B8C6B',
    areaStyle: {
      fill: 'l(270) 0:#ffffff 1:#5B8C6B1a',
    },
    tooltip: {
      formatter: (datum: TrendPoint) => ({
        name: '流水',
        value: `¥${Number(datum.revenue).toFixed(2)}`,
      }),
    },
    yAxis: {
      title: { text: '流水 (¥)', style: { fontSize: 12 } },
      label: { formatter: (v: string) => `¥${v}` },
    },
    xAxis: {
      label: {
        autoRotate: true,
        autoHide: false,
        style: { fontSize: 11 },
      },
    },
    interactions: [{ type: 'tooltip', enable: true }],
    animation: { appear: { animation: 'wave-in', duration: 800 } },
    height: 320,
  };

  const orderCountConfig = {
    ...lineConfig,
    yField: 'orderCount' as const,
    color: '#3D6B4A',
    yAxis: {
      title: { text: '订单数', style: { fontSize: 12 } },
      label: {},
    },
    areaStyle: {
      fill: 'l(270) 0:#ffffff 1:#3D6B4A1a',
    },
    tooltip: {
      formatter: (datum: TrendPoint) => ({
        name: '订单数',
        value: `${datum.orderCount} 单`,
      }),
    },
  };

  return (
    <Spin spinning={loading}>
      {/* ---- Overview cards ---- */}
      <Card title="运营数据看板" style={{ marginBottom: 16 }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="今日订单"
                value={overview.todayOrderCount ?? 0}
                prefix={<ShoppingCartOutlined />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="在线跑腿员"
                value={overview.onlineRunnerCount ?? 0}
                prefix={<TeamOutlined />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="完成率(%)"
                value={overview.completionRate ?? 0}
                suffix="%"
                precision={1}
                prefix={<CheckCircleOutlined />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} md={6}>
            <Card>
              <Statistic
                title="今日流水(¥)"
                value={overview.todayRevenue ?? 0}
                precision={2}
                prefix={<DollarOutlined />}
              />
            </Card>
          </Col>
        </Row>
      </Card>

      {/* ---- Revenue trend chart ---- */}
      <Card
        title="流水趋势分析"
        extra={
          <Radio.Group
            value={period}
            onChange={(e) => setPeriod(e.target.value)}
            options={PERIOD_OPTIONS}
            optionType="button"
            buttonStyle="solid"
            size="small"
          />
        }
      >
        <Spin spinning={trendLoading}>
          {hasChartData ? (
            <Row gutter={[16, 16]}>
              <Col xs={24} lg={12}>
                <div style={{ marginBottom: 8, fontWeight: 500, fontSize: 14, color: '#5B8C6B' }}>
                  💰 流水金额
                </div>
                <Line {...lineConfig} />
              </Col>
              <Col xs={24} lg={12}>
                <div style={{ marginBottom: 8, fontWeight: 500, fontSize: 14, color: '#3D6B4A' }}>
                  📦 完成订单数
                </div>
                <Line {...orderCountConfig} />
              </Col>
            </Row>
          ) : (
            <Empty
              description={
                period === 'day'
                  ? '今日暂无已完成订单'
                  : period === 'week'
                  ? '近7天暂无已完成订单'
                  : '近30天暂无已完成订单'
              }
              style={{ padding: 40 }}
            />
          )}
        </Spin>
      </Card>
    </Spin>
  );
};

export default Dashboard;
