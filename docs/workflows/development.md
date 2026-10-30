# Desenvolvimento incremental

Primeiro ciclo autorizado: planejamento e bootstrap documental. O código só começa após DoR e resolução dos bloqueios da tarefa viva. Não implementar sessenta tarefas de uma vez.

Leia AGENTS.md, a ADR-001, contexto, relatório e tarefa Notion. Crie spec antes de código. Verifique git status e preserve mudanças locais. O proprietário trabalha sozinho: usar main e commits diretos, sem PR obrigatório. Com working tree seguro, usar git switch main e git pull --ff-only origin main; não forçar uma atualização sobre divergências do usuário. Branches auxiliares são opcionais; develop é preservada como histórico.

A PDV-005 fornece a API em Java 21/Spring Boot 4.1.2 com Maven Wrapper 3.3.4/Maven 3.10.0. Usar scripts/api.ps1 -Action Verify na raiz; ele executa clean verify em toolchain Java 21 isolada, sem mudar o Java global. Instruções em backend/api/README.md. Verificar fontes oficiais e repetir testes ao atualizar versões. Worker/frontend ainda não têm comando de build.

Persistência: após PDV-004, iniciar PostgreSQL/RabbitMQ conforme serviços reais do Compose e validar health. Flyway via comando Maven verificado. Aplicar migrations no banco local descartável, ensaiar restore/rebuild seguro e replay; registrar ambiente e resultados. Não há undo automático presumido. Concorrência, atomicidade, inbox/outbox e compensações exigem testes reais.

Faça autorevisão do diff, segredos, contratos e documentação. Use commits convencionais com ID diretamente em main. Push normal apenas para remoto configurado/autorizado quando incluído no escopo. Não criar PR nem exigir aprovação externa por padrão; registrar hash/link do commit, spec, testes, evidências e limitações na própria tarefa. Não fazer deploy nem apagar branches sem autorização específica. Commit/push não equivalem a tarefa concluída. Aplicar o workflow Notion após releitura.
