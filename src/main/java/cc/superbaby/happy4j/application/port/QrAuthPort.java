package cc.superbaby.happy4j.application.port;

import cc.superbaby.happy4j.domain.PlatformCredential;

public interface QrAuthPort {
    String provider();

    QrChallenge start();

    QrPollResult poll(String challengeId);

    byte[] image(String challengeId);

    record QrChallenge(String id, byte[] png, long expiresInSeconds) {
        public QrChallenge {
            png = png.clone();
        }

        @Override
        public byte[] png() {
            return png.clone();
        }
    }

    record QrPollResult(Status status, String message, PlatformCredential credential) {
        public enum Status {WAITING, SCANNED, CONFIRMED, EXPIRED, FAILED}
    }
}
