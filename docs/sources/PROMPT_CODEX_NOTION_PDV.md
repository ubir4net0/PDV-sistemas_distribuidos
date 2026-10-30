# PROMPT PARA CODEX — Planejamento e execução do PDV de Sistemas Distribuídos

Você é o agente responsável por preparar o planejamento executivo de um PDV acadêmico e por manter o trabalho rastreável no Notion e no GitHub. Trabalhe de forma autônoma dentro dos limites explicitamente autorizados, com implementação incremental e evidências reais.

## Fontes obrigatórias
- Notion (página mestre): https://app.notion.com/p/Projeto-sistemas-distribuidos-4032c9a9991780cb9c18eb22cd7ca2a2
- GitHub: https://github.com/ubir4net0/PDV-sistemas_distribuidos
- Documento local neste pacote: `PDV_ESPECIFICACAO_E_BACKLOG.md` (fonte de requisitos e **60 tarefas propostas**).
- Documento humano: `PDV_Plano_Tecnico_e_Backlog.docx`.
- Leia quaisquer `AGENTS.md`, `docs/workflows/notion-task.md`, `sdd/features/TEMPLATE.md` existentes no repositório antes de agir; se ausentes, crie equivalentes coerentes na fase de bootstrap.

## PRIMEIRA MISSÃO — NÃO COMEÇAR CODIFICANDO
1. Confirme que o Notion MCP está conectado com OAuth e que você consegue abrir e LER A PÁGINA COMPLETA pelo URL acima (incluindo subpáginas, bancos e anexos/diagramas). Não dependa de snapshot local. Nunca use nem revele token compartilhado em chat. Se necessário, instrua o operador a executar `codex mcp add notion --url https://mcp.notion.com/mcp` e `codex mcp login notion` fora do repositório.
2. Inspecione o estado real do GitHub e do diretório local (`git status`, remotos, branches, docs), preserve alterações existentes e confirme se o repositório ainda está vazio. Não invente estrutura preexistente.
3. Compare os requisitos V/R/E/C/A, os RNFs 01-20, os diagramas e as 60 tarefas do arquivo MD com o conteúdo vivo do Notion. Se algo estiver inconsistente, registre uma página de 'Decisões e inconsistências' e deixe cards bloqueados quando a divergência inviabilizar implementação; não altere silenciosamente requisitos.
4. Dentro da página mestre, LOCALIZE primeiro qualquer board/database de tarefas existente. Reutilize e atualize quando apropriado; NÃO crie duplicatas. Se não existir, crie database 'PDV | Roadmap e Execução' com visualização Board por Status, Tabela geral, Por Fase e P0. Propriedades: Nome; ID PDV único; Status; Fase; Módulo; Prioridade; Escopo; Dependências; Requisitos; Repositório; Branch; Spec; PRs; Responsável; Bloqueio; Critérios de aceite; Validação; Última revisão da fonte.
5. Cadastre ou reconcilie **todos os 60 cards PDV-001 a PDV-060** definidos no MD, mantendo o objetivo e critério de aceite de cada um, referências RF/RNF e dependências explícitas. Não misture IDs `AV-*` de outro projeto. Todos entram em Backlog, sem responsável, prazo, evidência ou PR inventados. Se a página já contiver tarefas equivalentes, preserve-as e associe ID sem duplicar.
6. Estruture a board de modo que P0 seja executável primeiro, P1 venha depois dos contratos, e P2 represente laboratório avançado. Não marque RNF04/12/13/15/19 como cumpridos sem implementação e prova. Confirme em relatório a contagem de cards, links criados/reutilizados, campos, duplicações evitadas, bloqueios e divergências.
7. Só depois de finalizar a organização do board, prepare o bootstrap técnico do repositório e o plano de implementação. Não implemente 60 tarefas de uma vez. Em execução continuada, pegue sempre a próxima tarefa elegível no Notion.

