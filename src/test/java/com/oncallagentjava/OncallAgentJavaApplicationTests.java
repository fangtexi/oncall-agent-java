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

@SpringBootTest
class OncallAgentJavaApplicationTests {

    @Autowired
    private VectorIndexService vectorIndexService;
    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    @Test
    void contextLoads() throws IOException {
        vectorIndexService.indexSingleFile("C:\\Users\\user\\Desktop\\test-rag.md");
    }

}
