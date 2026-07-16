import React, { useEffect, useState, useCallback } from 'react';
import { Table, Tag, Button, Card, message } from 'antd';
import dayjs from 'dayjs';
import { getAnomalies, resolveAnomaly } from '../../api/ai';

const SEVERITY_MAP: Record<number, { label: string; color: string }> = {
  1: { label: '低', color: 'green' },
  2: { label: '中', color: 'orange' },
  3: { label: '高', color: 'red' },
};

interface AnomalyRecord {
  id: number;
  targetType: string;
  targetId: number;
  anomalyType: string;
  severity: number;
  description: string;
  aiAnalysis: string;
  isResolved: number;
  detectedAt: string;
}

const AnomalyList: React.FC = () => {
  const [dataSource, setDataSource] = useState<AnomalyRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const fetchAnomalies = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: pagination.current,
        size: pagination.pageSize,
      };
      const res = await getAnomalies(params);
      const data = res.data || res;
      setDataSource(data.records || data.list || []);
      setPagination((prev) => ({ ...prev, total: data.total || 0 }));
    } catch {
      // error handled by interceptor
    } finally {
      setLoading(false);
    }
  }, [pagination.current, pagination.pageSize]);

  useEffect(() => {
    fetchAnomalies();
  }, [fetchAnomalies]);

  const handleResolve = async (id: number) => {
    try {
      await resolveAnomaly(id);
      message.success('已标记为已处理');
      fetchAnomalies();
    } catch {
      // error handled by interceptor
    }
  };

  const columns = [
    {
      title: '目标类型',
      dataIndex: 'targetType',
      key: 'targetType',
      width: 100,
    },
    {
      title: '目标ID',
      dataIndex: 'targetId',
      key: 'targetId',
      width: 90,
    },
    {
      title: '异常类型',
      dataIndex: 'anomalyType',
      key: 'anomalyType',
      width: 120,
    },
    {
      title: '严重程度',
      dataIndex: 'severity',
      key: 'severity',
      width: 100,
      render: (val: number) => {
        const item = SEVERITY_MAP[val];
        return item ? <Tag color={item.color}>{item.label}</Tag> : '—';
      },
    },
    {
      title: '描述',
      dataIndex: 'description',
      key: 'description',
      width: 200,
    },
    {
      title: 'AI分析',
      dataIndex: 'aiAnalysis',
      key: 'aiAnalysis',
      width: 250,
      ellipsis: true,
    },
    {
      title: '状态',
      dataIndex: 'isResolved',
      key: 'isResolved',
      width: 100,
      render: (val: number) =>
        val === 0 ? (
          <Tag color="yellow">未处理</Tag>
        ) : (
          <Tag color="green">已处理</Tag>
        ),
    },
    {
      title: '检测时间',
      dataIndex: 'detectedAt',
      key: 'detectedAt',
      width: 170,
      render: (val: string) => (val ? dayjs(val).format('YYYY-MM-DD HH:mm:ss') : '—'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_: unknown, record: AnomalyRecord) => (
        <Button
          type="link"
          size="small"
          disabled={record.isResolved === 1}
          onClick={() => handleResolve(record.id)}
        >
          标记已处理
        </Button>
      ),
    },
  ];

  return (
    <Card title="异常预警">
      <Table
        rowKey="id"
        columns={columns}
        dataSource={dataSource}
        loading={loading}
        scroll={{ x: 1280 }}
        pagination={{
          current: pagination.current,
          pageSize: pagination.pageSize,
          total: pagination.total,
          showSizeChanger: true,
          showTotal: (total) => `共 ${total} 条`,
        }}
        onChange={(pag) =>
          setPagination((prev) => ({
            ...prev,
            current: pag.current || prev.current,
            pageSize: pag.pageSize || prev.pageSize,
          }))
        }
      />
    </Card>
  );
};

export default AnomalyList;
