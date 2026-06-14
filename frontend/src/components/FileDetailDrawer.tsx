import { Descriptions, Drawer, Tag, Typography } from 'antd';
import dayjs from 'dayjs';
import type { KnowledgeFile } from '../types/domain';

interface FileDetailDrawerProps {
  open: boolean;
  file?: KnowledgeFile;
  onClose: () => void;
}

export function FileDetailDrawer({ open, file, onClose }: FileDetailDrawerProps) {
  return (
    <Drawer title="文件详情" open={open} width={560} onClose={onClose}>
      {file ? (
        <Descriptions column={1} bordered size="small">
          <Descriptions.Item label="文件名">{file.originalFilename}</Descriptions.Item>
          <Descriptions.Item label="文件类型">{file.fileType}</Descriptions.Item>
          <Descriptions.Item label="MIME">{file.contentType || '-'}</Descriptions.Item>
          <Descriptions.Item label="大小">{formatFileSize(file.fileSize)}</Descriptions.Item>
          <Descriptions.Item label="状态">
            <Tag color={statusColor(file.fileStatus)}>{file.fileStatus}</Tag>
          </Descriptions.Item>
          <Descriptions.Item label="SHA256">
            <Typography.Text copyable>{file.checksumSha256}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label="MinIO Bucket">{file.storageBucket}</Descriptions.Item>
          <Descriptions.Item label="MinIO Object Key">
            <Typography.Text copyable>{file.storageObjectKey}</Typography.Text>
          </Descriptions.Item>
          <Descriptions.Item label="解析错误">{file.parseError || '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{dayjs(file.createdAt).format('YYYY-MM-DD HH:mm:ss')}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{dayjs(file.updatedAt).format('YYYY-MM-DD HH:mm:ss')}</Descriptions.Item>
        </Descriptions>
      ) : null}
    </Drawer>
  );
}

export function statusColor(status: string): string {
  if (status === 'READY') {
    return 'green';
  }
  if (status === 'PARSE_FAILED') {
    return 'red';
  }
  if (status === 'PARSING' || status === 'PENDING_PARSE') {
    return 'blue';
  }
  if (status === 'DISABLED') {
    return 'default';
  }
  return 'gold';
}

export function formatFileSize(size: number): string {
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`;
}
