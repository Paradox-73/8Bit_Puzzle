package com.eightbit.admin;

import com.eightbit.admin.dto.AdminDtos.*;
import com.eightbit.common.config.AppProperties;
import com.eightbit.common.web.ApiException;
import com.eightbit.game.Puzzle;
import com.eightbit.game.PuzzleRepository;
import com.eightbit.game.PuzzleStatus;
import com.eightbit.game.wordle.WordList;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
public class AdminPuzzleService {

    private final PuzzleRepository puzzles;
    private final WordList wordList;
    private final AppProperties props;

    public AdminPuzzleService(PuzzleRepository puzzles, WordList wordList, AppProperties props) {
        this.puzzles = puzzles;
        this.wordList = wordList;
        this.props = props;
    }

    @Transactional
    public PuzzleView create(long authorId, CreatePuzzleRequest req) {
        Puzzle p = new Puzzle();
        p.setGameType(req.gameType() == null ? "wordle" : req.gameType());
        p.setPublishDate(req.publishDate());
        p.setDifficulty(req.difficulty());
        p.setContent(req.content() == null ? new HashMap<>() : new HashMap<>(req.content()));
        p.setEasterEggs(req.easterEggs());
        p.setStatus(PuzzleStatus.DRAFT);
        p.setAuthorId(authorId);
        validateContent(p);
        return view(puzzles.save(p));
    }

    @Transactional
    public PuzzleView update(long id, UpdatePuzzleRequest req) {
        Puzzle p = require(id);
        if (PuzzleStatus.SCHEDULED.equals(p.getStatus()) || PuzzleStatus.PUBLISHED.equals(p.getStatus())) {
            throw ApiException.conflict("LOCKED", "Scheduled puzzles can't be edited; unschedule first");
        }
        if (req.publishDate() != null) p.setPublishDate(req.publishDate());
        if (req.difficulty() != null) p.setDifficulty(req.difficulty());
        if (req.content() != null) p.setContent(new HashMap<>(req.content()));
        if (req.easterEggs() != null) p.setEasterEggs(req.easterEggs());
        validateContent(p);
        return view(puzzles.save(p));
    }

    @Transactional
    public PuzzleView submitForReview(long id) {
        Puzzle p = require(id);
        if (!PuzzleStatus.DRAFT.equals(p.getStatus()) && !PuzzleStatus.IN_REVIEW.equals(p.getStatus())) {
            throw ApiException.conflict("BAD_STATE", "Only drafts can be sent for review");
        }
        validateContent(p);
        p.setStatus(PuzzleStatus.IN_REVIEW);
        return view(puzzles.save(p));
    }

    /** A SECOND editor must approve. The author can never approve their own puzzle. */
    @Transactional
    public PuzzleView approve(long id, long reviewerId) {
        Puzzle p = require(id);
        if (!PuzzleStatus.IN_REVIEW.equals(p.getStatus())) {
            throw ApiException.conflict("BAD_STATE", "Puzzle is not awaiting review");
        }
        if (p.getAuthorId() != null && p.getAuthorId() == reviewerId) {
            throw ApiException.conflict("SAME_AUTHOR", "A different editor must review this puzzle");
        }
        if (p.getPublishDate() == null) {
            throw ApiException.conflict("NO_DATE", "Set a publish date before approving");
        }
        p.setReviewerId(reviewerId);
        p.setStatus(PuzzleStatus.SCHEDULED);
        return view(puzzles.save(p));
    }

    @Transactional
    public PuzzleView schedule(long id, ScheduleRequest req) {
        Puzzle p = require(id);
        if (req != null && req.publishDate() != null) p.setPublishDate(req.publishDate());
        if (!PuzzleStatus.SCHEDULED.equals(p.getStatus())) {
            throw ApiException.conflict("NOT_APPROVED", "Approve the puzzle before scheduling");
        }
        if (p.getPublishDate() == null) {
            throw ApiException.conflict("NO_DATE", "A scheduled puzzle needs a publish date");
        }
        return view(puzzles.save(p));
    }

    @Transactional
    public void delete(long id) {
        puzzles.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PuzzleView> listForMonth(String type, String month) {
        YearMonth ym = YearMonth.parse(month);
        return puzzles.findByGameTypeAndPublishDateBetweenOrderByPublishDate(
                        type, ym.atDay(1), ym.atEndOfMonth())
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> calendar(String type, String month) {
        YearMonth ym = YearMonth.parse(month);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));

        Map<LocalDate, Puzzle> byDate = new HashMap<>();
        for (Puzzle p : puzzles.findByGameTypeAndPublishDateBetweenOrderByPublishDate(type, start, end)) {
            if (p.getPublishDate() != null) byDate.put(p.getPublishDate(), p);
        }

        List<Map<String, Object>> days = new ArrayList<>();
        List<String> gaps = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            Puzzle p = byDate.get(d);
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", d.toString());
            day.put("puzzleId", p == null ? null : p.getId());
            day.put("status", p == null ? null : p.getStatus());
            day.put("difficulty", p == null ? null : p.getDifficulty());
            days.add(day);

            boolean servable = p != null &&
                    (PuzzleStatus.SCHEDULED.equals(p.getStatus()) || PuzzleStatus.PUBLISHED.equals(p.getStatus()));
            if (!servable && !d.isBefore(today)) {
                gaps.add(d.toString());
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("month", month);
        out.put("days", days);
        out.put("gaps", gaps);
        out.put("bufferDays", bufferDays(type, today));
        out.put("warnBelow", props.getContent().getBufferWarnBelowDays());
        return out;
    }

    /** Consecutive days starting today that have a servable puzzle (the editor's runway). */
    private int bufferDays(String type, LocalDate today) {
        int n = 0;
        for (int i = 0; i < 120; i++) {
            if (puzzles.findServableForDate(type, today.plusDays(i)).isPresent()) n++;
            else break;
        }
        return n;
    }

    private void validateContent(Puzzle p) {
        if ("wordle".equals(p.getGameType())) {
            Object ans = p.getContent().get("answer");
            String a = ans == null ? "" : ans.toString().trim().toUpperCase();
            if (a.length() != 5 || !a.chars().allMatch(Character::isLetter)) {
                throw ApiException.badRequest("BAD_CONTENT", "Wordle answer must be exactly 5 letters");
            }
            p.getContent().put("answer", a);
            wordList.register(a); // guarantee the scheduled answer is itself a legal guess
        }
    }

    private Puzzle require(long id) {
        return puzzles.findById(id)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "Puzzle not found"));
    }

    private PuzzleView view(Puzzle p) {
        return new PuzzleView(p.getId(), p.getGameType(), p.getPublishDate(), p.getDifficulty(),
                p.getStatus(), p.getContent(), p.getEasterEggs(), p.getAuthorId(), p.getReviewerId());
    }
}
