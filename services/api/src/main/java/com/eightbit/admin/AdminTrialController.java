package com.eightbit.admin;

import com.eightbit.game.AttemptRepository;
import com.eightbit.game.Puzzle;
import com.eightbit.game.PuzzleRepository;
import com.eightbit.game.PuzzleStatus;
import com.eightbit.game.TrialPuzzleSync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-launch playtest dashboard for editors: aggregated solve-rates / timings / ratings / per-player
 * progress, a one-click purge, a force re-sync of the puzzle file, and a stats export. The real
 * junior-play stats (scope=live) are kept strictly separate from the trial stats (scope=trial) so the
 * editor can still see how the juniors play after launch. Gated to ROLE_EDITOR/ROLE_ADMIN (/admin/**).
 */
@RestController
@RequestMapping("/admin/trial")
public class AdminTrialController {

    private final AttemptRepository attempts;
    private final PuzzleRepository puzzles;
    private final TrialPuzzleSync trialSync;
    private final TrialStatsService statsService;

    public AdminTrialController(AttemptRepository attempts, PuzzleRepository puzzles,
                                TrialPuzzleSync trialSync, TrialStatsService statsService) {
        this.attempts = attempts;
        this.puzzles = puzzles;
        this.trialSync = trialSync;
        this.statsService = statsService;
    }

    /** scope=trial (default) → playtest stats; scope=live → real junior play (post-launch). */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(defaultValue = "trial") String scope) {
        return statsService.build(!"live".equalsIgnoreCase(scope));
    }

    /** Re-import puzzles-review.json now (also picks up edits without waiting for the next play). */
    @PostMapping("/sync")
    public Map<String, Object> sync() {
        return Map.of("puzzles", trialSync.forceSync());
    }

    /** Snapshot the current trial stats to the stats file (preserved across the pre-launch purge). */
    @PostMapping("/export")
    public Map<String, Object> export() {
        return statsService.export();
    }

    /**
     * Full teardown: snapshot the stats to the file first (so nothing is lost), then wipe every trial
     * attempt AND trial puzzle, leaving a clean slate for the real launch. To run the playtest again,
     * hit Sync to re-import the puzzle file.
     */
    @PostMapping("/reset")
    public Map<String, Object> reset() {
        Map<String, Object> exported = statsService.export();
        int attemptsDeleted = attempts.deleteAllTrial();
        List<Puzzle> trialPuzzles = puzzles.findByStatus(PuzzleStatus.TRIAL);
        puzzles.deleteAll(trialPuzzles);
        trialSync.invalidate();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("attemptsDeleted", attemptsDeleted);
        out.put("puzzlesDeleted", trialPuzzles.size());
        out.put("statsExport", exported);
        return out;
    }
}
