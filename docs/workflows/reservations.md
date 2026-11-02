# Reservas — PDV-029

Recorte confirmado pelo proprietário em 01/11/2026. ADMIN/OPERATOR com reservation.read/write consultam e modificam somente seus agregados. Produtos precisam estar ATIVOS; inteiros positivos, 1–100 produtos distintos por reserva. TTL obrigatório PDV_RESERVATION_TTL_SECONDS, demo/testes900s, positivo. A API não aceita prazo arbitrário do cliente.

| Rota | Contrato |
| --- | --- |
| POST /reservations | {requestId,items:[{productId,quantity,version}]} →201 Reservation |
| GET /reservations | Page própria, page0/size20, máximo100 |
| GET /reservations/{id} | Reservation própria; alheia404 |
| POST /reservations/{id}/renew | {requestId,version} →200 Reservation; version do agregado |

Reservation contém id/status/paymentProtected/expiresAt/version/createdAt/updatedAt/items[{productId,quantity}]. Resposta de escrita inclui Idempotent-Replay e no-store. Chave escopada por ator; fingerprint da operação/itens ordenados e resposta persistidos. Replay devolve snapshot original, sem renovar prazo/reservar novamente; conflito de conteúdo409. Não usar prazo do replay como leitura atual: consultar GET.

Lote tudo-ou-nada: bloqueia produtos em ordem e UPDATE condicional usa versão/disponível do saldo; reserved e versão aumentam, físico permanece. Guardas de saldo existentes impedem saída/ajuste abaixo do reservado. Versão da reserva e do saldo são independentes: renovar aumenta só a primeira. Renovação antes de vencer, desprotegida e com produtos ainda ATIVOS, define expiresAt=agora+TTL usando relógio PostgreSQL. Vencida não revive, mesmo se o worker ainda não rodou. Reservado vencido continua indisponível até liberação segura.

V12 mantém itens/operações/eventos imutáveis e funções comuns a API/worker. protect_inventory_reservation(id), destinada apenas à coordenação interna futura, bloqueia a reserva e ativa barreira antes de iniciar pagamento; chamada repetida não duplica evento. Barreira não pode ser limpa por este incremento. expire_inventory_reservation(id) só libera ACTIVE vencida/desprotegida, em transação, todos os itens/versões/status EXPIRED/auditoria juntos. Reservas protegidas continuam retidas durante aprovado/incerto até a futura reconciliação. Não há endpoint público para simular pagamento, liberar barreira ou confirmar/baixar venda. CONFIRMED/RELEASED são vocabulário reservado, sem transições implementadas aqui.

Sem venda_id fictício: checkout deverá vincular o agregado à venda em migration própria e na mesma transação que cria os itens/reserva/intenção. Worker automático é PDV-030; Saga, eventos e fechamento são tarefas próprias. Esta base não comprova sua integração.

400 validação/duplicata/frações,401/403 autenticação/permissão,404 inexistente/alheio,409 conflito/estado/saldo/renovação,503 falha de persistência com rollback. Nenhuma conta padrão. [Spec](../../sdd/features/PDV-029-reservas-ttl.md), [estoque](inventory.md), [Flyway/verificação](database-migrations.md).

## Worker — PDV-030

Modo explícito `scripts/worker.ps1 -Action Run -ExpireReservations` (ou configuração equivalente no JAR) executa a cada5s, até100 reservas por rodada. Banco deve ter V12 aplicada; worker não migra. Cada agregado tem transação própria, bloqueios/tempos limitados e status EXPIRED/evento/saldos atômicos. Barreira e renovação são reavaliadas sob lock; candidato antigo não autoriza liberação. Dois workers/repetição não duplicam o efeito. Diagnóstico AMQP é preservado.

Ativar proteção de pagamento e registrar/publicar sua intenção futura deverá ocorrer na mesma transação antes de qualquer processamento. Os testes desta etapa exercitam concorrência com essa barreira em outra conexão real; não simulam uma Saga completa ou uma aprovação financeira externa. Reservas protegidas são excluídas do polling e exigem reconciliação nas tarefas próprias. [Spec](../../sdd/features/PDV-030-expiracao-worker.md).
