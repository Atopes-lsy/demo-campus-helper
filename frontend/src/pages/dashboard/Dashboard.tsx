import React, { useEffect, useState, useCallback } from 'react';
import { Card, Row, Col, Statistic, Spin, Empty, Table, Tag, Segmented } from 'antd';
import {
  ShoppingCartOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  WarningOutlined,
  RiseOutlined,
  TrophyOutlined,
} from '@ant-design/icons';
import { Line, Column, Pie } from '@ant-design/charts';
import { getBIOverview, getBIDashboard, getBIRunnerRanking } from '../../api/bi';
import { ORDER_STATUS_MAP, ORDER_TYPE_MAP } from '../../utils/dict';

// ── Types ───────────────────────────────────────────

interface OverviewData {
  todayOrderCount?: number;
  onlineRunnerCount?: number;
  completedToday?: number;
  completionRate?: number;
  todayRevenue?: number;
  unresolvedAnomalies?: number;
  weekTrend?: TrendPoint[];
}

interface TrendPoint {
  date: string;
  revenue: number;
  orderCount: number;
  tipTotal?: number;
}

interface StatusBucket {
  status: number;
  count: number;
}

interface TypeBucket {
  orderType: number;
  count: number;
}

interface HourlyBucket {
  hour: string;
  count: number;
}

interface DashboardData {
  totalRevenue?: number;
  avgOrderAmount?: number;
  byStatus?: StatusBucket[];
  byType?: TypeBucket[];
  dailyTrend?: TrendPoint[];
  hourlyPeak?: HourlyBucket[];
  onlineRunners?: number;
}

interface RunnerItem {
  runnerId: number;
  name: string;
  rating: number;
  completedOrders: number;
  totalEarnings: number;
  status: number;
}

const PERIOD_OPTIONS = [
  { label: '7天', value: 7 },
  { label: '30天', value: 30 },
  { label: '90天', value: 90 },
];

// ── Chart color palette ──────────────────────────────

const GREEN = '#5B8C6B';
const GREEN_DARK = '#3D6B4A';
const GREEN_LIGHT = '#7CB88B';
const CHART_HEIGHT = 300;

// ── Component ────────────────────────────────────────

