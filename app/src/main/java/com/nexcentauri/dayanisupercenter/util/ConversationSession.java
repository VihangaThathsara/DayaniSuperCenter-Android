package com.nexcentauri.dayanisupercenter.util;

public final class ConversationSession {
    public static final String LEGACY = "legacy";

    private ConversationSession() {}

    public static String normalize(String session) {
        return session == null || session.isEmpty() ? LEGACY : session;
    }

    public static boolean includes(String activeSession, String messageSession) {
        return normalize(activeSession).equals(normalize(messageSession));
    }
}
