package com.kbv.education.service.ai;

import com.kbv.education.entity.User;
import com.kbv.education.entity.enums.RoleType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Removes names, emails and phone numbers before any text is sent to an AI
 * (memo 12.10.2026, section 3). A student's name becomes their code-name (e.g. "S-0007");
 * anyone else's name (parents, staff) becomes "[name]". Built once per run from the
 * current user list.
 */
public final class PiiRedactor {

    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    /** 8 or more digits, optionally with +, spaces, dots, dashes or brackets between them. */
    private static final Pattern PHONE = Pattern.compile("(?<![\\w/])\\+?\\(?\\d(?:[\\s.()-]*\\d){7,}(?![\\w/])");
    /** Shorter names match too many ordinary words. */
    private static final int MIN_NAME_LENGTH = 3;

    private final Pattern names;
    private final Map<String, String> replacements;

    private PiiRedactor(Pattern names, Map<String, String> replacements) {
        this.names = names;
        this.replacements = replacements;
    }

    public static PiiRedactor forUsers(List<User> users) {
        Map<String, String> replacements = new HashMap<>();
        for (User user : users) {
            boolean student = user.getRole() != null && user.getRole().getName() == RoleType.STUDENT;
            String replacement = student && user.getCodeName() != null ? user.getCodeName() : "[name]";
            put(replacements, user.getFullName(), replacement);
            put(replacements, user.getFirstName(), replacement);
            put(replacements, user.getLastName(), replacement);
        }
        if (replacements.isEmpty()) {
            return new PiiRedactor(null, replacements);
        }
        List<String> keys = new ArrayList<>(replacements.keySet());
        keys.sort(Comparator.comparingInt(String::length).reversed());
        String alternation = keys.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        Pattern pattern = Pattern.compile("(?<![\\p{L}\\p{N}])(" + alternation + ")(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return new PiiRedactor(pattern, replacements);
    }

    private static void put(Map<String, String> map, String name, String replacement) {
        if (name == null) {
            return;
        }
        String key = name.strip().toLowerCase(Locale.ROOT);
        if (key.length() >= MIN_NAME_LENGTH) {
            // A name shared by a student and a non-student keeps the student's code-name.
            map.merge(key, replacement, (a, b) -> a.startsWith("S-") ? a : b);
        }
    }

    public String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = EMAIL.matcher(text).replaceAll("[email]");
        out = PHONE.matcher(out).replaceAll("[phone]");
        if (names == null) {
            return out;
        }
        Matcher m = names.matcher(out);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = replacements.getOrDefault(m.group(1).toLowerCase(Locale.ROOT), "[name]");
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
