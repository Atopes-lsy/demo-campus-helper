import React, { useEffect, useState, useCallback } from 'react';
import { Table, Button, Select, Tag, Space, Card, message, Switch } from 'antd';
import dayjs from 'dayjs';
import { getStudents, updateStudent } from '../../api/student';

interface StudentRecord {
  id: number;
  studentNo: string;
  name: string;
  phone: string;
  dormitory: string;
  status: number;
  totalOrders: number;
  createdAt: string;
}

const STATUS_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 0, label: '禁用' },
];

const StudentManagement: React.FC = () => {
  const [dataSource, setDataSource] = useState<StudentRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);

  const fetchStudents = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = { page: pagination.current, size: pagination.pageSize };
      if (statusFilter !== undefined) params.status = statusFilter;
      const res = await getStudents(params as any);
      const data = res.data || res;
      setDataSource(data.records || data.list || []);
      setPagination((prev) => ({ ...prev, total: data.total || 0 }));
    } catch { } finally { setLoading(false); }
  }, [pagination.current, pagination.pageSize, statusFilter]);

  useEffect(() => { fetchStudents(); }, [fetchStudents]);

  const handleToggleStatus = async (record: StudentRecord, checked: boolean) => {
    const newStatus = checked ? 1 : 0;
    try {
      await updateStudent(record.id, { status: newStatus });
      message.success(checked ? '已启用' : '已禁用');
      fetchStudents();
    } catch { }
  };

  const columns = [
    { title: '学号', dataIndex: 'studentNo', key: 'studentNo', width: 130 },
    { title: '姓名', dataIndex: 'name', key: 'name', width: 100 },
    { title: '手机号', dataIndex: 'phone', key: 'phone', width: 130 },
    { title: '宿舍', dataIndex: 'dormitory', key: 'dormitory', width: 140 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (val: number) => val === 1 ? <Tag color="green">正常</Tag> : <Tag color="red">禁用</Tag>,
    },
    { title: '下单数', dataIndex: 'totalOrders', key: 'totalOrders', width: 90 },
    {
      title: '注册时间', dataIndex: 'createdAt', key: 'createdAt', width: 150,
      render: (val: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '—',
    },
    {
      title: '操作', key: 'actions', width: 80, fixed: 'right' as const,
      render: (_: unknown, record: StudentRecord) => (
        <Switch
          checked={record.status !== 0}
          checkedChildren="启用"
          unCheckedChildren="禁用"
          onChange={(checked) => handleToggleStatus(record, checked)}
        />
      ),
    },
  ];

  return (
    <Card title="学生用户管理">
      <div style={{ marginBottom: 16 }}>
        <Space>
          <Select placeholder="状态筛选" allowClear style={{ width: 140 }}
            options={STATUS_OPTIONS} value={statusFilter} onChange={(v) => setStatusFilter(v)} />
          <Button type="primary" onClick={() => fetchStudents()}>刷新</Button>
        </Space>
      </div>
      <Table rowKey="id" columns={columns} dataSource={dataSource} loading={loading} scroll={{ x: 1100 }}
        pagination={{ current: pagination.current, pageSize: pagination.pageSize, total: pagination.total,
          showSizeChanger: true, showTotal: (t: number) => `共 ${t} 名学生`, }}
        onChange={(pag) => setPagination((prev) => ({ ...prev, current: pag.current || prev.current, pageSize: pag.pageSize || prev.pageSize }))} />
    </Card>
  );
};

export default StudentManagement;
