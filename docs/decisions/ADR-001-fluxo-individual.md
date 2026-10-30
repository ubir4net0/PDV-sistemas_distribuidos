# ADR-001 fluxo individual com commits diretos

Data: 30/10/2026. Estado: decidido explicitamente pelo proprietário nesta conversa. Escopo: somente PDV-sistemas_distribuidos, vinculado ao refinamento de PDV-002.

## Contexto

O pacote inicial prescrevia Gitflow, feature branches, PR e revisão de terceiros. O proprietário informou que será o único desenvolvedor e pediu: não criar PR, apenas commitar as alterações. Essa decisão substitui as exigências anteriores de Gitflow/PR nos documentos e no Notion; não altera requisitos de negócio, IDs, aceites acadêmicos ou validações técnicas.

## Decisão

- main é a branch de trabalho vigente. Usar commits convencionais com ID PDV após autorevisão e verificações pertinentes.
- PR, feature branch, develop e aprovação de terceiros não são gates obrigatórios. Branches auxiliares permanecem opcionais.
- Preservar histórico e branches existentes; publicar commits por push normal somente no remoto autorizado e dentro do escopo. Não fazer force-push nem apagar branches.
- A proteção de main permite commits diretos, sem exigência de PR/revisões ou checks que impeçam o push. Force-push e exclusão continuam bloqueados, com proteção aplicada a administradores.
- Planning integrity roda nos pushes de main e pode ser executada manualmente. CI valida integridade do planejamento; não comprova requisitos de produto.
- Specs, fonte viva do Notion, dependências, testes, migrations reais quando aplicáveis e evidências continuam obrigatórios. Em revisão representa autorevisão técnica. Conclusão depende de validação/aceite real do proprietário, não de commit/push.
- Deploy e ações destrutivas continuam exigindo autorização específica. Nenhum gateway ou pagamento real foi autorizado.

## Transição do bootstrap

Os commits documentais 2914d23 e 6417518, antes preparados na feature inicial e no PR #1, são incorporados à main por fast-forward, preservando seus hashes. A mudança de fluxo recebe um novo commit diretamente em main. O PR #1 é encerrado como substituído pelo fluxo individual, sem novo PR ou exclusão da feature. develop e a feature inicial ficam preservadas no histórico.

## Rastreabilidade e fontes

Instrução atual do usuário prevalece sobre o protocolo anterior do pacote. Os arquivos originais em docs/sources/ e seus hashes ficam intactos. Relatórios do primeiro ciclo descrevem o momento anterior a esta decisão; usar esta ADR, AGENTS.md e docs/workflows/ para o fluxo vigente. O campo PRs do Notion passa a ser histórico/opcional; hashes e links de commits são registrados em Validação.

## Verificação

Reler a proteção pela API e confirmar ausência de required_pull_request_reviews, com allow_force_pushes=false e allow_deletions=false. Executar node scripts/verify-planning.mjs, git diff --check e conferir o push/CI em main. Nenhuma alteração de persistência ou código de produto neste ajuste.
