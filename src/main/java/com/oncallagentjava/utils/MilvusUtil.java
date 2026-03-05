package com.oncallagentjava.utils;

import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.oncallagentjava.config.VectorConfig;
import com.oncallagentjava.entity.MdChunk;
import com.oncallagentjava.entity.MdRecallChunk;
import com.oncallagentjava.services.VectorEmbeddingService;
import io.milvus.param.dml.InsertParam;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
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

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * milvus 向量数据库操作类
 * 封装连接、建表、创索引、批量插入、相似性查询，适配MD分片的MdChunk元数据
 */
public class MilvusUtil {
    private static final Logger logger = LoggerFactory.getLogger(VectorEmbeddingService.class);

    // 单例milvus客户端
    private static volatile MilvusClientV2 milvusClient;

    // 权重配置（可根据实际效果调整）
    private static final Map<String, Float> CHUNK_TYPE_WEIGHT = new HashMap<String, Float>() {{
        put("TITLE", 1.2f);
        put("CODE_BLOCK", 1.1f);
        put("LIST", 1.05f);
        put("PARAGRAPH", 1.0f);
        put("HORIZONTAL_RULE", 0.95f);
        put("OTHER", 0.95f);
    }};
    private static final float ATOMIC_WEIGHT = 1.0f;
    private static final float AGGREGATE_WEIGHT = 0.9f;
    private static final Map<Integer, Float> TITLE_LEVEL_WEIGHT = new HashMap<Integer, Float>() {{
        put(1, 1.1f);
        put(2, 1.05f);
        put(3, 1.0f);
        put(4, 1.0f);
        put(5, 1.0f);
        put(6, 1.0f);
    }};
    // 缓存标题分片（用于上下文补全，避免重复查询）
    // private Map<String, String> titleChunkCache = new HashMap<>();

