# Pagamento simulado local — PDV-037

[Spec/evidências](../../sdd/features/PDV-037-pagamento-simulado.md), [Saga V15](sale-saga.md) e [ADR-008 aprovada](../decisions/ADR-008-estados-venda-pagamento.md). Processador executável no worker independente, sem servidor HTTP, dependência financeira ou cobrança real.

Com PostgreSQL/RabbitMQ saudáveis e migrations API V15 aplicadas:

```powershell
./scripts/worker.ps1 -Action Verify
./scripts/worker.ps1 -Action Run -SimulatePayments -ExpireReservations
```

SimulatePayments e ExpireReservations são modos independentes/combináveis, apenas com Action Run. Sem modos ativos, padrão continua diagnóstico AMQP sem datasource de negócio. Para execução nativa, PDV_SIMULATED_PAYMENTS_ENABLED=true e credenciais PDV_DB_URL/USER/PASSWORD privadas; PDV_DB_SCHEMA padrão pdv. Worker valida funções existentes, falha se faltarem e nunca executa migrations em runtime. Um datasource é compartilhado pelos dois jobs quando combinados, com schema validado e timeouts conexão5s/socket10s/query5s, lock2s/statement4s por comando.

O checkout persiste método fictício e cenário explícitos. Worker seleciona intenções elegíveis no banco compartilhado, a cada5s/até100 por rodada. EM_PROCESSAMENTO protegido tem prioridade para retomar quedas; CRIADO exige reserva ACTIVE dentro do prazo. Reservas vencidas antes do início não monopolizam o lote e não são reanimadas. A seleção não é autorização: funções V15 revalidam sob locks.

Primeira transação inicia/protege pagamento; segunda registra decisão. APROVADO→APROVADO, RECUSADO→RECUSADO, TIMEOUT→INDETERMINADO, sem sorteio, espera de rede financeira ou mudança da decisão a cada retry. Queda/falha de resultado após início deixa EM_PROCESSAMENTO protegido; próxima rodada retoma a mesma identidade. Dois workers podem observar a mesma operação; resultado/auditoria duráveis garantem efeito único. completed no log conta observações bem-sucedidas, não novas cobranças ou novos registros únicos. Logs contêm contagens e erros constantes, sem corpos/credenciais.

Resultado conhecido fica em payment_results; GET /sales/{id} com propriedade/permissão mostra estado atual. TIMEOUT conserva venda AGUARDANDO_PAGAMENTO e reserva protegida; somente futura reconciliação pode resolver a incerteza. Recusa registrada ainda não libera reserva e aprovação ainda não baixa físico/conclui venda. Em V15, resultados terminalizados não entram novamente no poll.

Este adaptador local de polling demonstra o worker sobre coordenação persistida. O diagnóstico AMQP permanece separado; nenhuma fila financeira é criada e nenhum evento é publicado diretamente após commit. Transporte de negócio Outbox/Inbox/retry/DLQ pertence à PDV-038; baixa/comprovante à039; liberação/reconciliação à040; cancelamento/estorno/devolução à041.

Verify usa PostgreSQL/RabbitMQ reais e banco UUID exclusivo. Integrações cobrem cenários/repetição, trabalhadores concorrentes, falha após início/retry, lote limitado sem starvation por vencidos, agendamento combinado/datasource único, modo desligado, ausência de migration e execução do JAR em processo filho com três cenários/retomada. Processo, namespace, arquivos temporários e topologia AMQP próprios são encerrados/removidos; nenhum DDL de API ou fixture é empacotado no JAR.
