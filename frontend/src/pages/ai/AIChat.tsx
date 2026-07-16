import React, { useState, useRef, useEffect } from 'react';
import { Card, Input } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { chat as aiChat } from '../../api/ai';

const { Search } = Input;

interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

const AIChat: React.FC = () => {
  const [sessionId] = useState(Date.now().toString());
  const [messages, setMessages] = useState<ChatMessage[]>([
    { role: 'assistant', content: '你好！我是 AI 助手小帮，有什么可以帮你的？' },
  ]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

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
      const res = await aiChat({ sessionId, message: trimmed });
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

  return (
    <Card title="AI 助手" style={{ maxWidth: 800, margin: '0 auto' }}>
      <div
        style={{
          height: 500,
          overflowY: 'auto',
          border: '1px solid #f0f0f0',
          borderRadius: 8,
          padding: 16,
          marginBottom: 16,
          background: '#fafafa',
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
                background: msg.role === 'user' ? '#e6f7ff' : '#f5f5f5',
                color: '#333',
                whiteSpace: 'pre-wrap',
                wordBreak: 'break-word',
              }}
            >
              {msg.content}
            </div>
          </div>
        ))}
        {loading && (
          <div style={{ textAlign: 'left', color: '#999', marginBottom: 12 }}>思考中...</div>
        )}
        <div ref={messagesEndRef} />
      </div>
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
