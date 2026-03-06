package com.oncallagentjava.services;

import com.oncallagentjava.entity.MdRecallChunk;
import com.oncallagentjava.splitter.MdRagSplitter;
import com.oncallagentjava.utils.MilvusUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MdRagRecallService {
    private final MdRagSplitter mdRagSplitter;
    private final MilvusUtil milvusUtil;
    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    public MdRagRecallService() {
        this.mdRagSplitter = new MdRagSplitter(2000);
        this.milvusUtil = new MilvusUtil();
    }

    /**
     * 召回入口方法
     * @param question 用户提问
     * @param filterKeywords 结构化过滤关键词
     * @param topk 最终返回片数
     * @return 排序后的召回结果
     */
    public List<MdRecallChunk> recall(String question,String filterKeywords,int topk) {
        // 提问向量化
        List<Float> floats = vectorEmbeddingService.generateEmbedding(question);
        // 召回
        List<MdRecallChunk> mdRecallChunks = milvusUtil.optimalRecall(floats, filterKeywords, topk);
        return mdRecallChunks;
    }


}
