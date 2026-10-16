package com.nexcentauri.dayanisupercenter.model;

public class AppNotificationItem {
    private final String id;
    private final String title;
    private final String message;
    private final long timeMillis;
    private final String type;
    private final boolean read;

    public AppNotificationItem(String id, String title, String message, long timeMillis, String type, boolean read) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.timeMillis = timeMillis;
        this.type = type;
        this.read = read;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public long getTimeMillis() { return timeMillis; }
    public String getType() { return type; }
    public boolean isRead() { return read; }
}
