# ADR-008 — Estados de venda e pagamento simulado

Data: 01/11/2026 (Manaus). **Estado: aprovada explicitamente pelo proprietário nesta conversa.** Vinculada às specs [PDV-035](../../sdd/features/PDV-035-checkout-idempotente.md), [PDV-036](https://app.notion.com/p/4092c9a99917815aa26ffe26ced87409) e [PDV-037](https://app.notion.com/p/4092c9a99917817f9f8fdeff82b5dad9). Não altera a classificação das fontes originais ou o aceite de outras tarefas.

## Problema e evidências

DEC-01 no Notion e seção 10 da baseline divergem entre venda APROVADA e PAGAMENTO_APROVADO/CONCLUIDA; pagamento TIMEOUT e INDETERMINADO. A ADR-007 não resolveu essas escolhas. Aprovação de pagamento não comprova baixa de estoque; silêncio não comprova recusa. A [ADR-004](ADR-004-saga-e-consistencia.md) registra essas invariantes e exige refinamento.

## Decisão aprovada

Adotar o vocabulário detalhado da seção 9 da baseline: venda RASCUNHO → AGUARDANDO_PAGAMENTO → PAGAMENTO_APROVADO → CONCLUIDA, com PAGAMENTO_RECUSADO, CANCELAMENTO_PENDENTE e CANCELADA. RASCUNHO é representado pelo carrinho; checkout cria a venda AGUARDANDO_PAGAMENTO. Não usar APROVADA como sinônimo de conclusão.

Pagamento CRIADO → EM_PROCESSAMENTO → APROVADO / RECUSADO / INDETERMINADO. TIMEOUT designa cenário/ocorrência de simulação e resulta em estado INDETERMINADO. Estados conhecidos não são substituídos por timeout tardio. Repetir a mesma identidade recupera a decisão persistida, sem sortear novo resultado.

| Evento ou comando | Pré-condição | Estado/efeito permitido | Entrega do efeito |
| --- | --- | --- | --- |
| Checkout | Carrinho próprio, versão/preço/ativos/saldo válidos | Venda AGUARDANDO_PAGAMENTO; pagamento/intenção CRIADO; reserva atômica | PDV-035 |
| Início de pagamento | Intenção CRIADO e reserva ACTIVE antes do prazo | Proteger reserva na mesma transação; EM_PROCESSAMENTO | PDV-036 |
| Aprovação conhecida | Operação em processamento e identidade/vínculos válidos | Pagamento APROVADO; venda PAGAMENTO_APROVADO; reserva protegida | Coordenação 036, simulador 037 |
| Recusa conhecida | Operação em processamento e identidade/vínculos válidos | Pagamento RECUSADO; venda PAGAMENTO_RECUSADO; liberação ainda pendente | Coordenação 036, simulador 037; liberação 040 |
| Timeout desconhecido | Operação em processamento | Pagamento INDETERMINADO; venda continua AGUARDANDO_PAGAMENTO; reserva protegida | Coordenação 036, simulador 037 |
| Confirmação de estoque | Pagamento comprovadamente APROVADO e reserva válida/protegida | Baixa única, reserva CONFIRMED, venda CONCLUIDA e comprovante | PDV-039 |
| Reconciliação | Pagamento INDETERMINADO | Consultar decisão durável e aplicar resultado conhecido antes de qualquer liberação/baixa | PDV-040 |
| Cancelamento/estorno/devolução | Política e etapa de compensação validadas | CANCELAMENTO_PENDENTE até completar compensações; então CANCELADA | PDV-041, refinamento próprio |

Matriz descreve fronteiras de entrega. Não autoriza fabricar baixa/comprovante, liberação ou compensação só para permitir transição final. Guarda/modelo não equivale a efeito financeiro/estoque concluído. Políticas de nova tentativa, reconciliação tardia e disputa com cancelamento serão refinadas nas tarefas próprias.

## Checkout e idempotência aprovados

ADMIN/OPERATOR operam somente seus carrinhos/vendas. Versão esperada e Idempotency-Key obrigatórias. Preço diferente do capturado retorna 409 para revisão; valores nunca vêm do cliente. Snapshot, reserva, intenção e resposta são atômicos; carrinho convertido não admite edição nem segunda venda por outra chave. Mesma chave/conteúdo retorna resposta original; divergência retorna 409; sem expiração da chave neste incremento.

## Simulador e fronteira operacional

Cenário explícito APROVADO/RECUSADO/TIMEOUT e método fictício, persistidos por operação. Worker executável localmente, sem rede financeira, sorteio, dados de cartão ou cobrança real. Timeout mantém barreira de reserva. Transporte confiável Outbox/Inbox permanece PDV-038. Recusa registrada não anuncia liberação antes da 040; aprovação registrada não anuncia conclusão antes da 039.

## Consequências e verificação proposta

Histórico e interfaces distinguem pagamento, reserva e venda. Progresso precisa sobreviver a reinícios; escritores concorrentes e comandos repetidos não duplicam efeito. Testes de matriz, PostgreSQL real, corridas com expiração, rollback/auditoria e execução do worker devem comprovar o recorte implementado em cada commit. A aprovação resolve DEC-01 no recorte descrito; implementação, testes e aceite de entrega são registrados em cada spec.
