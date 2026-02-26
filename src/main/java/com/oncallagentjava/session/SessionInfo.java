package com.oncallagentjava.session;

import com.oncallagentjava.controller.ChatController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

public class SessionInfo {
    private static final Logger logger = LoggerFactory.getLogger(SessionInfo.class);

    private final String sessionId;
    // 存储历史消息对：[{"role": "user", "content": "..."}, {"role": "assistant", "content": "..."}]
    private final List<Map<String, String>> messageHistory;
    private final long createTime;
    private final ReentrantLock lock;
    // 最大历史消息窗口大小（成对计算：用户消息+AI回复=1对）
    private static final int MAX_WINDOW_SIZE = 6;

    public SessionInfo(String sessionId) {
        this.sessionId = sessionId;
        this.messageHistory = new ArrayList<>();
        this.createTime = System.currentTimeMillis();
        this.lock = new ReentrantLock();
    }

    /**
     * 添加一对消息（用户问题 + AI回复）
     * 自动管理历史消息窗口大小
     */
    public void addMessage(String userQuestion, String aiAnswer) {
        lock.lock();
        try {
            // 添加用户消息
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userQuestion);
            messageHistory.add(userMsg);

            // 添加AI回复
            Map<String, String> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", aiAnswer);
            messageHistory.add(assistantMsg);

            // 自动清理：保持最多 MAX_WINDOW_SIZE 对消息
            // 每对消息包含2条记录（user + assistant）
            int maxMessages = MAX_WINDOW_SIZE * 2;
            while (messageHistory.size() > maxMessages) {
                // 成对删除最旧的消息（删除前2条）
                messageHistory.remove(0); // 删除最旧的用户消息
                if (!messageHistory.isEmpty()) {
                    messageHistory.remove(0); // 删除对应的AI回复
                }
            }

            logger.debug("会话 {} 更新历史消息，当前消息对数: {}",
                    sessionId, messageHistory.size() / 2);

        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取历史消息（线程安全）
     * 返回副本以避免并发修改
     */
    public List<Map<String, String>> getHistory() {
        lock.lock();
        try {
            return new ArrayList<>(messageHistory);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清空历史消息
     */
    public void clearHistory() {
        lock.lock();
        try {
            messageHistory.clear();
            logger.info("会话 {} 历史消息已清空", sessionId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前消息对数
     */
    public int getMessagePairCount() {
        lock.lock();
        try {
            return messageHistory.size() / 2;
        } finally {
            lock.unlock();
        }
    }
}
