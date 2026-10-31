# Desenvolvimento incremental

Primeiro ciclo autorizado: planejamento e bootstrap documental. O código só começa após DoR e resolução dos bloqueios da tarefa viva. Não implementar sessenta tarefas de uma vez.

Para o ciclo atual, seguir o [roteiro de execução](task-execution.md), que reúne leitura viva, comandos, verificações, autorevisão e registro de entrega. O primeiro ciclo acima é histórico; infraestrutura e scaffolds atuais estão descritos no estado observado.

Leia AGENTS.md, a ADR-001, contexto, relatório e tarefa Notion. Crie spec antes de código. Verifique git status e preserve mudanças locais. O proprietário trabalha sozinho: usar main e commits diretos, sem PR obrigatório. Com working tree seguro, usar git switch main e git pull --ff-only origin main; não forçar uma atualização sobre divergências do usuário. Branches auxiliares são opcionais; develop é preservada como histórico.

A PDV-005 fornece a API em Java 21/Spring Boot 4.1.2 com Maven Wrapper 3.3.4/Maven 3.10.0. Usar scripts/api.ps1 -Action Verify na raiz. A PDV-006 fornece scripts/worker.ps1 -Action Verify, que exige RabbitMQ local saudável e executa testes AMQP reais. Ambos usam toolchain Java 21 isolada, sem mudar o Java global; instruções nos READMEs de backend/api e backend/worker. Verificar fontes oficiais e repetir testes ao atualizar versões. Frontend ainda não tem comando de build.

Persistência: iniciar PostgreSQL/RabbitMQ conforme serviços reais do Compose e validar health. A base técnica Flyway da PDV-009 fornece scripts/api.ps1 -Action Migrate/Validate/Info; [comandos e limites](database-migrations.md). Verify usa banco local descartável e testa rebuild/replay. Aplicar as escolhas já aprovadas na ADR-007; resolver somente os detalhes pendentes que afetem cada tarefa. Não há undo automático presumido. Concorrência de negócio, atomicidade, inbox/outbox e compensações exigem testes reais adicionais.

A PDV-008 adiciona Checkstyle a fontes/testes e separa Surefire (unitários) de Failsafe (integração). Executar clean verify, pois test sozinho não cobre HTTP/AMQP. O workflow Backend quality executa ambos os módulos e inicia RabbitMQ próprio para o worker; falha de infraestrutura não ignora testes. Ver docs/workflows/ci.md para gates e limites. Adicionar integração PostgreSQL e frontend junto ao respectivo código.

Faça autorevisão do diff, segredos, contratos e documentação. Use commits convencionais com ID diretamente em main. Push normal apenas para remoto configurado/autorizado quando incluído no escopo. Não criar PR nem exigir aprovação externa por padrão; registrar hash/link do commit, spec, testes, evidências e limitações na própria tarefa. Não fazer deploy nem apagar branches sem autorização específica. Commit/push não equivalem a tarefa concluída. Aplicar o workflow Notion após releitura.
