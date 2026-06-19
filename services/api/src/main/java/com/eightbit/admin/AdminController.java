package com.eightbit.admin;

import com.eightbit.admin.dto.AdminDtos.*;
import com.eightbit.common.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminPuzzleService svc;

    public AdminController(AdminPuzzleService svc) {
        this.svc = svc;
    }

    @GetMapping("/calendar")
    public Map<String, Object> calendar(@RequestParam(defaultValue = "wordle") String type,
                                        @RequestParam String month) {
        return svc.calendar(type, month);
    }

    @GetMapping("/puzzles")
    public List<PuzzleView> list(@RequestParam(defaultValue = "wordle") String type,
                                 @RequestParam String month) {
        return svc.listForMonth(type, month);
    }

    @PostMapping("/puzzles")
    public PuzzleView create(@RequestBody CreatePuzzleRequest req,
                             @AuthenticationPrincipal AuthUser user) {
        return svc.create(user.id(), req);
    }

    @PutMapping("/puzzles/{id}")
    public PuzzleView update(@PathVariable long id, @RequestBody UpdatePuzzleRequest req) {
        return svc.update(id, req);
    }

    @PostMapping("/puzzles/{id}/submit-review")
    public PuzzleView submitReview(@PathVariable long id) {
        return svc.submitForReview(id);
    }

    @PostMapping("/puzzles/{id}/approve")
    public PuzzleView approve(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return svc.approve(id, user.id());
    }

    @PostMapping("/puzzles/{id}/schedule")
    public PuzzleView schedule(@PathVariable long id, @RequestBody(required = false) ScheduleRequest req) {
        return svc.schedule(id, req);
    }

    @DeleteMapping("/puzzles/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        svc.delete(id);
        return Map.of("deleted", true);
    }
}
