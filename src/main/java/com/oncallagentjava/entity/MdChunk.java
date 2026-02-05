package com.oncallagentjava.entity;

import com.oncallagentjava.enums.MdChunkType;
import lombok.Data;

import java.util.UUID;

@Data
public class MdChunk {
    /** 块唯一ID（UUID），向量库主键 */
    private String chunkId;
    /** 父块ID（关联层次化聚合块/上级标题块，原子块的父块为最近的上级标题块） */
    private String parentChunkId;
    /** MD块类型 */
    private MdChunkType chunkType;
    /** 标题层级（仅标题块有值：1-6，其他块为0） */
    private int titleLevel;
    /** 块内容（核心，用于生成向量） */
    private String content;
    /** 所属文档名称（关联知识库源文档） */
    private String docName;
    /** 块在原文档的起始行号（溯源用） */
    private int startLine;
    /** 块在原文档的结束行号（溯源用） */
    private int endLine;
    /** 是否为聚合块（true：粗粒度层次聚合块，false：细粒度原子块） */
    private boolean aggregateChunk;

    // 无参构造（序列化必备）
    public MdChunk() {}

    // 原子块构造器（快速创建）
    public MdChunk(MdChunkType chunkType, int titleLevel, String content, String docName, int startLine, int endLine) {
        this.chunkId = UUID.randomUUID().toString().replace("-", "");
        this.chunkType = chunkType;
        this.titleLevel = titleLevel;
        this.content = content;
        this.docName = docName;
        this.startLine = startLine;
        this.endLine = endLine;
        this.aggregateChunk = false; // 原子块默认非聚合
        this.parentChunkId = "";     // 父块ID后续解析时赋值
    }

    // 聚合块构造器（快速创建）
    public MdChunk(MdChunkType chunkType, String content, String docName, int startLine, int endLine, String parentChunkId) {
        this.chunkId = UUID.randomUUID().toString().replace("-", "");
        this.chunkType = chunkType;
        this.content = content;
        this.docName = docName;
        this.startLine = startLine;
        this.endLine = endLine;
        this.aggregateChunk = true;  // 显式标记为聚合块
        this.parentChunkId = parentChunkId;
        this.titleLevel = 0;         // 聚合块无标题层级
    }

    // 重写toString，方便测试查看结果
    @Override
    public String toString() {
        return "MdChunk{" +
                "chunkId='" + chunkId.substring(0, 8) + "...'" + // 截断ID，方便查看
                ", parentChunkId='" + (parentChunkId.isEmpty() ? "" : parentChunkId.substring(0,8)+"...") + "'" +
                ", chunkType=" + chunkType +
                ", titleLevel=" + titleLevel +
                ", content='" + (content.length() > 50 ? content.substring(0,50) + "..." : content) + "'" +
                ", docName='" + docName + "'" +
                ", startLine=" + startLine +
                ", endLine=" + endLine +
                ", aggregateChunk=" + aggregateChunk +
                '}';
    }
}
