package com.radiotech.radiotech_backend.service;

import com.google.cloud.firestore.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/** Indexed archives: bounded reads, stable time/ID order, no offset scans. */
public final class ArchiveQueries {
    private ArchiveQueries() {}
    public record Page<T>(List<T> items, String nextCursor, boolean hasMore) {}
    public static List<String> identities(String... values) {
        return Arrays.stream(values).filter(Objects::nonNull).map(String::trim)
                .filter(value -> !value.isEmpty()).distinct().toList();
    }
    public static long timestamp(Object value) {
        if (value instanceof com.google.cloud.Timestamp time) return time.toDate().getTime();
        if (value instanceof String text) {
            try { return Instant.parse(text).toEpochMilli(); }
            catch (java.time.format.DateTimeParseException ignored) {
                try { return java.time.LocalDate.parse(text).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(); }
                catch (java.time.format.DateTimeParseException invalid) { return 0; }
            }
        }
        return 0;
    }
    public static Query operator(Query query, List<String> identities) {
        if (identities.isEmpty() || identities.size() > 2) throw new IllegalArgumentException("Identità archivio non valida.");
        return query.whereArrayContainsAny("operatorRefs", identities);
    }
    public static Query legacyOperator(Query query, String... identities) {
        var filters = new ArrayList<Filter>();
        for (String identity : identities(identities)) for (String field : List.of("operatorId", "operatorFirebaseUid", "operator_uid", "operator_id"))
            filters.add(Filter.equalTo(field, identity));
        if (filters.isEmpty()) throw new IllegalArgumentException("Identità archivio non valida.");
        return query.where(Filter.or(filters.toArray(Filter[]::new)));
    }
    public static <T> Page<T> page(Query query, int limit, String cursor, Function<DocumentSnapshot,T> mapper) throws Exception {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Dimensione pagina tra 1 e 100.");
        query = query.orderBy("archiveAt", Query.Direction.DESCENDING).orderBy(FieldPath.documentId(), Query.Direction.DESCENDING);
        if (cursor != null && !cursor.isBlank()) {
            try {
                if (cursor.length() > 4096) throw new IllegalArgumentException();
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\n", 2);
                if (parts.length != 2 || parts[1].isBlank() || parts[1].contains("/") || parts[1].length() > 1500
                        || parts[1].chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException();
                query = query.startAfter(Long.parseLong(parts[0]), parts[1]);
            } catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Cursore archivio non valido."); }
        }
        var documents = query.limit(limit + 1).get().get().getDocuments();
        boolean hasMore = documents.size() > limit;
        var visible = documents.subList(0, Math.min(limit, documents.size()));
        String next = null;
        if (hasMore) {
            var last = visible.getLast();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (last.getLong("archiveAt") + "\n" + last.getId()).getBytes(StandardCharsets.UTF_8));
        }
        return new Page<>(visible.stream().map(mapper).toList(), next, hasMore);
    }
}
