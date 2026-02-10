package com.oncallagentjava.services;

import com.oncallagentjava.entity.MdChunk;
import com.oncallagentjava.splitter.MdRagSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 向量索引服务
 * 负责读取文件、生成向量、存储到 Milvus
 */
@Service
public class VectorIndexService {
    private static final Logger logger = LoggerFactory.getLogger(VectorIndexService.class);

    public void indexSingleFile(String filePath) throws IOException {
        Path path = Paths.get(filePath).normalize();
        File file = path.toFile();

        if (!file.exists() || !file.isFile()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }
        logger.info("开始索引文件: {}", path);
        // todo 2. 删除旧数据
        // 3. 文档分片
        MdRagSplitter mdRagSplitter = new MdRagSplitter();
        List<MdChunk> allChunks = mdRagSplitter.splitMdFile(file);
        logger.info("=========== 文档分片完成：{}个分片，最大分块为：{} 字符 ===========",allChunks.size(),mdRagSplitter.getMaxChunkSize());
        List<MdChunk> atomicChunks = allChunks.stream().filter(c -> !c.isAggregateChunk()).toList();
        List<MdChunk> aggregateChunks = allChunks.stream().filter(MdChunk::isAggregateChunk).toList();
        logger.info("=========== 细粒度原子块数：{} 个 ===========",atomicChunks.size());
        logger.info("=========== 粗粒度原子块数：{} 个 ===========",aggregateChunks.size());
    }


}
