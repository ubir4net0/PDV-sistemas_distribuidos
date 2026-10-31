# ADR-007 — Proposta para destravar a base de domínio

Estado: **proposta, aguardando decisão explícita do proprietário**. Tarefa: [PDV-009](../../sdd/features/PDV-009-flyway-base.md). Não autoriza migrations de domínio enquanto não aprovada.

## Contexto e opções concretas

A base Flyway/JDBC não exige escolher estados ou cardinalidades. O avanço das tabelas de domínio exige resolver as pendências registradas na tarefa viva. A [especificação v1.0](../sources/PDV_ESPECIFICACAO_E_BACKLOG.md), seções 7, 9 e 10, e a [reconciliação](../reports/reconciliacao.md) são as evidências; a proposta abaixo conserva o modelo mais simples declarado, sem alegar comparação com originais ausentes.

| Pendência | Proposta para decisão | Alternativa e consequência |
| --- | --- | --- |
| SRC-01 | Usar a baseline textual v1.0 e os novos diagramas como referência de desenvolvimento, preservando a indicação de que os originais acadêmicos não foram recebidos | Aguardar originais/validação acadêmica antes do domínio; a base técnica continua utilizável |
| DEC-02 | Adotar os estados de OC da seção 9: CRIADA, ENVIADA, RECEBIDA e CANCELADA | Manter EMITIDA, EM_TRANSITO e ENTREGUE do outro modelo exige reconciliar contratos/transições antes do DDL |
| DEC-03 | Manter produto com no máximo um fornecedor (N:1), conforme produto.fornecedor_id do modelo original descrito no pacote | N:N exige relacionamento produto_fornecedores e regras explícitas para seleção do fornecedor na compra |
| DEC-04 | Admitir os complementos técnicos necessários, criados incrementalmente nas tarefas próprias; usar nomes SQL em inglês, preservando os conceitos/classes originais | Restringir o DER às entidades antigas exige novo desenho para deduplicação, entrega confiável e rastreabilidade; não omitir essas garantias silenciosamente |

## Limites da proposta

A inclusão de complementos técnicos não aprova todos os seus campos ou políticas. users/roles/permissions e associações pertencem à PDV-011; idempotência, à PDV-035; outbox/inbox, à PDV-038; comprovantes, à PDV-039; projeções somente quando justificadas pelas tarefas de leitura. Cada entrega exige spec, restrições e testes reais. Não criar tabelas vazias em massa para simular implementação.

Os estados de OC acima definem vocabulário; regras de transição, obrigatoriedade do fornecedor, recebimento parcial e cancelamento precisam de aceite nas tarefas de compra. Não inferir nullable, preços, descontos, TTLs ou regras financeiras dessa decisão. DEC-01 (venda/pagamento), DEC-05 (lote/recebimento parcial) e DEC-06 continuam abertas. Aprovação desta proposta não aprova equivalência acadêmica dos diagramas, requisitos completos, RBAC final ou cenários de desempenho.

## Aplicação da decisão

O proprietário pode aprovar a proposta ou indicar mudanças em qualquer linha. Só depois registrar a decisão e sua origem, ajustar a spec corrente e implementar o escopo autorizado. Não alterar fontes originais, aceites/IDs ou status de outras tarefas automaticamente. Enquanto proposta, nenhuma dessas escolhas entra no SQL da aplicação: V1 cria somente o namespace técnico.
