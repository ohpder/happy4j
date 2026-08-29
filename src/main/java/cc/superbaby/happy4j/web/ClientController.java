package cc.superbaby.happy4j.web;

import cc.superbaby.happy4j.application.HappyDdzClientService;
import cc.superbaby.happy4j.application.port.QrAuthPort;
import cc.superbaby.happy4j.domain.ActivityView;
import cc.superbaby.happy4j.domain.AppPrincipal;
import cc.superbaby.happy4j.domain.ClientSession;
import cc.superbaby.happy4j.domain.GameMode;
import cc.superbaby.happy4j.domain.MissionTask;
import cc.superbaby.happy4j.domain.PlayerProfile;
import cc.superbaby.happy4j.domain.RemoteEvent;
import cc.superbaby.happy4j.domain.TableSnapshot;
import cc.superbaby.happy4j.domain.WalletBalance;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api")
public final class ClientController {
    private final HappyDdzClientService client;

    public ClientController(HappyDdzClientService client) {
        this.client = client;
    }

    @PostMapping("/sessions")
    public ClientSession.Snapshot create(@AuthenticationPrincipal AppPrincipal user) {
        return client.createSession(user.userId());
    }

    @GetMapping("/sessions")
    public List<ClientSession.Snapshot> sessions(@AuthenticationPrincipal AppPrincipal user) {
        return client.userSessions(user.userId());
    }

    @PostMapping("/sessions/{id}/close")
    public Map<String, Boolean> close(@AuthenticationPrincipal AppPrincipal user, @PathVariable UUID id) {
        client.closeSession(user.userId(), id);
        return Map.of("ok", true);
    }

    @GetMapping("/sessions/{id}")
    public ClientSession.Snapshot snapshot(@PathVariable UUID id) {
        return client.session(id).snapshot();
    }

    @GetMapping("/sessions/{id}/event-history")
    public List<RemoteEvent> eventHistory(@PathVariable UUID id,
                                          @RequestParam(defaultValue = "0") long after) {
        return client.session(id).eventsAfter(after);
    }

    @PostMapping("/sessions/{id}/login/qr")
    public Map<String, Object> startQr(@PathVariable UUID id) {
        return startQr(id, "qq");
    }

    @PostMapping("/sessions/{id}/login/{provider}/qr")
    public Map<String, Object> startQr(@PathVariable UUID id, @PathVariable String provider) {
        QrAuthPort.QrChallenge challenge = client.startQr(id, provider);
        return Map.of("challengeId", challenge.id(), "expiresIn", challenge.expiresInSeconds(),
                "provider", provider, "image", "/api/sessions/" + id + "/login/qr/image");
    }

    @GetMapping("/sessions/{id}/login/qr/image")
    public ResponseEntity<byte[]> qrImage(@PathVariable UUID id) {
        byte[] image = client.qrImage(id);
        MediaType type = image.length > 2 && image[0] == (byte) 0xff && image[1] == (byte) 0xd8
                ? MediaType.IMAGE_JPEG : MediaType.IMAGE_PNG;
        return ResponseEntity.ok().contentType(type).cacheControl(CacheControl.noStore()).body(image);
    }

    @GetMapping("/sessions/{id}/login/qr/poll")
    public Map<String, Object> pollQr(@PathVariable UUID id) {
        QrAuthPort.QrPollResult result = client.pollQr(id);
        return Map.of("status", result.status(), "message", result.message());
    }

    @GetMapping("/modes")
    public List<GameMode> modes() {
        return client.gameModes();
    }

    @PostMapping("/sessions/{id}/activities/refresh")
    public CompletableFuture<List<ActivityView>> activities(@PathVariable UUID id) {
        return client.refreshActivities(id);
    }

    @PostMapping("/sessions/{id}/wallet/refresh")
    public CompletableFuture<WalletBalance> wallet(@PathVariable UUID id) {
        return client.refreshWallet(id);
    }

    @PostMapping("/sessions/{id}/profile/refresh")
    public CompletableFuture<PlayerProfile> profile(@PathVariable UUID id) {
        return client.refreshProfile(id);
    }

    @PostMapping("/sessions/{id}/missions/refresh")
    public CompletableFuture<List<MissionTask>> missions(@PathVariable UUID id) {
        return client.refreshMissions(id);
    }

    @PostMapping("/sessions/{id}/missions/{missionId}/claim")
    public CompletableFuture<ActivityView> claimMission(@PathVariable UUID id, @PathVariable @Min(1) int missionId) {
        return client.claimMission(id, missionId);
    }

    @PostMapping("/sessions/{id}/rewards/sign-in")
    public CompletableFuture<ActivityView> signIn(@PathVariable UUID id, @Valid @RequestBody SignInRequest body) {
        return client.claimSignIn(id, body.command(), body.prizeIndex());
    }

    @PostMapping("/sessions/{id}/rewards/treasure")
    public CompletableFuture<ActivityView> treasure(@PathVariable UUID id, @Valid @RequestBody TreasureRequest body) {
        return client.openTreasure(id, body.itemId(), body.itemSeq());
    }

    @PostMapping("/sessions/{id}/games/enter")
    public CompletableFuture<TableSnapshot> enter(@PathVariable UUID id, @Valid @RequestBody EnterRequest body) {
        return client.enterMode(id, body.sceneId());
    }

    @PostMapping("/sessions/{id}/games/play")
    public CompletableFuture<?> play(@PathVariable UUID id, @Valid @RequestBody PlayRequest body) {
        return client.play(id, body.cards(), body.pass(), body.payloadBase64());
    }

    @PostMapping("/sessions/{id}/games/ready")
    public CompletableFuture<?> ready(@PathVariable UUID id) {
        return client.ready(id);
    }

    @PostMapping("/sessions/{id}/games/order")
    public CompletableFuture<?> order(@PathVariable UUID id, @Valid @RequestBody ToggleRequest body) {
        return client.order(id, body.accept());
    }

    @PostMapping("/sessions/{id}/games/multiple")
    public CompletableFuture<?> multiple(@PathVariable UUID id, @Valid @RequestBody ToggleRequest body) {
        return client.multiple(id, body.accept());
    }

    @PostMapping("/sessions/{id}/games/super-multiple")
    public CompletableFuture<?> superMultiple(@PathVariable UUID id) {
        return client.superMultiple(id);
    }

    @PostMapping("/sessions/{id}/games/trust")
    public CompletableFuture<?> trust(@PathVariable UUID id, @Valid @RequestBody ToggleRequest body) {
        return client.trust(id, body.accept());
    }

    @PostMapping("/sessions/{id}/games/change-table")
    public CompletableFuture<TableSnapshot> changeTable(@PathVariable UUID id) {
        return client.changeTable(id);
    }

    @PostMapping("/sessions/{id}/games/leave")
    public CompletableFuture<TableSnapshot> leaveRoom(@PathVariable UUID id) {
        return client.leaveRoom(id);
    }

    public record SignInRequest(@Min(1) int command, int prizeIndex) {
    }

    public record TreasureRequest(@Min(1) int itemId, int itemSeq) {
    }

    public record EnterRequest(@Min(1) int sceneId) {
    }

    public record PlayRequest(@NotNull List<Integer> cards, boolean pass, String payloadBase64) {
    }

    public record ToggleRequest(boolean accept) {
    }
}
