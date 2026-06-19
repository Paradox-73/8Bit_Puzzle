package com.eightbit.game;

import com.eightbit.common.security.AuthUser;
import com.eightbit.game.dto.GameDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class GameController {

    private final GameService game;

    public GameController(GameService game) {
        this.game = game;
    }

    @GetMapping("/puzzles/today")
    public TodayResponse today(@RequestParam(defaultValue = "wordle") String type,
                               @AuthenticationPrincipal AuthUser user) {
        return game.today(user.id(), type);
    }

    @PostMapping("/puzzles/{id}/guess")
    public GuessResponse guess(@PathVariable("id") long puzzleId,
                               @Valid @RequestBody GuessRequest req,
                               @AuthenticationPrincipal AuthUser user) {
        int batch = user.batchYear() == null ? 0 : user.batchYear();
        return game.guess(user.id(), user.username(), batch, puzzleId, req.guess());
    }

    @GetMapping("/me/attempts")
    public List<AttemptSummary> myAttempts(@AuthenticationPrincipal AuthUser user) {
        return game.myAttempts(user.id());
    }
}
