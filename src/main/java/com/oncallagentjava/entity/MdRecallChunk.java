package com.oncallagentjava.entity;

import lombok.Data;

@Data
public class MdRecallChunk {
    private String chunkId;
    private String docName;
    private String chunkType;
    private int titleLevel;
    private String parentChunkId;
    private String content;
    private int startLine;
    private int endLine;
    private String chunkCategory;
    private float cosineSimilarity;
    private float weightScore;
    private String parentTitleContent;
}
