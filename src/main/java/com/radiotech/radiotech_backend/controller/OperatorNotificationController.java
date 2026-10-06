package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping({"/api/operator/me/notifications", "/api/v1/operator/me/notifications"})
public class OperatorNotificationController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OperatorNotificationController.class);
    private final NotificationService notifications;
    public OperatorNotificationController(NotificationService notifications) { this.notifications = notifications; }

    @PostMapping("/{id}/read") public Map<String,Object> read(@RequestAttribute("firebaseUid") String uid, @PathVariable String id) throws Exception { return notifications.receipt(uid,id,false); }
    @PostMapping("/{id}/acknowledge") public Map<String,Object> acknowledge(@RequestAttribute("firebaseUid") String uid, @PathVariable String id) throws Exception { return notifications.receipt(uid,id,true); }

    @GetMapping
    public ResponseEntity<?> mine(@RequestAttribute("firebaseUid") String uid) {
        try {
            return ResponseEntity.ok(notifications.getOperatorHistory(uid));
        } catch (SecurityException denied) {
            return ResponseEntity.status(403).body(Map.of("message", "Accesso alle notifiche non autorizzato."));
        } catch (Exception error) {
            log.error("Lettura notifiche operatore non riuscita", error);
            return ResponseEntity.internalServerError().body(Map.of("message", "Impossibile caricare le notifiche. Riprova."));
        }
    }
}
