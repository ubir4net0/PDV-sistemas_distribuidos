# Reconciliação das fontes do PDV

Nota de vigência: este relatório preserva a auditoria inicial. O proprietário posteriormente definiu trabalho individual com commits diretos em main, sem PR/aprovação externa, conforme a [ADR-001](../decisions/ADR-001-fluxo-individual.md). Regras operacionais anteriores de Gitflow/PR são históricas; os 60 IDs, fontes originais e aceites de produto permanecem preservados.

## Resultado da reconciliação
Em 30/10/2026, o acesso MCP autenticado permitiu ler a página mestre completa. O conteúdo anterior estava vazio. Assim, foi possível confirmar a ausência de conteúdo publicado nessa página, mas não validar os requisitos e diagramas acadêmicos originais. O pacote passa a ser uma baseline textual proposta, aguardando validação acadêmica.

## Fontes verificadas
- Página mestre viva: [Notion](https://app.notion.com/p/4032c9a9991780cb9c18eb22cd7ca2a2). Fetch sem aviso de truncamento ou blocos desconhecidos; apenas parágrafo vazio antes desta execução.
- Busca de PDV restrita à página e busca no workspace: nenhum resultado antes da criação. Nenhum board visível para reutilizar; descoberta limitada ao acesso da conexão.
- ZIP PDV_Codex.zip: três arquivos, sem código. MD com 19 RF, 20 RNF e 60 tarefas.
- DOCX: extração textual confirmou 60 títulos PDV e os mesmos 60 critérios de aceite do MD; zero imagens e objetos incorporados. Inspeção textual, sem validação visual de diagramas ausentes.
- GitHub: repositório ubir4net0/PDV-sistemas_distribuidos consultado ao vivo; isEmpty=true, sem branch padrão ou referências remotas. Clone local separado em C:/laragon/www/PDV-sistemas_distribuidos, originalmente contendo apenas .git.

## Board criado e conferido
[Notion](https://app.notion.com/p/26fb832899514c95a4bfec0c569b07ad)

60 cards novos, zero reutilizados e zero duplicados; IDs únicos PDV-001 a PDV-060. Prioridades originais: 37 P0, 19 P1, 4 P2. Todos em Backlog. Sem responsáveis, prazos, PRs ou evidências de implementação inventados. Onze cards têm bloqueio explícito; dependências restantes são gates de DoR.

Campos: Nome, ID PDV, Status, Fase, Módulo, Prioridade, Escopo, Dependências, Requisitos, Repositório, Branch, Spec, PRs, Responsável, Bloqueio, Critérios de aceite, Validação, Última revisão da fonte. ID PDV é texto; unicidade conferida na reconciliação, não uma restrição nativa do Notion. Dependências registradas como IDs textuais. Escopo derivado: P0=MVP, P1=Ampliação, P2=Laboratório; é organização proposta, não aprovação acadêmica.

Vistas verificadas: Tabela geral, Board por Status, Por Fase, P0, Bloqueadas e Em revisão. Filtros refletem os campos reais. A leitura da tabela retornou 60 registros, has_more=false, 60 IDs distintos e correspondência dos títulos, aceites, prioridades, fases, módulos, fontes e dependências com o MD, normalizando apenas escapes/links do transporte Notion.

## Decisões e inconsistências
| ID | Classificação | Achado | Impacto | Encaminhamento |
| --- | --- | --- | --- | --- |
| SRC-01 | Confirmado nesta sessão | Página mestre com apenas parágrafo vazio; sem subpáginas, databases, anexos ou diagramas no fetch integral. Buscas PDV no projeto e workspace sem resultados antes da criação. | PDV-001; contratos e estados dependentes | Solicitar/reconciliar fontes originais ou aceite explícito da baseline textual. Não afirmar comparação visual realizada. |
| DEC-01 | Declarado no pacote; pendente | Venda APROVADA versus PAGAMENTO_APROVADO/CONCLUIDA; pagamento TIMEOUT versus INDETERMINADO. | PDV-007, PDV-036 | ADR de estados e eventos antes de implementar a Saga. Timeout de resultado desconhecido exige reconciliação. |
| DEC-02 | Confirmado no texto do pacote | OC EMITIDA/EM_TRANSITO/ENTREGUE versus CRIADA/ENVIADA/RECEBIDA. | PDV-007, PDV-009, PDV-023 | Conciliar enum e transições; nenhuma escolha silenciosa. |
| DEC-03 | Pendente no pacote | Fornecedor-produto N:1 versus N:N. | PDV-007, PDV-009, PDV-022 | Validar cardinalidade antes de migrations. |
| DEC-04 | Proposto no pacote | Permissões, outbox/inbox, idempotência, comprovantes e controle de projeção complementam DER original. | PDV-003, PDV-009, PDV-011, PDV-038 | Aprovar via ADR; não confundir modelo proposto com schema implementado. Padronizar nomes PT/EN do PDV-011. |
| DEC-05 | Pendente no pacote | Lote de estoque tudo-ou-nada versus erro por item; recebimento parcial de OC condicionado a refinamento. | PDV-024, PDV-027 | Definir semântica transacional e critérios antes da implementação. |
| PLAN-01 | Confirmado por análise de dependências | PDV-033 (P0) depende de PDV-025/031 (P1). PDV-060 (P0) depende de oito tarefas P1. | PDV-033, PDV-060 | Não existe entrega completa exclusivamente P0. Preservadas prioridades; executar P1 pré-requisito após contratos ou refinar recorte/prioridades com responsável. |
| PLAN-02 | Lacuna de planejamento | Não há card exclusivo para scaffold React/TypeScript/Vite/Tailwind, design system ou testes frontend; esses pré-requisitos não aparecem no grafo. | PDV-002, PDV-015, PDV-025, PDV-043 | Propor alocação explícita no refinamento de PDV-015/002, sem criar card 61 nem alterar aceites. |
| DEC-06 | Pendente | Estado de bloqueio do cliente, revisão de produto, política de permissão, carga média, TTLs, limites de desconto e janela de medição de uptime não estão definidos. | Specs de domínio e performance | Definir na tarefa elegível. Não inventar regras ou parâmetros operacionais. |
| ENV-01 | Confirmado nesta sessão | java -version retorna OpenJDK 17.0.20.1; requisito exige Java 21. Docker Engine 29.9.2 responde; Maven global não foi encontrado. | PDV-005 e builds Java | Preparar Java 21 e Maven Wrapper no ciclo de scaffold. Nenhuma ferramenta foi instalada neste ciclo de documentação. |
| LAB-01 | Requisito, sem evidência de implementação | RNF04/12/13/15/19 exigem ambiente e testes; RNF13 é meta 99,5%, não medição. | PDV-048, PDV-055 a PDV-059 | Manter pendentes até evidência real. Perfil local HTTP não comprova TLS 1.3; documentação não comprova HA ou zero downtime. |

## Fronteira deste ciclo
Somente planejamento, reconciliação e bootstrap documental. Nenhum RF/RNF está implementado ou aceito. O bootstrap pode criar histórico inicial main/develop e uma feature de documentação, usando a exceção de repositório vazio. Código Java/React, Docker Compose de produto, Flyway, schema, pagamentos e checkout pertencem às tarefas futuras e dependem de DoR. Proteções e revisão de PR não se presumem ativas.

## Próxima ação elegível
Resolver SRC-01 em PDV-001 e validar os refinamentos bloqueantes. Não marcar Pronta ou Concluída apenas por criar o board ou abrir PR. Responsável e prazos continuam sem atribuição.
