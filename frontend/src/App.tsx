import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { KnowledgeBaseLayout } from './pages/KnowledgeBaseLayout';

export default function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <KnowledgeBaseLayout />
    </ConfigProvider>
  );
}
