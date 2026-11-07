package ru.lectograph.app.models;

public record Lecture(long id, String title, String subject, String notes, String audio,
                      long created, long duration, String status) {}
