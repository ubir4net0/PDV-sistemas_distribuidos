# PDV-XXX título

## Estado e fonte

Notion: URL viva obrigatória. Data da leitura, requisitos RF/RNF e classificação confirmado/proposto/pendente. Spec não substitui estado vivo.

## Contexto e objetivo

## Definition of Ready

Fonte e objetivo claros; aceite testável; dependências resolvidas; decisões impeditivas fechadas; responsável apenas se confirmado.

## Fronteiras

Incluído e excluído; nunca integrar pagamentos/fiscal reais.

## Contratos de API e eventos

DTOs, erros, versionamento, idempotência, estados e compatibilidade; indicar não aplicável quando documental.

## Fluxos e invariantes

## Dependências e decisões

## Critérios de aceite

## Plano de teste e migrações

Comandos verificados no projeto. PostgreSQL/RabbitMQ reais para persistência e mensageria; Flyway migrate, restauração/rebuild seguro e replay. Não supor undo automático.

## Evidências executadas

Comando, resultado, ambiente e limitações desta sessão. Planejamento não equivale a teste.

## Revisão e aceite

Branch main, hash/link do commit, autorevisão e validação reais. PR e aprovação de terceiros não são exigidos no fluxo individual da ADR-001. Commit/push não concluem a tarefa por si só.
