package com.festpass.ticket;

import com.festpass.event.Event;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Builds a small iCalendar (.ics) file for an event (RFC 5545), so it can be added to any calendar. */
public final class CalendarFiles {

    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private CalendarFiles() {}

    public static String forEvent(Event event, String description, Instant now) {
        StringBuilder ics = new StringBuilder();
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//FestPass//Tickets//EN");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "BEGIN:VEVENT");
        line(ics, "UID:event-" + event.getId() + "@festpass");
        line(ics, "DTSTAMP:" + UTC.format(now));
        line(ics, "DTSTART:" + UTC.format(event.getStartsAt()));
        line(ics, "DTEND:" + UTC.format(event.getEndsAt()));
        line(ics, "SUMMARY:" + escape(event.getTitle()));
        line(ics, "LOCATION:" + escape(event.getVenueName()));
        line(ics, "DESCRIPTION:" + escape(description));
        line(ics, "END:VEVENT");
        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    /** Text values escape backslashes, commas, semicolons and newlines. */
    static String escape(String text) {
        return text == null ? "" : text
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n");
    }

    /** Lines end with CRLF, and long lines are folded at 75 characters with a leading space. */
    private static void line(StringBuilder ics, String content) {
        int start = 0;
        int width = 75;
        while (content.length() - start > width) {
            ics.append(content, start, start + width).append("\r\n ");
            start += width;
            width = 74; // continuation lines start with a space
        }
        ics.append(content.substring(start)).append("\r\n");
    }
}
