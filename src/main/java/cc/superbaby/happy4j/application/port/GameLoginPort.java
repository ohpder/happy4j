package cc.superbaby.happy4j.application.port;

import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.PlatformCredential;

public interface GameLoginPort {
    GameCredential login(PlatformCredential credential);
}
