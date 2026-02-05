package com.oncallagentjava.splitter;

import com.oncallagentjava.entity.MdChunk;
import com.oncallagentjava.enums.MdChunkType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

/**
 * MD文档RAG分片器：结合文档结构分块+层次化分块+大小限制+语义化二次切分
 * 纯Java实现，无外部依赖，适配向量数据库存储
 */
public class MdRagSplitter {
    // 代码块标记
    private static final String CODE_BLOCK_START = "```";
    private static final String CODE_BLOCK_END = "```";
    // 分隔线标记（支持---和***）
    private static final Set<String> HR_MARKS = new HashSet<>(Arrays.asList("---", "***"));
    // 无序列表标记
    private static final Set<String> UNORDERED_LIST_MARKS = new HashSet<>(Arrays.asList("- ", "* ", "+ "));
    // 有序列表前缀正则（匹配 1. 2. 10. 等）
    private static final String ORDERED_LIST_REGEX = "^\\d+\\. .*$";
    // 中文语义分割标点（段落切分用，保证句子完整性）
    private static final Set<Character> SEMANTIC_PUNCTUATIONS = new HashSet<>(Arrays.asList('。', '！', '？', '；'));

    // 【新增】可配置最大分块字符数，默认2000字符，用户可自定义
    private int maxChunkSize;
    // 【新增】最小分块字符数，避免切分过细（如单个标点），默认50字符
    private int minChunkSize = 50;

    /**
     * 默认构造器：默认最大分块2000字符
     */
    public MdRagSplitter() {
        this.maxChunkSize = 2000;
    }

    /**
     * 自定义构造器：用户指定最大分块字符数
     * @param maxChunkSize 最大分块字符数（建议500-4000，适配主流向量模型上下文）
     */
    public MdRagSplitter(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        // 校验：最大分块不能小于最小分块
        if (this.maxChunkSize < this.minChunkSize) {
            this.maxChunkSize = this.minChunkSize;
        }
    }
    public int getMaxChunkSize() {
        return this.maxChunkSize;
    }


    // 【新增】自定义最大/最小分块大小（灵活配置）
    public void setMaxChunkSize(int maxChunkSize) {
        this.maxChunkSize = maxChunkSize;
        if (this.maxChunkSize < this.minChunkSize) {
            this.maxChunkSize = this.minChunkSize;
        }
    }
    public void setMinChunkSize(int minChunkSize) {
        this.minChunkSize = minChunkSize;
        if (this.maxChunkSize < this.minChunkSize) {
            this.maxChunkSize = this.minChunkSize;
        }
    }

    /**
     * 从MD文件读取内容并分片（核心入口方法）
     * @param mdFile MD文件
     * @return 包含原子块+聚合块的所有分片（已做大小限制）
     * @throws IOException 文件读取异常
     */
    public List<MdChunk> splitMdFile(File mdFile) throws IOException {
        if (!mdFile.exists() || !mdFile.getName().endsWith(".md")) {
            throw new IllegalArgumentException("文件不存在或非MD文件：" + mdFile.getAbsolutePath());
        }
        // 读取文件所有行
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(mdFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }
        // 切分原子块 + 聚合粗粒度块
        List<MdChunk> atomicChunks = splitToAtomicChunks(lines, mdFile.getName());
        List<MdChunk> aggregateChunks = aggregateChunksByHierarchy(atomicChunks);
        // 合并原子块和聚合块（向量库可按需存储，原子块为主，聚合块为辅）
        List<MdChunk> allChunks = new ArrayList<>();
        allChunks.addAll(atomicChunks);
        allChunks.addAll(aggregateChunks);
        return allChunks;
    }

