# ADR-007 — Base de domínio aprovada

Estado: **aprovada explicitamente pelo proprietário** em 30/10/2026, horário de Manaus (31/10/2026 UTC). Tarefa: [PDV-009](../../sdd/features/PDV-009-flyway-base.md). O caminho deste arquivo preserva o nome da proposta para manter os links.

Origem: resposta “pode aprovar tudo” à pergunta que enumerava a baseline textual, os quatro estados de OC, fornecedor N:1 e complementos técnicos nas tarefas próprias. A aprovação abrange as quatro linhas abaixo e seus limites; não é aceite automático de outras tarefas.

## Contexto e decisões

A base Flyway/JDBC não exige escolher estados ou cardinalidades. O avanço das tabelas de domínio exigia resolver as pendências registradas na tarefa viva. A [especificação v1.0](../sources/PDV_ESPECIFICACAO_E_BACKLOG.md), seções 7, 9 e 10, e a [reconciliação](../reports/reconciliacao.md) são as evidências; a decisão abaixo conserva o modelo mais simples declarado, sem alegar comparação com originais ausentes.

| Referência | Decisão aprovada | Alternativa considerada e consequência |
| --- | --- | --- |
| SRC-01 | Usar a baseline textual v1.0 e os novos diagramas como referência de desenvolvimento, preservando a indicação de que os originais acadêmicos não foram recebidos | Aguardar originais/validação acadêmica antes do domínio; a base técnica continua utilizável |
| DEC-02 | Adotar os estados de OC da seção 9: CRIADA, ENVIADA, RECEBIDA e CANCELADA | Manter EMITIDA, EM_TRANSITO e ENTREGUE do outro modelo exige reconciliar contratos/transições antes do DDL |
| DEC-03 | Manter produto com no máximo um fornecedor (N:1), conforme produto.fornecedor_id do modelo original descrito no pacote | N:N exige relacionamento produto_fornecedores e regras explícitas para seleção do fornecedor na compra |
| DEC-04 | Admitir os complementos técnicos necessários, criados incrementalmente nas tarefas próprias; usar nomes SQL em inglês, preservando os conceitos/classes originais | Restringir o DER às entidades antigas exige novo desenho para deduplicação, entrega confiável e rastreabilidade; não omitir essas garantias silenciosamente |

## Limites da decisão

A inclusão de complementos técnicos não aprova todos os seus campos ou políticas. users/roles/permissions e associações pertencem à PDV-011; idempotência, à PDV-035; outbox/inbox, à PDV-038; comprovantes, à PDV-039; projeções somente quando justificadas pelas tarefas de leitura. Cada entrega exige spec, restrições e testes reais. Não criar tabelas vazias em massa para simular implementação.

Os estados de OC acima definem vocabulário; regras de transição, obrigatoriedade do fornecedor, recebimento parcial e cancelamento precisam de aceite nas tarefas de compra. Não inferir nullable, preços, descontos, TTLs ou regras financeiras dessa decisão. DEC-01 (venda/pagamento), DEC-05 (lote/recebimento parcial) e DEC-06 continuam abertas. Aprovação desta proposta não aprova equivalência acadêmica dos diagramas, requisitos completos, RBAC final ou cenários de desempenho.

## Aplicação e rastreabilidade

A aprovação remove a necessidade de nova confirmação dessas quatro escolhas nas tarefas que as utilizem. Atualizar a spec corrente e criar cada migration na tarefa responsável, com restrições e testes reais. A V1 existente continua criando somente o namespace técnico; esta aprovação não adiciona tabelas ou regras por si só.

Este registro substitui as indicações de pendência sobre referência de desenvolvimento, vocabulário de OC, cardinalidade máxima do fornecedor e inclusão dos complementos nos registros anteriores. Preservar fontes originais e evidências históricas. Não alterar aceites/IDs ou status de outras tarefas automaticamente; continuam pendentes os detalhes expressamente excluídos acima.
