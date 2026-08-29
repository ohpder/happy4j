package cc.superbaby.happy4j.protocol.game;

import cc.superbaby.happy4j.protocol.wire.ProtocolException;
import cc.superbaby.happy4j.protocol.wire.TdrBufferWriter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encoder for the GameBase {@code Pm/uJa} request union sent inside 15813.
 *
 * <p>The union always starts with {@code i32 subtype + i32 errno}. The selected request body is
 * the exact TDR structure used by {@code g.vK}: zKa for order(6), JKa for multiple(11), and sKa
 * for give(4).</p>
 */
public final class MiniGameActionProtocol {
    public byte[] order(int value) {
        if (value < 0 || value > 6) throw new IllegalArgumentException("order value must be 0..6");
        return header(GameSoType.ORDER)
                .writeInt8(value)       // zKa.Xyd
                .writeInt32(0)
                .writeInt32(0).writeInt32(0)
                .toByteArray();
    }

    public byte[] multiple(int value, int calculationMode) {
        if (value < 0 || value > 127)
            throw new IllegalArgumentException("multiple value is invalid");
        if (calculationMode < 0 || calculationMode > 127)
            throw new IllegalArgumentException("multiple calculation mode is invalid");
        return header(GameSoType.DOUBLE_RESULT)
                .writeInt8(calculationMode) // JKa.Yg.mHc
                .writeInt8(value)           // JKa.Yg.Yg
                .writeInt32(0)              // JKa.Yg.kme
                .writeInt32(0).writeInt32(0)
                .toByteArray();
    }

    public byte[] trust(boolean enabled) {
        return header(GameSoType.TRUST).writeInt8(enabled ? 1 : 0).toByteArray();
    }

    public byte[] give(List<Integer> cardCodes, boolean pass) {
        List<Integer> cards = normalize(cardCodes);
        Combination combination = pass ? new Combination(DdzPattern.PASS, 0) : classify(cards);
        TdrBufferWriter output = header(GameSoType.PLAY)
                .writeInt16(cards.size())
                .writeInt32(combination.type())
                .writeInt8(combination.mainRank());
        cards.forEach(output::writeInt8);
        return output
                .writeInt32(0)
                .writeInt32(0)
                .writeInt8(pass ? 1 : 0)
                .writeInt32(0).writeInt32(0)
                .toByteArray();
    }

    private static TdrBufferWriter header(GameSoType type) {
        return new TdrBufferWriter().writeInt32(type.code()).writeInt32(0);
    }

    private static List<Integer> normalize(List<Integer> values) {
        if (values == null) return List.of();
        if (values.size() > 25) throw new IllegalArgumentException("单次出牌超过 25 张");
        List<Integer> result = new ArrayList<>(values.size());
        for (Integer value : values) {
            if (value == null || value < 1 || value > 54)
                throw new IllegalArgumentException("非法牌编码: " + value);
            if (result.contains(value)) throw new IllegalArgumentException("重复牌编码: " + value);
            result.add(value);
        }
        result.sort(Comparator.comparingInt(MiniGameActionProtocol::rank).thenComparingInt(Integer::intValue));
        return List.copyOf(result);
    }

