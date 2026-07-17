import React, { useState, useRef, useEffect } from 'react';
import { Card, Input, Space, Button, Collapse, Slider, Select, Tag } from 'antd';
import { SendOutlined, ThunderboltOutlined, SettingOutlined } from '@ant-design/icons';
import { chat as aiChat } from '../../api/ai';

const { Search } = Input;

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

/** Quick-access questions for the admin AI */
const QUICK_QUESTIONS = [
  '今天的运营数据怎么样？',
  '查看配送距离矩阵',
  '跑腿员效率排名',
  '近7天收入趋势',
  '未处理的异常有哪些？',
];

const STYLE_OPTIONS = [
  { value: 'precise', label: '精确数据' },
  { value: 'concise', label: '简洁概述' },
  { value: 'detailed', label: '详细分析' },
];

const STYLE_COLOR: Record<string, string> = {
  precise: '#4A7C59',
  concise: '#5B8C6B',
  detailed: '#7CB88B',
};

const AIChat: React.FC = () => {
  const [sessionId] = useState(Date.now().toString());
  const [messages, setMessages] = useState<ChatMessage[]>([
    {
      role: 'assistant',
      content: '你好！我是管理员专属AI助手小帮，可以帮你：\n\n📊 查询运营数据（今日订单、收入、完成率）\n🗺️ 查看配送距离矩阵（取货点→送货点预估时间）\n👤 分析跑腿员效率（排名、评分、平均配送时长）\n📈 查看收入趋势和时段分布\n⚠️ 监控异常预警\n\n试着问我吧！也可以点击下方快捷问题 👇',
    },
  ]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Tuning params
  const [temperature, setTemperature] = useState(0.5);
  const [style, setStyle] = useState('detailed');

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (value: string) => {
    const trimmed = value.trim();
    if (!trimmed) return;

    const userMsg: ChatMessage = { role: 'user', content: trimmed };
    setMessages((prev) => [...prev, userMsg]);
    setInputValue('');
    setLoading(true);

    try {
      const res = await aiChat({
        sessionId,
        message: trimmed,
        userType: 'ADMIN',
        temperature,
        style,
      });
      const reply = (res as any).data?.reply || '抱歉，我暂时无法回复。';
      const assistantMsg: ChatMessage = { role: 'assistant', content: reply };
      setMessages((prev) => [...prev, assistantMsg]);
    } catch {
      const errorMsg: ChatMessage = {
        role: 'assistant',
        content: '请求失败，请稍后重试。',
      };
      setMessages((prev) => [...prev, errorMsg]);
    } finally {
      setLoading(false);
    }
  };

  const tempLabel = () => {
    if (temperature <= 0.3) return '精确';
    if (temperature <= 0.6) return '平衡';
    return '创意';
  };

  return (
    <Card title="AI 助手（管理员专属）" style={{ maxWidth: 800, margin: '0 auto' }}>
      <div
        style={{
          height: 420,
          overflowY: 'auto',
          border: '1px solid #E8EDE9',
          borderRadius: 8,
          padding: 16,
          marginBottom: 12,
          background: '#F7F9F7',
        }}
      >
        {messages.map((msg, index) => (
          <div
            key={index}
            style={{
              display: 'flex',
              justifyContent: msg.role === 'user' ? 'flex-end' : 'flex-start',
              marginBottom: 12,
            }}
          >
            <div
              style={{
                maxWidth: '75%',
                padding: '10px 16px',
                borderRadius: 12,
                background: msg.role === 'user' ? '#DCE5DF' : '#F3F3F3',
                color: '#17181C',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
              }}
            >
              {msg.content}
            </div>
          </div>
        ))}
        {loading && (
          <div style={{ textAlign: 'left', color: '#768E7D', marginBottom: 12 }}>思考中...</div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Quick questions */}
      <div style={{ marginBottom: 8, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
        {QUICK_QUESTIONS.map((q) => (
          <Button
            key={q}
            size="small"
            icon={<ThunderboltOutlined />}
            onClick={() => handleSend(q)}
            disabled={loading}
          >
            {q}
          </Button>
        ))}
      </div>

      {/* Advanced settings */}
      <Collapse
        size="small"
        ghost
        style={{ marginBottom: 12 }}
        items={[
          {
            key: 'settings',
            label: (
              <Space size={4}>
                <SettingOutlined />
                <span>高级设置</span>
                <Tag color={STYLE_COLOR[style]} style={{ marginLeft: 8 }}>
                  {STYLE_OPTIONS.find((s) => s.value === style)?.label}
                </Tag>
                <Tag>{tempLabel()} ({(temperature * 100).toFixed(0)}%)</Tag>
              </Space>
            ),
            children: (
              <div style={{ padding: '8px 0' }}>
                <div style={{ marginBottom: 16 }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 4 }}>
                    <span><strong>Temperature</strong> — 控制回答的随机性</span>
                    <Tag>{tempLabel()}</Tag>
                  </div>
                  <Slider
                    min={0.1}
                    max={0.9}
                    step={0.1}
                    value={temperature}
                    onChange={setTemperature}
                    marks={{
                      0.1: '精确',
                      0.3: '较精确',
                      0.5: '平衡',
                      0.7: '较创意',
                      0.9: '创意',
                    }}
                    tipFormatter={(v) => `${(v! * 100).toFixed(0)}%`}
                  />
                  <div style={{ fontSize: 12, color: '#768E7D' }}>
                    低值：数据更精确一致，适合查询运营数据；高值：回答更有创意，适合策略建议
                  </div>
                </div>

                <div>
                  <div style={{ marginBottom: 4 }}>
                    <strong>回复风格</strong> — 控制回答的呈现方式
                  </div>
                  <Select
                    value={style}
                    onChange={setStyle}
                    options={STYLE_OPTIONS}
                    style={{ width: 200 }}
                  />
                  <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>
                    精确数据：表格/列表呈现，客观严谨；简洁概述：精炼概括，150字内；详细分析：完整解读+趋势+建议
                  </div>
                </div>
              </div>
            ),
          },
        ]}
      />

      <Search
        placeholder="输入你的问题..."
        enterButton={<><SendOutlined /> 发送</>}
        size="large"
        value={inputValue}
        loading={loading}
        onChange={(e) => setInputValue(e.target.value)}
        onSearch={handleSend}
      />
    </Card>
  );
};

export default AIChat;
