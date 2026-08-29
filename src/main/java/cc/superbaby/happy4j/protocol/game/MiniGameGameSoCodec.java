package cc.superbaby.happy4j.protocol.game;

import cc.superbaby.happy4j.protocol.wire.ProtocolException;
import cc.superbaby.happy4j.protocol.wire.TdrBufferReader;
import cc.superbaby.happy4j.protocol.wire.TdrBufferWriter;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-state decoder for the wxpkg HuanLe GameSvr GameSo union.
 */
public final class MiniGameGameSoCodec {
    public List<RoomEvent> decodeRoomEvents(byte[] payload) {
        TdrBufferReader input = new TdrBufferReader(payload);
        int count = input.readUInt16();
        if (count > 32) throw new ProtocolException("room event count exceeds 32: " + count);
        List<RoomEvent> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int type = input.readInt32();
            long playerId = input.readUInt32();
            int roomId = input.readInt16();
            int tableId = input.readInt16();
            int seatId = input.readInt16();
            int tlvCount = input.readUInt16();
            if (tlvCount > 32)
                throw new ProtocolException("room TLV count exceeds 32: " + tlvCount);
            List<PlayerDetail> players = new ArrayList<>();
            List<ItemBalance> items = new ArrayList<>();
            long beans = -1;
            long beanChange = 0;
            for (int tlv = 0; tlv < tlvCount; tlv++) {
                int tlvType = input.readInt16();
                int tlvLength = input.readUInt16();
                byte[] value = input.readBytes(tlvLength);
                if (tlvType == 10001) players.add(decodePlayerDetailTlv(value));
                if (tlvType == 10006) {
                    PlayerBeans balance = decodePlayerBeans(value);
                    beans = balance.current();
                    beanChange = balance.change();
                }
                if (tlvType == 10007) items.add(decodeItemBalance(value));
            }
            result.add(new RoomEvent(type, playerId, roomId, tableId, seatId,
                    players, beans, beanChange, items));
        }
        return result;
    }

    /**
     * 15815 is Hja: u16 length + PZa. PZa starts with u16 metadata, u8 reserved, i8 seat,
     * i32 union subtype and i32 errno. Standard DDZ subtype 1 is the initial hand and contains
     * u16 cardCount followed by the sequential 1..54 card codes. This layout is verified by the
     * live subtype-1 payload {@code 001f0000000100000001000000000011...}. The response union
     * is {@code uJa}: its discriminator is followed by a four-byte errno before the selected
     * subtype structure.
     */
    public GameSoEvent decodeGameSo(byte[] payload) {
        TdrBufferReader outer = new TdrBufferReader(payload);
        int length = outer.readUInt16();
        if (length > outer.remaining())
            throw new ProtocolException("GameSo length exceeds payload: " + length);
        TdrBufferReader input = new TdrBufferReader(outer.readBytes(length));
        int metadata = input.readUInt16();
        input.readUInt8();
        int seatId = input.readInt8();
        int resolvedSeatId = seatId;
        int subtype = input.readInt32();
        int error = input.readInt32();
        int actorSeat = -1;
        int nextSeat = -1;
        int decision = 0;
        int cardType = 0;
        int mainRank = 0;
        int landlordSeat = -1;
        int multiplier = 0;
        int nextTimeLimit = 0;
        int superDoubleItemId = 0;
        List<Integer> cards = List.of();
        List<Integer> bottomCards = List.of();
        List<List<Integer>> hands = List.of();
        Settlement settlement = Settlement.empty();
        switch (GameSoType.fromCode(subtype)) {
            case INITIAL_HAND -> cards = readCards16(input, 25); // rKa: initial local hand
            case PLAY -> { // tKa: last play and next chair
                require(input, 3 + 2 + 4 + 1, subtype);
                input.readInt8();
                actorSeat = input.readInt8();
                nextSeat = input.readInt8();
                int count = input.readUInt16();
                cardType = input.readInt32();
                mainRank = input.readInt8();
                cards = readCards(input, count, 25);
                // tKa.VO: the authoritative operation limit for the next chair.
                if (input.remaining() > 0) nextTimeLimit = Math.max(0, input.readInt8());
            }
            case SETTLEMENT -> {
                try {
                    settlement = decodeSettlement(input, subtype);
                    hands = settlement.hands();
                } catch (ProtocolException incompleteSettlementExtension) {
                    settlement = Settlement.empty();
                }
            }
            case ORDER -> { // AKa: completed order + next player that may call/rob
                require(input, 4, subtype);
                actorSeat = input.readInt8();
                nextSeat = input.readInt8();
                decision = input.readInt8();
                input.readInt8();
            }
            case LANDLORD_CARDS -> { // BKa: landlord and bottom cards
                require(input, 2, subtype);
                landlordSeat = input.readInt8();
                int count = input.readUInt8();
                bottomCards = readCards(input, count, 16);
            }
            case TRUST -> { // DKa: trust/auto-play state
                require(input, 5, subtype);
                actorSeat = input.readInt8();
                decision = input.readInt32();
            }
            case DOUBLE_OPTIONS -> { // IKa/XJa: available ordinary/super-double modes
                require(input, 1, subtype);
                int count = input.readUInt8();
                if (count > 16 || input.remaining() < count * 6)
                    throw new ProtocolException("invalid multiple option count " + count);
                int modes = 0;
                for (int index = 0; index < count; index++) {
                    int calculationMode = input.readInt8();
                    input.readInt8();
                    int itemId = input.readInt32();
                    if (calculationMode == 1) modes |= 1;
                    if (calculationMode == 2) {
                        modes |= 2;
                        superDoubleItemId = Math.max(0, itemId);
                    }
                }
                decision = modes;
            }
            case DOUBLE_RESULT -> { // KKa: a player's double result
                require(input, 3, subtype);
                actorSeat = input.readInt8();
                int calculationMode = input.readInt8();
                multiplier = input.readInt8();
                decision = calculationMode;
            }
            case MULTIPLIER_SYNC -> { // LKa / CmdSCSOMultiSync: authoritative per-chair multipliers
                require(input, 3 * Long.BYTES + 1 + 15 * Integer.BYTES + Integer.BYTES, subtype);
                long[] perChair = {input.readInt64(), input.readInt64(), input.readInt64()};
                int localChair = resolvedSeatId >= 0 && resolvedSeatId < perChair.length
                        ? resolvedSeatId : 0;
                long value = Math.max(0, perChair[localChair]);
                multiplier = value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
                int count = input.readUInt8();
                if (count > 16 || input.remaining() < count * Integer.BYTES + Integer.BYTES)
                    throw new ProtocolException("invalid multi-sync factor count " + count);
                input.readBytes(count * Integer.BYTES); // Zvd component factors, retained by native round model
                decision = input.readInt32(); // Zge calculation mode
            }
            case RECONNECT_SNAPSHOT -> { // PKa: disconnect/re-enter snapshot
                require(input, 14, subtype);
                input.readInt8();          // zt
                decision = input.readInt8(); // tl / section
                nextSeat = input.readInt8(); // JXa / current operation chair
                input.readInt8();          // n7a
                input.readInt8();          // tT
                input.readBytes(3);        // gLa
                input.readBytes(3);        // Tha
                byte[] share = input.readBytes(3); // Share visibility; local chair is the zero entry
                for (int chair = 0; chair < share.length; chair++)
                    if (share[chair] == 0) {
                        resolvedSeatId = chair;
                        break;
                    }
                List<List<Integer>> decodedHands = new ArrayList<>(3);
                for (int chair = 0; chair < 3; chair++) decodedHands.add(readCards16(input, 25));
                hands = List.copyOf(decodedHands);
                readCards16(input, 25);    // dIb, previous play
                bottomCards = readCards16(input, 25); // HLb, latest play
                require(input, 3, subtype);
                actorSeat = input.readInt8(); // oob / latest-play chair
                input.readInt8();             // pob
                landlordSeat = input.readInt8(); // tr
            }
            case HAND_SYNC -> { // TKa/km: authoritative hand synchronization
                require(input, 4, subtype);
                int count = input.readInt32();
                cards = readCards(input, count, 120);
            }
            case LANDLORD_RESULT -> { // YKa: landlord decision result
                require(input, 1, subtype);
                landlordSeat = input.readInt8();
            }
            case FIRST_TURN -> { // wLa: first player to act
                require(input, 3, subtype);
                input.readInt8();
                actorSeat = input.readInt8();
                nextSeat = input.readInt8();
            }
            default -> {
            }
        }
        return new GameSoEvent(metadata, resolvedSeatId, subtype, error, actorSeat, nextSeat, decision,
                cardType, mainRank, landlordSeat, multiplier, cards, bottomCards, hands, settlement,
                nextTimeLimit, superDoubleItemId);
    }

    /**
     * Decodes replay TLV 10014: u16 byte length + raw OZa/uJa response union.
     */
    public GameSoEvent decodeReplayTlv(byte[] tlvValue) {
        TdrBufferReader input = new TdrBufferReader(tlvValue);
        int length = input.readUInt16();
        if (length > input.remaining())
            throw new ProtocolException("replay union length exceeds TLV: " + length);
        byte[] union = input.readBytes(length);
        TdrBufferWriter body = new TdrBufferWriter(4 + union.length)
                .writeInt16(0).writeInt8(0).writeInt8(-1).writeBytes(union);
        byte[] bytes = body.toByteArray();
        return decodeGameSo(new TdrBufferWriter(2 + bytes.length)
                .writeInt16(bytes.length).writeBytes(bytes).toByteArray());
    }

    private static List<Integer> readCards16(TdrBufferReader input, int maximum) {
        if (input.remaining() < 2) return List.of();
        return readCards(input, input.readUInt16(), maximum);
    }

    private static Settlement decodeSettlement(TdrBufferReader input, int subtype) {
        require(input, 1 + 9 * Long.BYTES, subtype);
        int resultFlag = input.readInt8();
        List<Long> changes = readLongs(input, 3);
        // wKa.An is copied to the native player's ns field. The original client derives the
        // post-round bean balance by adding Ee (round delta) to the player's prior score.
        List<Long> resultValues = readLongs(input, 3);
        List<Long> factors = readLongs(input, 3);
        List<List<Integer>> hands = new ArrayList<>(3);
        for (int seat = 0; seat < 3; seat++) {
            require(input, Integer.BYTES, subtype);
            hands.add(readCards(input, input.readInt32(), 25));
        }
        // wKa continues with level/title/round counters. They are not monetary settlement fields.
        return new Settlement(resultFlag, changes, resultValues, factors, List.copyOf(hands));
    }

    private static List<Long> readLongs(TdrBufferReader input, int count) {
        List<Long> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) values.add(input.readInt64());
        return List.copyOf(values);
    }

    private static List<Integer> readCards(TdrBufferReader input, int count, int maximum) {
        if (count < 0 || count > maximum || count > input.remaining())
            throw new ProtocolException("invalid GameSo card count " + count + ", remaining " + input.remaining());
        List<Integer> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) result.add(input.readUInt8());
        return List.copyOf(result);
    }

    private static void require(TdrBufferReader input, int bytes, int subtype) {
        if (input.remaining() < bytes)
            throw new ProtocolException("GameSo subtype " + subtype + " truncated: need " + bytes
                    + ", remaining " + input.remaining());
    }

    public PlayerDetail decodePlayerDetailTlv(byte[] value) {
        TdrBufferReader input = new TdrBufferReader(value);
        require(input, 16, 10001);
        long playerId = input.readUInt32();
        long uin = input.readUInt32();
        int state = input.readInt16();
        int tableId = input.readInt16();
        int seatId = input.readInt16();
        int nickLength = input.readUInt16();
        if (nickLength > 64 || nickLength > input.remaining())
            throw new ProtocolException("invalid player nickname length " + nickLength);
        byte[] nick = input.readBytes(nickLength);
        int end = 0;
        while (end < nick.length && nick[end] != 0) end++;
        String nickname = new String(nick, 0, end, java.nio.charset.StandardCharsets.UTF_8);
        String avatarUrl = "";
        long beans = 0;
        if (input.remaining() >= 20) {
            input.readInt8();
            input.readInt8();
            input.readInt16();
            input.readUInt32();
            beans = Math.max(0, input.readInt64());
            input.readInt16();
            int openIdLength = input.readUInt16();
            if (openIdLength <= 128 && openIdLength <= input.remaining())
                input.readBytes(openIdLength);
            if (input.remaining() >= 12) {
                input.readInt32();
                input.readInt32();
                int count = input.readInt32();
                if (count >= 0 && count <= 32 && input.remaining() >= count * 4 + 10) {
                    input.readBytes(count * 4);
                    input.readInt32();
                    input.readInt32();
                    int avatarLength = input.readUInt16();
                    if (avatarLength <= 256 && avatarLength <= input.remaining()) {
                        byte[] avatar = input.readBytes(avatarLength);
                        int avatarEnd = 0;
                        while (avatarEnd < avatar.length && avatar[avatarEnd] != 0) avatarEnd++;
                        avatarUrl = new String(avatar, 0, avatarEnd,
                                java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return new PlayerDetail(playerId, uin, state, tableId, seatId, nickname, avatarUrl, beans);
    }

    private static PlayerBeans decodePlayerBeans(byte[] value) {
        TdrBufferReader input = new TdrBufferReader(value);
        if (input.remaining() < Short.BYTES + Long.BYTES * 2) return new PlayerBeans(0, -1);
        input.readInt16();
        long change = input.readInt64();
        return new PlayerBeans(change, Math.max(0, input.readInt64()));
    }

    private record PlayerBeans(long change, long current) {
    }

    /** wxpkg item-count notification: reason, item id, current count, previous count, sequence. */
    private static ItemBalance decodeItemBalance(byte[] value) {
        TdrBufferReader input = new TdrBufferReader(value);
        require(input, 5 * Integer.BYTES, 10007);
        int reason = input.readInt32();
        int itemId = input.readInt32();
        long current = Integer.toUnsignedLong(input.readInt32());
        long previous = Integer.toUnsignedLong(input.readInt32());
        int sequence = input.readInt32();
        return new ItemBalance(itemId, current, previous, reason, sequence);
    }

    public record RoomEvent(int type, long playerId, int roomId, int tableId, int seatId,
                            List<PlayerDetail> players, long beans, long beanChange,
                            List<ItemBalance> items) {
        public RoomEvent {
            players = List.copyOf(players == null ? List.of() : players);
            items = List.copyOf(items == null ? List.of() : items);
        }

        public RoomEventType eventType() {
            return RoomEventType.fromCode(type);
        }

        public RoomEvent(int type, long playerId, int roomId, int tableId, int seatId) {
            this(type, playerId, roomId, tableId, seatId, List.of(), -1, 0, List.of());
        }

        public RoomEvent(int type, long playerId, int roomId, int tableId, int seatId,
                         List<PlayerDetail> players) {
            this(type, playerId, roomId, tableId, seatId, players, -1, 0, List.of());
        }

        public RoomEvent(int type, long playerId, int roomId, int tableId, int seatId,
                         List<PlayerDetail> players, long beans) {
            this(type, playerId, roomId, tableId, seatId, players, beans, 0, List.of());
        }

        public RoomEvent(int type, long playerId, int roomId, int tableId, int seatId,
                         List<PlayerDetail> players, long beans, long beanChange) {
            this(type, playerId, roomId, tableId, seatId, players, beans, beanChange, List.of());
        }
    }

    public record ItemBalance(int itemId, long currentCount, long previousCount,
                              int reason, int sequence) {
    }

    public record PlayerDetail(long playerId, long uin, int state, int tableId, int seatId,
                               String nickname, String avatarUrl, long beans) {
        public PlayerDetail(long playerId, long uin, int state, int tableId, int seatId,
                            String nickname) {
            this(playerId, uin, state, tableId, seatId, nickname, "", 0);
        }

        public PlayerDetail(long playerId, long uin, int state, int tableId, int seatId,
                            String nickname, String avatarUrl) {
            this(playerId, uin, state, tableId, seatId, nickname, avatarUrl, 0);
        }
    }

    public record GameSoEvent(int metadata, int seatId, int subtype, int error,
                              int actorSeat, int nextSeat, int decision,
                              int cardType, int mainRank, int landlordSeat, int multiplier,
                              List<Integer> cards, List<Integer> bottomCards,
                              List<List<Integer>> hands, Settlement settlement, int nextTimeLimit,
                              int superDoubleItemId) {
        public GameSoType type() {
            return GameSoType.fromCode(subtype);
        }

        public GameSoEvent(int metadata, int seatId, int subtype, int error,
                           int actorSeat, int nextSeat, int decision,
                           int cardType, int mainRank, int landlordSeat, int multiplier,
                           List<Integer> cards, List<Integer> bottomCards, List<List<Integer>> hands,
                           Settlement settlement) {
            this(metadata, seatId, subtype, error, actorSeat, nextSeat, decision, cardType,
                    mainRank, landlordSeat, multiplier, cards, bottomCards, hands, settlement, 0, 0);
        }

        public GameSoEvent(int metadata, int seatId, int subtype, int error,
                           int actorSeat, int nextSeat, int decision,
                           int cardType, int mainRank, int landlordSeat, int multiplier,
                           List<Integer> cards, List<Integer> bottomCards, List<List<Integer>> hands) {
            this(metadata, seatId, subtype, error, actorSeat, nextSeat, decision, cardType,
                    mainRank, landlordSeat, multiplier, cards, bottomCards, hands, Settlement.empty(), 0, 0);
        }

        public GameSoEvent(int metadata, int seatId, int subtype, int error,
                           int actorSeat, int nextSeat, int decision,
                           int cardType, int mainRank, int landlordSeat, int multiplier,
                           List<Integer> cards, List<Integer> bottomCards) {
            this(metadata, seatId, subtype, error, actorSeat, nextSeat, decision, cardType,
                    mainRank, landlordSeat, multiplier, cards, bottomCards, List.of(), Settlement.empty(), 0, 0);
        }
    }

    public record Settlement(int resultFlag, List<Long> beanChanges, List<Long> resultValues,
                             List<Long> factors, List<List<Integer>> hands) {
        public Settlement {
            beanChanges = List.copyOf(beanChanges);
            resultValues = List.copyOf(resultValues);
            factors = List.copyOf(factors);
            hands = hands.stream().map(List::copyOf).toList();
        }

        public static Settlement empty() {
            return new Settlement(0, List.of(), List.of(), List.of(), List.of());
        }

        public boolean available() {
            return beanChanges.size() == 3;
        }
    }
}
