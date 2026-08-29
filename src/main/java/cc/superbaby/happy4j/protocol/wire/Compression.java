package cc.superbaby.happy4j.protocol.wire;

/**
 * Values used by CCompressAlgorithm and CCSHead flag bits 2..3.
 */
public enum Compression {
    NONE(0),
    LZ4_BLOCK(1),
    ZLIB(2),
    RESERVED(3);

    private final int id;

    Compression(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static Compression fromId(int id) {
        return switch (id) {
            case 0 -> NONE;
            case 1 -> LZ4_BLOCK;
            case 2 -> ZLIB;
            case 3 -> RESERVED;
            default -> throw new IllegalArgumentException("compression id: " + id);
        };
    }
}
