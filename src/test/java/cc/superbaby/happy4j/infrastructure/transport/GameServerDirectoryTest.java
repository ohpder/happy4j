package cc.superbaby.happy4j.infrastructure.transport;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameServerDirectoryTest {
    @Test
    void resolvesExactNodeThenCombinesEveryHostAndPort() {
        GameServerDirectory directory = GameServerDirectory.parse("""
                [AppInfo]
                port=443;80;
                [Server0]
                ServerID=30000
                IPLevel0=fallback.example;
                [Server1]
                ServerID=7540
                IPLevel0=primary.example;
                IPLevel1=10.0.0.1;10.0.0.2;
                """);

        assertThat(directory.resolve(7540)).extracting("host", "port").containsExactly(
                org.assertj.core.groups.Tuple.tuple("primary.example", 443),
                org.assertj.core.groups.Tuple.tuple("primary.example", 80),
                org.assertj.core.groups.Tuple.tuple("10.0.0.1", 443),
                org.assertj.core.groups.Tuple.tuple("10.0.0.1", 80),
                org.assertj.core.groups.Tuple.tuple("10.0.0.2", 443),
                org.assertj.core.groups.Tuple.tuple("10.0.0.2", 80));
    }

    @Test
    void fallsBackToServer30000LikeNativeParser() {
        GameServerDirectory directory = GameServerDirectory.parse("""
                [AppInfo]
                port=443;
                [Server0]
                ServerID=30000
                IPLevel0=gamelotus.example;
                """);

        assertThat(directory.resolve(7540)).singleElement().satisfies(endpoint -> {
            assertThat(endpoint.serverId()).isEqualTo(7540);
            assertThat(endpoint.host()).isEqualTo("gamelotus.example");
            assertThat(endpoint.port()).isEqualTo(443);
        });
    }
}