## DECISÕES DE PROJETO (OBRIGATÓRIAS)
- Isto é um projeto universitário de Sistemas Distribuídos, porém precisa ficar apresentável no portfólio: código limpo, UX refinada, documentação, logs, testes unitários/integrados/e2e e commits rastreáveis.
- Backend EXIGIDO: Java 21 + Spring Boot (versão estável compatível confirmada na hora do scaffold), Maven e PostgreSQL. Use Spring Security, Bean Validation, Flyway, RabbitMQ/Spring AMQP e Docker Compose.
- Frontend livre, escolha **React + TypeScript + Vite + Tailwind** para produtividade e UX moderna; não usar JavaScript solto quando TS estiver disponível.
- Arquitetura pragmática: monorepo com `backend/api`, `backend/worker`, `frontend`, `infra`, `docs`, `sdd/features`, 2 processos Java independentes. Mensageria RabbitMQ, transactional outbox/inbox, idempotency keys e Saga para finalizar/cancelar vendas. PostgreSQL compartilhado é uma simplificação acadêmica documentada, não alegar arquitetura 100% isolada.
- **PROIBIDO integrar gateway de pagamento, bancos, Pix real, cartão real, NF-e ou qualquer API externa comercial.** Pagamentos são **100% locais e fictícios**, com cenários determinísticos aprovação/recusa/timeout/estorno, comprovante interno e nenhuma cobrança real.
- Reservar produto não significa baixa definitiva; saldo físico, reservado e disponível distintos; não permitir estoque negativo nem confirmação dupla. Timeout com resultado desconhecido requer reconciliação.
- RNFs de cluster/failover/réplica/TLS são demonstrados se houver ambiente; caso contrário, documentar como pendência explícita e nunca falsificar status de teste/aceite.
- Design: sidebar organizada, dashboard real, tabelas boas, formulários acessíveis, visual moderno, tema claro/escuro se viável, ótima navegação desktop/tablet, PDV rápido e estados vazios/erro/loading. Usar seeds de demonstração identificados, nunca KPIs inventados em produção.

## WORKFLOW OBRIGATÓRIO PARA CADA PDV-XXX
1. Ler AGENTS.md raiz e do componente, contexto, task ao vivo no Notion e instruções Git. Verificar working tree e preservar mudanças do usuário.
2. Conferir Definition of Ready: fonte, objetivo, aceite, bloqueios e dependências. Nenhum trabalho em card apenas com snapshot local.
3. Gerar `sdd/features/PDV-XXX-slug.md` a partir de TEMPLATE, com contexto, fronteiras, API/eventos, fluxos, critérios, dependências e plano de teste.
4. Gitflow: `git switch develop`, atualizar do remoto com `git pull --ff-only` se configurado, criar `feature/PDV-XXX-slug`. Se repo vazio, realizar bootstrap inicial documentado uma única vez; depois não commitar direto em main/develop.
5. Preflight persistência: `docker compose up -d postgres rabbitmq` (ou nomes de serviços realmente existentes); aguardar health; aplicar `./mvnw ... flyway:migrate` ou comando Maven correto VERIFICADO no projeto; nunca copiar `npm --prefix amor-e-vida-api run migration:run` pois este repositório é Spring Boot.
6. Implementar com testes. Tarefas Flyway exigem migração em PostgreSQL real, ensaio de rollback via backup/rebuild compatível e replay; não afirmar que Flyway faz undo automaticamente. Testar invariantes transacionais em concorrência.
7. Revisar diff, conflitos, dependências e segredos. Commit convencional `feat(PDV-XXX): ...`, `fix(PDV-XXX): ...`, `test(PDV-XXX): ...`, `docs(PDV-XXX): ...`. Fazer push de feature branch para o remoto configurado e abrir PR para develop quando autenticação e permissões estiverem configuradas; não fazer merge ou deploy sem autorização específica.
8. PR: título `PDV-XXX — entrega`; corpo obrigatório: `Notion: <URL viva>`, `Spec: <caminho>`, `Testes: <comando e resultado>`, `Evidências`, `Limitações`, `Riscos`. Preservar branches locais e remotas; NUNCA `--delete-branch`, `git flow feature finish`, force-push de main/develop ou segredos no Git.
9. Atualizar somente a task atual no Notion após releitura: Branch, Spec, PRs, resultados, bloqueio e Status correspondente. Não alterar escopo/prioridade/outras tasks sem refinamento; não atribuir usuário que não confirmou identidade. Não concluir tarefa por ter aberto PR.

## FLUXO DE STATUS / DoR / DoD
Backlog -> Pronta -> Em andamento -> Em revisão -> Em validação -> Concluída. Reprovação devolve a Em andamento. Campo Bloqueio separado.
DoR: objetivo e fonte claros, critérios testáveis, dependências resolvidas, decisões bloqueantes fechadas.
DoD: código revisado, testes pertinentes executados com resultado, APIs/eventos compatíveis, migrações verificadas, documentação e spec atualizadas, integração revisada e aceite aplicado. Só mover para Concluída depois de validação real.

## ENTREGA DO PRIMEIRO CICLO
Entregue agora apenas: (a) board no Notion criada/conciliada com 60 cards e links; (b) relatório de reconciliação dos requisitos com a página ao vivo; (c) plano de ordem de implementação; (d) bootstrap do repositório SOMENTE se as condições de acesso/segurança permitirem, com commits separados. Não mergulhe diretamente na implementação do checkout antes de concluir o planejamento.
Se o Notion MCP estiver indisponível, pare escritas em Notion, apresente o problema e não alegue que os cards foram criados. É permitido produzir specs locais provisórias, mas não sinalizar tarefas como Pronta ou Concluída.
