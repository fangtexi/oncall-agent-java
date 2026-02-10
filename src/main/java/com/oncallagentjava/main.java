package com.oncallagentjava;


import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.oncallagentjava.entity.MdChunk;
import com.oncallagentjava.services.VectorEmbeddingService;
import com.oncallagentjava.splitter.MdRagSplitter;
import org.springframework.ai.evaluation.EvaluationRequest;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class main {
    public static void main(String[] args) {
        // VectorEmbeddingService embeddingService = new VectorEmbeddingService();
        // List<Float> floatList = embeddingService.generateEmbedding("知识库 Agent 的架构设计需遵循高可扩展性、高检索精度、低延迟三大核心原则，其中高检索精度依赖于合理的文档分片策略和高效的向量检索机制，低延迟则要求向量数据库的查询性能和大模型的响应速度达到平衡，高可扩展性则需要各个模块解耦设计，支持后续新增数据源、更换大模型、扩展向量数据库等操作。");
        // System.out.println(floatList);

        // try {
        //     Path path = Paths.get("D:\\desktop\\test-rag.md").normalize();
        //     File file = path.toFile();
        //     MdRagSplitter mdRagSplitter = new MdRagSplitter();
        //     List<MdChunk> allChunks = mdRagSplitter.splitMdFile(file);
        //     System.out.println("=========== 文档分片完成："+ allChunks.size() + "个分片，最大分块为："+mdRagSplitter.getMaxChunkSize() + "字符 ===========");
        //     List<MdChunk> atomicChunks = allChunks.stream().filter(c -> !c.isAggregateChunk()).toList();
        //     List<MdChunk> aggregateChunks = allChunks.stream().filter(MdChunk::isAggregateChunk).toList();
        //     System.out.println("=========== 细粒度原子块数："+ atomicChunks.size()+ "个 ===========");
        //     System.out.println("=========== 粗粒度原子块数："+ aggregateChunks.size()+ "个 ===========");
        //     // 输出原子块（新增打印每个块的字符数）
        //     System.out.println("\n===== 细粒度原子块（含字符数） =====");
        //     atomicChunks.forEach(chunk -> {
        //         int contentLen = chunk.getContent().length();
        //         System.out.println(chunk + " | 字符数：" + contentLen);
        //     });
        //
        //     // 输出聚合块
        //     System.out.println("\n===== 粗粒度聚合块 =====");
        //     aggregateChunks.forEach(chunk -> System.out.println(chunk));
        // }catch (Exception e) {
        //
        // }

        // 1. 创建测试用的Map（包含不同类型的键值对）
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("username", "李四");
        userMap.put("age", 30);
        userMap.put("isVip", true);
        userMap.put("balance", 199.99);

        // 2. 初始化Gson对象（可复用，建议全局单例）
        Gson gson = new Gson();

        // 3. 核心转换逻辑：Map → JsonElement → JsonObject
        JsonElement jsonElement = gson.toJsonTree(userMap);
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        // 4. 验证转换结果（读取JsonObject中的值）
        System.out.println("完整JsonObject：" + jsonObject);
        String username = jsonObject.get("username").getAsString();
        double balance = jsonObject.get("balance").getAsDouble();
        System.out.println("用户名：" + username + "，余额：" + balance);




    }
}