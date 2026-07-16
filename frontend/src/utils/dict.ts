export const ORDER_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待接单', color: 'blue' },
  1: { label: '已接单', color: 'cyan' },
  2: { label: '取件中', color: 'orange' },
  3: { label: '配送中', color: 'geekblue' },
  4: { label: '已送达', color: 'purple' },
  5: { label: '已完成', color: 'green' },
  6: { label: '已取消', color: 'red' },
};

export const ORDER_TYPE_MAP: Record<number, { label: string; icon: string }> = {
  1: { label: '代取外卖', icon: '🍔' },
  2: { label: '代取包裹', icon: '📦' },
};

export const RUNNER_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '空闲', color: 'green' },
  1: { label: '接单中', color: 'orange' },
  2: { label: '休息', color: 'gray' },
};

export const PAY_STATUS_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '待支付', color: 'orange' },
  1: { label: '支付中', color: 'blue' },
  2: { label: '已支付', color: 'cyan' },
  3: { label: '已结算', color: 'green' },
  4: { label: '已退款', color: 'red' },
};

export const ANOMALY_SEVERITY_MAP: Record<number, { label: string; color: string }> = {
  1: { label: '低', color: 'green' },
  2: { label: '中', color: 'orange' },
  3: { label: '高', color: 'red' },
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
