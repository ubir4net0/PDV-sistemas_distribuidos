# PDV-003 arquitetura e ADRs

## Estado e fonte

Tarefa viva: https://app.notion.com/p/4092c9a99917814dac59ea052c2c0706. Leitura em 30/10/2026. Requisitos originais: RNF04, RNF05, RNF06, RNF08 e RNF10. Fonte primária: [especificação v1.0](../../docs/sources/PDV_ESPECIFICACAO_E_BACKLOG.md), seções 1, 2, 5, 7, 8, 10 e PDV-003 na seção 12.

## Contexto e objetivo

Transformar escolhas arquiteturais explícitas do pacote em registros consultáveis com contexto, justificativa, alternativas, consequências e validação futura. O aceite original é: “ADRs explicam API+worker, PostgreSQL, RabbitMQ, Saga, consistência e limites acadêmicos.”

## Definition of Ready e dependências

A dependência PDV-001 permanece Em validação; sua conclusão e o aceite da baseline não são presumidos. A auditoria e os novos diagramas já permitem preparar documentação das escolhas declaradas no texto. Este incremento é documental e preparatório: não libera implementação de domínio nem satisfaz o DoR integral das tarefas dependentes.

SRC-01 e DEC-01 a DEC-06 continuam pendentes. As ADRs distinguem escolha declarada no pacote, proposta de implementação e decisão ainda aberta. Responsável e prazo não são preenchidos sem identidade/acordo. A execução individual é autorizada pelo proprietário; não exige PR conforme ADR-001.

## Fronteiras

Inclui índice de decisões, cinco ADRs arquiteturais (ADR-002 a ADR-006), visão de componentes, responsabilidades, consistência, falhas e matriz de aceite. Exclui código Java/React, Compose, migrations, schema aprovado, enums finais, topologia RabbitMQ e parâmetros operacionais. Pagamentos e estornos continuam fictícios e locais.

## Contratos de API e eventos

Nenhum endpoint, DTO, enum, nome de fila/evento ou versão de payload é aprovado aqui. PDV-007 definirá contratos; PDV-009 validará schema e migrations. ADR-005 explica os requisitos de idempotência e entrega sem transformá-los em contrato implementado.

## Fluxos e invariantes

API recebe comandos; transações locais protegem reserva/baixa; outbox publica trabalho ao RabbitMQ; worker registra resultado fictício; Saga coordena confirmação, compensação e reconciliação. Reserva não baixa físico; disponível = físico - reservado; nenhuma baixa/devolução/estorno duplicado. Timeout desconhecido não autoriza liberação. A ordem exata das transições depende de DEC-01/PDV-036.

## Critérios de aceite e evidências esperadas

| Critério original | Entrega | Verificação documental |
| --- | --- | --- |
| API + worker | ADR-002 e arquitetura | Dois processos, fronteiras e banco compartilhado explícitos |
| PostgreSQL | ADR-003 | Fonte de verdade, transação local e evolução Flyway |
| RabbitMQ | ADR-005 | Confirms/ack, duplicação, inbox/outbox, retry e DLQ |
| Saga e consistência | ADR-004 | Compensação, resultado incerto, invariantes e falhas |
| Limites acadêmicos | ADR-006 | Simulação financeira, RNFs sem prova e simplificações |
| Rastreabilidade | Índice e contexto | Fontes, tarefas, pendências e links locais válidos |

## Plano de verificação

Executar `node scripts/verify-planning.mjs`, `git diff --check` e conferência dos links Markdown locais nos documentos alterados. Autorevisar cada linha da matriz acima contra pacote e diagramas; verificar que nenhuma proposta vira schema/contrato aceito. O verificador protege os 60 IDs/aceites/dependências e hashes das fontes.

Não há alteração de persistência, mensageria ou runtime; testes de aplicação e ciclo Flyway não se aplicam a este incremento. Cenários futuros nas ADRs são planos, não resultados executados. Não instalar dependências ou iniciar serviços apenas para produzir documentação.

## Evidências executadas

Em 30/10/2026, `node scripts/verify-planning.mjs` passou: 60 IDs, títulos/aceites/dependências originais, registry, grafo acíclico, 19 RF + 20 RNF e SHA256 das três fontes preservados. `git diff --check` passou. Conferência por Node dos links Markdown locais: 38 destinos válidos em dez documentos novos/alterados.

Autorevisão da matriz: os seis temas originais estão cobertos pelas ADRs 002–006 e índice; o texto distingue decisões declaradas de propostas e pendências. Conferidas as janelas de falha de publicação/consumo, escopo de deduplicação por consumidor, confirmação de estoque e timeout desconhecido. Consultadas as documentações oficiais RabbitMQ (confirms/reliability), PostgreSQL (locks) e Flyway (undo), vinculadas nos registros correspondentes.

Não foram executados testes de aplicação, PostgreSQL, RabbitMQ ou migrations, pois nenhum runtime/persistência foi alterado. Os ensaios descritos nas ADRs permanecem futuros. Hash/link final e CI serão registrados na própria tarefa Notion após publicação.

## Revisão e aceite

Branch main, commit direto com ID PDV-003. Aceite técnico documental não substitui validação da baseline em PDV-001. Registrar no Notion o incremento entregue, a dependência aberta e a ausência de implementação; não concluir a tarefa automaticamente por publicar arquivos.
