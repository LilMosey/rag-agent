import { Button, Space, Table, Tag } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { Download, Info, Trash2 } from 'lucide-react';
import type { KnowledgeFile } from '../types/domain';
import { formatFileSize, statusColor } from './FileDetailDrawer';

interface FileTableProps {
  files: KnowledgeFile[];
  loading: boolean;
  onDetail: (file: KnowledgeFile) => void;
  onDownload: (file: KnowledgeFile) => void;
  onDelete: (file: KnowledgeFile) => void;
}

export function FileTable({ files, loading, onDetail, onDownload, onDelete }: FileTableProps) {
  const columns: ColumnsType<KnowledgeFile> = [
    {
      title: '文件名',
      dataIndex: 'originalFilename',
      ellipsis: true
    },
    {
      title: '类型',
      dataIndex: 'fileType',
      width: 120
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      width: 120,
      render: (size: number) => formatFileSize(size)
    },
    {
      title: '状态',
      dataIndex: 'fileStatus',
      width: 140,
      render: (status: string) => <Tag color={statusColor(status)}>{status}</Tag>
    },
    {
      title: '上传时间',
      dataIndex: 'createdAt',
      width: 180,
      render: (createdAt: string) => dayjs(createdAt).format('YYYY-MM-DD HH:mm')
    },
    {
      title: '操作',
      width: 180,
      render: (_, file) => (
        <Space size={4}>
          <Button type="text" icon={<Info size={16} />} onClick={() => onDetail(file)} />
          <Button type="text" icon={<Download size={16} />} onClick={() => onDownload(file)} />
          <Button danger type="text" icon={<Trash2 size={16} />} onClick={() => onDelete(file)} />
        </Space>
      )
    }
  ];

  return (
    <Table
      rowKey="id"
      columns={columns}
      dataSource={files}
      loading={loading}
      pagination={false}
      locale={{ emptyText: '暂无文件' }}
    />
  );
}
