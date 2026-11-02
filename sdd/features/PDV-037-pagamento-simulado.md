# PDV-037 — Criar processador de pagamento simulado

## Estado e fonte

[PDV-037](https://app.notion.com/p/4092c9a99917817f9f8fdeff82b5dad9), leitura viva em 01/11/2026 (Manaus), Backlog, RF-V03/Tela 07. Sem responsável/prazo informado. [Baseline textual, seções 8–10 e 12](../../docs/sources/PDV_ESPECIFICACAO_E_BACKLOG.md).

Aceite original: “Worker com cenários determinísticos APROVADO/RECUSADO/TIMEOUT; nenhuma API financeira externa.”

**Refinamento aprovado explicitamente pelo proprietário nesta conversa em 01/11/2026 (Manaus).** Checkout confirmado e estados/recorte 035–037 aprovados; implementação e testes registrados abaixo conforme execução.

## Contexto e objetivo

Adicionar um processador executável no worker independente. Cenário explícito e identidade durável produzem sempre a mesma decisão; repetição/reinício não sorteiam aprovação diferente e não recriam operação de pagamento.

## Definition of Ready e dependências

PDV-006 lida ao vivo, Em validação: processo e contrato AMQP de diagnóstico existem, sem pagamentos. PDV-036 relida ao vivo: implementação/269testes/0falhas publicada em988394a, CI Backend41382438009/Planning41382438050 SUCCESS; Em validação sem aceite automático. Estados/recorte aprovados e ADR-008 publicada. Pre-flight main/origin988394a, PG/Rabbit saudáveis e Migrate V15 SUCCESS. Spec criada antes do código; DoR satisfeito para worker sobre funções verificadas.

## Refinamento aprovado e fronteiras

APROVADO → resultado APROVADO; RECUSADO → RECUSADO; TIMEOUT → INDETERMINADO. A escolha é explícita no comando de simulação e persistida com método fictício, valor capturado e ID da operação; não depende de relógio, rede externa ou sorteio.

Worker grava decisão por identidade de operação, com transação e concorrência protegidas, usando a coordenação da 036 e a proteção de reserva antes do pagamento. Repetição recupera decisão original; conteúdo divergente é erro. Nenhum número de cartão, credencial financeira ou cobrança real.

Modo local explícito permite executar e observar o processador neste incremento. Transporte via Outbox/Inbox e consumidor confiável integrado à API pertencem à PDV-038; não publicar diretamente após commit alegando confiabilidade. Resultado do worker não baixa físico, libera reserva, emite comprovante ou conclui/cancela venda. Esses efeitos pertencem à 039/040/041. Logs sintéticos, sem corpos de mensagens ou credenciais.

## Operação técnica implementada

[Contrato operacional](../../docs/workflows/simulated-payments.md). Modo explícito scripts/worker.ps1 -Action Run -SimulatePayments (compatível com -ExpireReservations). Padrão desligado; não cria datasource de negócio sem modo ativo. Poll local limitado consulta intenções persistidas elegíveis no banco compartilhado; prioriza EM_PROCESSAMENTO para recuperar queda, filtra reservas vencidas antes do início e usa as funções V15 em transações separadas. Esse adaptador local não é consumidor RabbitMQ de negócio e não implementa Outbox/Inbox.

Configuração de conexão compartilhada evita dois datasources quando os modos são combinados; timeouts e logs sintéticos limitam cada rodada. Etapa concluída observada não é contagem de novos débitos: efeito único é provado por constraints/resultados/auditoria.

Verificação do JAR em processo filho, namespace e topologia AMQP próprios, com três cenários e retomada de etapa iniciada antes do boot. Processo encerrado e objetos próprios removidos antes do teardown; sem portas HTTP.

## Fluxos e invariantes

Progresso persistido sobrevive ao reinício. Dois workers não aplicam duas decisões. Falha de escrita/auditoria reverte decisão e permite retry seguro. TIMEOUT deixa reserva protegida mesmo depois do TTL; nenhuma liberação presumida. Resultado de pagamento não constitui confirmação de estoque.

## Plano de teste e migrações

Worker Verify em PostgreSQL e RabbitMQ reais: três cenários determinísticos, repetição/reinício, concorrência, conflito, falhas/rollback e reserva protegida. Verificar JAR independente executável sem servidor HTTP, modo opt-in, limpeza apenas de objetos próprios e ausência de migration/test fixture no JAR. Se alterar DDL compartilhado, validar upgrade/rebuild/replay e repetir API Verify apropriado. Planning/diff/links/segredos e CI do SHA próprio.

## Evidências executadas

Worker Verify SUCCESS2m42s:6unitários+30integrações=36, zero falhas/erros/skips/lint. Dez novos testes PG/JAR cobrem3cenários determinísticos/replay, dois workers, retry após início commitado/falha de resultado, lote limitado sem starvation por vencidos, agendamento combinado com datasource único/TTL protegido, modo desligado, migration ausente e JAR em processo filho processando3cenários/retomada. Regressões26 anteriores (AMQP, publicação e expiração) PASS. SQL API é recurso apenas de teste; nenhuma migration nova ou alteração em script aplicado.

JAR0migrations/0fixtures. Smoke adicional via runner real -Action Run -SimulatePayments -ExpireReservations com prefixo próprio confirmou os dois modos habilitados, startup7,369s, processo sem porta publicada e lab users/sales/payments/carts0. Parada graciosa do container exato/--rm e runnerexit0; topologia própria removida; consulta final0objetos smoke/teste, PG/Rabbit healthy,0VerifyDBs e0schemas de precheck SQL. A primeira consulta de contagem de filas não tratava resposta vazia; leitura corrigida e verificada novamente, sem mudar código ou ampliar exclusões.

AST PowerShell/planning/diff/links/UTF-8/segredos e autorevisão registrados antes do commit. CI/SHA próprios serão vinculados à tarefa viva após push. API não mudou neste incremento; sua suíte269 passou na036 e será repetida pela CI do SHA final, sem declarar repetição local nesta037. Fontes/testes congelados durante Verify.

## Revisão e aceite

main direta sem PR; testes/commit/CI separados por tarefa. Em validação após verificação/autorrevisão, aguardando aceite do proprietário. Preservar estados/aceites das predecessoras e não concluir tarefas futuras por este simulador.