    /**
     * 将MD内容行切分为【细粒度原子块】（核心结构分块）
     * 【修改】新增：生成原子块后检查大小，超大分块执行语义化二次切分
     */
    private List<MdChunk> splitToAtomicChunks(List<String> lines, String docName) {
        List<MdChunk> atomicChunks = new ArrayList<>();
        int lineNum = 0; // 行号（从1开始）
        boolean inCodeBlock = false; // 是否在代码块内
        StringBuilder currentBlockContent = new StringBuilder(); // 当前块内容
        MdChunkType currentBlockType = null; // 当前块类型
        int currentBlockStartLine = 0; // 当前块起始行号
        // 标题层级栈：记录当前上下文的标题层级和对应的ChunkID，用于关联子块的父ID
        Deque<Map.Entry<Integer, String>> titleLevelStack = new ArrayDeque<>();

        for (String rawLine : lines) {
            lineNum++;
            String line = rawLine.trim(); // 行内容去首尾空格（保留内部格式）
            // 1. 代码块处理：整块读取，不拆分
            if (inCodeBlock) {
                currentBlockContent.append(rawLine).append("\n");
                if (rawLine.startsWith(CODE_BLOCK_END)) {
                    inCodeBlock = false;
                    // 代码块结束，生成原子块
                    completeAndCheckChunk(atomicChunks, currentBlockType, currentBlockContent, docName, currentBlockStartLine, lineNum, titleLevelStack);
                    resetCurrentBlock(currentBlockContent, currentBlockType, currentBlockStartLine);
                }
                continue;
            }

            // 2. 空行处理：连续空行作为块分隔符，单个空行忽略
            if (line.isEmpty()) {
                if (currentBlockType != null) {
                    completeAndCheckChunk(atomicChunks, currentBlockType, currentBlockContent, docName, currentBlockStartLine, lineNum - 1, titleLevelStack);
                    resetCurrentBlock(currentBlockContent, currentBlockType, currentBlockStartLine);
                }
                continue;
            }

            // 3. 代码块开始：初始化代码块
            if (rawLine.startsWith(CODE_BLOCK_START)) {
                if (currentBlockType != null) {
                    completeAndCheckChunk(atomicChunks, currentBlockType, currentBlockContent, docName, currentBlockStartLine, lineNum - 1, titleLevelStack);
                    resetCurrentBlock(currentBlockContent, currentBlockType, currentBlockStartLine);
                }
                inCodeBlock = true;
                currentBlockType = MdChunkType.CODE_BLOCK;
                currentBlockStartLine = lineNum;
                currentBlockContent.append(rawLine).append("\n");
                continue;
            }

            // 4. 标题处理：# ~ ######，单独切分为标题块
            int titleLevel = getTitleLevel(rawLine);
            if (titleLevel > 0) {
                if (currentBlockType != null) {
                    completeAndCheckChunk(atomicChunks, currentBlockType, currentBlockContent, docName, currentBlockStartLine, lineNum - 1, titleLevelStack);
                    resetCurrentBlock(currentBlockContent, currentBlockType, currentBlockStartLine);
                }
                // 生成标题块（内容为去除#和空格后的纯标题）
                String titleContent = rawLine.substring(titleLevel + 1).trim();
                MdChunk titleChunk = new MdChunk(MdChunkType.TITLE, titleLevel, titleContent, docName, lineNum, lineNum);
                // 维护标题层级栈：弹出比当前层级大的标题（层级回退）
                while (!titleLevelStack.isEmpty() && titleLevelStack.peek().getKey() >= titleLevel) {
                    titleLevelStack.pop();
                }
                // 关联标题块的父ID（栈顶为上级标题）
                if (!titleLevelStack.isEmpty()) {
                    titleChunk.setParentChunkId(titleLevelStack.peek().getValue());
                }
                atomicChunks.add(titleChunk);
                // 将当前标题压入栈（作为后续子块的父标题）
                titleLevelStack.push(new AbstractMap.SimpleEntry<>(titleLevel, titleChunk.getChunkId()));
                continue;
            }

            // 5. 分隔线处理：单独切分为分隔线块
            if (HR_MARKS.contains(line)) {
                if (currentBlockType != null) {
                    completeAndCheckChunk(atomicChunks, currentBlockType, currentBlockContent, docName, currentBlockStartLine, lineNum - 1, titleLevelStack);
                    resetCurrentBlock(currentBlockContent, currentBlockType, currentBlockStartLine);
                }
                MdChunk hrChunk = new MdChunk(MdChunkType.HORIZONTAL_RULE, 0, line, docName, lineNum, lineNum);
                setParentChunkId(hrChunk, titleLevelStack);
                atomicChunks.add(hrChunk);
                continue;
            }

            // 6. 列表处理：有序/无序列表，连续项合并为一个列表块
            if (isListLine(rawLine)) {
                if (currentBlockType == null || currentBlockType != MdChunkType.LIST) {
                    if (currentBlockType != null) {
                        completeAndCheckChunk(atomicChunks, currentBlockType, currentBlockContent, docName, currentBlockStartLine, lineNum - 1, titleLevelStack);
                        resetCurrentBlock(currentBlockContent, currentBlockType, currentBlockStartLine);
                    }
                    currentBlockType = MdChunkType.LIST;
                    currentBlockStartLine = lineNum;
                }
                currentBlockContent.append(rawLine).append("\n");
                continue;
            }

            // 7. 段落处理：剩余内容为段落，连续行合并为一个段落块
            if (currentBlockType == null || currentBlockType != MdChunkType.PARAGRAPH) {
                if (currentBlockType != null) {
                    completeAndCheckChunk(atomicChunks, currentBlockType, currentBlockContent, docName, currentBlockStartLine, lineNum - 1, titleLevelStack);
                    resetCurrentBlock(currentBlockContent, currentBlockType, currentBlockStartLine);
                }
                currentBlockType = MdChunkType.PARAGRAPH;
                currentBlockStartLine = lineNum;
            }
            currentBlockContent.append(rawLine).append("\n");
        }

        // 处理最后一个未结束的块
        if (currentBlockType != null) {
            completeAndCheckChunk(atomicChunks, currentBlockType, currentBlockContent, docName, currentBlockStartLine, lineNum, titleLevelStack);
        }

        return atomicChunks;
    }

