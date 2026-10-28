package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.Card;
import cc.superbaby.happy4j.domain.TableAction;
import cc.superbaby.happy4j.domain.TableSnapshot;
import cc.superbaby.happy4j.protocol.game.DdzPattern;
import cc.superbaby.happy4j.protocol.game.MiniGameActionProtocol;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Deterministic server-side fallback policy used when server hosting does not use an external model.
 * It deliberately validates every generated move with the same protocol rules as the game client.
 */
@Service
public final class ServerAiDecisionService {
    private static final int CALL_LANDLORD_THRESHOLD = 12;
    private static final int ROB_LANDLORD_THRESHOLD = 18;
    private static final int DOUBLE_THRESHOLD = 16;
    private static final int SUPER_DOUBLE_THRESHOLD = 24;

    private final MiniGameActionProtocol rules = new MiniGameActionProtocol();

    public boolean chooseLandlord(TableSnapshot table) {
        boolean robbing = table.availableActions().contains(TableAction.ROB_LANDLORD)
                || table.availableActions().contains(TableAction.PASS_ROB);
        return strength(table.hand()) >= (robbing ? ROB_LANDLORD_THRESHOLD : CALL_LANDLORD_THRESHOLD);
    }

    public DoubleDecision chooseDouble(TableSnapshot table, long superDoubleItems) {
        int strength = strength(table.hand());
        if (table.availableActions().contains(TableAction.SUPER_DOUBLE)
                && superDoubleItems > 0 && strength >= SUPER_DOUBLE_THRESHOLD)
            return new DoubleDecision("SUPER_DOUBLE", strength);
        return new DoubleDecision(table.availableActions().contains(TableAction.DOUBLE)
                && strength >= DOUBLE_THRESHOLD ? "DOUBLE" : "NO_DOUBLE", strength);
    }

    public PlayDecision choosePlay(TableSnapshot table) {
        List<List<Integer>> candidates = legalMoves(table);
        if (candidates.isEmpty()) return new PlayDecision(List.of(), true, 0, "没有可出的合法牌");
        List<Integer> selected = candidates.get(0);
        return new PlayDecision(selected, selected.isEmpty(), candidates.size(),
                selected.isEmpty() ? "按规则不出" : "服务器规则策略选择合法动作");
    }

