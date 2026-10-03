package com.plantride.tracking;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.security.CurrentUser;

@RestController
@RequestMapping("/api/admin/safety-events")
@Transactional
public class SafetyAdminController {

    private final SafetyEventRepository events;

    public SafetyAdminController(SafetyEventRepository events) {
        this.events = events;
    }

    @GetMapping
    public List<SafetyEvent> recent() {
        return events.findTop100ByPlantIdOrderByOccurredAtDesc(CurrentUser.get().plantId());
    }

    @PostMapping("/{id}/acknowledge")
    public SafetyEvent acknowledge(@PathVariable Long id) {
        SafetyEvent e = events.require(id, CurrentUser.get().plantId(), "Safety event");
        e.setAcknowledged(true);
        return e;
    }
}