    /**
     * 【新增】核心方法：完成当前块生成，检查大小并执行语义化二次切分，最终添加到原子块列表
     * 替代原有completeCurrentBlock，整合「块生成+大小检查+语义切分」
     */
    private void completeAndCheckChunk(List<MdChunk> atomicChunks, MdChunkType type, StringBuilder content,
                                       String docName, int startLine, int endLine,
                                       Deque<Map.Entry<Integer, String>> titleLevelStack) {
        String blockContent = content.toString().trim();
        if (blockContent.isEmpty()) {
            return;
        }
        // 生成原始原子块
        MdChunk originalChunk = new MdChunk(type, 0, blockContent, docName, startLine, endLine);
        setParentChunkId(originalChunk, titleLevelStack);

        // 检查块大小：未超过阈值，直接添加
        if (blockContent.length() <= maxChunkSize) {
            atomicChunks.add(originalChunk);
            return;
        }

        // 【核心】超过阈值，执行语义化二次切分，切分后的子块添加到列表
        List<MdChunk> splitSubChunks = splitLargeChunkBySemantic(originalChunk, startLine, endLine);
        atomicChunks.addAll(splitSubChunks);
    }

    /**
     * 【新增】核心方法：按块类型执行**语义化二次切分**，保证拆分后语义完整
     * 不同块类型采用不同切分策略，子块继承原块所有属性，仅更新内容/行号/ID
     * @param originalChunk 超大原始块
     * @param originalStartLine 原块起始行号
     * @param originalEndLine 原块结束行号
     * @return 切分后的子块列表
     */
    private List<MdChunk> splitLargeChunkBySemantic(MdChunk originalChunk, int originalStartLine, int originalEndLine) {
        List<MdChunk> subChunks = new ArrayList<>();
        MdChunkType chunkType = originalChunk.getChunkType();
        String content = originalChunk.getContent();
        String docName = originalChunk.getDocName();
        String parentChunkId = originalChunk.getParentChunkId();

        // 按块类型执行不同切分策略
        switch (chunkType) {
            case PARAGRAPH:
                // 段落块：按中文语义标点（。！？；）切分，保证句子完整
                subChunks = splitParagraphBySemantic(content, chunkType, docName, parentChunkId, originalStartLine, originalEndLine);
                break;
            case LIST:
                // 列表块：按单个列表项切分，一个项为最小语义单位
                subChunks = splitListByItem(content, chunkType, docName, parentChunkId, originalStartLine, originalEndLine);
                break;
            case CODE_BLOCK:
                // 代码块：按代码行切分，保留单行代码完整性
                subChunks = splitCodeBlockByLine(content, chunkType, docName, parentChunkId, originalStartLine, originalEndLine);
                break;
            default:
                // 标题/分隔线/其他块：天然短块，不会走到这里，强制按字符切分兜底
                subChunks = splitByForceChar(content, chunkType, docName, parentChunkId, originalStartLine, originalEndLine);
                break;
        }

        // 过滤过细的子块（小于最小分块大小，合并到前一个块）
        mergeMinSizeChunks(subChunks);
        return subChunks;
    }

