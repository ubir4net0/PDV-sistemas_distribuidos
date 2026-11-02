# PDV-002 bootstrap documental do repositório vazio

## Revalidação técnica — 02/11/2026 (Manaus)

Execução solicitada no lote PDV-002 → PDV-007 → PDV-015. A tarefa viva foi relida integralmente; permanecia Backlog apesar do bootstrap e refinamento já entregues. Não há novo bootstrap ou alteração de proteção neste incremento: conferir evidência atual, documentar o fluxo vigente e disponibilizar o aceite técnico da tarefa. Responsável/prazo não foram inferidos.

DoR: PDV-001 e página de decisões relidas ao vivo. ADR-007 já aprovou baseline textual/novos diagramas como referência de desenvolvimento; os originais acadêmicos continuam ausentes, sem equivalência presumida ou aceite da predecessora. ADR-001 contém a instrução explícita de commits diretos em main e substitui Gitflow/PR. As indicações históricas abaixo não são gates atuais.

Pre-flight: árvore limpa, main sincronizada com origin/main em 1f21482968b7d10c01823019be416b73163a87f5, PostgreSQL/RabbitMQ healthy; Migrate V18 sem pendências (10,827s). main/develop e feature/PDV-002-bootstrap-planejamento existem local/remoto; nenhuma branch excluída ou reescrita.

API GitHub consultada nesta sessão: required_pull_request_reviews=null, required_status_checks=null, enforce_admins.enabled=true, allow_force_pushes.enabled=false e allow_deletions.enabled=false. Confirma o fluxo individual autorizado, sem mutação das configurações. [Guia operacional vigente](../../docs/workflows/bootstrap-verification.md).

Verificações documentais deste incremento: planejamento/60 IDs/aceites/dependências/39 requisitos e três hashes, whitespace, links locais/UTF8 e inspeção de caminhos/credenciais privadas do diff. Não há mudança de aplicação ou schema que justifique repetir testes de produto locais. CI do SHA desta revalidação e commit serão registrados na própria tarefa viva. Os resultados históricos abaixo permanecem identificados por data/hash.

Estado de entrega: Em validação após verificações/autorrevisão/CI, aguardando aceite explícito. Nenhuma outra tarefa será aceita ou normalizada por esta revalidação. Conteúdo abaixo preserva o registro de 30/10/2026.

## Refinamento autorizado para trabalho individual

Em 30/10/2026 o proprietário informou que trabalhará sozinho e pediu remover a exigência de PR, usando commits diretos. Este refinamento substitui o fluxo Gitflow/PR anterior; o restante desta spec preserva a baseline histórica.

Escopo: registrar ADR do fluxo individual, atualizar AGENTS.md, README, workflows e template SDD, executar CI em pushes de main, remover a exigência de PR/revisão no GitHub e manter bloqueados force-push/exclusão. Consolidar os commits já preparados do bootstrap na main por fast-forward, sem novo PR, rebase ou exclusão de branches; encerrar o PR anterior como substituído pelo fluxo direto. Código de produto, migrations e deploy permanecem fora do escopo.

Aceite do refinamento: main recebe commits diretos; PR e revisão de terceiros deixam de ser gates; autorevisão, testes, specs e evidências continuam; fontes originais e histórico preservados. API deve confirmar ausência de required_pull_request_reviews e manter allow_force_pushes=false/allow_deletions=false. Validar o planejamento, whitespace, push e CI da main. Este ajuste não conclui a auditoria acadêmica nem os demais aceites de PDV-002.

## Estado e fonte

Notion: https://app.notion.com/p/4092c9a9991781359c70ed9efb999125?pvs=204

Leitura viva em 30/10/2026. Critério original: Bootstrap documentado do repositório vazio; main/develop e convenções; sem segredos; proteção após setup.

Status inicial Backlog preservado. PDV-001 ainda possui lacuna de fontes; este é exclusivamente o bootstrap inicial documental permitido pela seção Entrega do primeiro ciclo e pela exceção de repositório vazio do pacote. Não libera implementação das tarefas dependentes.

## Contexto e objetivo

GitHub consultado: repositório vazio, sem referências. Clone separado em C:/laragon/www/PDV-sistemas_distribuidos, sem arquivos do usuário. Preparar histórico, fontes, workflow, specs e plano revisáveis, preservando o workspace Amor e Vida.

## Fronteiras

Inclui main/develop iniciais, uma feature de documentação com commits separados, monorepo reservado por READMEs, relatório e registry de links Notion, verificação do planejamento e PR para develop. Exclui código de produto, dependências instaladas, migrations, schema, infraestrutura iniciada, mudanças em outros projetos, merge e deploy. Proteções dependem da revisão do setup e devem ser registradas como pendentes se não configuradas.

## Contratos de API e eventos

Não aplicável ao bootstrap documental. Serão definidos em PDV-007 após decisões de domínio.

## Dependências e decisões

PDV-001 continua em Backlog com SRC-01. Java padrão 17; alvo 21 ainda precisa de toolchain. Spring Boot estável deverá ser confirmado na implementação de PDV-005. Diagrama original e aceites acadêmicos ausentes.

## Critérios de aceite deste incremento

- Fontes e 60 cards rastreáveis, sem duplicação nem atribuições inventadas.
- Histórico main/develop inicial e feature/PDV-002-bootstrap-planejamento com PR revisável.
- Instruções, template SDD, relatório de divergências e ordem topológica.
- Nenhuma alegação de implementação, teste de aplicação ou proteção não verificada.

## Plano de verificação

Conferir Notion integralmente, unicidade, títulos, aceites e dependências. Executar verificador do planejamento, git diff --check e inspeção de segredos; verificar branches/PR remotos. Sem testes de produto nesta entrega documental.

## Evidências e aceite

Registrar resultados em docs/reports/primeiro-ciclo.md no incremento da feature. Aceite integral de PDV-002 permanece pendente; revisão do bootstrap e proteções não são presumidas.
