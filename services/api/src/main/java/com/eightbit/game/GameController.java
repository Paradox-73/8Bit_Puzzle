package com.eightbit.game;

import com.eightbit.common.security.AuthUser;
import com.eightbit.game.dto.GameDtos.AttemptSummary;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class GameController {

    private final GameService game;

    public GameController(GameService game) {
        this.game = game;
    }

    @GetMapping("/puzzles/today")
    public Map<String, Object> today(@RequestParam(defaultValue = "wordle") String type,
                                     @AuthenticationPrincipal AuthUser user) {
        return game.today(user.id(), type);
    }

    /**
     * One move. Body shape is game-specific: Wordle sends {"guess":"CRANE"}, Connections sends
     * {"selection":["A","B","C","D"]}. The per-game {@code GamePlay} validates it.
     */
    @PostMapping("/puzzles/{id}/guess")
    public Map<String, Object> guess(@PathVariable("id") long puzzleId,
                                     @RequestBody Map<String, Object> move,
                                     @AuthenticationPrincipal AuthUser user) {
        int batch = user.batchYear() == null ? 0 : user.batchYear();
        return game.guess(user.id(), user.username(), batch, puzzleId, move);
    }

    /**
     * Reveal one hint for today's puzzle. Body: {"kind":"vowel"|"consonant"} for Wordle, or
     * {"kind":"definition"|"indicator"|"fodder"} for Cryptic. Server-authoritative and capped.
     */
    @PostMapping("/puzzles/{id}/hint")
    public Map<String, Object> hint(@PathVariable("id") long puzzleId,
                                    @RequestBody Map<String, Object> body,
                                    @AuthenticationPrincipal AuthUser user) {
        Object kind = body == null ? null : body.get("kind");
        return game.hint(user.id(), puzzleId, kind == null ? null : kind.toString());
    }

    @GetMapping("/me/attempts")
    public List<AttemptSummary> myAttempts(@AuthenticationPrincipal AuthUser user) {
        return game.myAttempts(user.id());
    }
}
