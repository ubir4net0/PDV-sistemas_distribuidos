# PDV-036 — Modelar Saga e máquina de estados de venda

## Estado e fonte

[PDV-036](https://app.notion.com/p/4092c9a99917815aa26ffe26ced87409), lida ao vivo em 01/11/2026 (Manaus), Backlog, RF-V03. Bloqueio vivo DEC-01. Sem responsável/prazo informado.

Aceite original: “Transições válidas/ilegais testadas; pagamento, reserva e confirmação reentrantes.” Fontes: [baseline textual, seções 8–10 e 12](../../docs/sources/PDV_ESPECIFICACAO_E_BACKLOG.md), [ADR-004](../../docs/decisions/ADR-004-saga-e-consistencia.md), página viva de decisões.

**Refinamento aprovado explicitamente pelo proprietário nesta conversa em 01/11/2026 (Manaus).** Checkout confirmado e estados/recorte 035–037 aprovados; implementação e testes registrados abaixo conforme execução.

## Contexto e objetivo

Persistir progresso e coordenar transações locais sem tratar aprovação isolada como venda concluída. Evento repetido recupera efeito conhecido; transição ilegal é rejeitada; resultado desconhecido conserva estoque reservado.

## Definition of Ready e dependências

PDV-003 lida ao vivo, Em validação; ADRs e matriz de falhas disponíveis, decisões específicas ainda pendentes. PDV-035 relida ao vivo e entregue tecnicamente em f67a808, 258 testes e CI Backend41380567988/Planning41380567995 SUCCESS, Em validação sem aceite automático. Pre-flight repetido: main/origin f67a808, PG/Rabbit saudáveis e Migrate V14 SUCCESS. Refinamento aprovado, DoR satisfeito para a coordenação.

## Refinamento aprovado

Vocabulário canônico de venda: RASCUNHO (etapa do carrinho), AGUARDANDO_PAGAMENTO, PAGAMENTO_APROVADO, CONCLUIDA, PAGAMENTO_RECUSADO, CANCELAMENTO_PENDENTE, CANCELADA. Checkout cria AGUARDANDO_PAGAMENTO. APROVADA isolada não é estado alternativo.

Pagamento: CRIADO → EM_PROCESSAMENTO → APROVADO / RECUSADO / INDETERMINADO. TIMEOUT é cenário do simulador, cujo estado durável é INDETERMINADO. Aprovação e recusa conhecidas não podem ser substituídas por um timeout atrasado. Resultado desconhecido não equivale a recusa e não libera reserva. Reconciliação e tentativas futuras pertencem à PDV-040.

Coordenação persistida liga venda, reserva, pagamento e identidade de operação. Antes de iniciar pagamento, a mesma transação adquire a barreira de proteção da reserva e valida seu estado/prazo. Reserva vencida não pode ser reanimada. Reinício/repetição preservam decisão e auditoria.

Este incremento entrega modelo/guardas de estados e coordenação persistida, com idempotência por operação e evidência de transições. Efeitos finais de baixa/comprovante pertencem à PDV-039; liberação/reconciliação à 040; estorno/devolução/cancelamento à 041. Guardas de confirmação precisam distinguir repetição de confirmação conhecida de primeira confirmação inválida; não expor rota que conclua venda sem esses efeitos.

## Coordenação interna implementada

Nova migration V15, sem editar V14 aplicada. Contrato detalhado em [Saga persistida](../../docs/workflows/sale-saga.md). Funções PostgreSQL compartilhadas pela API/worker adquirem locks de venda/pagamento/reserva em ordem fixa. Início valida prazo e protege reserva antes de mudar pagamento; registro de resultado exige proteção e identidade válidas. Progresso da Saga e eventos ficam na mesma transação. Registro de resultado imutável por paymentId permite recuperar decisão depois de reinício; outra decisão para a mesma identidade é conflito.

O simulador usa duas transações locais (início e resultado): uma queda entre ambas deixa EM_PROCESSAMENTO protegido e retomável. Estado final de venda depende de confirmação real de reserva/estoque na 039; a função de guarda/modelo não fabrica esse efeito. Nenhuma rota pública de transição de estados neste recorte.

## Contratos e invariantes

Nenhum chamador HTTP pode declarar seu próprio pagamento aprovado ou transição final. Identidades e vínculos são persistidos, com chaves únicas, versionamento e histórico imutável na transação. Coordenador interno rejeita conflito de identidade/conteúdo e eventos fora de ordem; publicação de negócio confiável permanece na 038.

Nenhuma liberação, baixa, conclusão, cancelamento ou compensação automática pelo simples decurso de tempo. Métodos são rótulos fictícios, sem integração financeira/fiscal.

## Plano de teste e migrações

Testar matriz completa de transições legais/ilegais e replay; persistência PostgreSQL real, barreira em disputa com expiração, idempotência concorrente, rollback de auditoria, recuperação após reinício e identidade divergente. Se houver migration, upgrade com dados, rebuild em schema próprio e replay; nenhuma edição de migration aplicada. API Verify e worker Verify quando seus contratos forem afetados, planning/diff/links/segredos e CI no SHA próprio.

## Evidências executadas

Verify SUCCESS10m38s:21 unitários+248 integrações=269, zero falhas/erros/skips/lint. Onze PG novos cobrem matriz de 980 combinações (7 estados×7 destinos×5 pagamentos×4 reservas), três desfechos/replay, conclusão sem confirmação rejeitada, estados/identidade/valores falsificados, decisão conflitante/fora de ordem, expiração anterior ao início, reinício após TTL com proteção, rollback nas duas etapas/auditorias e dois coordenadores concorrentes. Upgrade V14 com venda/pagamento preservados e Saga inicializada, rebuild/replay PASS. Precheck SQL V1–V15 em transação própria com ROLLBACK PASS; não substitui execução Flyway. Banco Verify removido.

Migrate/Validate V15 SUCCESS. Carga de regressão80518requests/0erros/p95 15,097159ms no laboratório aprovado; sem inferir produção. Fontes/testes congelados durante Verify. Guards, FKs, efeitos e limites revisados: não há baixa/comprovante/liberação/cancelamento ou transporte confiável. JAR/links/segredos/planejamento e CI do SHA próprio serão registrados na tarefa viva após publicação.

## Revisão e aceite

Commit próprio em main, testes e CI separados, sem PR. Autorrevisão antes de Em validação; aceite explícito do proprietário para concluir. Registrar a resolução de DEC-01 no documento decisório e somente no card em execução; preservar outras tarefas/páginas.