const Dashboard: React.FC = () => {
  const [overview, setOverview] = useState<OverviewData>({});
  const [overviewLoading, setOverviewLoading] = useState(false);

  const [days, setDays] = useState(30);
  const [dashData, setDashData] = useState<DashboardData>({});
  const [dashLoading, setDashLoading] = useState(false);

  const [runners, setRunners] = useState<RunnerItem[]>([]);
  const [runnersLoading, setRunnersLoading] = useState(false);

  // ── Fetch KPI overview ──────────────────────────────

  useEffect(() => {
    const fetchOverview = async () => {
      setOverviewLoading(true);
      try {
        const res = await getBIOverview();
        const data = res.data || res;
        setOverview(data);
      } finally {
        setOverviewLoading(false);
      }
    };
    fetchOverview();
  }, []);

  // ── Fetch dashboard data on period change ───────────

  const fetchDash = useCallback(async (d: number) => {
    setDashLoading(true);
    try {
      const res = await getBIDashboard(d);
      setDashData((res.data || res) as DashboardData);
    } finally {
      setDashLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDash(days);
  }, [days, fetchDash]);

  // ── Fetch runner ranking ────────────────────────────

  useEffect(() => {
    const fetchRunners = async () => {
      setRunnersLoading(true);
      try {
        const res = await getBIRunnerRanking(10);
        const data = res.data || res;
        setRunners(data?.ranking || []);
      } finally {
        setRunnersLoading(false);
      }
    };
    fetchRunners();
  }, []);

  // ── Chart: Daily revenue trend ──────────────────────

  const trendData: TrendPoint[] = dashData.dailyTrend || [];

  const revenueLineConfig = {
    data: trendData,
    xField: 'date',
    yField: 'revenue',
    smooth: true,
    point: { size: 3, shape: 'circle' as const },
    lineStyle: { lineWidth: 2 },
    color: GREEN,
    areaStyle: { fill: 'l(270) 0:#ffffff 1:#5B8C6B1a' },
    tooltip: {
      formatter: (d: TrendPoint) => ({ name: '流水', value: `¥${Number(d.revenue).toFixed(2)}` }),
    },
    yAxis: { title: { text: '流水 (¥)', style: { fontSize: 12 } }, label: { formatter: (v: string) => `¥${v}` } },
    xAxis: { label: { autoRotate: true, style: { fontSize: 11 } } },
    animation: { appear: { animation: 'wave-in' as const, duration: 800 } },
    height: CHART_HEIGHT,
  };

  const orderCountLineConfig = {
    ...revenueLineConfig,
    yField: 'orderCount' as const,
    color: GREEN_DARK,
    areaStyle: { fill: 'l(270) 0:#ffffff 1:#3D6B4A1a' },
    yAxis: { title: { text: '订单数', style: { fontSize: 12 } } },
    tooltip: { formatter: (d: TrendPoint) => ({ name: '订单数', value: `${d.orderCount} 单` }) },
  };

  // ── Chart: Status distribution (Column) ─────────────

  const statusData = (dashData.byStatus || [])
    .sort((a, b) => a.status - b.status)
    .map((s) => ({
      name: ORDER_STATUS_MAP[s.status]?.label || `状态${s.status}`,
      value: s.count,
      color: ORDER_STATUS_MAP[s.status]?.color || '#ACB9B0',
    }));

  const statusColumnConfig = {
    data: statusData,
    xField: 'name',
    yField: 'value',
    color: ({ name }: { name: string }) => {
      const found = statusData.find((s) => s.name === name);
      return found?.color || '#ACB9B0';
    },
    columnStyle: { radius: [6, 6, 0, 0] },
    label: { position: 'top' as const, style: { fontSize: 12, fontWeight: 500 } },
    tooltip: { formatter: (d: { name: string; value: number }) => ({ name: d.name, value: `${d.value} 单` }) },
    xAxis: { label: { style: { fontSize: 11 }, autoRotate: true } },
    yAxis: { grid: { line: { style: { stroke: '#f0f0f0' } } } },
    animation: { appear: { animation: 'wave-in' as const, duration: 600 } },
    height: CHART_HEIGHT,
  };

  // ── Chart: Type distribution (Pie) ──────────────────

  const typeData = (dashData.byType || []).map((t) => ({
    type: ORDER_TYPE_MAP[t.orderType]?.label || `类型${t.orderType}`,
    value: t.count,
  }));

  const pieConfig = {
    data: typeData,
    angleField: 'value',
    colorField: 'type',
    radius: 0.8,
    innerRadius: 0.55,
    label: { type: 'outer' as const, content: '{name}\n{percentage}' },
    statistic: {
      title: { content: '订单数', style: { fontSize: 13 } },
      content: {
        content: () => {
          const total = typeData.reduce((s, d) => s + d.value, 0);
          return `${total} 单`;
        },
        style: { fontSize: 20, fontWeight: 600 },
      },
    },
    color: [GREEN, '#E8A648', '#87A8D0', '#D96C6C'],
    interactions: [{ type: 'element-selected' }, { type: 'element-active' }],
    animation: { appear: { animation: 'wave-in' as const, duration: 800 } },
    height: CHART_HEIGHT,
  };

  // ── Chart: Hourly peak (Column) ─────────────────────

  const hourlyData = (dashData.hourlyPeak || [])
    .map((h) => ({ hour: h.hour, count: h.count }))
    .sort((a, b) => parseInt(a.hour) - parseInt(b.hour));

  const hourlyColumnConfig = {
    data: hourlyData,
    xField: 'hour',
    yField: 'count',
    color: GREEN,
    columnStyle: { radius: [4, 4, 0, 0] },
    label: { position: 'top' as const, style: { fontSize: 10 } },
    tooltip: { formatter: (d: { hour: string; count: number }) => ({ name: '单量', value: `${d.count} 单` }) },
    xAxis: { title: { text: '时段', style: { fontSize: 12 } }, label: { style: { fontSize: 10 }, autoRotate: true } },
    yAxis: { grid: { line: { style: { stroke: '#f0f0f0' } } } },
    animation: { appear: { animation: 'wave-in' as const, duration: 600 } },
    height: CHART_HEIGHT,
  };

  // ── Chart: Week trend mini (from overview) ──────────

  const miniTrend = (overview.weekTrend || []).map((p) => ({
    date: p.date,
    revenue: typeof p.revenue === 'string' ? parseFloat(p.revenue as any) : p.revenue,
  }));

  const miniLineConfig = {
    data: miniTrend,
    xField: 'date',
    yField: 'revenue',
    smooth: true,
    point: { size: 2 },
    lineStyle: { lineWidth: 2 },
    color: '#fff',
    tooltip: false,
    xAxis: false,
    yAxis: false,
    padding: 0,
    height: 60,
    animation: { appear: { animation: 'wave-in' as const, duration: 500 } },
  };

  // ── Runner ranking table columns ────────────────────

  const runnerColumns = [
    { title: '#', dataIndex: 'index', key: 'index', width: 40, render: (_: any, __: any, idx: number) => idx + 1 },
    { title: '姓名', dataIndex: 'name', key: 'name' },
    {
      title: '评分',
      dataIndex: 'rating',
      key: 'rating',
      render: (v: number) => <Tag color={v >= 4.5 ? 'green' : 'gold'}>{v?.toFixed(1) || '-'}</Tag>,
    },
    { title: '完成订单', dataIndex: 'completedOrders', key: 'completedOrders' },
    {
      title: '总收入',
      dataIndex: 'totalEarnings',
      key: 'totalEarnings',
      render: (v: number) => <span style={{ fontWeight: 500 }}>¥{Number(v || 0).toFixed(2)}</span>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (v: number) => (
        <Tag color={v === 0 ? 'green' : v === 1 ? 'gold' : 'default'}>
          {v === 0 ? '空闲' : v === 1 ? '接单中' : '休息'}
        </Tag>
      ),
    },
  ];

  // ── Revenue summary stats ───────────────────────────

  const totalRevenue = dashData.totalRevenue || 0;
  const avgOrder = dashData.avgOrderAmount || 0;
  const totalOrders = trendData.reduce((s, d) => s + (d.orderCount || 0), 0);

  // ── Render ──────────────────────────────────────────

  const hasTrend = trendData.some((d) => d.revenue > 0 || d.orderCount > 0);
  const hasStatus = statusData.length > 0 && statusData.some((s) => s.value > 0);
  const hasType = typeData.length > 0 && typeData.some((t) => t.value > 0);
  const hasHourly = hourlyData.length > 0 && hourlyData.some((h) => h.count > 0);

  return (
    <Spin spinning={overviewLoading}>
      {/* ── Period selector (global) ── */}
      <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ margin: 0, fontSize: 18, fontWeight: 600, color: '#3D6B4A' }}>📊 运营 BI 看板</h2>
        <Segmented
          value={days}
          onChange={(val) => setDays(val as number)}
          options={PERIOD_OPTIONS}
        />
      </div>

      {/* ════ KPI Cards ════ */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card size="small">
            <Statistic
              title="今日订单"
              value={overview.todayOrderCount ?? 0}
              prefix={<ShoppingCartOutlined style={{ color: GREEN }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card size="small">
            <Statistic
              title="在线跑腿员"
              value={overview.onlineRunnerCount ?? 0}
              prefix={<TeamOutlined style={{ color: GREEN }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card size="small">
            <Statistic
              title="完成率"
              value={overview.completionRate ?? 0}
              suffix="%"
              precision={1}
              prefix={<CheckCircleOutlined style={{ color: GREEN }} />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card size="small">
            <Statistic
              title="今日流水"
              value={overview.todayRevenue ?? 0}
              precision={2}
              prefix="¥"
              valueStyle={{ color: GREEN }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card size="small">
            <Statistic
              title="均价"
              value={avgOrder}
              precision={2}
              prefix="¥"
              valueStyle={{ color: GREEN_DARK }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} md={8} lg={4}>
          <Card size="small">
            <Statistic
              title="未处理异常"
              value={overview.unresolvedAnomalies ?? 0}
              prefix={<WarningOutlined style={{ color: (overview.unresolvedAnomalies || 0) > 0 ? '#D96C6C' : GREEN }} />}
              valueStyle={{ color: (overview.unresolvedAnomalies || 0) > 0 ? '#D96C6C' : GREEN }}
            />
          </Card>
        </Col>
      </Row>

      {/* ════ Revenue summary bar ════ */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic title={`近${days}天总收入`} value={totalRevenue} precision={2} prefix="¥" valueStyle={{ color: GREEN }} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic title={`近${days}天均价`} value={avgOrder} precision={2} prefix="¥" valueStyle={{ color: GREEN_DARK }} />
          </Card>
        </Col>
        <Col xs={24} sm={8}>
          <Card size="small">
            <Statistic title={`近${days}天总单数`} value={totalOrders} prefix={<RiseOutlined />} valueStyle={{ color: GREEN }} />
          </Card>
        </Col>
        {/* Mini week trend sparkline */}
        <Col xs={24}>
          <Card size="small" title={<span style={{ fontSize: 13, fontWeight: 500 }}>近7日流水趋势</span>}>
            {miniTrend.length > 0 ? <Line {...miniLineConfig} /> : <Empty description="暂无数据" />}
          </Card>
        </Col>
      </Row>

      {/* ════ Daily Revenue Trend ════ */}
      <Spin spinning={dashLoading}>
        <Card
          title={<span style={{ fontWeight: 500 }}>💰 流水趋势分析</span>}
          style={{ marginBottom: 16 }}
        >
          {hasTrend ? (
            <Row gutter={[16, 16]}>
              <Col xs={24} lg={12}>
                <div style={{ marginBottom: 8, fontWeight: 500, fontSize: 14, color: GREEN }}>流水金额</div>
                <Line {...revenueLineConfig} />
              </Col>
              <Col xs={24} lg={12}>
                <div style={{ marginBottom: 8, fontWeight: 500, fontSize: 14, color: GREEN_DARK }}>完成订单数</div>
                <Line {...orderCountLineConfig} />
              </Col>
            </Row>
          ) : (
            <Empty description={`近${days}天暂无已完成订单`} style={{ padding: 40 }} />
          )}
        </Card>
      </Spin>

      {/* ════ Status & Type Distribution ════ */}
      <Spin spinning={dashLoading}>
        <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
          <Col xs={24} lg={14}>
            <Card title={<span style={{ fontWeight: 500 }}>📋 订单状态分布</span>}>
              {hasStatus ? <Column {...statusColumnConfig} /> : <Empty style={{ padding: 40 }} />}
            </Card>
          </Col>
          <Col xs={24} lg={10}>
            <Card title={<span style={{ fontWeight: 500 }}>📦 订单类型分布</span>}>
              {hasType ? <Pie {...pieConfig} /> : <Empty style={{ padding: 40 }} />}
            </Card>
          </Col>
        </Row>
      </Spin>

      {/* ════ Hourly Peak ════ */}
      <Spin spinning={dashLoading}>
        <Card title={<span style={{ fontWeight: 500 }}>⏰ 下单时段分布</span>} style={{ marginBottom: 16 }}>
          {hasHourly ? <Column {...hourlyColumnConfig} /> : <Empty style={{ padding: 40 }} />}
        </Card>
      </Spin>

      {/* ════ Runner Ranking ════ */}
      <Spin spinning={runnersLoading}>
        <Card
          title={
            <span style={{ fontWeight: 500 }}>
              <TrophyOutlined style={{ color: '#E8A648', marginRight: 8 }} />
              跑腿员排行榜
            </span>
          }
        >
          {runners.length > 0 ? (
            <Table
              dataSource={runners}
              columns={runnerColumns}
              rowKey="runnerId"
              pagination={false}
              size="small"
              locale={{ emptyText: '暂无数据' }}
            />
          ) : (
            <Empty style={{ padding: 40 }} />
          )}
        </Card>
      </Spin>
    </Spin>
  );
};

export default Dashboard;
