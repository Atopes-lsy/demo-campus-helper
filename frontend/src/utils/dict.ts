export const ORDER_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待接单', color: '#87A8D0' },
  1: { label: '已接单', color: '#5B8C6B' },
  2: { label: '取件中', color: '#E8A648' },
  3: { label: '配送中', color: '#4A7C59' },
  4: { label: '已送达', color: '#7CB88B' },
  5: { label: '已完成', color: '#3D6B4A' },
  6: { label: '已取消', color: '#D96C6C' },
};

export const ORDER_TYPE_MAP: Record<number, { label: string; icon: string }> = {
  1: { label: '代取快递', icon: '📦' },
  2: { label: '代买餐饮', icon: '🍔' },
  3: { label: '代购商品', icon: '🛒' },
};

export const RUNNER_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '空闲', color: '#5B8C6B' },
  1: { label: '接单中', color: '#E8A648' },
  2: { label: '休息', color: '#ACB9B0' },
};

export const PAY_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待支付', color: '#E8A648' },
  1: { label: '支付中', color: '#87A8D0' },
  2: { label: '已支付', color: '#7CB88B' },
  3: { label: '已结算', color: '#3D6B4A' },
  4: { label: '已退款', color: '#D96C6C' },
};

export const ANOMALY_SEVERITY_MAP: Record<number, { label: string; color: string }> = {
  1: { label: '低', color: '#7CB88B' },
  2: { label: '中', color: '#E8A648' },
  3: { label: '高', color: '#D96C6C' },
};

export const ORDER_STATUS_OPTIONS = Object.entries(ORDER_STATUS_MAP)
  .map(([v, { label }]) => ({ value: Number(v), label }));

export const ORDER_TYPE_OPTIONS = Object.entries(ORDER_TYPE_MAP)
  .map(([v, { label, icon }]) => ({ value: Number(v), label: `${icon} ${label}` }));

// ========== Package Size ==========

export const PACKAGE_SIZE_MAP: Record<string, { label: string; size: string; price: number }> = {
  small:  { label: '小件', size: '20×20cm', price: 4 },
  medium: { label: '中件', size: '40×40cm', price: 5 },
  large:  { label: '大件', size: '60×60cm', price: 8 },
};

export const PACKAGE_SIZE_OPTIONS = Object.entries(PACKAGE_SIZE_MAP)
  .map(([value, { label, size, price }]) => ({
    value,
    label: `${label}（${size}）— ¥${price}`,
  }));

// ========== Pickup Locations ==========

export const PICKUP_LOCATION_OPTIONS = [
  { value: '南三门外卖柜', label: '南三门外卖柜' },
  { value: '九斋外卖柜',   label: '九斋外卖柜' },
  { value: '北门外卖柜',   label: '北门外卖柜' },
  { value: '南三门快递站', label: '南三门快递站' },
];
