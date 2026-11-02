package dev.pdv.api.operations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class MessagingModels {
    private MessagingModels() { }
    public record Command(@NotNull UUID requestId,@NotNull @PositiveOrZero Long version,@NotBlank @Size(max=500) String reason) { }
    public record Delivery(UUID eventId,String type,String status,int attempts,long version,String error,Instant updatedAt,boolean processed) { }
    public record Queue(String namespace,String kind,boolean available,Long ready,Integer consumers,Instant observedAt,boolean stale) { }
    public record Page(List<Delivery> items,long total,int page,int size) { }
    public record Dashboard(Instant generatedAt,long pending,long dead,long published,Instant lastSynchronizedAt,List<Queue> queues,Page deliveries) { }
    public record Retry(UUID requestId,UUID eventId,String status,long version,Instant recordedAt) { }
    public record Execution(Retry response,boolean replayed) { }
}
