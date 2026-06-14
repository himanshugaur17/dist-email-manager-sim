package com.example.email.server;

public interface EmailServer {
    void sendEmail(String recipient, String subject, String body);
}
