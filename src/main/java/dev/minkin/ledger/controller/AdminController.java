package dev.minkin.ledger.controller;

import dev.minkin.projector.RebuildPoller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/v1/admin")
public class AdminController {
    private final RebuildPoller rebuildPoller;

    public AdminController(RebuildPoller rebuildPoller) {
        this.rebuildPoller = rebuildPoller;
    }

    @PostMapping("/rebuild")
    public ResponseEntity<?> rebuild() {
        rebuildPoller.poll();
        return ResponseEntity.accepted().build();
    }
}
