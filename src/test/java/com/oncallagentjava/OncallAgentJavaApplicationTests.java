package com.oncallagentjava;

import com.oncallagentjava.services.VectorEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest
class OncallAgentJavaApplicationTests {
    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    @Test
    void contextLoads() throws IOException {
        List<Float> floatList = vectorEmbeddingService.generateEmbedding("知识库 Agent 的架构设计需遵循高可扩展性、高检索精度、低延迟三大核心原则，其中高检索精度依赖于合理的文档分片策略和高效的向量检索机制，低延迟则要求向量数据库的查询性能和大模型的响应速度达到平衡，高可扩展性则需要各个模块解耦设计，支持后续新增数据源、更换大模型、扩展向量数据库等操作。");
        System.out.println(floatList);
    }

}
