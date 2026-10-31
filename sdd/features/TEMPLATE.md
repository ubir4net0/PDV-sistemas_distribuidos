# PDV-XXX título

## Estado e fonte

Notion: URL viva obrigatória. Data da leitura, requisitos RF/RNF e classificação confirmado/proposto/pendente. Spec não substitui estado vivo.

Transcrever aceite original e vincular refinamento autorizado, quando houver. Registrar responsável/prazo somente se confirmados. Não modificar fontes originais para reconciliar instruções posteriores.

## Contexto e objetivo

## Definition of Ready

Fonte e objetivo claros; aceite testável; dependências resolvidas; decisões impeditivas fechadas; responsável apenas se confirmado.

Indicar páginas vivas consultadas, artefatos necessários e divergências de status/evidências. Decisão pendente que afeta o incremento impede seu código; não aceitar predecessoras implicitamente.

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

Separar verificação executada nesta sessão de evidência histórica (com SHA/run). Registrar falhas corrigidas, contagens e itens não verificados; vincular CI do SHA publicado. Usar o formato mínimo do [roteiro](../../docs/workflows/task-execution.md).

## Revisão e aceite

Branch main, hash/link do commit, autorevisão e validação reais. PR e aprovação de terceiros não são exigidos no fluxo individual da ADR-001. Commit/push não concluem a tarefa por si só.

Registrar estado aplicado à tarefa corrente e evidência do aceite do proprietário, quando houver. Em validação indica entrega técnica aguardando aceite; não inferir conclusão de um pedido para executar outra tarefa.
