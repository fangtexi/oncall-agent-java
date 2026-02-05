package com.oncallagentjava.enums;

/**
 * MD块类型枚举，基于原生文档结构定义
 */
public enum MdChunkType {
    TITLE("标题"),        // 各级标题 # ~ ######
    PARAGRAPH("段落"),    // 普通文本段落
    CODE_BLOCK("代码块"), // ``` 包裹的代码块
    LIST("列表"),         // 有序/无序列表（-/*/+ / 1./2.）
    HORIZONTAL_RULE("分隔线"), // ---/***
    OTHER("其他");        // 未识别的内容

    private final String desc;

    MdChunkType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }
}
