# Estado observado em 30 de outubro de 2026

## Scaffold do worker na PDV-006

backend/worker fornece processo Java 21/Spring Boot 4.1.2 independente da API, sem HTTP. O diagnóstico técnico consome JSON tipado por AMQP, publica resposta com correlação, confirma publicação/roteamento e rejeita entradas inválidas para DLQ, sem logging do corpo. O namespace pdv.scaffold.probe não contém filas ou contratos de negócio. Scripts e instruções estão em backend/worker/README.md; testes usam RabbitMQ real e namespace próprio com limpeza restrita.

Não há pagamento, Saga, API produtora, inbox/outbox ou persistência implementados. Ack/confirm não elimina duplicação ou incerteza em falhas. A spec sdd/features/PDV-006-worker-scaffold.md registra evidências e limites; contratos de domínio continuam pendentes.

## Scaffold técnico da API na PDV-005

backend/api contém aplicação Spring Boot 4.1.2/Java 21, Maven Wrapper oficial 3.3.4 (bin), Maven 3.10.0 com checksum e pacotes por domínio/camada. Web MVC, Validation, Security e Actuator compõem o bootstrap. Health e probes são públicos sem detalhes; outros caminhos permanecem bloqueados. Não existe conta padrão em memória.

scripts/api.ps1 usa imagem oficial com JDK 21 fixada por digest; Java 17 global permanece intacto. Comandos e limites em backend/api/README.md. O scaffold não inclui datasource, migrations, JPA, AMQP, login/RBAC ou casos de uso. As decisões de domínio anteriores continuam abertas. Resultados dos testes desta entrega constam em sdd/features/PDV-005-api-scaffold.md.

## Infraestrutura local da PDV-004

O Compose da raiz fornece PostgreSQL 16.15 e RabbitMQ 4.3.7-management, fixados por digest, com health checks, timezone UTC e volumes/rede do projeto pdv-local. Portas loopback 55432/5673/15673, sem colisão com Amor e Vida. Credenciais são geradas em .env ignorado pelo Git. O script scripts/verify-infra.ps1 valida autenticação, SQL e publish/consume por HTTP management; -Restart exercita persistência após reinício. Evidências e limitações desta entrega estão em sdd/features/PDV-004-infra-local.md.

Na PDV-004 somente infraestrutura local foi adicionada, sem aplicação, schema de domínio ou migration. Aceites e decisões anteriores permanecem no Notion, sem aprovação implícita por iniciar serviços.

## Arquitetura detalhada na PDV-003

Cinco ADRs novas (ADR-002 a ADR-006), índice de decisões e spec registram API/worker independentes, PostgreSQL/Flyway, transações locais, Saga, RabbitMQ/outbox/inbox, idempotência e limites acadêmicos. A visão de arquitetura inclui as fronteiras de consistência; os registros descrevem alternativas, consequências e cenários futuros de falha.

Este é um avanço documental das escolhas explícitas do pacote enquanto PDV-001 aguarda validação. SRC-01 e DEC-01 a DEC-06 não foram encerradas. Estados finais, schema e contratos não foram aprovados; nenhum serviço, migration ou regra de domínio foi implementado. Consulte o índice docs/decisions/README.md e a tarefa viva para evidências e status.

## Novos diagramas derivados da baseline textual

O proprietário solicitou novos diagramas para a PDV-001. Foram gerados 15 arquivos Mermaid e 15 SVGs em docs/diagrams/, com índice, requisitos, notas de decisão e comando reproduzível. Todos renderizados por Mermaid CLI 12.0.0 e inspecionados visualmente por prévias PNG locais. XML/viewBox dos SVGs verificados; fontes originais e os 60 IDs/aceites/dependências preservados. A página Diagramas propostos do PDV v1 no Notion é a cópia para revisão.

O incremento técnico está pronto para validação do proprietário. Os diagramas são novos modelos propostos; os originais acadêmicos continuam ausentes e as decisões de domínio não foram aprovadas por sua geração. A aplicação permanece sem implementação.

## Atualização vigente do fluxo individual

O proprietário informou que trabalhará sozinho e dispensou PR e aprovação externa. A ADR-001 define commits diretos em main, com autorevisão, specs, testes e rastreabilidade. A exigência de revisão de PR foi removida no GitHub; a API confirmou required_pull_request_reviews=null, mantendo force-push e exclusão bloqueados. O bootstrap documental antes preparado na feature foi trazido para main por fast-forward; develop e a feature permanecem como histórico. A CI passa a executar no push de main. Os registros abaixo descrevem o primeiro ciclo anterior a esse refinamento.

## Evidências históricas do primeiro ciclo

- Pacote ZIP com dois Markdown e um DOCX; sem código de aplicação ou diagramas incorporados.
- Página mestre Notion inicialmente vazia, sem subpáginas/databases/anexos no fetch; busca PDV sem resultados no projeto/workspace acessível.
- GitHub inicialmente vazio (isEmpty=true, sem referências), clone novo separado do Amor e Vida.
- Board criado com 60 cards Backlog, 37 P0, 19 P1, 4 P2, 11 bloqueios explícitos, seis vistas e 18 campos. Leitura integral has_more=false; títulos, aceites, fontes e dependências correspondem ao MD.
- DOCX: mesmos 60 critérios de aceite do MD, sem imagens/objetos incorporados. Análise textual, sem inspeção visual de layout.
- Docker Engine 29.9.2 disponível; Node 22.18.0. Java padrão OpenJDK 17.0.20.1, incompatível com alvo 21; Maven global não encontrado. Toolchain futura pode usar Wrapper.
- Histórico inicial main/develop publicado usando a exceção documentada de repositório vazio. Esta feature contém apenas planejamento e estrutura documental.

## Pendente

Fontes acadêmicas/diagramas, conciliação de estados e cardinalidade, política de lote e recebimento parcial, scaffold frontend e definição de carga/uptime. Responsáveis, prazos, revisão e aceite não foram inventados.

## Implementação

Há Compose funcional, API com health e worker independente com diagnóstico AMQP. Frontend, migrations e regras de domínio não estão implementados. Testes técnicos e de infraestrutura não comprovam RF/RNF completos ou integração de negócio API→worker. A main está protegida contra force-push/exclusão e permite commits diretos no fluxo individual.

Consulte relatório de reconciliação e Notion para próximos gates. Histórico local e arquivos de planejamento não substituem status vivo.
