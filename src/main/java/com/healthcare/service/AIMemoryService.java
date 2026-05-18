package com.healthcare.service;

import com.healthcare.model.ChatMessage;
import com.healthcare.model.User;
import com.healthcare.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIMemoryService {

    private final ChatMessageRepository chatMessageRepository;

    @Value("${ai.memory.max-messages:20}")
    private int maxMessages;

    // In-memory session store (simulates vector DB memory)
    private final Map<String, List<Map<String, String>>> sessionMemory = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> sessionLastActivity = new ConcurrentHashMap<>();

    public void addToMemory(String sessionId, String role, String content, User user) {
        // Save to DB
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setUser(user);
        message.setTimestamp(LocalDateTime.now());
        chatMessageRepository.save(message);

        // Update in-memory
        sessionMemory.computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(Map.of("role", role, "content", content));
        sessionLastActivity.put(sessionId, LocalDateTime.now());

        // Trim to max messages
        List<Map<String, String>> history = sessionMemory.get(sessionId);
        if (history.size() > maxMessages) {
            sessionMemory.put(sessionId, new ArrayList<>(history.subList(history.size() - maxMessages, history.size())));
        }

        log.debug("Memory updated for session: {}", sessionId);
    }

    public List<Map<String, String>> getMemory(String sessionId) {
        // First try in-memory
        List<Map<String, String>> inMemory = sessionMemory.get(sessionId);
        if (inMemory != null && !inMemory.isEmpty()) {
            return new ArrayList<>(inMemory);
        }

        // Fall back to DB
        List<ChatMessage> dbMessages = chatMessageRepository.findBySessionIdOrderByTimestampAsc(sessionId);
        List<Map<String, String>> history = new ArrayList<>();
        for (ChatMessage msg : dbMessages) {
            history.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        // Cache in memory
        if (!history.isEmpty()) {
            sessionMemory.put(sessionId, history);
            sessionLastActivity.put(sessionId, LocalDateTime.now());
        }

        return history;
    }

    public void clearMemory(String sessionId) {
        sessionMemory.remove(sessionId);
        sessionLastActivity.remove(sessionId);
        chatMessageRepository.deleteBySessionId(sessionId);
        log.info("Memory cleared for session: {}", sessionId);
    }

    public String buildContextSummary(String sessionId) {
        List<Map<String, String>> history = getMemory(sessionId);
        if (history.isEmpty()) return "";

        StringBuilder summary = new StringBuilder("Previous conversation context:\n");
        int start = Math.max(0, history.size() - 6); // last 3 exchanges
        for (int i = start; i < history.size(); i++) {
            Map<String, String> msg = history.get(i);
            summary.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n");
        }
        return summary.toString();
    }

    @Scheduled(fixedRate = 3600000) // cleanup every hour
    public void cleanupExpiredSessions() {
        LocalDateTime expiry = LocalDateTime.now().minusHours(1);
        sessionLastActivity.entrySet().removeIf(entry -> {
            if (entry.getValue().isBefore(expiry)) {
                sessionMemory.remove(entry.getKey());
                return true;
            }
            return false;
        });
        log.debug("Session cleanup completed. Active sessions: {}", sessionMemory.size());
    }

    public int getActiveSessionCount() {
        return sessionMemory.size();
    }
}
