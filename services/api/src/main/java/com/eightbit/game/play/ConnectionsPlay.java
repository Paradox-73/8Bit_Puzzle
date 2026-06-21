package com.eightbit.game.play;

import com.eightbit.common.web.ApiException;
import com.eightbit.game.Attempt;
import com.eightbit.game.Puzzle;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Connections: 16 tiles, sort into four hidden groups of four. Server-authoritative — the grouping
 * never reaches the client until the round is over. State is replayed from the stored selections so
 * we reuse the same Attempt row as Wordle (each guess stored as "A|B|C|D").
 */
@Component
public class ConnectionsPlay implements GamePlay {

    public static final int GROUP_COUNT = 4;
    public static final int GROUP_SIZE = 4;
    public static final int MAX_MISTAKES = 4;

    @Override
    public String type() {
        return "connections";
    }

    private record Group(int level, String category, List<String> members) {
        Set<String> set() { return new HashSet<>(members); }
    }

    @SuppressWarnings("unchecked")
    private List<Group> groups(Puzzle p) {
        Object raw = p.getContent().get("groups");
        if (!(raw instanceof List<?> list)) {
            throw ApiException.notFound("BAD_PUZZLE", "Connections puzzle is misconfigured");
        }
        List<Group> out = new ArrayList<>();
        for (Object o : list) {
            Map<String, Object> m = (Map<String, Object>) o;
            int level = ((Number) m.get("level")).intValue();
            String category = String.valueOf(m.get("category"));
            List<String> members = ((List<Object>) m.get("members")).stream()
                    .map(x -> x.toString().toUpperCase()).toList();
            out.add(new Group(level, category, members));
        }
        return out;
    }

    private List<String> allTiles(List<Group> groups) {
        List<String> t = new ArrayList<>();
        groups.forEach(g -> t.addAll(g.members()));
        return t;
    }

    private List<String> parse(String stored) {
        return Arrays.stream(stored.split("\\|")).map(String::toUpperCase).toList();
    }

    /** Replays stored guesses → which groups are solved (by index) and how many mistakes were made. */
    private record State(Set<Integer> solved, int mistakes) {}

    private State replay(List<Group> groups, List<String> stored) {
        Set<Integer> solved = new LinkedHashSet<>();
        int mistakes = 0;
        for (String s : stored) {
            Set<String> sel = new HashSet<>(parse(s));
            int matched = -1;
            for (int i = 0; i < groups.size(); i++) {
                if (groups.get(i).set().equals(sel)) { matched = i; break; }
            }
            if (matched >= 0) solved.add(matched);
            else mistakes++;
        }
        return new State(solved, mistakes);
    }

