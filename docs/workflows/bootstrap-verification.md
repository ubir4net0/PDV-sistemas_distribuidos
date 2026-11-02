# Verificar o bootstrap individual — PDV-002

Vigência: [ADR-001](../decisions/ADR-001-fluxo-individual.md). [Spec e evidências datadas](../../sdd/features/PDV-002-bootstrap-documental.md). main é a branch de trabalho; develop e a feature inicial preservam histórico. O proprietário dispensou PR e revisão de terceiros; autorevisão, verificações e aceite continuam.

Com árvore de trabalho preservada, conferir branch/status/remoto e atualizar main por fast-forward. Não usar reset, force-push ou exclusão de branches para sincronizar. Ler a tarefa viva e as decisões antes de interpretar relatórios históricos.

```powershell
git status --short
git branch -a
git pull --ff-only origin main
node scripts/verify-planning.mjs
git diff --check
```

Consultar a proteção pela API GitHub em modo de leitura: deve permitir commits diretos, sem required_pull_request_reviews/required_status_checks, com enforce_admins=true, allow_force_pushes=false e allow_deletions=false. Mudanças futuras na política precisam de escopo próprio; não alterar configurações só para produzir evidência.

Arquivos privados, .env e artefatos de build permanecem ignorados; verificar os caminhos e o diff antes de cada commit, sem imprimir credenciais. Fontes originais e seus hashes não são reescritos para acompanhar decisões posteriores. A CI de Planning integrity valida planejamento; Backend quality valida aplicações com serviços isolados e não faz deploy.

Reler somente a tarefa corrente antes de registrar testes, commit/spec imutáveis e CI do SHA publicado. Em validação significa entrega disponível para aceite; publicação, CI verde ou execução da tarefa seguinte não são aceite automático. O registro de bootstrap de 30/10 é evidência histórica; a revalidação atual tem data e comandos próprios.
