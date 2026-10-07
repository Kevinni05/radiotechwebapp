package com.radiotech.radiotech_backend.controller;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import com.radiotech.radiotech_backend.security.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/v1/calendar")
public class CalendarController {
    private String tenant() { return TenantAccessPolicy.requireTenantAccess(SecurityContextAccessor.currentTenantId(), null); }
    private String uid() { String uid = SecurityContextAccessor.currentUid(); if (uid == null) throw new SecurityException("Accedi per usare il calendario."); return uid; }
    private String key(String tenant, String uid, String day) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((tenant+":"+uid+":"+day).getBytes(StandardCharsets.UTF_8))); }
    public static String validateDay(String value) { try { return LocalDate.parse(value).toString(); } catch (Exception e) { throw new IllegalArgumentException("Data del calendario non valida."); } }
    public static String validateReminder(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try { Instant at = Instant.parse(value.toString()); if (!at.isAfter(Instant.now())) throw new IllegalArgumentException(); return at.toString(); }
        catch (Exception e) { throw new IllegalArgumentException("Il promemoria deve indicare un istante futuro valido."); }
    }
    @GetMapping public List<Map<String,Object>> list() throws Exception {
        String t = tenant(), u = uid();
        List<Map<String,Object>> notes = new ArrayList<>();
        // One indexed equality keeps this portable; enforce the owner before returning data.
        for (var doc: FirestoreClient.getFirestore().collection("calendarNotes").whereEqualTo("tenantId", t).get().get().getDocuments())
            if (u.equals(doc.getString("ownerUid"))) { var data = new LinkedHashMap<>(doc.getData()); data.put("id", doc.getId()); notes.add(data); }
        notes.sort(Comparator.comparing(n -> n.get("day").toString())); return notes;
    }
    @PutMapping("/{day}") public Map<String,Object> save(@PathVariable String day, @RequestBody Map<String,Object> request) throws Exception {
        String t = tenant(), u = uid(), d = validateDay(day);
        String text = Objects.toString(request.get("note"), "").trim();
        if (text.length() > 4000) throw new IllegalArgumentException("La nota supera 4000 caratteri.");
        String reminder = validateReminder(request.get("reminderAt"));
        if (reminder != null && text.isBlank()) throw new IllegalArgumentException("Aggiungi una nota per il promemoria.");
        var db = FirestoreClient.getFirestore(); var ref = db.collection("calendarNotes").document(key(t,u,d));
        // Resolve the notification target from persisted ownership, never from client input.
        String target = "UID:"+u;
        for (var op : db.collection("operators").whereEqualTo("firebaseUid", u).get().get().getDocuments())
            if (t.equals(op.getString("tenantId"))) { target = op.getId(); break; }
        Map<String,Object> data = new LinkedHashMap<>();
        data.put("tenantId", t); data.put("ownerUid", u); data.put("day", d); data.put("note", text);
        data.put("reminderAt", reminder); data.put("reminderPending", reminder != null); data.put("target", target);
        data.put("revision", UUID.randomUUID().toString()); data.put("updatedAt", Instant.now().toString());
        db.runTransaction(tx -> { var current = tx.get(ref).get(); if (current.exists() && (!t.equals(current.getString("tenantId")) || !u.equals(current.getString("ownerUid")))) throw new SecurityException("Nota non accessibile."); tx.set(ref, data); return null; }).get();
        return data;
    }
}
