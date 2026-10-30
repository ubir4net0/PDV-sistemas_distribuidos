# Desenvolvimento incremental

Primeiro ciclo autorizado: planejamento e bootstrap documental. O código só começa após DoR e resolução dos bloqueios da tarefa viva. Não implementar sessenta tarefas de uma vez.

Leia AGENTS.md, contexto, relatório e tarefa Notion. Crie spec antes de código. Verifique git status e preserve mudanças locais. Com working tree seguro, use git switch develop, git pull --ff-only e git switch -c feature/PDV-XXX-slug. Não forçar uma atualização sobre divergências do usuário.

Confirme Java 21 e versão estável compatível do Spring Boot na fonte oficial ao iniciar PDV-005. Maven Wrapper será criado no scaffold. Não existe comando de build de API/worker/frontend nesta baseline.

Persistência: após PDV-004, iniciar PostgreSQL/RabbitMQ conforme serviços reais do Compose e validar health. Flyway via comando Maven verificado. Aplicar migrations no banco local descartável, ensaiar restore/rebuild seguro e replay; registrar ambiente e resultados. Não há undo automático presumido. Concorrência, atomicidade, inbox/outbox e compensações exigem testes reais.

Revise diff, segredos, contratos e documentação. Use commits convencionais com ID. Push apenas para remoto configurado/autorizado e PR para develop; não fazer merge ou deploy nem apagar branch. PR aberto não equivale a tarefa concluída. Aplicar o workflow Notion após releitura.
