package com.example.kb.infrastructure.vector;

import com.example.kb.application.port.VectorIndexCleaner;
import com.example.kb.application.port.VectorIndexWriter;
import com.example.kb.infrastructure.embedding.EmbeddingProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MilvusVectorIndexStore implements VectorIndexWriter, VectorIndexCleaner {

    private static final Logger log = LoggerFactory.getLogger(MilvusVectorIndexStore.class);
    private static final int INSERT_BATCH_SIZE = 100;
    private static final String VECTOR_FIELD = "vector";

    private final MilvusProperties milvusProperties;
    private final EmbeddingProperties embeddingProperties;
    private final MilvusClientV2 milvusClient;

    public MilvusVectorIndexStore(MilvusProperties milvusProperties, EmbeddingProperties embeddingProperties) {
        this.milvusProperties = milvusProperties;
        this.embeddingProperties = embeddingProperties;
        this.milvusClient = connectWithRetry();
    }

    @Override
    public void upsertChunks(UpsertChunksCommand command) {
        log.info("Milvus 向量写入入参: knowledgeBaseId={}, fileId={}, count={}",
                command.knowledgeBaseId(), command.fileId(), command.chunks().size());
        if (command.chunks().isEmpty()) {
            log.info("Milvus 向量写入分支: 空列表, fileId={}", command.fileId());
            return;
        }
        ensureCollection();
        deleteByFileId(command.fileId());
        int insertedRows = 0;
        int batchCount = 0;
        List<JsonObject> rows = buildRows(command);
        for (int fromIndex = 0; fromIndex < rows.size(); fromIndex += INSERT_BATCH_SIZE) {
            int toIndex = Math.min(fromIndex + INSERT_BATCH_SIZE, rows.size());
            List<JsonObject> batchRows = rows.subList(fromIndex, toIndex);
            InsertReq insertReq = InsertReq.builder()
                    .databaseName(milvusProperties.database())
                    .collectionName(milvusProperties.collectionName())
                    .data(batchRows)
                    .build();
            milvusClient.insert(insertReq);
            insertedRows += batchRows.size();
            batchCount++;
        }
        log.info("Milvus 向量写入出参: fileId={}, insertedRows={}, batchSize={}, batchCount={}",
                command.fileId(), insertedRows, INSERT_BATCH_SIZE, batchCount);
    }

    @Override
    public void deleteByFileId(Long fileId) {
        log.info("删除向量索引入参: fileId={}", fileId);
        if (!hasCollection()) {
            log.info("删除向量索引分支: collection 不存在, fileId={}, collection={}",
                    fileId, milvusProperties.collectionName());
            return;
        }
        DeleteReq deleteReq = DeleteReq.builder()
                .databaseName(milvusProperties.database())
                .collectionName(milvusProperties.collectionName())
                .filter("file_id == " + fileId)
                .build();
        milvusClient.delete(deleteReq);
        log.info("删除向量索引出参: fileId={}, collection={}", fileId, milvusProperties.collectionName());
    }

    private List<JsonObject> buildRows(UpsertChunksCommand command) {
        List<JsonObject> rows = new ArrayList<>(command.chunks().size());
        for (VectorChunk chunk : command.chunks()) {
            JsonObject row = new JsonObject();
            row.addProperty("knowledge_base_id", command.knowledgeBaseId());
            row.addProperty("file_id", command.fileId());
            row.addProperty("chunk_id", chunk.chunkId());
            row.addProperty("chunk_index", chunk.chunkIndex());
            row.addProperty("embedding_source", chunk.embeddingSource());
            row.addProperty("content_hash", chunk.contentHash());
            row.add(VECTOR_FIELD, toJsonArray(chunk.vector()));
            rows.add(row);
        }
        return rows;
    }

    private JsonArray toJsonArray(List<Float> vector) {
        if (vector.size() != embeddingProperties.dimension()) {
            throw new IllegalStateException("Milvus 写入向量维度不匹配，期望="
                    + embeddingProperties.dimension() + "，实际=" + vector.size());
        }
        JsonArray jsonArray = new JsonArray();
        for (Float value : vector) {
            jsonArray.add(value);
        }
        return jsonArray;
    }

    private void ensureCollection() {
        if (hasCollection()) {
            return;
        }
        log.info("Milvus collection 创建入参: database={}, collection={}, dimension={}",
                milvusProperties.database(), milvusProperties.collectionName(), embeddingProperties.dimension());
        CreateCollectionReq.CollectionSchema collectionSchema = CreateCollectionReq.CollectionSchema.builder()
                .enableDynamicField(false)
                .fieldSchemaList(List.of(
                        CreateCollectionReq.FieldSchema.builder()
                                .name("id")
                                .dataType(DataType.Int64)
                                .isPrimaryKey(Boolean.TRUE)
                                .autoID(Boolean.TRUE)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("knowledge_base_id")
                                .dataType(DataType.Int64)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("file_id")
                                .dataType(DataType.Int64)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("chunk_id")
                                .dataType(DataType.Int64)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("chunk_index")
                                .dataType(DataType.Int32)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("embedding_source")
                                .dataType(DataType.VarChar)
                                .maxLength(64)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name("content_hash")
                                .dataType(DataType.VarChar)
                                .maxLength(128)
                                .build(),
                        CreateCollectionReq.FieldSchema.builder()
                                .name(VECTOR_FIELD)
                                .dataType(DataType.FloatVector)
                                .dimension(embeddingProperties.dimension())
                                .build()
                ))
                .build();
        IndexParam indexParam = IndexParam.builder()
                .fieldName(VECTOR_FIELD)
                .indexName("idx_vector")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();
        CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                .databaseName(milvusProperties.database())
                .collectionName(milvusProperties.collectionName())
                .collectionSchema(collectionSchema)
                .indexParam(indexParam)
                .build();
        milvusClient.createCollection(createCollectionReq);
        log.info("Milvus collection 创建出参: database={}, collection={}",
                milvusProperties.database(), milvusProperties.collectionName());
    }

    private boolean hasCollection() {
        HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                .databaseName(milvusProperties.database())
                .collectionName(milvusProperties.collectionName())
                .build();
        return milvusClient.hasCollection(hasCollectionReq);
    }

    private MilvusClientV2 connectWithRetry() {
        log.info("Milvus client 连接入参: uri={}, database={}",
                milvusProperties.uri(), milvusProperties.database());
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(milvusProperties.uri())
                .dbName(milvusProperties.database())
                .build();
        MilvusClientV2 client = new MilvusClientV2(connectConfig);
        log.info("Milvus client 连接出参: uri={}, database={}",
                milvusProperties.uri(), milvusProperties.database());
        return client;
    }


}
