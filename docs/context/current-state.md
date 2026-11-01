# Estado observado em 31 de outubro de 2026

## Movimentos de estoque — PDV-027

V11 e SQL condicional implementam entrada/saída/ajuste absoluto de inteiros, ADMIN/inventory.write, motivo/ator e trilha imutável. Lote tudo-ou-nada e identidade requestId/ator evitam efeitos parciais ou repetidos; versões, reservado e overflow são protegidos. [Contrato](../workflows/inventory.md). If-Match/ETags seguem na PDV-028; reservas/compras/vendas/eventos não são inferidos deste incremento.

## Saldos de estoque — PDV-026

V10 inicializa saldos zero e abertura técnica imutável de produtos existentes/novos na mesma transação. Leituras inventory.read para ADMIN/OPERATOR calculam disponível=físico-reservado; CHECKs/FKs e versão própria protegem invariantes. [Contrato](../workflows/inventory.md). Movimentações/ETags seguem nos incrementos seguintes; reserva/sincronização/Saga não são inferidas desta base.

## Fornecedores — PDV-022

V9 suppliers/supplier_changes e vínculo opcional N:1 em products. CRUD JPA com CNPJ canônico único e validação local, versões e histórico transacional; ADMIN gerencia, OPERATOR consulta. Inativação/DELETE lógico preservam cadastro e vínculos; novos vínculos exigem fornecedor ativo, locks serializam disputa. Contratos em [fornecedores](../workflows/suppliers.md). Sem compras/recebimento/estoque/frontend; evidências reais na spec/tarefa viva, sem aceite implícito.

## Consulta do catálogo — PDV-021

GET /products e /categories paginados; prefixo literal nome/SKU, filtros de categoria/estado e índices V8. Guardas catalog.read/ADMIN ou OPERATOR preservadas. Ensaio HTTP real usa carga confirmada pelo proprietário; resultados e limites na [spec](../../sdd/features/PDV-021-consulta-catalogo.md), sem inferir capacidade de produção ou aceite.

## Catálogo — PDV-020

Categorias/produtos com JPA e V7, SKU único canônico e Decimal validado sem arredondamento. Refinamento confirmado: ADMIN gerencia, OPERATOR consulta; estados CADASTRADO→EM_REVISAO→ATIVO↔INATIVO, edição conserva estado. Histórico atômico, locks categoria→produto, versões e FKs preservam vínculos. Contrato em [catálogo](../workflows/catalog.md); busca paginada/fornecedores pertencem aos incrementos seguintes. Evidências na spec e tarefa viva; não implica aceite do proprietário.

## Endereços e regiões — PDV-018

V6 customer_addresses com FK 1:N, principal único parcial e versão. API JPA permite consultar/criar/editar endereços com permissões explícitas; principal alternado, versões e histórico são transacionais. Consulta regional real usa principal e conserva grupo sem localização, sem duplicar clientes. Sem frontend, exclusão física ou relatório de vendas. Contrato em docs/workflows/customers.md.


## Ciclo de clientes — PDV-017

Consulta/edição/inativação disponíveis com filtros e paginação, JPA/@Version/lock e auditoria V5 na mesma transação. ADMIN/OPERATOR consultam/editam; só ADMIN muda status. Sem exclusão física, com ator/snapshots e rollback em falha do histórico. Regras de checkout de inativos seguem pendentes; endereços entram na PDV-018. Contrato em docs/workflows/customers.md.


## Cadastro de clientes — PDV-016

V4 cria customers e documento canônico único. POST /customers usa JPA (schema validado, sem auto-DDL), valida CPF/CNPJ numérico/alfanumérico e exige ADMIN ou OPERATOR com customer.create, conforme recorte aprovado para o lote 016–018. Duplicidade 409, entrada inválida 400; regressões e concorrência PostgreSQL em testes próprios. Não há consultas/alterações de clientes neste incremento; entram na PDV-017.


## Matriz inicial na PDV-013

Matriz aprovada pelo proprietário aplicada aos endpoints existentes: health/login públicos, perfil/logout próprios autenticados, catálogo GET /access/permissions somente ADMIN com concessão access.permissions.read. Demais métodos/caminhos negados com 401/403 conforme autenticação. Guardas de rota/método consultam autoridades atuais por token; seed development concede só o catálogo a ADMIN. Testes HTTP/PostgreSQL exercitam matriz e revogação de autoridades. Não há gestão HTTP de usuários ou permissões de negócios futuros.


