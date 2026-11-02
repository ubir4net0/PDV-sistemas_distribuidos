package dev.pdv.worker.business;

import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/** Passive observation of exactly the configured business namespace. Never drains queues. */
public class QueueMonitor {
    private final JdbcTemplate jdbc;
    private final RabbitAdmin admin;
    private final BusinessTopology names;
    public QueueMonitor(JdbcTemplate jdbc,RabbitAdmin admin,BusinessTopology names) { this.jdbc=jdbc; this.admin=admin; this.names=names; }
    @Scheduled(fixedDelayString="${pdv.operations.interval-ms:5000}",initialDelayString="${pdv.operations.interval-ms:5000}")
    public void sample() {
        try {
            for(var kind:List.of("REQUESTS","RESULTS","DEAD")) {
                String queue=switch(kind) { case "REQUESTS" -> names.requests(); case "RESULTS" -> names.results(); default -> names.dead(); };
                QueueInformation info=null; try { info=admin.getQueueInfo(queue); } catch(AmqpException unavailable) { /* unavailable is a state, never a zero count */ }
                jdbc.update("""
                        INSERT INTO messaging_queue_samples(namespace,kind,available,ready,consumers) VALUES (?,?,?,?,?)
                        ON CONFLICT(namespace,kind) DO UPDATE SET available=EXCLUDED.available,ready=EXCLUDED.ready,consumers=EXCLUDED.consumers,observed_at=clock_timestamp()
                        """,names.prefix(),kind,info!=null,info==null ? null : (long)info.getMessageCount(),info==null ? null : info.getConsumerCount());
            }
        } catch(DataAccessException unavailable) { LoggerFactory.getLogger(QueueMonitor.class).warn("Queue observations unavailable; previous samples must be treated as stale"); }
    }
}
