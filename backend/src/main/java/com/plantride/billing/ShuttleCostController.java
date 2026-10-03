package com.plantride.billing;

import java.time.YearMonth;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.plantride.security.CurrentUser;

@RestController
@RequestMapping("/api/admin/shuttle-costs")
@PreAuthorize("hasRole('ADMIN')")
public class ShuttleCostController {

    private final ShuttleCostService service;

    public ShuttleCostController(ShuttleCostService service) {
        this.service = service;
    }

    @GetMapping
    public ShuttleCostService.Preview preview(@RequestParam YearMonth month) {
        return service.preview(CurrentUser.get().plantId(), month);
    }

    @PostMapping("/post")
    public ShuttleCostService.Preview post(@RequestParam YearMonth month) {
        return service.post(CurrentUser.get().plantId(), month);
    }
}
