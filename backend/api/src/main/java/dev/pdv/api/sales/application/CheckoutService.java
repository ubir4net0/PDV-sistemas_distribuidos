package dev.pdv.api.sales.application;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.inventory.application.ReservationModels.Item;
import dev.pdv.api.inventory.infrastructure.ReservationStore;
import dev.pdv.api.sales.application.CheckoutModels.Checkout;
import dev.pdv.api.sales.application.CheckoutModels.Execution;
import dev.pdv.api.sales.application.CheckoutModels.Sale;
import dev.pdv.api.sales.infrastructure.CartStore;
import dev.pdv.api.sales.infrastructure.CheckoutStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CheckoutService {
    private final CartStore carts;
    private final CheckoutStore store;
    private final ReservationStore reservations;
    private final int ttl;
    public CheckoutService(CartStore carts, CheckoutStore store, ReservationStore reservations, @Value("${pdv.reservations.ttl-seconds}") int ttl) {
        if (ttl<=0) { throw new IllegalArgumentException("Reservation TTL must be positive"); }
        this.carts = carts; this.store = store; this.reservations = reservations; this.ttl = ttl;
    }
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('sale.checkout')")
    public Execution checkout(UUID id, Checkout input, String key, UUID actor) {
        if (key == null || !key.matches("[A-Za-z0-9_.:-]{1,128}")) { throw new ApiFailure(400, "invalid_idempotency_key"); }
        String fingerprint = digest(id + ":" + input.version() + ":" + input.method() + ":" + input.scenario());
        var replay = store.replay(actor, key, fingerprint); if (replay.isPresent()) { return new Execution(replay.get(), true); }
        var entity = carts.own(id, actor, true); carts.editable(id);
        if (entity.version()!=input.version() || entity.version()==Long.MAX_VALUE) { throw new ApiFailure(409, "version_conflict"); }
        var cart = carts.view(entity); if (cart.items().isEmpty()) { throw new ApiFailure(409, "cart_empty"); }
        carts.activeCustomer(cart.customerId());
        for (var line : cart.items()) {
            if (carts.activePrice(line.productId()).compareTo(line.unitPrice())!=0) { throw new ApiFailure(409, "price_changed"); }
        }
        for (var line : cart.items()) { store.reserve(line); }
        UUID sale = UUID.randomUUID(); UUID reservation = UUID.randomUUID(); UUID payment = UUID.randomUUID();
        reservations.create(reservation, actor, cart.items().stream().map(i -> new Item(i.productId(), i.quantity(), 0L)).toList(), ttl);
        entity.touch(carts.now()); carts.flush();
        store.insert(sale, reservation, payment, actor, cart, input); var result = store.own(sale, actor); store.record(actor, key, fingerprint, result); return new Execution(result, false);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('sale.read')")
    public Sale get(UUID id, UUID actor) { return store.own(id, actor); }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('sale.read')")
    public Receipt receipt(UUID id, UUID actor) { return store.receipt(id, actor); }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('sale.read')")
    public SaleTimeline timeline(UUID id, UUID actor) { return store.timeline(id, actor); }
    private static String digest(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
