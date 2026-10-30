# Trabalho rastreável no Notion

## Fonte e identidade

Board: https://app.notion.com/p/26fb832899514c95a4bfec0c569b07ad. Usar docs/integrations/notion-registry.json apenas para localizar IDs/URLs. Não implementar por snapshot local.

ID PDV é chave canônica; pesquisar/consultar existentes e contar antes de criar. O Notion não impõe UNIQUE sobre o campo texto; checar unicidade no processo. Preservar títulos, aceites, prioridades, relações, responsáveis e conteúdo editado por humanos. Só alterar o escopo de outras tarefas com refinamento autorizado.

## Gates

Backlog -> Pronta -> Em andamento -> Em revisão -> Em validação -> Concluída. Em revisão é a autorevisão técnica do proprietário/agente, sem gate de PR ou terceiro revisor. Reprovação em revisão/validação retorna a Em andamento. Bloqueio é campo separado. A ADR-001 substitui o workflow Gitflow/PR original do pacote para este projeto individual.

DoR: fonte e objetivo claros, aceite testável, dependências resolvidas e decisões impeditivas fechadas. Sem identidade confirmada, não preencher responsável. Sem prazo acordado, não inventar datas.

DoD: autorevisão real, testes pertinentes com resultados, integração e migrations verificadas quando aplicável, documentação/contratos atualizados e aceite aplicado pelo proprietário. Nunca concluir somente por commit/push.

## Atualização

Ler a própria tarefa imediatamente antes de atualizar. Gravar apenas Branch, Spec, Validação, Bloqueio e Status correspondentes à evidência real da tarefa em execução, sem sobrescrever modificações concorrentes. Incluir hash/link do commit em Validação. PRs fica opcional para preservar referências históricas; não criar PR para preencher o campo. Cadastrar o backlog inicial não é implementação: todos os 60 cards iniciam em Backlog.

OAuth deve ficar no armazenamento de credenciais do cliente, fora do Git e do chat. Não persistir tokens, dados reais de clientes/saúde ou dumps em páginas.