    public List<List<Integer>> legalMoves(TableSnapshot table) {
        List<List<Integer>> all = enumerateAll(table.hand());
        boolean freeTurn = table.lastPlay().isEmpty()
                || table.lastPlaySeatId() == table.seatId();
        if (!freeTurn) {
            MiniGameActionProtocol.Combination previous = new MiniGameActionProtocol.Combination(
                    table.lastPlayType(), table.lastPlayRank());
            all = all.stream().filter(move -> {
                try {
                    MiniGameActionProtocol.Combination candidate = rules.classifyCards(move);
                    return rules.canBeat(candidate, move.size(), previous.type(), previous.mainRank(),
                            table.lastPlay().size());
                } catch (RuntimeException ignored) {
                    return false;
                }
            }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            all.add(List.of());
        }
        all.sort(moveComparator());
        return all.size() > 200 ? List.copyOf(all.subList(0, 200)) : List.copyOf(all);
    }

    private List<List<Integer>> enumerateAll(List<Card> hand) {
        Map<Integer, List<Card>> groups = groupByRank(hand);
        List<Integer> ranks = new ArrayList<>(groups.keySet());
        List<List<Integer>> moves = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (List<Card> group : groups.values()) {
            append(moves, seen, codes(group, 1), rules);
            if (group.size() >= 2) append(moves, seen, codes(group, 2), rules);
            if (group.size() >= 3) append(moves, seen, codes(group, 3), rules);
            if (group.size() == 4) append(moves, seen, codes(group, 4), rules);
        }
        if (groups.containsKey(14) && groups.containsKey(15))
            append(moves, seen, List.of(groups.get(14).get(0).code(), groups.get(15).get(0).code()), rules);

        for (int rank : ranks) {
            List<Card> group = groups.get(rank);
            if (group.size() < 3) continue;
            List<Integer> body = codes(group, 3);
            for (int wing : ranks) {
                if (wing == rank) continue;
                append(moves, seen, concat(body, codes(groups.get(wing), 1)), rules);
            }
            for (int wing : ranks) {
                if (wing == rank || groups.get(wing).size() < 2) continue;
                append(moves, seen, concat(body, codes(groups.get(wing), 2)), rules);
            }
        }

        for (List<Integer> run : consecutiveRuns(ranks, 5))
            append(moves, seen, cardsForRanks(groups, run, 1), rules);
        for (List<Integer> run : consecutiveRuns(ranks.stream()
                .filter(rank -> groups.get(rank).size() >= 2).toList(), 3))
            append(moves, seen, cardsForRanks(groups, run, 2), rules);

        List<Integer> tripleRanks = ranks.stream().filter(rank -> groups.get(rank).size() >= 3).toList();
        for (List<Integer> run : consecutiveRuns(tripleRanks, 2)) {
            List<Integer> body = cardsForRanks(groups, run, 3);
            append(moves, seen, body, rules);
            List<Integer> wingRanks = ranks.stream().filter(rank -> !run.contains(rank)).toList();
            for (List<Integer> wings : combinations(wingRanks, run.size()))
                append(moves, seen, concat(body, wings.stream()
                        .map(rank -> groups.get(rank).get(0).code()).toList()), rules);
            List<Integer> pairRanks = wingRanks.stream()
                    .filter(rank -> groups.get(rank).size() >= 2).toList();
            for (List<Integer> wings : combinations(pairRanks, run.size()))
                append(moves, seen, concat(body, cardsForRanks(groups, wings, 2)), rules);
        }

        for (int rank : ranks) {
            List<Card> group = groups.get(rank);
            if (group.size() != 4) continue;
            List<Integer> otherRanks = ranks.stream().filter(candidate -> candidate != rank).toList();
            for (List<Integer> wings : combinations(otherRanks, 2))
                append(moves, seen, concat(codes(group, 4), wings.stream()
                        .map(wing -> groups.get(wing).get(0).code()).toList()), rules);
            List<Integer> pairRanks = otherRanks.stream()
                    .filter(candidate -> groups.get(candidate).size() >= 2).toList();
            for (List<Integer> wings : combinations(pairRanks, 2))
                append(moves, seen, concat(codes(group, 4), cardsForRanks(groups, wings, 2)), rules);
        }
        return moves;
    }

    private static void append(List<List<Integer>> moves, Set<String> seen, List<Integer> values,
                               MiniGameActionProtocol rules) {
        List<Integer> cards = values.stream().distinct().sorted().toList();
        if (cards.isEmpty()) return;
        try {
            rules.classifyCards(cards);
            String key = cards.stream().map(String::valueOf).reduce((left, right) -> left + "-" + right).orElse("");
            if (seen.add(key)) moves.add(cards);
        } catch (RuntimeException ignored) {
            // A generated attachment may not form a valid native pattern; discard it.
        }
    }

    private static Comparator<List<Integer>> moveComparator() {
        MiniGameActionProtocol rules = new MiniGameActionProtocol();
        return Comparator.<List<Integer>>comparingInt(move -> {
            if (move.isEmpty()) return 2;
            try {
                DdzPattern pattern = rules.classifyCards(move).pattern();
                return pattern == DdzPattern.BOMB || pattern == DdzPattern.ROCKET ? 1 : 0;
            } catch (RuntimeException ignored) {
                return 3;
            }
        }).thenComparingInt(move -> move.size())
                .thenComparingInt(move -> {
                    if (move.isEmpty()) return Integer.MAX_VALUE;
                    try {
                        return rules.classifyCards(move).mainRank();
                    } catch (RuntimeException ignored) {
                        return Integer.MAX_VALUE;
                    }
                }).thenComparing(move -> move.stream().map(String::valueOf).reduce("", (left, right) -> left + right));
    }

    private static Map<Integer, List<Card>> groupByRank(List<Card> hand) {
        Map<Integer, List<Card>> result = new TreeMap<>();
        for (Card card : hand) result.computeIfAbsent(rank(card.code()), ignored -> new ArrayList<>()).add(card);
        result.values().forEach(cards -> cards.sort(Comparator.comparingInt(Card::code)));
        return result;
    }

    private static List<List<Integer>> consecutiveRuns(List<Integer> ranks, int minimum) {
        List<Integer> allowed = ranks.stream().filter(rank -> rank < 13).sorted().toList();
        List<List<Integer>> result = new ArrayList<>();
        for (int start = 0; start < allowed.size(); start++) {
            for (int end = start + minimum; end <= allowed.size(); end++) {
                List<Integer> run = allowed.subList(start, end);
                boolean consecutive = true;
                for (int index = 1; index < run.size(); index++)
                    if (run.get(index) != run.get(index - 1) + 1) consecutive = false;
                if (consecutive) result.add(List.copyOf(run));
            }
        }
        return result;
    }

    private static List<List<Integer>> combinations(List<Integer> values, int count) {
        List<List<Integer>> result = new ArrayList<>();
        combinations(values, count, 0, new ArrayList<>(), result);
        return result;
    }

    private static void combinations(List<Integer> values, int count, int start, List<Integer> current,
                                     List<List<Integer>> result) {
        if (current.size() == count) {
            result.add(List.copyOf(current));
            return;
        }
        for (int index = start; index <= values.size() - (count - current.size()); index++) {
            current.add(values.get(index));
            combinations(values, count, index + 1, current, result);
            current.remove(current.size() - 1);
        }
    }

    private static List<Integer> cardsForRanks(Map<Integer, List<Card>> groups, List<Integer> ranks, int width) {
        return ranks.stream().flatMap(rank -> groups.get(rank).stream().limit(width))
                .map(Card::code).toList();
    }

    private static List<Integer> codes(List<Card> cards, int width) {
        return cards.stream().limit(width).map(Card::code).toList();
    }

    private static List<Integer> concat(List<Integer> first, List<Integer> second) {
        List<Integer> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    private static int strength(List<Card> hand) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        hand.forEach(card -> counts.merge(rank(card.code()), 1, Integer::sum));
        int score = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int rank = entry.getKey();
            int count = entry.getValue();
            if (rank >= 12) score += rank == 15 ? 8 : 5;
            if (count == 2) score += 2;
            if (count == 3) score += 4;
            if (count == 4) score += 8;
        }
        if (counts.containsKey(14) && counts.containsKey(15)) score += 10;
        return score;
    }

    private static int rank(int code) {
        if (code == 53) return 14;
        if (code == 54) return 15;
        int nativeRank = (code - 1) % 13 + 1;
        return nativeRank > 2 ? nativeRank - 2 : nativeRank + 11;
    }

    public record DoubleDecision(String action, int strength) {
    }

    public record PlayDecision(List<Integer> cards, boolean pass, int candidates, String reason) {
        public PlayDecision {
            cards = List.copyOf(cards);
        }
    }
}
