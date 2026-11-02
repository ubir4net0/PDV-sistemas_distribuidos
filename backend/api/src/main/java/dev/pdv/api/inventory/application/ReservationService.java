package dev.pdv.api.inventory.application;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.ReservationModels.Create;
import dev.pdv.api.inventory.application.ReservationModels.Execution;
import dev.pdv.api.inventory.application.ReservationModels.Renew;
import dev.pdv.api.inventory.application.ReservationModels.Reservation;
import dev.pdv.api.inventory.infrastructure.ReservationStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ReservationService {
    private final ReservationStore store;
    private final JsonMapper json;
    private final int ttl;
    public ReservationService(ReservationStore store, JsonMapper json, @Value("${pdv.reservations.ttl-seconds}") int ttl) {
        if (ttl <= 0) { throw new IllegalArgumentException("Reservation TTL must be positive"); }
        this.store = store; this.json = json; this.ttl = ttl;
    }
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('reservation.write')")
    public Execution create(Create input, UUID actor) {
        var seen = new HashSet<UUID>();
        var items = input.items().stream().sorted(Comparator.comparing(i -> i.productId().toString())).toList();
        if (items.stream().anyMatch(i -> !seen.add(i.productId()))) { throw new ApiFailure(400, "invalid_request"); }
        String fingerprint = digest("create:" + json.writeValueAsString(items));
        var replay = store.replay(actor, input.requestId(), fingerprint); if (replay.isPresent()) { return new Execution(replay.get(), true); }
        store.activeProducts(items.stream().map(ReservationModels.Item::productId).toList());
        for (var item : items) { store.reserve(item); }
        UUID id = UUID.randomUUID(); store.create(id, actor, items, ttl);
        var result = store.own(id, actor, false); store.saveResponse(actor, input.requestId(), fingerprint, result); return new Execution(result, false);
    }
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('reservation.write')")
    public Execution renew(UUID id, Renew input, UUID actor) {
        String fingerprint = digest("renew:" + id + ":" + input.version());
        var replay = store.replay(actor, input.requestId(), fingerprint); if (replay.isPresent()) { return new Execution(replay.get(), true); }
        var before = store.own(id, actor, true);
        if (before.version() != input.version()) { throw new ApiFailure(409, "version_conflict"); }
        if (!"ACTIVE".equals(before.status()) || before.paymentProtected() || !before.expiresAt().isAfter(store.now())) { throw new ApiFailure(409, "reservation_not_renewable"); }
        store.activeProducts(before.items().stream().map(ReservationModels.ReservedItem::productId).toList()); store.renew(before, ttl);
        var result = store.own(id, actor, false); store.saveResponse(actor, input.requestId(), fingerprint, result); return new Execution(result, false);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('reservation.read')")
    public Reservation get(UUID id, UUID actor) { return store.own(id, actor, false); }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('reservation.read')")
    public Page<Reservation> list(UUID actor, int page, int size) { Page.validate(page, size); return store.list(actor, page, size); }
    private static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
