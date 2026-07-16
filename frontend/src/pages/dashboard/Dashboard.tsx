import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Spin } from 'antd';
import {
  ShoppingCartOutlined,
  TeamOutlined,
  CheckCircleOutlined,
  DollarOutlined,
} from '@ant-design/icons';
import { getOverview } from '../../api/statistics';

interface OverviewData {
  todayOrderCount?: number;
  onlineRunnerCount?: number;
  completionRate?: number;
  todayRevenue?: number;
}

const Dashboard: React.FC = () => {
  const [overview, setOverview] = useState<OverviewData>({});
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      try {
        const res = await getOverview();
        const data = res.data || res;
        setOverview(data);
      } catch {
        // error handled by interceptor
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  return (
    <Spin spinning={loading}>
      <Card title="运营数据看板">
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
    </Spin>
  );
};

export default Dashboard;
