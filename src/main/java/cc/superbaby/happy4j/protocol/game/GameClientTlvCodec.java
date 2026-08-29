package cc.superbaby.happy4j.protocol.game;

import com.google.protobuf.CodedOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Encodes the Unity/3D client's protobuf union values for adapters that explicitly request them.
 * The WeChat mini-game's ordinary quick-match path sends an empty sit-down TLV list and therefore
 * must not call {@link #sitDownTlvs()}.
 */
public final class GameClientTlvCodec {
    public static final int TRANSPARENT_3D_TYPE = 20012;
    public static final int CLIENT_ABILITY_TYPE = 20017;
    public static final int CURRENT_TS_CODE_VERSION = 202605210;
    /**
     * Verified against a live device capture: "ReqSitDown CAPABILITY is: 402652783" for a sit-down that returned nResult 0.
     */
    public static final int CURRENT_CLIENT_ABILITY = 402652783;

    private GameClientTlvCodec() {
    }

    public static List<GameTlv> sitDownTlvs() {
        return List.of(transparent3d(false), clientAbility(CURRENT_TS_CODE_VERSION));
    }

    /**
     * roleType=1 is the 2D avatar and roleType=2 is the normal 3D client.
     */
    public static GameTlv transparent3d(boolean avatar2d) {
        byte[] role = message(out -> out.writeInt32(19, avatar2d ? 1 : 2));
        byte[] transparent = message(out -> {
            out.writeInt32(1, role.length);
            out.writeByteArray(2, role);
        });
        byte[] union = message(out -> out.writeByteArray(81, transparent));
        return new GameTlv(TRANSPARENT_3D_TYPE, union);
    }

    public static GameTlv clientAbility(int tsCodeVersion) {
        byte[] ability = message(out -> {
            out.writeInt32(1, CURRENT_CLIENT_ABILITY);
            out.writeInt32(2, tsCodeVersion);
        });
        byte[] union = message(out -> out.writeByteArray(89, ability));
        return new GameTlv(CLIENT_ABILITY_TYPE, union);
    }

    private static byte[] message(Encoder encoder) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(32);
            CodedOutputStream output = CodedOutputStream.newInstance(bytes);
            encoder.encode(output);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException("编码 GameSvr 客户端 TLV 失败", failure);
        }
    }

    @FunctionalInterface
    private interface Encoder {
        void encode(CodedOutputStream output) throws IOException;
    }

    public record GameTlv(int type, byte[] value) {
        public GameTlv {
            value = value.clone();
        }

        @Override
        public byte[] value() {
            return value.clone();
        }
    }
}
