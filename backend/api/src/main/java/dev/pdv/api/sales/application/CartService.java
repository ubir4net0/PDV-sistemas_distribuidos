package dev.pdv.api.sales.application;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.sales.application.CartModels.Cart;
import dev.pdv.api.sales.application.CartModels.Create;
import dev.pdv.api.sales.application.CartModels.Execution;
import dev.pdv.api.sales.application.CartModels.SetItem;
import dev.pdv.api.sales.infrastructure.CartEntity;
import dev.pdv.api.sales.infrastructure.CartItemEntity;
import dev.pdv.api.sales.infrastructure.CartStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {
    private final CartStore store;
    public CartService(CartStore store) { this.store = store; }
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('cart.write')")
    public Execution create(Create input, UUID actor) {
        String fingerprint = digest(input.customerId().toString()); var replay = store.replay(actor, input.requestId(), fingerprint);
        if (replay.isPresent()) { return new Execution(replay.get(), true); }
        store.activeCustomer(input.customerId()); var entity = new CartEntity(UUID.randomUUID(), actor, input.customerId(), store.now()); store.add(entity); store.flush();
        var cart = store.view(entity); store.audit(actor, "CREATED", null, cart); store.response(actor, input.requestId(), fingerprint, cart); return new Execution(cart, false);
    }
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('cart.write')")
    public Cart setItem(UUID id, UUID product, SetItem input, UUID actor) {
        var entity = store.own(id, actor, true); store.editable(id); check(entity, input.version()); var before = store.view(entity);
        store.activeCustomer(entity.customerId()); var price = store.activePrice(product); var existing = store.item(id, product);
        if (existing.isPresent()) { existing.get().quantity(input.quantity()); } else { store.add(new CartItemEntity(id, product, input.quantity(), price)); }
        entity.touch(store.now()); store.flush(); var after = store.view(entity); store.audit(actor, "SET_ITEM", before, after); return after;
    }
    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('cart.write')")
    public Cart remove(UUID id, UUID product, Long version, UUID actor) {
        if (version == null || version < 0) { throw new ApiFailure(400, "invalid_request"); }
        var entity = store.own(id, actor, true); store.editable(id); check(entity, version); var before = store.view(entity);
        store.remove(store.item(id, product).orElseThrow(() -> new ApiFailure(404, "cart_item_not_found")));
        entity.touch(store.now()); store.flush(); var after = store.view(entity); store.audit(actor, "REMOVE_ITEM", before, after); return after;
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('cart.read')")
    public Cart get(UUID id, UUID actor) { return store.view(store.own(id, actor, false)); }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('cart.read')")
    public Page<Cart> list(UUID actor, int page, int size) { Page.validate(page, size); return store.list(actor, page, size); }
    private static void check(CartEntity entity, long version) {
        if (entity.version() != version) { throw new ApiFailure(409, "version_conflict"); }
        if (version == Long.MAX_VALUE) { throw new ApiFailure(409, "version_limit"); }
    }
    private static String digest(String input) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
