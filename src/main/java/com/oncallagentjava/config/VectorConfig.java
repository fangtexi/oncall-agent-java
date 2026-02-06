package com.oncallagentjava.config;

import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.v2.common.IndexParam;
import org.checkerframework.checker.index.qual.PolyUpperBound;

public class VectorConfig {
    // =================== 阿里云DashScope 配置 ===================
    public static final int VECTOR_DIMENSION = 1024; // text-embedding-v4 默认维度为1024
    // =================== MILVUS 配置 ===================
    public static final String MILVUS_HOST = "127.0.0.1";
    public static final String MILVUS_PORT = "19530";
    public static final String MILVUS_TOKEN = "root:Milvus";
    public static final String MILVUS_DATABASE_NAME = "default";
    public static final String MILVUS_TABLE_NAME_1 = "md_knowledge_rag";
    // 向量索引类型(HNSM：海量数据推荐，检索速度快 )
    public static final IndexParam.IndexType MILVUS_INDEX_TYPE = IndexParam.IndexType.HNSW;
    // 相似度度量方式
    public static final IndexParam.MetricType MILVUS_METRIC_TYPE = IndexParam.MetricType.COSINE;
    // HNSW索引参数（M：候选节点数，EF_CONSTRUCTION：构建时的搜索宽度，经验值即可）
    public static final int HNSW_M = 16;
    public static final int HNSW_EF_CONSTRUCTION = 64;
    // Milvus批量插入条数（建议500条以内，平衡性能）
    public static final int MILVUS_BATCH_SIZE = 500;
}