## Autenticação na PDV-012

Spring Security + BCrypt implementam login, perfil próprio e logout por token Bearer opaco. V3 persiste somente digest, expiração/revogação e fingerprint de credenciais; TTL obrigatório, sem conta padrão. Provisionamento development requer opt-in e credenciais privadas. Testes HTTP/PostgreSQL verificam cenários negativos e invalidações. Contrato e limites em docs/workflows/identity-security.md. RBAC do catálogo pertence ao commit PDV-013; demais negócios seguem pendentes. Seções anteriores abaixo descrevem o estado histórico de cada incremento.


## Identidade na PDV-011

V2 adiciona users/roles/permissions e associações com FKs/unicidade no PostgreSQL. Adaptador JDBC consulta credenciais e autoridades; seed explícito development insere só ADMIN/OPERATOR, sem contas/senhas. Testes cobrem constraints, concorrência de email, rollback e rebuild/replay. Operação em docs/workflows/identity-security.md. Login e RBAC serão implementados nos commits próprios PDV-012/013; tabelas não comprovam essas funcionalidades.

## ADR-007 aprovada pelo proprietário

O proprietário respondeu “pode aprovar tudo” à pergunta da ADR-007: baseline textual e novos diagramas como referência de desenvolvimento; OC CRIADA/ENVIADA/RECEBIDA/CANCELADA; produto com no máximo um fornecedor (N:1); complementos técnicos incrementais com nomes SQL em inglês. A decisão está em docs/decisions/ADR-007-proposta-base-dominio.md, cujo caminho histórico foi preservado.

Essas escolhas não precisam de nova confirmação. Originais acadêmicos continuam ausentes; DEC-01/05/06 e campos/políticas/transições excluídos da ADR continuam pendentes. As seções de entregas anteriores abaixo preservam o contexto de cada incremento; a ADR-007 prevalece sobre as indicações históricas de pendência das escolhas aprovadas. Nenhum status de outra tarefa foi aceito automaticamente.

## Base técnica Flyway na PDV-009

API conecta PostgreSQL por JDBC e aplica V1 pelo Flyway 12.4.0. V1 cria somente o namespace pdv; histórico em pdv_migrations. Não há tabelas de domínio, JPA ou seeds. Health/readiness incluem banco sem detalhes públicos; liveness continua do processo. Scripts Migrate/Validate/Info operam o banco local, Verify usa banco UUID descartável, e CI da API sobe PostgreSQL temporário. Testes exercitam rebuild/replay, checksum, clean bloqueado, DDL transacional e migradores concorrentes.

docs/workflows/database-migrations.md e sdd/features/PDV-009-flyway-base.md registram operação, resultados e limites. A aprovação da ADR-007 remove o bloqueio das escolhas acima; tabelas de domínio serão implementadas nas tarefas próprias. A base técnica está pronta para validação, sem afirmar schema completo ou aceite de predecessoras.

## Guia de execução na PDV-010

AGENTS.md e o workflow Notion do bootstrap foram revisados e complementados com roteiro operacional, matriz de escrita, leitura de dependências, evidências e condições de transição. O roteiro em docs/workflows/task-execution.md aplica commits diretos em main conforme ADR-001 e diferencia entrega técnica de aceite. A spec sdd/features/PDV-010-guia-agentes-workflow.md registra o critério original, o refinamento de Gitflow e verificações desta entrega documental. Baseline e decisões de domínio permanecem pendentes; nenhum código ou infraestrutura foi alterado.

## CI e qualidade na PDV-008

Backend quality executa API e worker em Java 21 com Checkstyle compartilhado, compilação, Surefire para seis unitários do publisher, empacotamento e Failsafe para dez testes HTTP e onze AMQP reais. O job worker cria RabbitMQ temporário com senha aleatória e health obrigatório, removendo seu container ao final. Planning integrity continua separado. Comandos, limites e fontes estão em docs/workflows/ci.md; resultados desta sessão na spec sdd/features/PDV-008-ci-qualidade.md.

Não há frontend nem integração de persistência nas aplicações; esses gates não estão implementados. CI dos scaffolds não comprova regras de negócio, disponibilidade RNF19, migrations ou aceite das tarefas anteriores.

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
