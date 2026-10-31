# PDV-010 — Guia de agentes e workflow Notion

## Estado e fonte

Tarefa viva: https://app.notion.com/p/4092c9a999178148a078eb7d549cecce. Leitura em 30/10/2026. Requisito: Processo, seção 12 da especificação original.

Aceite preservado: “Instruções de leitura viva, DoR/DoD, Gitflow, evidências e limites de escrita versionadas.” Gitflow foi substituído por decisão explícita do proprietário na [ADR-001](../../docs/decisions/ADR-001-fluxo-individual.md): main, commits diretos, sem PR obrigatório. Não alterar a fonte original para esconder essa mudança.

## Contexto, objetivo e Definition of Ready

AGENTS.md e workflows foram criados no bootstrap e refinados nas entregas anteriores. Inspecionar e completar esses arquivos, com roteiro operacional e matriz de rastreabilidade do aceite, sem recriar documentos ou registrar o bootstrap como trabalho novo.

Dependências vivas consultadas: PDV-001 permanece Em validação, com baseline/diagramas pendentes; PDV-002 permanece Backlog, mas registra bootstrap e alteração do fluxo já publicados. Existem artefatos suficientes para documentar o processo solicitado pelo proprietário. Isso não equivale a aceitar essas tarefas ou liberar domínio. PDV-009 foi consultada e preservada: DEC-02/03/04 continuam bloqueando migrations de domínio. Responsável e prazo não confirmados; não preencher.

## Fronteiras, contratos e invariantes

Entrega exclusivamente documental no repositório PDV e atualização da própria PDV-010. Preservar fontes, 60 IDs/aceites/dependências, histórico e decisões abertas. Sem alteração em Java, frontend, banco, filas ou infraestrutura. Contratos de API/eventos e migrations: não aplicável.

## Critérios de aceite e rastreabilidade

| Critério | Destino e verificação |
| --- | --- |
| Leitura viva e fontes | AGENTS.md + docs/workflows/notion-task.md: identidade, responsável confirmado, dependências, fonte imutável versus estado vivo |
| DoR/DoD | Workflow Notion: condição para iniciar, evidência para avançar, aceite final e tratamento de bloqueio |
| Fluxo Git vigente | ADR-001 + docs/workflows/gitflow.md + roteiro de execução: main/fast-forward, preservação local, commit/push normal sem PR |
| Evidências | Roteiro e template de spec: comandos desta sessão, resultados, limites, commit e execução de CI correspondente |
| Limites de escrita | Workflow Notion: matriz de destinos/campos, tarefa corrente, proteção de dados e preservação de edições humanas |
| Uso reproduzível | README e AGENTS apontam para roteiro; links locais válidos; verificador de planejamento e whitespace sem erros |

## Plano de verificação

Revisar conteúdo contra instrução do proprietário, tarefa viva e ADR-001. Executar node scripts/verify-planning.mjs, git diff --check e conferir destinos dos links Markdown nos documentos alterados. Ler proteção de main sem mutação. Conferir diff/staging sem dados sensíveis; commit/push normal e checar CI do SHA publicado. Não repetir builds locais por mudança somente documental; CI existente continuará executando no push.

## Evidências executadas e aceite

Verificações executadas nesta sessão em 30/10/2026:

| Verificação | Resultado |
| --- | --- |
| git pull --ff-only origin main | main limpa na abertura, sincronizada sem alterações a incorporar |
| node scripts/verify-planning.mjs | PASS: 60 IDs/aceites/dependências/registry, DAG, 19 RF/20 RNF, hashes das três fontes originais |
| Links Markdown de oito documentos do incremento | 36 destinos locais existentes |
| git diff --check | PASS, sem erros de whitespace |
| Leitura da proteção GitHub de main | reviewsRequired=false; forcePushAllowed=false; deletionAllowed=false; adminsEnforced=true |
| Autorrevisão | Fluxo individual consistente com ADR-001; campos/estados reais do Notion preservados; nenhuma fonte original, regra de domínio, dependência ou aplicação alterada |

O roteiro complementa arquivos preexistentes do bootstrap; não apresenta sua criação anterior como implementação nova. Builds locais não foram repetidos por mudança exclusivamente documental. Evidências da CI do novo SHA e commit serão vinculadas à própria tarefa após publicação; resultados de builds anteriores são históricos.

Entrega técnica aguarda aceite do proprietário ao final. Nenhuma predecessora será aceita ou modificada nesta execução. PR/deploy, banco/filas e outros sistemas ficaram fora do escopo.