    /**
     * 【新增】段落切分：按中文语义标点切分，优先在。！？；后拆分，保证句子完整
     */
    private List<MdChunk> splitParagraphBySemantic(String content, MdChunkType type, String docName,
                                                   String parentId, int startLine, int endLine) {
        List<MdChunk> subChunks = new ArrayList<>();
        int contentLen = content.length();
        int start = 0;

        for (int i = 0; i < contentLen; i++) {
            char c = content.charAt(i);
            // 遇到语义标点，且当前片段长度超过阈值，执行拆分
            if (SEMANTIC_PUNCTUATIONS.contains(c) && (i - start + 1) >= maxChunkSize) {
                String subContent = content.substring(start, i + 1).trim();
                subChunks.add(createSubChunk(subContent, type, docName, parentId, startLine, endLine));
                start = i + 1;
            }
        }

        // 处理最后一段
        if (start < contentLen) {
            String subContent = content.substring(start).trim();
            subChunks.add(createSubChunk(subContent, type, docName, parentId, startLine, endLine));
        }

        // 若单个句子超过阈值，强制按字符切分兜底
        return subChunks.stream().flatMap(chunk -> {
            if (chunk.getContent().length() > maxChunkSize) {
                return splitByForceChar(chunk.getContent(), type, docName, parentId, startLine, endLine).stream();
            }
            return Collections.singletonList(chunk).stream();
        }).toList();
    }

    /**
     * 【新增】列表切分：按单个列表项切分'（-/*数字.），保留列表项完整性
    */
    private List<MdChunk> splitListByItem(String content, MdChunkType type, String docName,
                                          String parentId, int startLine, int endLine) {
        List<MdChunk> subChunks = new ArrayList<>();
        List<String> listItems = new ArrayList<>();
        StringBuilder currentItem = new StringBuilder();

        // 按换行分割所有行，提取单个列表项
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (isListLine(line)) {
                // 遇到新的列表项，保存上一个项
                if (currentItem.length() > 0) {
                    listItems.add(currentItem.toString().trim());
                    currentItem.setLength(0);
                }
                currentItem.append(line);
            } else {
                // 列表项的换行内容（如项内换行），追加到当前项
                currentItem.append("\n").append(line);
            }
        }
        // 添加最后一个列表项
        if (currentItem.length() > 0) {
            listItems.add(currentItem.toString().trim());
        }

        // 按项聚合，直到接近最大分块大小，避免单个项单独成块（过细）
        StringBuilder currentAgg = new StringBuilder();
        for (String item : listItems) {
            // 若添加当前项后超过阈值，拆分当前聚合块
            if (currentAgg.length() + item.length() > maxChunkSize && currentAgg.length() > 0) {
                subChunks.add(createSubChunk(currentAgg.toString(), type, docName, parentId, startLine, endLine));
                currentAgg.setLength(0);
            }
            currentAgg.append(item).append("\n");
        }
        // 添加最后一个聚合块
        if (currentAgg.length() > 0) {
            subChunks.add(createSubChunk(currentAgg.toString().trim(), type, docName, parentId, startLine, endLine));
        }

