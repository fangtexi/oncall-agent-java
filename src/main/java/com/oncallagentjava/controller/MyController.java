package com.oncallagentjava.controller;

import com.oncallagentjava.services.VectorIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class MyController {
    // private final

    @Autowired
    private VectorIndexService vectorIndexService;

    @GetMapping("/test")
    public void test() throws IOException {
        vectorIndexService.indexSingleFile("docs/memory_high_usage.md");

    }
}
