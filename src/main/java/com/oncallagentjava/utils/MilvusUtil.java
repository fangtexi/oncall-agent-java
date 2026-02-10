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
        logger.info("Milvus索引创建成功，索引类型：{}", VectorConfig.MILVUS_INDEX_TYPE);
        // 加载表到内存
        loadCollection(VectorConfig.MILVUS_TABLE_NAME_1);
    }

    /**
     * 插入分片数据到 Milvus
     * @param chunks  细粒度原子块列表
     * @param vectors 向量列表，顺序与 chunks 完全一致
     */
    public static void insertDataToMilvus(List<MdChunk> chunks, List<Float[]> vectors) {
        if (chunks == null || chunks.isEmpty() || vectors == null || vectors.isEmpty() || chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("原子块列表与向量列表长度必须一致！");
        }
        MilvusClientV2 client = getMilvusClient();
        List<List<MdChunk>> bathChunks = splitBatch(chunks, VectorConfig.MILVUS_BATCH_SIZE);
        List<List<Float[]>> bathVectors = splitBatch(vectors, VectorConfig.MILVUS_BATCH_SIZE);
        for (int i = 0; i < chunks.size(); i++) {
            MdChunk chunk = chunks.get(i);
            Float[] vector = vectors.get(i);
            // 构造插入的数据行
            Map<String, Object> chunkMap = new HashMap<>();
            chunkMap.put("chunk_id", chunk.getChunkId());
            chunkMap.put("parent_chunk_id", chunk.getParentChunkId());
            chunkMap.put("chunk_type", chunk.getChunkType());
            chunkMap.put("doc_name", chunk.getDocName());
            chunkMap.put("start_line", chunk.getStartLine());
            chunkMap.put("end_line", chunk.getEndLine());
            chunkMap.put("content", chunk.getContent());
            chunkMap.put("vector", vector);

            Gson gson = new Gson();
            JsonElement jsonTree = gson.toJsonTree(chunkMap);
            JsonObject jsonObject = jsonTree.getAsJsonObject();

            InsertReq insertReq = InsertReq.builder()
                    .collectionName(VectorConfig.MILVUS_TABLE_NAME_1)
                    .data(Collections.singletonList(jsonObject))
                    .build();
            InsertResp insertResp = client.insert(insertReq);
            logger.info("insertResp:{}", insertResp);
        }
        // 加载表
        loadCollection(VectorConfig.MILVUS_TABLE_NAME_1);
        logger.info("所有数据插入完成，总条数：{}", chunks.size());
    }

    /**
     * 相似性查询
     * @param queryVector 用户问题的1024维向量
     * @param topK 返回最相似的k个结果
     * @return 相似块的元数据+相似度得分
     */
    public static List<Map<String,Object>> searchSimilar(float[] queryVector,int topK) {
        MilvusClientV2 client = getMilvusClient();
        // 判断表是否加载
        loadCollection(VectorConfig.MILVUS_TABLE_NAME_1);
        // 构造查询参数
        SearchReq searchReq = SearchReq.builder()
                .collectionName(VectorConfig.MILVUS_TABLE_NAME_1)
                .metricType(VectorConfig.MILVUS_METRIC_TYPE)
                .topK(topK)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .outputFields(Arrays.asList("chunk_id", "parent_chunk_id", "chunk_type", "doc_name", "start_line", "end_line", "content"))
                .searchParams(Map.of("ef", 128)) // // HNSW检索参数，经验值
                .build();
        SearchResp searchResp = client.search(searchReq);
        // 解析查询结果
        List<List<SearchResp.SearchResult>> searchResults = searchResp.getSearchResults();
        List<Map<String, Object>> result = new ArrayList<>();
        for (List<SearchResp.SearchResult> results : searchResults) {
            for (SearchResp.SearchResult res : results) {
                Map<String, Object> data = new HashMap<>();
                data.put("similarity", res.getScore()); // 相似度得分（余弦相似度，越接近1越相似）
                data.putAll(res.getEntity()); // 元数据（chunk_id/parent_chunk_id/content等）
                result.add(data);
                System.out.printf("ID: %d, Score: %f, %s\n", (long)res.getId(), res.getScore(), res.getEntity().toString());
            }
        }

        return result;
    }

    /**
     * 拆分批次工具方法
     */
    private static <T> List<List<T>> splitBatch(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            int end = Math.min(i + batchSize, list.size());
            batches.add(list.subList(i, end));
        }
        return batches;
    }

    /**
     * 加载表到内存（Milvus检索/查询前必须执行）
     */
    private static void loadCollection(String tableName) {
        MilvusClientV2 client = getMilvusClient();
        HasCollectionReq hasCollectionReq = HasCollectionReq.builder()
                .collectionName(VectorConfig.MILVUS_TABLE_NAME_1)
                .build();
        Boolean hasCollection = client.hasCollection(hasCollectionReq);
        if (hasCollection == Boolean.FALSE) {
            client.loadCollection(LoadCollectionReq.builder().collectionName(tableName).build());
            Boolean loadState = client.getLoadState(GetLoadStateReq.builder().collectionName(tableName).build());
            if (!loadState) {
                throw new RuntimeException("Milvus加载表失败!");
            }
            logger.info("Milvus表 {} 已加载到内存", tableName);
        }
    }


}
