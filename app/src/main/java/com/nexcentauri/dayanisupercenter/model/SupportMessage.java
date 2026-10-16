package com.nexcentauri.dayanisupercenter.model;

public class SupportMessage {
    private final String id;
    private final String text;
    private final String senderType;
    private final long createdAtMillis;

    public SupportMessage(String id, String text, String senderType, long createdAtMillis) {
        this.id = id;
        this.text = text;
        this.senderType = senderType;
        this.createdAtMillis = createdAtMillis;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public String getSenderType() { return senderType; }
    public long getCreatedAtMillis() { return createdAtMillis; }
    public boolean isFromUser() { return "user".equalsIgnoreCase(senderType); }
}
