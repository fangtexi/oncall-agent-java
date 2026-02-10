package com.oncallagentjava.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.oncallagentjava.config.VectorConfig;
import com.oncallagentjava.entity.MdChunk;
import com.oncallagentjava.services.VectorEmbeddingService;
import io.milvus.param.dml.InsertParam;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;

import io.milvus.v2.service.collection.response.GetCollectionStatsResp;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * milvus 向量数据库操作类
 * 封装连接、建表、创索引、批量插入、相似性查询，适配MD分片的MdChunk元数据
 */
public class MilvusUtil {
    private static final Logger logger = LoggerFactory.getLogger(VectorEmbeddingService.class);

    // 单例milvus客户端
    private static volatile MilvusClientV2 milvusClient;

    /**
     * 获取milvus客户端
     *
     * @return
     */
    public static MilvusClientV2 getMilvusClient() {
        if (milvusClient == null) {
            synchronized (MilvusUtil.class) {
                if (milvusClient == null) {
                    // 构造连接参数
                    ConnectConfig connectConfig = ConnectConfig.builder()
                            .uri("http://" + VectorConfig.MILVUS_HOST + ":" + VectorConfig.MILVUS_PORT)
                            .token(VectorConfig.MILVUS_TOKEN)
                            .dbName(VectorConfig.MILVUS_DATABASE_NAME)
                            .build();
                    milvusClient = new MilvusClientV2(connectConfig);
                }
            }
        }
        return milvusClient;
    }

    /**
     * 初始化md知识库向量表
     * 1. 创建表
     * 2. 创建索引
     */
    public static void initMilvusTable() {
        MilvusClientV2 client = getMilvusClient();
        // 检查表是否存在，不存在则创建
        Boolean hasTable = client.hasCollection(HasCollectionReq.builder()
                .databaseName(VectorConfig.MILVUS_DATABASE_NAME)
                .collectionName(VectorConfig.MILVUS_TABLE_NAME_1)
                .build()
        );
        if (hasTable) {
            logger.info("Milvus表 --> {}，已存在，无需重复创建", VectorConfig.MILVUS_TABLE_NAME_1);
            return;
        }

        // 创建 schema
        CreateCollectionReq.CollectionSchema collectionSchema = client.createSchema();
        // 主键：分片唯一ID
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("chunk_id")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .isPrimaryKey(true)
                .autoID(false)
                .build());
        // 父块ID
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("parent_chunk_id")
                .dataType(DataType.VarChar)
                .maxLength(64)
                .build());
        // 分块类型
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("chunk_type")
                .dataType(DataType.VarChar)
                .maxLength(32)
                .build());
        // 文档名称
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("doc_name")
                .dataType(DataType.VarChar)
                .maxLength(128)
                .build());
        // 起始行号
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("start_line")
                .dataType(DataType.Int32)
                .build());
        // 结束行号
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("end_line")
                .dataType(DataType.Int32)
                .build());
        // 分片内容
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("content")
                .dataType(DataType.VarChar)
                .maxLength(5000) // 最大分片大小为2000，设置为5000足够
                .build());
        // 核心向量字段
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("vector")
                .dataType(DataType.FloatVector)
                .dimension(VectorConfig.VECTOR_DIMENSION) // 与embedding模型的维度一致
                .build());

        // 创建表
        CreateCollectionReq createCollectionReq = CreateCollectionReq.builder()
                .collectionName(VectorConfig.MILVUS_TABLE_NAME_1)
                .collectionSchema(collectionSchema)
                .numShards(1) // 1个分片
                .build();
        client.createCollection(createCollectionReq);
        logger.info("Milvus表 --> {}，创建成功", VectorConfig.MILVUS_TABLE_NAME_1);
        // 创建索引
        IndexParam indexParam = IndexParam.builder()
                .fieldName("vector")
                .indexType(VectorConfig.MILVUS_INDEX_TYPE)
                .metricType(VectorConfig.MILVUS_METRIC_TYPE)
                .build();
        CreateIndexReq createIndexReq = CreateIndexReq.builder()
                .collectionName(VectorConfig.MILVUS_TABLE_NAME_1)
                .indexParams(Collections.singletonList(indexParam))
                .build();
        client.createIndex(createIndexReq);
        logger.info("Milvus索引创建成功，索引类型：{}",VectorConfig.MILVUS_INDEX_TYPE);
    }
    
}
