import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider } from './contexts/AuthContext';
import App from './App';
import './index.css';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#5B8C6B',
          colorSuccess: '#5B8C6B',
          colorWarning: '#E8A648',
          colorError: '#D96C6C',
          colorInfo: '#5B8C6B',
          colorTextBase: '#17181C',
          colorTextSecondary: '#768E7D',
          colorBgBase: '#FFFFFF',
          colorBorder: '#D8E0DA',
          borderRadius: 8,
          borderRadiusSM: 6,
          borderRadiusLG: 12,
          fontFamily:
            '-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
          fontSize: 14,
          lineHeight: 1.6,
          boxShadow:
            '0 1px 3px rgba(23, 24, 28, 0.06), 0 1px 2px rgba(23, 24, 28, 0.04)',
          boxShadowSecondary:
            '0 4px 12px rgba(23, 24, 28, 0.06), 0 2px 4px rgba(23, 24, 28, 0.04)',
        },
        components: {
          Layout: {
            bodyBg: '#E9EEEA',
            headerBg: '#FFFFFF',
            headerHeight: 56,
            siderBg: '#F3F3F3',
            triggerBg: '#EBF0EC',
            triggerColor: '#768E7D',
          },
          Menu: {
            itemBg: 'transparent',
            itemColor: '#3D4F42',
            itemHoverBg: '#E9EEEA',
            itemHoverColor: '#17181C',
            itemSelectedBg: '#DCE5DF',
            itemSelectedColor: '#4A7C59',
            itemActiveBg: '#E9EEEA',
            itemHeight: 44,
            itemBorderRadius: 8,
            iconSize: 18,
            collapsedIconSize: 18,
            subMenuItemBg: 'transparent',
            groupTitleColor: '#768E7D',
            groupTitleFontSize: 12,
          },
          Table: {
            headerBg: '#F7F9F7',
            headerColor: '#768E7D',
            headerSplitColor: 'transparent',
            rowHoverBg: '#F2F6F3',
            rowSelectedBg: '#E8F0EA',
            rowSelectedHoverBg: '#DCE5DF',
            borderColor: '#E8EDE9',
            cellPaddingBlock: 12,
            cellPaddingInline: 16,
            headerBorderRadius: 8,
          },
          Card: {
            colorBgContainer: '#FFFFFF',
            borderRadiusLG: 12,
            paddingLG: 24,
            boxShadow:
              '0 1px 3px rgba(23, 24, 28, 0.04), 0 1px 2px rgba(23, 24, 28, 0.03)',
          },
          Button: {
            primaryColor: '#FFFFFF',
            colorPrimary: '#5B8C6B',
            colorPrimaryHover: '#4A7C59',
            colorPrimaryActive: '#3D6B4A',
            borderRadius: 8,
            borderRadiusSM: 6,
            borderRadiusLG: 10,
            controlHeight: 36,
            controlHeightSM: 28,
            controlHeightLG: 44,
            fontWeight: 500,
            defaultBg: '#FFFFFF',
            defaultBorderColor: '#D8E0DA',
            defaultColor: '#3D4F42',
            defaultHoverBorderColor: '#5B8C6B',
            defaultHoverColor: '#5B8C6B',
          },
          Switch: {
            colorPrimary: '#5B8C6B',
            colorPrimaryHover: '#4A7C59',
            trackHeight: 24,
            handleSize: 20,
          },
          Tag: {
            defaultBg: '#F3F3F3',
            defaultColor: '#768E7D',
            borderRadiusSM: 6,
          },
          Input: {
            activeBorderColor: '#5B8C6B',
            hoverBorderColor: '#A8C4B0',
            borderRadius: 8,
            borderRadiusSM: 6,
            borderRadiusLG: 10,
            colorBorder: '#D8E0DA',
          },
          Select: {
            activeBorderColor: '#5B8C6B',
            hoverBorderColor: '#A8C4B0',
            borderRadius: 8,
            colorBorder: '#D8E0DA',
            optionSelectedBg: '#E8F0EA',
          },
          Pagination: {
            itemActiveBg: '#DCE5DF',
            colorPrimary: '#5B8C6B',
            colorPrimaryHover: '#4A7C59',
          },
          Modal: {
            borderRadiusLG: 12,
            colorBgElevated: '#FFFFFF',
          },
          Breadcrumb: {
            linkColor: '#768E7D',
            linkHoverColor: '#5B8C6B',
            lastItemColor: '#17181C',
            separatorColor: '#ACB9B0',
          },
          FloatButton: {
            colorPrimary: '#5B8C6B',
            colorPrimaryHover: '#4A7C59',
          },
        },
      }}
    >
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </ConfigProvider>
  </React.StrictMode>
);
