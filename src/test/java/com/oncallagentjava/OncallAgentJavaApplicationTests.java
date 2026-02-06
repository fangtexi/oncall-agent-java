package com.oncallagentjava;

import com.oncallagentjava.services.VectorEmbeddingService;
import com.oncallagentjava.services.VectorIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.List;

@SpringBootTest
class OncallAgentJavaApplicationTests {

    @Autowired
    private VectorIndexService vectorIndexService;
    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    @Test
    void contextLoads() throws IOException {
        List<Float> floatList = vectorEmbeddingService.generateEmbedding("模型的能力主要源于庞大的训练数据，这也意味着无论模型多强大，它的知识都是固化的。 它无法掌握企业的新产品、专属 API、数据库实时信息等业务独有内容，所有能力都基于预训练数据中的模式与逻辑。模型不了解企业业务场景的独有规则和工具，需要企业将这些信息结构化提供，才能动态理解并结合自身通用知识生成内容。此外，模型只是 AI 原生应用的重要组成部分，二者并不等价。  ");
        System.out.println(floatList);
    }

}
