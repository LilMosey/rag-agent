import { Form, Input, Modal } from 'antd';
import { useEffect } from 'react';
import type { KnowledgeBase, KnowledgeBasePayload } from '../types/domain';

interface KnowledgeBaseModalProps {
  open: boolean;
  knowledgeBase?: KnowledgeBase;
  submitting: boolean;
  onCancel: () => void;
  onSubmit: (payload: KnowledgeBasePayload) => void;
}

export function KnowledgeBaseModal({
  open,
  knowledgeBase,
  submitting,
  onCancel,
  onSubmit
}: KnowledgeBaseModalProps) {
  const [form] = Form.useForm<KnowledgeBasePayload>();

  useEffect(() => {
    if (open) {
      form.setFieldsValue({
        name: knowledgeBase?.name || '',
        description: knowledgeBase?.description || ''
      });
    }
  }, [form, knowledgeBase, open]);

  return (
    <Modal
      title={knowledgeBase ? '编辑知识库' : '创建知识库'}
      open={open}
      okText="保存"
      cancelText="取消"
      confirmLoading={submitting}
      onCancel={onCancel}
      onOk={() => form.submit()}
      destroyOnClose
    >
      <Form form={form} layout="vertical" onFinish={onSubmit}>
        <Form.Item
          name="name"
          label="名称"
          rules={[
            { required: true, message: '请输入知识库名称' },
            { max: 128, message: '知识库名称不能超过 128 个字符' }
          ]}
        >
          <Input placeholder="例如：产品文档库" />
        </Form.Item>
        <Form.Item
          name="description"
          label="描述"
          rules={[{ max: 1024, message: '知识库描述不能超过 1024 个字符' }]}
        >
          <Input.TextArea rows={4} placeholder="描述这个知识库的用途" />
        </Form.Item>
      </Form>
    </Modal>
  );
}
