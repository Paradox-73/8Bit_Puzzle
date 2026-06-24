package com.eightbit.game;

/**
 * Puzzle lifecycle. draft -> in_review -> scheduled (servable on its date).
 * EVERGREEN puzzles have no publish_date and are the failsafe pool: if a day has no
 * scheduled puzzle, the game serves one of these instead of a 404 (build doc section 11c).
 */
public final class PuzzleStatus {
    public static final String DRAFT = "draft";
    public static final String IN_REVIEW = "in_review";
    public static final String SCHEDULED = "scheduled";
    public static final String PUBLISHED = "published";
    public static final String EVERGREEN = "evergreen";
    /** Pre-launch playtest puzzles, synced from puzzles-review.json. Never served by the real
     *  daily flow; only the trial pool picks these up. Purged at go-live. */
    public static final String TRIAL = "trial";

    private PuzzleStatus() {}
}
