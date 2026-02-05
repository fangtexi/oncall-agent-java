package com.oncallagentjava;

import com.oncallagentjava.services.VectorIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
class OncallAgentJavaApplicationTests {

    @Autowired
    private VectorIndexService vectorIndexService;

    @Test
    void contextLoads() throws IOException {
        vectorIndexService.indexSingleFile("docs/memory_high_usage.md");
    }

}
