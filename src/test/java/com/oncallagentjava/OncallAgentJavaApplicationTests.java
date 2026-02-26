package com.oncallagentjava;

import com.oncallagentjava.entity.MdChunk;
import com.oncallagentjava.services.VectorEmbeddingService;
import com.oncallagentjava.services.VectorIndexService;
import com.oncallagentjava.splitter.MdRagSplitter;
import com.oncallagentjava.utils.MilvusUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

@SpringBootTest
class OncallAgentJavaApplicationTests {

    @Autowired
    private VectorIndexService vectorIndexService;
    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    @Test
    void contextLoads() throws IOException {
//        vectorIndexService.indexSingleFile("C:\\Users\\user\\Desktop\\test-rag.md");
        String question = "知识库 Agent 核心技术架构";
        List<Float> floats = vectorEmbeddingService.generateEmbedding(question);
        List<Map<String, Object>> similarChunks  = MilvusUtil.searchSimilar(floats, 10);
        System.out.println("用户问题：" + question);
        System.out.println("检索到相似块数：" + similarChunks.size());
        // 打印检索结果
        for (int i = 0; i < similarChunks.size(); i++) {
            Map<String, Object> chunk = similarChunks.get(i);
            System.out.println((i+1) + "、相似度：" + String.format("%.4f", chunk.get("similarity"))
                    + "，文档：" + chunk.get("doc_name")
                    + "，块类型：" + chunk.get("chunk_type")
                    + "，内容：" + chunk.get("content").toString().substring(0, 100) + "...");
        }
    }
}