    static Combination classify(List<Integer> cards) {
        if (cards.isEmpty()) throw new IllegalArgumentException("出牌时至少选择一张牌");
        Map<Integer, Integer> counts = new HashMap<>();
        cards.forEach(card -> counts.merge(rank(card), 1, Integer::sum));
        List<Integer> ranks = counts.keySet().stream().sorted().toList();
        int size = cards.size();
        int high = ranks.get(ranks.size() - 1);
        if (size == 1) return new Combination(DdzPattern.SINGLE, high);
        if (size == 2 && counts.size() == 2 && counts.containsKey(14) && counts.containsKey(15))
            return new Combination(DdzPattern.ROCKET, 15);
        if (counts.size() == 1) {
            int count = counts.values().iterator().next();
            return switch (count) {
                case 2 -> new Combination(DdzPattern.PAIR, high);
                case 3 -> new Combination(DdzPattern.TRIPLE, high);
                case 4 -> new Combination(DdzPattern.BOMB, high);
                default -> throw invalid(cards);
            };
        }
        if (size == 4 && countValues(counts, 3) == 1)
            return new Combination(DdzPattern.TRIPLE_WITH_SINGLE, rankWithCount(counts, 3));
        if (size == 5 && countValues(counts, 3) == 1 && countValues(counts, 2) == 1)
            return new Combination(DdzPattern.TRIPLE_WITH_PAIR, rankWithCount(counts, 3));
        if (size >= 5 && counts.size() == size && consecutive(ranks))
            return new Combination(DdzPattern.STRAIGHT, high);
        if (size >= 6 && size % 2 == 0 && counts.values().stream().allMatch(value -> value == 2)
                && consecutive(ranks)) return new Combination(DdzPattern.PAIR_STRAIGHT, high);
        if (size >= 6 && size % 3 == 0 && counts.values().stream().allMatch(value -> value == 3)
                && consecutive(ranks)) return new Combination(DdzPattern.TRIPLE_STRAIGHT, high);
        List<Integer> triples = counts.entrySet().stream().filter(entry -> entry.getValue() == 3)
                .map(Map.Entry::getKey).sorted().toList();
        if (triples.size() >= 2 && consecutive(triples)) {
            int wings = size - triples.size() * 3;
            if (wings == triples.size() && counts.entrySet().stream()
                    .filter(entry -> !triples.contains(entry.getKey())).allMatch(entry -> entry.getValue() == 1))
                return new Combination(DdzPattern.AIRPLANE_WITH_SINGLES, triples.get(triples.size() - 1));
            if (wings == triples.size() * 2 && counts.entrySet().stream()
                    .filter(entry -> !triples.contains(entry.getKey())).allMatch(entry -> entry.getValue() == 2))
                return new Combination(DdzPattern.AIRPLANE_WITH_PAIRS, triples.get(triples.size() - 1));
        }
        if (size == 6 && countValues(counts, 4) == 1)
            return new Combination(DdzPattern.FOUR_WITH_SINGLES, rankWithCount(counts, 4));
        if (size == 8 && countValues(counts, 4) == 1 && counts.entrySet().stream()
                .filter(entry -> entry.getValue() != 4).allMatch(entry -> entry.getValue() == 2))
            return new Combination(DdzPattern.FOUR_WITH_PAIRS, rankWithCount(counts, 4));
        throw invalid(cards);
    }

    public Combination classifyCards(List<Integer> cards) {
        return classify(normalize(cards));
    }

    public boolean canBeat(Combination candidate, int candidateCount,
                           int previousType, int previousRank, int previousCount) {
        if (previousType == 0 || previousCount == 0) return true;
        if (candidate.pattern() == DdzPattern.ROCKET)
            return previousType != DdzPattern.ROCKET.code();
        if (previousType == DdzPattern.ROCKET.code()) return false;
        if (candidate.pattern() == DdzPattern.BOMB && previousType != DdzPattern.BOMB.code())
            return true;
        return candidate.type() == previousType && candidateCount == previousCount
                && candidate.mainRank() > previousRank;
    }

    private static int countValues(Map<Integer, Integer> counts, int expected) {
        return (int) counts.values().stream().filter(value -> value == expected).count();
    }

    private static int rankWithCount(Map<Integer, Integer> counts, int count) {
        return counts.entrySet().stream().filter(entry -> entry.getValue() == count)
                .mapToInt(Map.Entry::getKey).max().orElseThrow();
    }

    private static boolean consecutive(List<Integer> ranks) {
        if (ranks.isEmpty() || ranks.get(ranks.size() - 1) >= 13) return false;
        for (int index = 1; index < ranks.size(); index++)
            if (ranks.get(index) != ranks.get(index - 1) + 1) return false;
        return true;
    }

    private static int rank(int code) {
        if (code == 53) return 14;
        if (code == 54) return 15;
        int nativeRank = (code - 1) % 13 + 1;
        // GameBase g.au with the classic (efb=false) mapping: 3..K,A,2 => 1..13.
        return nativeRank > 2 ? nativeRank - 2 : nativeRank + 11;
    }

    private static ProtocolException invalid(List<Integer> cards) {
        return new ProtocolException("所选牌不构成经典斗地主合法牌型: " + cards);
    }

    public record Combination(DdzPattern pattern, int mainRank) {
        public Combination(int type, int mainRank) {
            this(DdzPattern.fromCode(type), mainRank);
        }

        public int type() {
            return pattern.code();
        }
    }
}