    private Map<String, Object> groupView(Group g) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("level", g.level());
        m.put("category", g.category());
        m.put("members", g.members());
        return m;
    }

    private List<Map<String, Object>> solvedGroupViews(List<Group> groups, State st, boolean gameOver) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            if (gameOver || st.solved().contains(i)) out.add(groupView(groups.get(i)));
        }
        return out;
    }

    @Override
    public Map<String, Object> todayView(Puzzle p, Attempt attempt) {
        List<Group> groups = groups(p);
        List<String> stored = attempt == null ? List.of() : attempt.getGuesses();
        State st = replay(groups, stored);
        boolean gameOver = st.solved().size() == GROUP_COUNT || st.mistakes() >= MAX_MISTAKES;

        // Stable shuffle per puzzle so every player sees the same tile order.
        List<String> tiles = new ArrayList<>(allTiles(groups));
        Collections.shuffle(tiles, new Random(p.getId() == null ? 0 : p.getId()));

        // History with per-guess correct/oneAway.
        List<Map<String, Object>> history = new ArrayList<>();
        for (String s : stored) {
            List<String> sel = parse(s);
            history.add(judge(groups, sel));
        }

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("config", Map.of("groupCount", GROUP_COUNT, "groupSize", GROUP_SIZE, "maxMistakes", MAX_MISTAKES));
        view.put("tiles", tiles);
        view.put("solvedGroups", solvedGroupViews(groups, st, gameOver));
        view.put("mistakesUsed", st.mistakes());
        view.put("mistakesRemaining", Math.max(0, MAX_MISTAKES - st.mistakes()));
        view.put("guesses", history);
        return view;
    }

    /** {selection, correct, oneAway} for one selection. */
    private Map<String, Object> judge(List<Group> groups, List<String> selection) {
        Set<String> sel = new HashSet<>(selection);
        int best = 0;
        boolean exact = false;
        for (Group g : groups) {
            int c = 0;
            for (String m : g.members()) if (sel.contains(m)) c++;
            best = Math.max(best, c);
            if (g.set().equals(sel)) exact = true;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("selection", selection);
        m.put("correct", exact);
        m.put("oneAway", !exact && best == GROUP_SIZE - 1);
        return m;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MoveStep step(Puzzle p, Attempt attempt, Map<String, Object> move) {
        List<Group> groups = groups(p);
        List<String> allTiles = allTiles(groups);
        State prior = replay(groups, attempt.getGuesses());

        Object rawSel = move.get("selection");
        if (!(rawSel instanceof List<?> list) || list.size() != GROUP_SIZE) {
            throw ApiException.badRequest("BAD_SELECTION", "Select exactly " + GROUP_SIZE + " tiles");
        }
        List<String> selection = ((List<Object>) rawSel).stream().map(x -> x.toString().toUpperCase()).toList();
        Set<String> selSet = new HashSet<>(selection);
        if (selSet.size() != GROUP_SIZE) {
            throw ApiException.badRequest("BAD_SELECTION", "Tiles must be distinct");
        }
        // already-solved tiles can't be reselected
        Set<String> solvedTiles = new HashSet<>();
        prior.solved().forEach(i -> solvedTiles.addAll(groups.get(i).members()));
        for (String t : selection) {
            if (!allTiles.contains(t) || solvedTiles.contains(t)) {
                throw ApiException.badRequest("BAD_SELECTION", "Invalid tile selection");
            }
        }

        attempt.getGuesses().add(String.join("|", selection));
        State now = replay(groups, attempt.getGuesses());
        Map<String, Object> judged = judge(groups, selection);
        boolean correct = (boolean) judged.get("correct");
        boolean gameOver = now.solved().size() == GROUP_COUNT || now.mistakes() >= MAX_MISTAKES;
        boolean solved = now.solved().size() == GROUP_COUNT;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("correct", correct);
        resp.put("oneAway", judged.get("oneAway"));
        resp.put("group", correct ? groupViewForSelection(groups, selSet) : null);
        resp.put("mistakesUsed", now.mistakes());
        resp.put("mistakesRemaining", Math.max(0, MAX_MISTAKES - now.mistakes()));
        resp.put("solvedGroups", solvedGroupViews(groups, now, gameOver));
        return new MoveStep(solved, gameOver, resp);
    }

    private Map<String, Object> groupViewForSelection(List<Group> groups, Set<String> sel) {
        for (Group g : groups) if (g.set().equals(sel)) return groupView(g);
        return null;
    }

    @Override
    public int score(Puzzle p, Attempt a, int currentStreak) {
        if (!Boolean.TRUE.equals(a.getSolved())) return 0;
        State st = replay(groups(p), a.getGuesses());
        int base = 1000;
        int mistakeBonus = (MAX_MISTAKES - Math.min(MAX_MISTAKES, st.mistakes())) * 150; // 0..600
        double streakMult = Math.min(1.5, 1.0 + currentStreak * 0.02);
        return (int) Math.round((base + mistakeBonus) * streakMult);
    }

    @Override
    public String shareGrid(Puzzle p, Attempt a) {
        List<Group> groups = groups(p);
        Map<String, Integer> levelOf = new HashMap<>();
        for (Group g : groups) for (String m : g.members()) levelOf.put(m, g.level());
        State st = replay(groups, a.getGuesses());
        boolean solved = st.solved().size() == GROUP_COUNT;

        StringBuilder sb = new StringBuilder();
        sb.append("8Bit Connections • IIITB ").append(solved ? "✓" : "✗").append("\n");
        String[] sq = {"🟨", "🟩", "🟦", "🟪"}; // one per group level
        for (String s : a.getGuesses()) {
            for (String tile : parse(s)) {
                int lvl = levelOf.getOrDefault(tile, 0);
                sb.append(sq[Math.floorMod(lvl, sq.length)]);
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    public Map<String, Object> reveal(Puzzle p, Attempt a) {
        // On finish, the full grouping is revealed via solvedGroups already; nothing extra needed.
        return Map.of();
    }
}