        return subChunks;
    }

    /**
     * 【新增】代码块切分：按代码行切分，保留单行代码完整性，避免语法错误
     */
    private List<MdChunk> splitCodeBlockByLine(String content, MdChunkType type, String docName,
                                               String parentId, int startLine, int endLine) {
        List<MdChunk> subChunks = new ArrayList<>();
        String[] codeLines = content.split("\n");
        StringBuilder currentCode = new StringBuilder();

        // 按行聚合，直到接近最大分块大小
        for (String line : codeLines) {
            if (currentCode.length() + line.length() > maxChunkSize && currentCode.length() > 0) {
                subChunks.add(createSubChunk(currentCode.toString(), type, docName, parentId, startLine, endLine));
                currentCode.setLength(0);
            }
            currentCode.append(line).append("\n");
        }
        // 添加最后一个代码块
        if (currentCode.length() > 0) {
            subChunks.add(createSubChunk(currentCode.toString().trim(), type, docName, parentId, startLine, endLine));
        }

        return subChunks;
    }

    /**
     * 【新增】兜底切分：当语义单位仍超过阈值时，强制按字符数切分
     */
    private List<MdChunk> splitByForceChar(String content, MdChunkType type, String docName,
                                           String parentId, int startLine, int endLine) {
        List<MdChunk> subChunks = new ArrayList<>();
        int contentLen = content.length();
        int start = 0;

        while (start < contentLen) {
            int end = Math.min(start + maxChunkSize, contentLen);
            String subContent = content.substring(start, end).trim();
            subChunks.add(createSubChunk(subContent, type, docName, parentId, startLine, endLine));
            start = end;
        }

        return subChunks;
    }

    /**
     * 【新增】创建切分子块：继承原块所有属性，仅生成新ID、设置内容和行号
     */
    private MdChunk createSubChunk(String content, MdChunkType type, String docName,
                                   String parentId, int startLine, int endLine) {
        MdChunk subChunk = new MdChunk(type, 0, content, docName, startLine, endLine);
        subChunk.setParentChunkId(parentId); // 继承原块的父ID，保持层级
        return subChunk;
    }

    /**
     * 【新增】合并过细子块：将小于最小分块大小的子块合并到前一个块，避免分片过细
     */
    private void mergeMinSizeChunks(List<MdChunk> subChunks) {
        if (subChunks.size() <= 1) {
            return;
        }
        // 从后往前遍历，合并过细块
        for (int i = subChunks.size() - 1; i > 0; i--) {
            MdChunk current = subChunks.get(i);
            MdChunk prev = subChunks.get(i - 1);
            if (current.getContent().length() < minChunkSize) {
                // 合并内容
                String mergeContent = prev.getContent() + "\n" + current.getContent();
                prev.setContent(mergeContent);
                // 更新结束行号为当前块的行号
                prev.setEndLine(current.getEndLine());
                // 移除当前过细块
                subChunks.remove(i);
            }
        }
    }

    /**
     * 将原子块按【标题层级】聚合为【粗粒度聚合块】（层次化分块核心，原有逻辑无修改）
     */
    private List<MdChunk> aggregateChunksByHierarchy(List<MdChunk> atomicChunks) {
        List<MdChunk> aggregateChunks = new ArrayList<>();
        if (atomicChunks.isEmpty()) {
            return aggregateChunks;
        }
        String docName = atomicChunks.get(0).getDocName();
        // 存储各层级标题的聚合上下文：key=标题ChunkID，value=【聚合内容+起始行+结束行】
        Map<String, Triple<StringBuilder, Integer, Integer>> titleAggregateMap = new HashMap<>();
        // 标题层级映射：key=标题ChunkID，value=标题层级
        Map<String, Integer> titleLevelMap = new HashMap<>();
        // 父标题映射：key=原子块/子标题ChunkID，value=父标题ChunkID
        Map<String, String> parentTitleMap = new HashMap<>();

        // 第一步：初始化标题聚合上下文，记录标题层级和父关系
        for (MdChunk chunk : atomicChunks) {
            if (chunk.getChunkType() == MdChunkType.TITLE) {
                titleLevelMap.put(chunk.getChunkId(), chunk.getTitleLevel());
                parentTitleMap.put(chunk.getChunkId(), chunk.getParentChunkId());
                // 【已修正】初始化聚合内容为标题本身，补全append方法，解决语法错误
                StringBuilder sb = new StringBuilder("【").append(chunk.getTitleLevel()).append("级标题】").append(chunk.getContent()).append("\n\n");
                titleAggregateMap.put(chunk.getChunkId(), new Triple<>(sb, chunk.getStartLine(), chunk.getEndLine()));
            }
        }

        // 第二步：遍历原子块，将非标题块聚合到所有上级标题的上下文中
        for (MdChunk chunk : atomicChunks) {
            if (chunk.getChunkType() == MdChunkType.TITLE) {
                continue;
            }
            // 递归获取当前块的所有上级标题ID
            Set<String> parentTitleIds = new HashSet<>();
            String currentParentId = chunk.getParentChunkId();
            while (!currentParentId.isEmpty() && titleLevelMap.containsKey(currentParentId)) {
                parentTitleIds.add(currentParentId);
                currentParentId = parentTitleMap.get(currentParentId);
            }
            // 将当前块内容追加到所有上级标题的聚合上下文中
            for (String titleId : parentTitleIds) {
                Triple<StringBuilder, Integer, Integer> triple = titleAggregateMap.get(titleId);
                triple.first.append("【").append(chunk.getChunkType().getDesc()).append("】").append(chunk.getContent()).append("\n\n");
                triple.third = chunk.getEndLine(); // 更新聚合块结束行号
            }
        }

        // 第三步：生成粗粒度聚合块
        for (Map.Entry<String, Triple<StringBuilder, Integer, Integer>> entry : titleAggregateMap.entrySet()) {
            String titleId = entry.getKey();
            Triple<StringBuilder, Integer, Integer> triple = entry.getValue();
            String aggregateContent = triple.first.toString().trim();
            int startLine = triple.second;
            int endLine = triple.third;
            // 聚合块的父ID为原标题的父ID
            String parentId = parentTitleMap.get(titleId);
            MdChunk aggregateChunk = new MdChunk(MdChunkType.OTHER, aggregateContent, docName, startLine, endLine, parentId);
            aggregateChunks.add(aggregateChunk);
        }

        return aggregateChunks;
    }

    // ---------------------- 原有工具方法，无修改 ----------------------
    /**
     * 判断是否为标题行，返回标题层级（1-6），非标题返回0
     */
    private int getTitleLevel(String line) {
        if (line == null || line.length() < 2) {
            return 0;
        }
        int level = 0;
        for (char c : line.toCharArray()) {
            if (c == '#') {
                level++;
            } else {
                break;
            }
        }
        // 验证：层级1-6，且#后是空格
        return (level >= 1 && level <= 6 && line.charAt(level) == ' ') ? level : 0;
    }

    /**
     * 判断是否为列表行（有序/无序）
     */
    private boolean isListLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        // 无序列表：行首以-/*/+ 开头（后跟空格）
        for (String mark : UNORDERED_LIST_MARKS) {
            if (line.startsWith(mark)) {
                return true;
            }
        }
        // 有序列表：行首匹配 数字. 空格
        return line.matches(ORDERED_LIST_REGEX);
    }

    /**
     * 为块设置父块ID（关联最近的上级标题）
     */
    private void setParentChunkId(MdChunk chunk, Deque<Map.Entry<Integer, String>> titleLevelStack) {
        if (!titleLevelStack.isEmpty()) {
            chunk.setParentChunkId(titleLevelStack.peek().getValue());
        }
    }

    /**
     * 重置当前块的临时变量
     */
    private void resetCurrentBlock(StringBuilder content, MdChunkType type, int startLine) {
        content.setLength(0);
    }

    // ---------------------- 内部辅助类，无修改 ----------------------
    /**
     * 简单三元组类，用于存储聚合块的【内容构建器+起始行+结束行】
     */
    private static class Triple<A, B, C> {
        A first;
        B second;
        C third;

        Triple(A first, B second, C third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }
}
