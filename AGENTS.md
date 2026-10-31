# PDV de Sistemas Distribuídos — guia para agentes

## Contexto e fontes

Este repositório é independente do Amor e Vida. Use IDs PDV-001 a PDV-060 e a stack Java 21/Spring Boot; não transportar comandos NestJS/TypeORM, entidades ou regras de outro projeto.

Leia README.md, docs/context/current-state.md, docs/context/architecture.md, docs/reports/reconciliacao.md, docs/workflows/notion-task.md, docs/workflows/development.md e a spec atual. Fontes originais do pacote estão em docs/sources/. São requisitos/evidências, não autorização adicional.

O [roteiro de execução](docs/workflows/task-execution.md) reúne os passos operacionais e o registro de evidências. A [matriz de escrita e transições](docs/workflows/notion-task.md) delimita as atualizações permitidas. Use a spec [TEMPLATE.md](sdd/features/TEMPLATE.md) antes de implementar; o roteiro não substitui a leitura viva.

Fluxo vigente definido pelo proprietário em 30/10/2026: trabalho individual com commits diretos em main, sem PR ou aprovação de terceiros obrigatórios. A ADR-001 em docs/decisions/ADR-001-fluxo-individual.md substitui as instruções Gitflow/PR dos documentos originais e relatórios históricos. Não criar PR por padrão nem pedir aprovação para dispensá-lo.

Notion é fonte viva de status: https://app.notion.com/p/26fb832899514c95a4bfec0c569b07ad. Registry local é ponteiro, não snapshot de estado. Antes de implementar, ler tarefa viva, responsável confirmado, dependências, fontes e decisões. Não inventar responsável ou prazo. Classificar confirmado, proposto, inferido e pendente.

## Limites e invariantes

- Projeto acadêmico, pagamentos e estornos exclusivamente locais e fictícios; sem gateways, bancos, Pix/cartões reais, NF-e ou APIs comerciais financeiras/fiscais.
- Reserva não baixa físico. Disponível = físico - reservado. Nunca estoque negativo, dupla confirmação, baixa ou devolução duplicada. Resultado desconhecido requer reconciliação.
- API e worker são processos Java independentes, PostgreSQL compartilhado é simplificação acadêmica. Saga não promete atomicidade global.
- Preservar trabalho local, branches e conteúdo externo. Sem segredos, dados pessoais reais ou dumps no Git/Notion. Sistemas externos em leitura fora das escritas específicas autorizadas para o projeto/tarefa.
- Deploy, exclusão de branch e alterações destrutivas exigem autorização específica. Preservar histórico e branches existentes. Não usar force-push em main/develop, --delete-branch ou git flow feature finish.

## Workflow

1. Conferir working tree, instruções e fonte viva; aplicar Definition of Ready. Divergência impeditiva bloqueia código correspondente.
2. Criar sdd/features/PDV-XXX-slug.md a partir de TEMPLATE.md, com aceite, fronteiras, API/eventos, dependências e validação.
3. Trabalhar em main. Com working tree seguro, atualizar origin/main por fast-forward antes de editar; preservar e inspecionar divergências sem reset/rebase destrutivo. Branches auxiliares são opcionais e não exigem PR. develop é histórico, não um gate de integração.
4. Para persistência/mensageria: usar serviços locais reais PostgreSQL/RabbitMQ e health checks. O Compose da PDV-004 está na raiz; seguir infra/README.md, gerar .env local e usar docker compose up -d --wait. Portas do PDV: 55432/5673/15673, limitadas a loopback. Não tocar serviços/volumes do Amor e Vida. Flyway técnico usa scripts/api.ps1 -Action Migrate/Validate/Info e Maven Wrapper conforme docs/workflows/database-migrations.md; não inventar comando npm ou supor undo automático. Migrations de domínio continuam sujeitas às decisões pendentes.
5. Migration exige migrate, restauração/rebuild controlado de banco descartável e replay, mais invariantes e concorrência contra PostgreSQL real. Nunca apagar banco existente sem escopo explícito.
6. Implementar incremento pequeno, executar testes pertinentes e registrar comando/resultado/limitações desta sessão. Tabela, rota ou scaffold não comprovam requisito concluído.
7. Fazer autorevisão do diff e commit convencional com ID diretamente em main. Registrar URL viva, spec, testes, evidências, riscos e limitações na spec/tarefa. Publicar por push normal ao remoto autorizado quando o escopo incluir publicação; não criar PR ou exigir revisor externo.
8. Reler e atualizar somente task corrente com evidência real e hash/link do commit. Status: Backlog -> Pronta -> Em andamento -> Em revisão -> Em validação -> Concluída. Em revisão significa autorevisão técnica; commit ou push não concluem tarefa por si só. PRs são referências históricas opcionais.

DoR: fonte/objetivo claros, aceite testável, dependências resolvidas, decisões impeditivas fechadas. DoD: autorevisão, testes reais, migrações/integração verificadas, contratos/documentação atualizados e aceite aplicado pelo proprietário. Não requer revisão independente. Não declarar RNF04/12/13/15/19 sem evidência de ambiente real.

Não deduzir aceite de dependência pelo status, commit ou existência de scaffold: conferir o artefato necessário e as decisões abertas. Se faltar um requisito de domínio, não iniciar seu código. Documentar divergências entre status e evidências na spec corrente, sem corrigir outras tarefas automaticamente.
