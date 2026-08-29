package cc.superbaby.happy4j.protocol;

import cc.superbaby.happy4j.protocol.game.MiniGameGameSoCodec;
import cc.superbaby.happy4j.protocol.wire.TdrBufferWriter;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MiniGameGameSoCodecTest {
    private final MiniGameGameSoCodec codec = new MiniGameGameSoCodec();

    @Test
    void decodesRoomReadyAndStartEvents() {
        byte[] payload = new TdrBufferWriter().writeInt16(2)
                .writeInt32(6).writeInt32(42).writeInt16(0).writeInt16(10496).writeInt16(0).writeInt16(0)
                .writeInt32(5).writeInt32(42).writeInt16(0).writeInt16(10496).writeInt16(0).writeInt16(0)
                .toByteArray();

        assertThat(codec.decodeRoomEvents(payload)).extracting(MiniGameGameSoCodec.RoomEvent::type)
                .containsExactly(6, 5);
    }

    @Test
    void decodesRoomPlayerNicknameUinSeatAndReadyState() {
        byte[] nick = "Alice".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] avatar = "https://img.example/a.png".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        TdrBufferWriter detail = new TdrBufferWriter().writeInt32(42).writeInt32(397911882)
                .writeInt16(5).writeInt16(88).writeInt16(2).writeInt16(nick.length).writeBytes(nick)
                .writeInt8(1).writeInt8(1).writeInt16(1).writeInt32(0).writeInt64(123456).writeInt16(0)
                .writeInt16(2).writeBytes(new byte[]{'w', 'x'}).writeInt32(0).writeInt32(0)
                .writeInt32(0).writeInt32(260).writeInt32(64)
                .writeInt16(avatar.length).writeBytes(avatar);
        byte[] player = detail.toByteArray();
        byte[] payload = new TdrBufferWriter().writeInt16(1)
                .writeInt32(3).writeInt32(42).writeInt16(0).writeInt16(88).writeInt16(2)
                .writeInt16(1).writeInt16(10001).writeInt16(player.length).writeBytes(player)
                .toByteArray();

        MiniGameGameSoCodec.PlayerDetail decoded = codec.decodeRoomEvents(payload).get(0).players().get(0);
        assertThat(decoded.uin()).isEqualTo(397911882L);
        assertThat(decoded.nickname()).isEqualTo("Alice");
        assertThat(decoded.seatId()).isEqualTo(2);
        assertThat(decoded.state()).isEqualTo(5);
        assertThat(decoded.avatarUrl()).isEqualTo("https://img.example/a.png");
        assertThat(decoded.beans()).isEqualTo(123456);
    }

    @Test
    void decodesPlayerBalanceChangeTlv() {
        byte[] balance = new TdrBufferWriter().writeInt16(1).writeInt64(-600).writeInt64(8800)
                .writeInt32(0).writeInt64(0).toByteArray();
        byte[] payload = new TdrBufferWriter().writeInt16(1)
                .writeInt32(11).writeInt32(42).writeInt16(0).writeInt16(88).writeInt16(2)
                .writeInt16(1).writeInt16(10006).writeInt16(balance.length).writeBytes(balance)
                .toByteArray();

        MiniGameGameSoCodec.RoomEvent event = codec.decodeRoomEvents(payload).get(0);
        assertThat(event.beans()).isEqualTo(8800);
        assertThat(event.beanChange()).isEqualTo(-600);
    }

    @Test
    void decodesAuthoritativeItemCountChangeTlv() {
        byte[] item = new TdrBufferWriter().writeInt32(7).writeInt32(30020001)
                .writeInt32(3).writeInt32(4).writeInt32(9).toByteArray();
        byte[] payload = new TdrBufferWriter().writeInt16(1)
                .writeInt32(15).writeInt32(42).writeInt16(0).writeInt16(88).writeInt16(2)
                .writeInt16(1).writeInt16(10007).writeInt16(item.length).writeBytes(item)
                .toByteArray();

        MiniGameGameSoCodec.ItemBalance value = codec.decodeRoomEvents(payload).get(0).items().get(0);
        assertThat(value.itemId()).isEqualTo(30020001);
        assertThat(value.currentCount()).isEqualTo(3);
        assertThat(value.previousCount()).isEqualTo(4);
    }

    @Test
    void decodesStandardInitialSeventeenCardHand() {
        TdrBufferWriter game = new TdrBufferWriter().writeInt16(0).writeInt8(0)
                .writeInt8(0).writeInt32(1).writeInt32(0).writeInt16(17);
        for (int card = 1; card <= 17; card++) game.writeInt8(card);
        byte[] body = game.toByteArray();
        byte[] payload = new TdrBufferWriter().writeInt16(body.length).writeBytes(body).toByteArray();

        MiniGameGameSoCodec.GameSoEvent event = codec.decodeGameSo(payload);
        assertThat(event.seatId()).isZero();
        assertThat(event.subtype()).isEqualTo(1);
        assertThat(event.error()).isZero();
        assertThat(event.cards()).isEqualTo(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9,
                10, 11, 12, 13, 14, 15, 16, 17));
    }

    @Test
    void decodesCapturedInitialHand() {
        byte[] payload = HexFormat.of().parseHex(
                "001f00000001000000010000000000112e2102180b2308012f1d30161332280d06");

        MiniGameGameSoCodec.GameSoEvent event = codec.decodeGameSo(payload);
        assertThat(event.seatId()).isEqualTo(1);
        assertThat(event.subtype()).isEqualTo(1);
        assertThat(event.cards()).containsExactly(46, 33, 2, 24, 11, 35, 8, 1, 47,
                29, 48, 22, 19, 50, 40, 13, 6);
    }

    @Test
    void decodesCallRobTurnAndPlayTurn() {
        TdrBufferWriter order = gameSo(6).writeInt8(-1).writeInt8(2).writeInt8(0).writeInt8(0);
        MiniGameGameSoCodec.GameSoEvent orderEvent = codec.decodeGameSo(frame(order));
        assertThat(orderEvent.actorSeat()).isEqualTo(-1);
        assertThat(orderEvent.nextSeat()).isEqualTo(2);
        assertThat(orderEvent.decision()).isZero();

        TdrBufferWriter play = gameSo(4).writeInt8(0).writeInt8(1).writeInt8(2)
                .writeInt16(1).writeInt32(1).writeInt8(14).writeInt8(1).writeInt8(25);
        MiniGameGameSoCodec.GameSoEvent playEvent = codec.decodeGameSo(frame(play));
        assertThat(playEvent.actorSeat()).isEqualTo(1);
        assertThat(playEvent.nextSeat()).isEqualTo(2);
        assertThat(playEvent.cards()).containsExactly(1);
        assertThat(playEvent.nextTimeLimit()).isEqualTo(25);
    }

    @Test
    void decodesOrdinaryAndSuperDoubleOptions() {
        TdrBufferWriter multiple = gameSo(10).writeInt8(2)
                .writeInt8(1).writeInt8(0).writeInt32(0)
                .writeInt8(2).writeInt8(0).writeInt32(30020001)
                .writeInt8(0).writeInt8(0).writeInt8(0);

        MiniGameGameSoCodec.GameSoEvent event = codec.decodeGameSo(frame(multiple));
        assertThat(event.decision()).isEqualTo(3);
        assertThat(event.superDoubleItemId()).isEqualTo(30020001);
    }

    @Test
    void decodesAuthoritativeLocalMultiplierFromMultiSync() {
        TdrBufferWriter sync = new TdrBufferWriter().writeInt16(0).writeInt8(0).writeInt8(1)
                .writeInt32(12).writeInt32(0)
                .writeInt64(1).writeInt64(8).writeInt64(4).writeInt8(15);
        for (int i = 0; i < 15; i++) sync.writeInt32(i + 1);
        sync.writeInt32(2);

        MiniGameGameSoCodec.GameSoEvent event = codec.decodeGameSo(frame(sync));

        assertThat(event.multiplier()).isEqualTo(8);
        assertThat(event.decision()).isEqualTo(2);
    }

    @Test
    void decodesOriginalSettlementDeltaResultValuesFactorAndRemainingHands() {
        TdrBufferWriter result = gameSo(5).writeInt8(1)
                .writeInt64(1200).writeInt64(-600).writeInt64(-600)
                .writeInt64(11200).writeInt64(8400).writeInt64(9400)
                .writeInt64(30).writeInt64(15).writeInt64(15)
                .writeInt32(0)
                .writeInt32(2).writeInt8(3).writeInt8(16)
                .writeInt32(1).writeInt8(54);

        MiniGameGameSoCodec.GameSoEvent event = codec.decodeGameSo(frame(result));

        assertThat(event.settlement().available()).isTrue();
        assertThat(event.settlement().beanChanges()).containsExactly(1200L, -600L, -600L);
        assertThat(event.settlement().resultValues()).containsExactly(11200L, 8400L, 9400L);
        assertThat(event.settlement().factors()).containsExactly(30L, 15L, 15L);
        assertThat(event.settlement().hands()).containsExactly(List.of(), List.of(3, 16), List.of(54));
    }

    @Test
    void decodesReconnectHandsAndCurrentTurn() {
        TdrBufferWriter replay = gameSo(18)
                .writeInt8(0).writeInt8(7).writeInt8(0).writeInt8(-1).writeInt8(-1)
                .writeBytes(new byte[6]).writeInt8(5).writeInt8(5).writeInt8(0)
                .writeInt16(2).writeInt8(1).writeInt8(2)
                .writeInt16(0).writeInt16(0)
                .writeInt16(0)
                .writeInt16(1).writeInt8(3)
                .writeInt8(1).writeInt8(0).writeInt8(2);

        MiniGameGameSoCodec.GameSoEvent event = codec.decodeGameSo(frame(replay));
        assertThat(event.decision()).isEqualTo(7);
        assertThat(event.nextSeat()).isZero();
        assertThat(event.landlordSeat()).isEqualTo(2);
        assertThat(event.seatId()).isEqualTo(2);
        assertThat(event.hands().get(0)).containsExactly(1, 2);
        assertThat(event.bottomCards()).containsExactly(3);
    }

    @Test
    void decodesGameSoUnionInsideReplayTlv10014() {
        TdrBufferWriter union = new TdrBufferWriter().writeInt32(1).writeInt32(0)
                .writeInt16(2).writeInt8(3).writeInt8(16);
        byte[] bytes = union.toByteArray();
        MiniGameGameSoCodec.GameSoEvent event = codec.decodeReplayTlv(
                new TdrBufferWriter().writeInt16(bytes.length).writeBytes(bytes).toByteArray());
        assertThat(event.subtype()).isEqualTo(1);
        assertThat(event.cards()).containsExactly(3, 16);
    }

    private static TdrBufferWriter gameSo(int subtype) {
        return new TdrBufferWriter().writeInt16(0).writeInt8(0).writeInt8(-1)
                .writeInt32(subtype).writeInt32(0);
    }

    private static byte[] frame(TdrBufferWriter game) {
        byte[] body = game.toByteArray();
        return new TdrBufferWriter().writeInt16(body.length).writeBytes(body).toByteArray();
    }
}
