# Executar uma tarefa PDV

Roteiro da [PDV-010](../../sdd/features/PDV-010-guia-agentes-workflow.md), para o proprietário ou agente no repositório PDV. Seguir [AGENTS.md](../../AGENTS.md), [workflow Notion](notion-task.md) e [ADR-001](../decisions/ADR-001-fluxo-individual.md). Não copiar o pre-flight NestJS/TypeORM do Amor e Vida.

## 1. Identificar tarefa e preservar o checkout

Na raiz do PDV, inspecionar:

```powershell
git status --short
git branch --show-current
git remote -v
git diff
git diff --cached
```

Preservar alterações locais. Não fazer reset, stash automático ou troca de branch sobre trabalho alheio. Quando o checkout estiver seguro e main estiver selecionada, executar:

```powershell
git pull --ff-only origin main
```

Divergência exige inspeção; não contornar com force-push. Ler README, estado atual, arquitetura, reconciliação, workflow e fontes relevantes. Localizar a tarefa pelo [registry](../integrations/notion-registry.json) e abrir sua página viva. Confirmar ID, URL, aceite, dependências e decisões antes de escolher o incremento.

## 2. Aplicar DoR e escrever a spec

Ler as dependências vivas, conferir artefatos e separar confirmado, declarado, inferido, proposto e pendente. Dependência com artefato técnico entregue pode continuar sem aceite formal; registrar isso sem encerrar a predecessora. Se o incremento depende da decisão pendente, não iniciar seu código. Consultar o índice de ADRs para não repetir pedidos de escolhas já aprovadas: a ADR-007 resolveu vocabulário de OC, fornecedor N:1 e inclusão dos complementos, mantendo seus detalhes nos cards próprios.

Criar sdd/features/PDV-XXX-slug.md a partir do [template](../../sdd/features/TEMPLATE.md), com critério original, refinamento autorizado, escopo, contratos, dependências, plano de verificação e limitações. Preservar os 60 IDs e fontes; não criar card para ocultar bloqueio. Responsável e prazo somente quando confirmados.

Com DoR satisfeito para o escopo e execução iniciada, atualizar somente a tarefa corrente para Em andamento e registrar branch/spec. Não usar o texto da tarefa como autorização para deploy, exclusões ou ações financeiras.

## 3. Implementar e verificar o incremento

Aplicar [desenvolvimento incremental](development.md) e [gates da CI](ci.md). Executar somente verificações pertinentes à mudança:

| Mudança | Verificação |
| --- | --- |
| Documentação/planejamento | Integridade das fontes/IDs, links locais, leitura do diff e whitespace; não exige repetir build local sem alteração de código |
| API | scripts/api.ps1 -Action Verify: lint, compilação/JAR e testes HTTP reais |
| Worker | scripts/worker.ps1 -Action Verify: lint, unitários/JAR e integração com RabbitMQ real saudável |
| Persistência | PostgreSQL real, migrate, restore/rebuild em banco descartável e replay; base técnica via scripts/api.ps1 -Action Migrate/Validate/Info e Verify. Migrations de domínio exigem decisões, dados/invariantes/concorrência próprios conforme database-migrations.md |
| Frontend | npm --prefix frontend run typecheck / test / build / e2e; Chromium com API/PostgreSQL próprios conforme frontend/README.md |

Para documentação/planejamento:

```powershell
node scripts/verify-planning.mjs
git diff --check
```

Usar [infra/README](../../infra/README.md) para serviços PDV, com objetos de teste próprios e cleanup restrito. Não apagar volumes ou tocar serviços do Amor e Vida. Ausência de broker/banco necessário é falha de verificação, sem fallback fake ou teste ignorado. Não inventar undo automático do Flyway.

Registrar comando, ambiente, contagem/resultado, falhas corrigidas e limites. Resultado histórico deve incluir SHA/execução e ser identificado como histórico. Não afirmar que leu uma fonte ausente ou que executou teste apenas previsto.

## 4. Autorrevisar e publicar

Com o incremento pronto, registrar Em revisão e conferir escopo, contratos, fontes, documentação, decisões abertas e ausência de segredos. Revisar os caminhos antes de adicionar somente os arquivos do incremento:

```powershell
git add -- <caminhos-revisados>
git diff --cached --check
git diff --cached
git status --short
git commit -m "tipo(PDV-XXX): descricao do incremento"
git push origin main
```

Substituir os marcadores antes de executar. Não usar git add indiscriminado quando houver trabalho local preexistente. Preservar .env, credenciais e target fora do Git; nunca imprimir seus valores para demonstrar a ausência.

Conferir a CI do SHA publicado, sem confundir o último run de outra tarefa:

```powershell
git rev-parse HEAD
gh run list --commit <sha-publicado>
gh run view <id-da-execucao>
```

Planning integrity e Backend quality rodam no push de main. Se falharem, investigar e corrigir dentro do escopo, registrar a falha e publicar novo commit normal; não ocultar com force-push. Se a ferramenta/execução estiver indisponível, registrar a limitação sem declarar sucesso.

## 5. Registrar entrega e aplicar o estado correto

Reler a tarefa corrente imediatamente antes de gravar. Atualizar somente campos/conteúdo permitidos na [matriz de escrita](notion-task.md). Usar links reais do commit, spec e execução de CI. Preservar responsáveis, datas, aceites, prioridades, relações e demais tarefas.

Formato mínimo para evidência na spec e tarefa:

```text
PDV-ID e escopo efetivamente entregue:
Branch / commit / URL:
Spec e documentos/contratos:
Ambiente e comandos executados nesta sessão:
Resultados e contagens; falhas corrigidas:
CI do SHA publicado e conclusão confirmada:
Limitações, decisões abertas e itens não verificados:
Estado aplicado e evidência de aceite, quando houver:
```

Após autorevisão e verificações aprovadas, Em validação significa entrega disponível para aceite do proprietário. Concluída exige esse aceite e DoD atendida. Pedido de outra tarefa ou CI verde não aceita automaticamente a anterior. Bloqueio não é status; descrever causa/impacto na tarefa corrente. Não atualizar o board inteiro como efeito de uma entrega.

Exemplo histórico de vínculo: [commit da PDV-008](https://github.com/ubir4net0/PDV-sistemas_distribuidos/commit/1f30c69f473cda1eee713259c2adeaed7f4855b5) e [CI correspondente](https://github.com/ubir4net0/PDV-sistemas_distribuidos/actions/runs/41138204156). A spec daquela tarefa registra o build local; esse exemplo não é um teste repetido nesta sessão.
