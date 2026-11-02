# PDV-045 — Rastreamento da venda

## Estado e fonte

Tarefa viva lida em 02/11/2026: https://app.notion.com/p/4092c9a9991781e0a5b1c606d381a133. Requisito Tela 10. Aceite original: “Timeline exibe estados, hora/trace e falhas, sem expor segredos.” Responsável e prazo não definidos; preservados.

## DoR e dependências

PDV-036/039/040/044 lidas no Notion; permanecem Em validação, sem aceite inferido. Código já possui sale_events imutável, outbox e telas de acompanhamento. ADR-008/009 aprovadas bastam para leitura; cancelamento continua fora deste incremento. Preflight: main sincronizada, Compose PG/Rabbit saudável, Flyway V18 aplicado. Nenhum DDL necessário.

## Contrato e fronteiras

GET /sales/{id}/timeline, ADMIN/OPERATOR com sale.read, somente venda própria (404 para outro dono), no-store. Lista ordenada por versão com ID, versão, tipo, estado de venda/pagamento, horário e correlação estável do pagamento (UUID sem hífens). Falhas de transporte vêm da outbox vinculada à mesma venda: evento/tipo/status/tentativas/código permitido/horário, nunca payload, snapshot, motivo administrativo ou stacktrace. Leitura REPEATABLE_READ. Sem transição de estado, novo pagamento, cancelamento ou acesso administrativo a vendas alheias.

Frontend acrescenta trilha consultada explicitamente à tela da PDV-044; mostra estados observados e falhas separadas, horários locais com timezone identificado, reconsulta, erro sem dados antigos e limpeza ao trocar venda/sessão. Não inventar eventos para etapas futuras. A correlação identifica a operação de pagamento, não comprova tracing distribuído (PDV-049).

## Aceite e plano de teste

HTTP real/PG: propriedade, papel/permissão, ordenação, checkout/início/resultado/conclusão e TIMEOUT, falhas sem snapshots/dados sensíveis, no-store. Atualizar OpenAPI e inventário. Frontend typecheck/unit/build e Playwright com API/PG/Rabbit/worker próprios: trilha aprovada/recusada/desconhecida e erro/reconsulta. Autorrevisão, segredos, planejamento e diff. Commit separado em main sem PR; Em validação somente após evidências reais, aguardando aceite.

## Evidências executadas

Verify API SUCCESS15m21:21 unitários +278 integrações =299, zero falhas/erros/skips, PG real. Os2 testes HTTP novos passaram, incluindo autorização, privacidade, falha de publicação e ordenação/conclusão. A primeira tentativa parou no clean porque o log estava aberto dentro de target; destino corrigido para TEMP e suíte completa repetida com sucesso.

Frontend: typecheck,23 unitários, build e16 Playwright PASS1m24 com API/PG/Rabbit/worker reais próprios. Aprovação, recusa, TIMEOUT e erro/reconsulta da trilha; desktop/celular inspecionados, sem overflow. Contratos:60 operações/55 conjuntos de campos Java/68 schemas/15 exemplos válidos e11 inválidos PASS. Planejamento/fontes, UTF-8/342 links locais, staged diff e segredos PASS. Sem DDL; Flyway V18 validado e rebuild/replay existentes aprovados. Recursos E2E e banco Verify removidos.

Autorrevisão aprovada; commit direto main sem PR. SHA e CI do artefato publicado serão registrados na tarefa viva. Entrega técnica aguardando aceite explícito do proprietário; nenhuma predecessora concluída.
