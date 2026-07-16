import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Select, Tag, Space, Card, message, Popconfirm,
  Modal, Form, Input, InputNumber,
} from 'antd';
import { PlusOutlined, EditOutlined, StopOutlined, CheckOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { getRunners, updateRunner, createRunner } from '../../api/runner';
import { RUNNER_STATUS_MAP } from '../../utils/dict';

interface RunnerRecord {
  id: number;
  runnerNo: string;
  name: string;
  phone: string;
  idCard: string;
  status: number;
  rating: number;
  completedOrders: number;
  maxConcurrent: number;
  createdAt: string;
}

const STATUS_OPTIONS = [
  { value: 0, label: '空闲' },
  { value: 1, label: '接单中' },
  { value: 2, label: '休息' },
];

const RunnerManagement: React.FC = () => {
  const [dataSource, setDataSource] = useState<RunnerRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);

  // Modal state
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRunner, setEditingRunner] = useState<RunnerRecord | null>(null);
  const [modalSubmitting, setModalSubmitting] = useState(false);
  const [form] = Form.useForm();

  const fetchRunners = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: pagination.current,
        size: pagination.pageSize,
      };
      if (statusFilter !== undefined) params.status = statusFilter;

      const res = await getRunners(params as any);
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
  }, [pagination.current, pagination.pageSize, statusFilter]);

  useEffect(() => {
    fetchRunners();
  }, [fetchRunners]);

  const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
    setPagination((prev) => ({
      ...prev,
      current: pag.current || prev.current,
      pageSize: pag.pageSize || prev.pageSize,
    }));
  };

  // ---- Open modal for add/edit ----
  const openAddModal = () => {
    setEditingRunner(null);
    form.resetFields();
    setModalOpen(true);
  };

  const openEditModal = (record: RunnerRecord) => {
    setEditingRunner(record);
    form.setFieldsValue({
      name: record.name,
      phone: record.phone,
      idCard: record.idCard,
      maxConcurrent: record.maxConcurrent,
      status: record.status,
    });
    setModalOpen(true);
  };

  // ---- Submit modal ----
  const handleModalSubmit = async () => {
    try {
      const values = await form.validateFields();
      setModalSubmitting(true);

      if (editingRunner) {
        await updateRunner(editingRunner.id, values as unknown as Record<string, unknown>);
        message.success('跑腿员信息已更新');
      } else {
        await createRunner(values as unknown as Record<string, unknown>);
        message.success('跑腿员创建成功');
      }

      setModalOpen(false);
      fetchRunners();
    } catch {
      // validation error or API error
    } finally {
      setModalSubmitting(false);
    }
  };

  // ---- Toggle status (activate/deactivate) ----
  const handleToggleStatus = async (record: RunnerRecord) => {
    const newStatus = record.status === 2 ? 0 : 2; // toggle rest ↔ idle
    const actionLabel = newStatus === 2 ? '停用' : '启用';
    try {
      await updateRunner(record.id, { status: newStatus } as unknown as Record<string, unknown>);
      message.success(`${actionLabel}成功`);
      fetchRunners();
    } catch {
      // error handled by interceptor
    }
  };

  const columns = [
    {
      title: '编号',
      dataIndex: 'runnerNo',
      key: 'runnerNo',
      width: 130,
    },
    {
      title: '姓名',
      dataIndex: 'name',
      key: 'name',
      width: 100,
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      key: 'phone',
      width: 130,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (val: number) => {
        const item = RUNNER_STATUS_MAP[val];
        return item ? <Tag color={item.color}>{item.label}</Tag> : '—';
      },
    },
    {
      title: '评分',
      dataIndex: 'rating',
      key: 'rating',
      width: 80,
      render: (val: number) => val?.toFixed(1) || '—',
    },
    {
      title: '完成单数',
      dataIndex: 'completedOrders',
      key: 'completedOrders',
      width: 100,
    },
    {
      title: '最大接单',
      dataIndex: 'maxConcurrent',
      key: 'maxConcurrent',
      width: 90,
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (val: string) =>
        val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '—',
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right' as const,
      render: (_: unknown, record: RunnerRecord) => (
        <Space>
          <a onClick={() => openEditModal(record)}>
            <EditOutlined /> 编辑
          </a>
          {record.status !== 1 && (
            <Popconfirm
              title={record.status === 2 ? '确认启用该跑腿员？' : '确认停用该跑腿员？'}
              onConfirm={() => handleToggleStatus(record)}
            >
              <a style={{ color: record.status === 2 ? 'green' : 'red' }}>
                {record.status === 2 ? <><CheckOutlined /> 启用</> : <><StopOutlined /> 停用</>}
              </a>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card title="跑腿员管理">
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Space>
          <Select
            placeholder="状态筛选"
            allowClear
            style={{ width: 140 }}
            options={STATUS_OPTIONS}
            value={statusFilter}
            onChange={(val) => setStatusFilter(val)}
          />
          <Button type="primary" onClick={() => fetchRunners()}>
            刷新
          </Button>
        </Space>
        <Button type="primary" icon={<PlusOutlined />} onClick={openAddModal}>
          新增跑腿员
        </Button>
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
          showTotal: (total: number) => `共 ${total} 位跑腿员`,
        }}
        onChange={handleTableChange}
      />

      {/* ---- Add / Edit Modal ---- */}
      <Modal
        title={editingRunner ? '编辑跑腿员' : '新增跑腿员'}
        open={modalOpen}
        onOk={handleModalSubmit}
        onCancel={() => setModalOpen(false)}
        confirmLoading={modalSubmitting}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="姓名"
            rules={[{ required: true, message: '请输入姓名' }]}
          >
            <Input placeholder="真实姓名" />
          </Form.Item>
          <Form.Item
            name="phone"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' },
            ]}
          >
            <Input placeholder="11位手机号" maxLength={11} />
          </Form.Item>
          <Form.Item
            name="idCard"
            label="身份证号"
            rules={[
              { required: true, message: '请输入身份证号' },
              { pattern: /^\d{17}[\dXx]$/, message: '请输入18位身份证号' },
            ]}
          >
            <Input placeholder="18位身份证号" maxLength={18} />
          </Form.Item>
          <Form.Item
            name="maxConcurrent"
            label="最大同时接单数"
            rules={[{ required: true, message: '请输入' }]}
          >
            <InputNumber min={1} max={20} style={{ width: '100%' }} />
          </Form.Item>
          {editingRunner && (
            <Form.Item
              name="status"
              label="状态"
              rules={[{ required: true, message: '请选择' }]}
            >
              <Select options={STATUS_OPTIONS} />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
};

export default RunnerManagement;
