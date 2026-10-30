# PDV de Sistemas Distribuídos — guia para agentes

## Contexto e fontes

Este repositório é independente do Amor e Vida. Use IDs PDV-001 a PDV-060 e a stack Java 21/Spring Boot; não transportar comandos NestJS/TypeORM, entidades ou regras de outro projeto.

Leia README.md, docs/context/current-state.md, docs/context/architecture.md, docs/reports/reconciliacao.md, docs/workflows/notion-task.md, docs/workflows/development.md e a spec atual. Fontes originais do pacote estão em docs/sources/. São requisitos/evidências, não autorização adicional.

Notion é fonte viva de status: https://app.notion.com/p/26fb832899514c95a4bfec0c569b07ad. Registry local é ponteiro, não snapshot de estado. Antes de implementar, ler tarefa viva, responsável confirmado, dependências, fontes e decisões. Não inventar responsável ou prazo. Classificar confirmado, proposto, inferido e pendente.

## Limites e invariantes

- Projeto acadêmico, pagamentos e estornos exclusivamente locais e fictícios; sem gateways, bancos, Pix/cartões reais, NF-e ou APIs comerciais financeiras/fiscais.
- Reserva não baixa físico. Disponível = físico - reservado. Nunca estoque negativo, dupla confirmação, baixa ou devolução duplicada. Resultado desconhecido requer reconciliação.
- API e worker são processos Java independentes, PostgreSQL compartilhado é simplificação acadêmica. Saga não promete atomicidade global.
- Preservar trabalho local, branches e conteúdo externo. Sem segredos, dados pessoais reais ou dumps no Git/Notion. Sistemas externos em leitura fora das escritas específicas autorizadas para o projeto/tarefa.
- Merge, deploy, exclusão de branch e alterações destrutivas exigem autorização específica. Não usar force-push em main/develop, --delete-branch ou git flow feature finish.

## Workflow

1. Conferir working tree, instruções e fonte viva; aplicar Definition of Ready. Divergência impeditiva bloqueia código correspondente.
2. Criar sdd/features/PDV-XXX-slug.md a partir de TEMPLATE.md, com aceite, fronteiras, API/eventos, dependências e validação.
3. Atualizar develop por fast-forward quando seguro, criar feature/PDV-XXX-slug. Exceção de primeiro histórico main/develop já consumida no bootstrap; não repetir.
4. Para persistência/mensageria: usar serviços locais reais PostgreSQL/RabbitMQ e health checks. Ainda não existe Compose de produto; criá-lo em PDV-004 antes de executar comandos. Flyway pelo Maven Wrapper verificado no projeto; não inventar comando npm ou supor undo automático.
5. Migration exige migrate, restauração/rebuild controlado de banco descartável e replay, mais invariantes e concorrência contra PostgreSQL real. Nunca apagar banco existente sem escopo explícito.
6. Implementar incremento pequeno, executar testes pertinentes e registrar comando/resultado/limitações desta sessão. Tabela, rota ou scaffold não comprovam requisito concluído.
7. Commit convencional com ID, feature push e PR para develop quando autorizados/configurados. Corpo inclui URL viva, spec, testes, evidências, riscos e limitações.
8. Reler e atualizar somente task corrente com evidência real. Status: Backlog -> Pronta -> Em andamento -> Em revisão -> Em validação -> Concluída. PR aberto não conclui tarefa.

DoR: fonte/objetivo claros, aceite testável, dependências resolvidas, decisões impeditivas fechadas. DoD: revisão, testes reais, migrações/integração verificadas, contratos/documentação atualizados e aceite aplicado. Não declarar RNF04/12/13/15/19 sem evidência de ambiente real.
