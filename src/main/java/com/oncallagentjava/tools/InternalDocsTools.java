package com.oncallagentjava.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oncallagentjava.services.VectorEmbeddingService;
import com.oncallagentjava.utils.MilvusUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InternalDocsTools {
    private static final Logger logger = LoggerFactory.getLogger(InternalDocsTools.class);

    /** 工具名常量，用于动态构建提示词 */
    public static final String TOOL_QUERY_INTERNAL_DOCS = "queryInternalDocs";

    @Value("${rag.topK}")
    private int topK = 8; // 默认值
    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 查询内部文档工具
     *
     * @param query 搜索查询，描述您要查找的信息
     * @return JSON 格式的搜索结果，包含相关文档内容、相似度分数和元数据
     */
    @Tool(description = "Use this tool to search internal documentation and knowledge base for relevant information. " +
            "It performs RAG (Retrieval-Augmented Generation) to find similar documents and extract processing steps. " +
            "This is useful when you need to understand internal procedures, best practices, or step-by-step guides " +
            "stored in the company's documentation.")
    public String queryInternalDocs(
            @ToolParam(description = "Search query describing what information you are looking for")
            String query) {
        // try {
        //     // 使用向量搜索服务检索相关文档
        //     List<Float> queryVector = vectorEmbeddingService.generateEmbedding(query);
        //     List<Map<String, Object>> searchedSimilar = MilvusUtil.searchSimilar(queryVector, topK);
        //
        //     if (searchedSimilar.isEmpty()) {
        //         return "{\"status\": \"no_results\", \"message\": \"No relevant documents found in the knowledge base.\"}";
        //     }
        //
        //     // 将搜索结果转换为 JSON 格式
        //     String resultJson = objectMapper.writeValueAsString(searchedSimilar);
        //     return resultJson;
        // } catch (Exception e) {
        //     logger.error("[工具错误] queryInternalDocs 执行失败", e);
        //     return String.format("{\"status\": \"error\", \"message\": \"Failed to query internal docs: %s\"}",
        //             e.getMessage());
        // }
        return null;
    }
}