    /**
     * 获取milvus客户端
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
        // 分块类型
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("title_level")
                .dataType(DataType.Int8)
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
        // 分片分类（ATOMIC：原子块，AGGREGATE：聚合块）
        collectionSchema.addField(AddFieldReq.builder()
                .fieldName("chunk_category")
                .dataType(DataType.VarChar)
                .maxLength(50)
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
        logger.info("Milvus 集合初始化完成：{}", VectorConfig.MILVUS_TABLE_NAME_1);

    }

    /**
     * 插入分片数据到 Milvus
     * @param chunks  细粒度原子块列表
     * @param vectors 向量列表，顺序与 chunks 完全一致
     */
    public static void insertDataToMilvus(List<MdChunk> chunks, List<List<Float>> vectors) {
        if (chunks == null || chunks.isEmpty() || vectors == null || vectors.isEmpty() || chunks.size() != vectors.size()) {
            throw new IllegalArgumentException("原子块列表与向量列表长度必须一致！");
        }
        MilvusClientV2 client = getMilvusClient();

        List<JsonObject> dataList = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            MdChunk chunk = chunks.get(i);
            List<Float> vector = vectors.get(i);
            Map<String, Object> chunkMap = new HashMap<>();
            chunkMap.put("chunk_id", chunk.getChunkId());
            chunkMap.put("parent_chunk_id", chunk.getParentChunkId());
            chunkMap.put("chunk_type", chunk.getChunkType());
            chunkMap.put("doc_name", chunk.getDocName());
            chunkMap.put("start_line", chunk.getStartLine());
            chunkMap.put("end_line", chunk.getEndLine());
            chunkMap.put("content", chunk.getContent());
            chunkMap.put("vector", vector);
            chunkMap.put("title_level",chunk.getTitleLevel());
            chunkMap.put("chunk_category",chunk.isAggregateChunk() ? "AGGREGATE":"ATOMIC");

            Gson gson = new Gson();
            JsonElement jsonTree = gson.toJsonTree(chunkMap);
            JsonObject jsonObject = jsonTree.getAsJsonObject();
            dataList.add(jsonObject);
        }
        InsertReq insertReq = InsertReq.builder()
                .collectionName(VectorConfig.MILVUS_TABLE_NAME_1)
                .data(dataList)
                .build();
        InsertResp insertResp = client.insert(insertReq);
        logger.info("insertResp:{}", insertResp);
        // 加载表
        loadCollection(VectorConfig.MILVUS_TABLE_NAME_1);
        logger.info("所有数据插入完成，总条数：{}", chunks.size());
    }

    /**
     * 最优召回核心逻辑（复用原有，仅适配V2的SearchReq）
     */
    public List<MdRecallChunk> optimalRecall(Float[] queryVector, String filterKeywords, int topK) {
        // 1. 分层召回：原子块(100) + 聚合块(20)
        List<MdRecallChunk> atomicChunks = recallByCategory(queryVector, "ATOMIC", 100, filterKeywords);
        List<MdRecallChunk> aggregateChunks = recallByCategory(queryVector, "AGGREGATE", 20, filterKeywords);
        // 2. 合并召回结果
        List<MdRecallChunk> allRecallChunks = new ArrayList<>();
        allRecallChunks.addAll(atomicChunks);
        allRecallChunks.addAll(aggregateChunks);
        // 3. 混合权重排序（核心：余弦相似度×多维度权重）
        calculateWeightScore(allRecallChunks);
        allRecallChunks.sort((a, b) -> Float.compare(b.getWeightScore(), a.getWeightScore()));
        // 4. 去重
        List<MdRecallChunk> deduplicatedChunks = deduplicateByContent(allRecallChunks);
        // 5. 上下文补全（补充父标题内容）
        completeParentTitle(deduplicatedChunks);
        // 6. 截断 top k
        if (deduplicatedChunks.size() > topK) {
            deduplicatedChunks = deduplicatedChunks.subList(0, topK);
        }

        logger.info("最优召回完成，最终返回 {} 个分片",deduplicatedChunks);
        return deduplicatedChunks;
    }

    /**
     * 上下文补全：补充父标题内容
     */
    private void completeParentTitle(List<MdRecallChunk> recallChunks) {
        for (MdRecallChunk chunk : recallChunks) {
            String parentChunkId = chunk.getParentChunkId();
            if (parentChunkId == null || parentChunkId.isEmpty()) {
                continue;
            }
            // 获取父分块内容 todo
            String parentTitle = "";
            if (parentTitle != null) {
                chunk.setParentTitleContent(parentTitle);
            }
        }
    }
    /**
     * 计算加权得分（核心：多维度权重相乘）
     */
    private void calculateWeightScore(List<MdRecallChunk> recallChunks) {
        for (MdRecallChunk chunk : recallChunks) {
            // 1. 块类型权重
            float typeWeight = CHUNK_TYPE_WEIGHT.getOrDefault(chunk.getChunkType(), 1.0f);
            // 2. 分片分类权重
            float categoryWeight = chunk.getChunkCategory().equals("ATOMIC") ? ATOMIC_WEIGHT : AGGREGATE_WEIGHT;
            // 3. 标题层级权重
            float levelWeight = TITLE_LEVEL_WEIGHT.getOrDefault(chunk.getTitleLevel(), 1.0f);
            // 4. 最终加权得分
            float finalScore = chunk.getCosineSimilarity() * typeWeight * categoryWeight * levelWeight;
            chunk.setWeightScore(finalScore);
        }
    }

    /**
     * 基于内容MD5去重（避免原子块和聚合块重复）
     */
    private List<MdRecallChunk> deduplicateByContent(List<MdRecallChunk> recallChunks) {
        Map<String, MdRecallChunk> contentMd5Map = new HashMap<>();
        for (MdRecallChunk chunk : recallChunks) {
            try {
                // 计算内容MD5
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] digest = md.digest(chunk.getContent().getBytes(StandardCharsets.UTF_8));
                String md5 = new BigInteger(1, digest).toString(16);

                // 保留得分更高的分片
                if (!contentMd5Map.containsKey(md5) || contentMd5Map.get(md5).getWeightScore() < chunk.getWeightScore()) {
                    contentMd5Map.put(md5, chunk);
                }
            } catch (Exception e) {
                logger.error("计算MD5失败：{}",e.getMessage());
                contentMd5Map.put(UUID.randomUUID().toString(), chunk); // 兜底
            }
        }
        return new ArrayList<>(contentMd5Map.values());
    }

    /**
     * 分层召回逻辑
     * @param queryVector 问题向量
     * @param category 分片类型（聚合/原子）
     * @param topN
     * @param filterKeywords
     * @return
     */
    private List<MdRecallChunk> recallByCategory(Float[] queryVector, String category, int topN, String filterKeywords) {
        MilvusClientV2 client = getMilvusClient();
        // 构造过滤表达式
        StringBuilder filterExpr = new StringBuilder();
        filterExpr.append("chunk_category == '").append(category).append("'");
        if (filterKeywords != null && !filterKeywords.isEmpty()) {
            if (filterKeywords.contains("代码")) {
                filterExpr.append(" && chunk_type == 'CODE_BLOCK'");
            } else if (filterKeywords.contains("列表")) {
                filterExpr.append(" && chunk_type == 'LIST'");
            } else if (filterKeywords.contains("标题")) {
                filterExpr.append(" && chunk_type == 'TITLE'");
            }
        }
        // 构造 SearchReq
        SearchReq searchReq = SearchReq.builder()
                .collectionName(VectorConfig.MILVUS_TABLE_NAME_1)
                .databaseName(VectorConfig.MILVUS_DATABASE_NAME)
                .metricType(VectorConfig.MILVUS_METRIC_TYPE)
                .topK(topN)
                .annsField("vector")
                .filter(filterExpr.toString())
                .data(List.of(new FloatVec(List.of(queryVector))))
                .outputFields(List.of( // 需要返回的字段
                        "chunk_id", "doc_name", "chunk_type", "title_level",
                        "parent_chunk_id", "content", "start_line", "end_line", "chunk_category"
                ))
                .consistencyLevel(ConsistencyLevel.EVENTUALLY)
                .build();
        // 执行搜索
        SearchResp searchResp = client.search(searchReq);
        if (searchResp.getSearchResults() == null || searchResp.getSearchResults().isEmpty()) {
            logger.info("Milvus 召回{}类型分片为空", category);
            return new ArrayList<>();
        }
        // 解析召回结果
        List<MdRecallChunk> recallChunks = new ArrayList<>();
        List<SearchResp.SearchResult> searchResults = searchResp.getSearchResults().get(0);// 单向量搜索取第一个结果
        for (int i = 0; i < searchResults.size(); i++) {
            // 获取分片相似度
            float similarity = searchResults.get(i).getScore();
            // 获取字段数据（V2返回Map<String, Object>）
            Map<String, Object> fieldMap = searchResults.get(i).getEntity();
            MdRecallChunk recallChunk = new MdRecallChunk();
            recallChunk.setChunkId((String) fieldMap.get("chunk_id"));
            recallChunk.setDocName((String) fieldMap.get("doc_name"));
            recallChunk.setChunkType((String) fieldMap.get("chunk_type"));
            recallChunk.setTitleLevel((Integer) fieldMap.get("title_level"));
            recallChunk.setParentChunkId((String) fieldMap.get("parent_chunk_id"));
            recallChunk.setContent((String) fieldMap.get("content"));
            recallChunk.setStartLine((Integer) fieldMap.get("start_line"));
            recallChunk.setEndLine((Integer) fieldMap.get("end_line"));
            recallChunk.setChunkCategory((String) fieldMap.get("chunk_category"));
            recallChunk.setCosineSimilarity(similarity);
            recallChunks.add(recallChunk);
        }
        return recallChunks;
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

    private String searchById() {
        return null;
    }
}