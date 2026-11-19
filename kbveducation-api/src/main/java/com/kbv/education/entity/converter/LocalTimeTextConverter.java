package com.kbv.education.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;

/**
 * Stores a wall-clock time as "HH:mm" text. A TIME column would be shifted by the server's
 * UTC offset, because the app sets {@code hibernate.jdbc.time_zone: UTC}; "23:00 Dubai"
 * must stay 23:00 whatever time zone the server runs in.
 */
@Converter
public class LocalTimeTextConverter implements AttributeConverter<LocalTime, String> {

    @Override
    public String convertToDatabaseColumn(LocalTime time) {
        return time == null ? null : String.format("%02d:%02d", time.getHour(), time.getMinute());
    }

    @Override
    public LocalTime convertToEntityAttribute(String text) {
        return text == null || text.isBlank() ? null : LocalTime.parse(text.strip());
    }
}
