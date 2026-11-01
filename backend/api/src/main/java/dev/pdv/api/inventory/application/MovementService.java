package dev.pdv.api.inventory.application;

import dev.pdv.api.configuration.ApiFailure;
import dev.pdv.api.customers.application.Page;
import dev.pdv.api.inventory.application.InventoryModels.Balance;
import dev.pdv.api.inventory.application.MovementModels.Batch;
import dev.pdv.api.inventory.application.MovementModels.Execution;
import dev.pdv.api.inventory.application.MovementModels.Item;
import dev.pdv.api.inventory.application.MovementModels.Movement;
import dev.pdv.api.inventory.application.MovementModels.Operation;
import dev.pdv.api.inventory.application.MovementModels.Single;
import dev.pdv.api.inventory.infrastructure.InventoryStore;
import dev.pdv.api.inventory.infrastructure.MovementStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MovementService {
    private final InventoryStore balances;
    private final MovementStore movements;
    private final JsonMapper json;
    public MovementService(InventoryStore balances, MovementStore movements, JsonMapper json) { this.balances = balances; this.movements = movements; this.json = json; }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('inventory.write')")
    public Execution single(UUID id, Single input, UUID actor) { return execute(input.requestId(), List.of(input.item(id)), actor); }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('inventory.write')")
    public Execution batch(Batch input, UUID actor) { return execute(input.requestId(), input.items(), actor); }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR') and hasAuthority('inventory.read')")
    public Page<Movement> history(UUID id, int page, int size) { Page.validate(page, size); balances.require(id); return movements.history(id, page, size); }
    private Execution execute(UUID request, List<Item> raw, UUID actor) {
        var seen = new HashSet<UUID>();
        var items = raw.stream().map(item -> {
            if (!seen.add(item.productId()) || (item.quantity() == 0 && !"ADJUST".equals(item.type()))) { throw new ApiFailure(400, "invalid_request"); }
            return new Item(item.productId(), item.type(), item.quantity(), item.reason().strip(), item.version());
        }).sorted(Comparator.comparing(item -> item.productId().toString())).toList();
        String fingerprint = digest(json.writeValueAsString(items));
        movements.lockRequest(actor, request); var previous = movements.replay(actor, request, fingerprint);
        if (previous.isPresent()) { return new Execution(previous.get(), true); }
        var changes = new ArrayList<Change>();
        for (var item : items) {
            var before = balances.require(item.productId());
            if (before.version() != item.version()) { throw new ApiFailure(409, "version_conflict"); }
            var after = movements.change(item);
            if (after == null) {
                var latest = balances.require(item.productId());
                if (latest.version() != item.version()) { throw new ApiFailure(409, "version_conflict"); }
                if (latest.version() == Long.MAX_VALUE) { throw new ApiFailure(409, "version_limit"); }
                throw new ApiFailure(409, switch (item.type()) { case "ENTRY" -> "quantity_overflow"; case "EXIT" -> "insufficient_stock"; default -> "stock_reserved"; });
            }
            changes.add(new Change(item, before, after));
        }
        UUID operation = UUID.randomUUID(); var now = movements.now();
        var result = changes.stream().map(change -> new Movement(UUID.randomUUID(), operation, change.item().productId(), change.item().type(), change.item().quantity(),
                change.after().physical() - change.before().physical(), change.item().reason(), actor, change.before().physical(), change.before().reserved(),
                change.before().version(), change.after(), now)).toList();
        var response = new Operation(operation, request, result, now); movements.append(response, actor, fingerprint);
        return new Execution(response, false);
    }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
    private record Change(Item item, Balance before, Balance after) { }
}
