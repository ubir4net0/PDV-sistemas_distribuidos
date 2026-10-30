# PDV de Sistemas Distribuídos — Especificação e Backlog Técnico
**Versão:** 1.0 · **Data:** 30/10/2026 · **Natureza:** projeto acadêmico para portfólio
**Repositório:** https://github.com/ubir4net0/PDV-sistemas_distribuidos
**Página mestre no Notion:** https://app.notion.com/p/Projeto-sistemas-distribuidos-4032c9a9991780cb9c18eb22cd7ca2a2
**Fonte de referência:** requisitos textuais, diagramas descritos e fluxo de desenvolvimento fornecidos pelo solicitante. O conteúdo vivo do Notion deverá ser conferido pelo Codex antes de gravar tarefas.

## 1. Propósito, objetivos e limites
Implementar um PDV (ponto de venda) acadêmico com vendas, cadastro de clientes/fornecedores, catálogo, controle de estoque, relatórios, RBAC e rastreamento distribuído. O resultado precisa ser funcional, demonstrável, bem testado e tecnicamente apresentável no GitHub, sem integrar serviços financeiros ou fiscais externos.
**Exclusões deliberadas:** gateway real, cobrança ou estorno bancário, NF-e, API comercial externa, estoque em marketplace real, dados reais de cartão, credenciais versionadas, deploy produtivo não autorizado.
**Cenários financeiros fictícios:** aprovação, recusa, demora/timeout, estorno e reprocessamento via worker local com resultados determinísticos configuráveis. Comprovante comercial interno não se confunde com nota fiscal.

## 2. Stack e arquitetura-alvo
- **Backend:** Java 21 LTS, Spring Boot (versão estável compatível validada na inicialização), Spring Web, Spring Security, Validation, JPA, Spring AMQP, Actuator, Maven Wrapper.
- **Frontend:** React + TypeScript + Vite + Tailwind CSS + componentes acessíveis (por exemplo shadcn/ui), TanStack Query, React Router, biblioteca de gráficos.
- **Persistência:** PostgreSQL + Flyway; JPA/Hibernate para CRUD; SQL transacional explícito nas operações críticas de saldo.
- **Assíncrono:** RabbitMQ com DLQ, outbox/inbox, retries idempotentes; comunicação API -> worker de processamento fictício.
- **Testes:** JUnit 5, Spring Boot Test, Testcontainers (PostgreSQL/RabbitMQ), Vitest/Testing Library, Playwright, k6 (ou equivalente).
- **Infra:** Docker Compose em desenvolvimento; health checks; logs JSON e rastreamento OpenTelemetry onde possível.
- **Estrutura:** Monorepo: backend/api, backend/worker, frontend, infra, docs, sdd/features, .github/workflows.
**Compromisso arquitetural:** dois processos Spring Boot (API e worker) são serviços independentes e demonstram mensageria distribuída, mas compartilham PostgreSQL no laboratório acadêmico. Isso não é isolamento de dados típico de microserviços de produção.
**Consistência:** reserva e baixa exigem garantia transacional local/atômica; o fechamento de venda entre serviços segue Saga e consistência eventual, com compensações e reconciliação.

## 3. Mapa das dez telas
- **Tela 01 — Login:** Email e senha, validação, feedback e sessão.
- **Tela 02 — Venda:** PDV rápido, carrinho, cliente, pesquisa, totais e checkout.
- **Tela 03 — Fornecedor:** Cadastro, fornecedores associados, ordens de compra.
- **Tela 04 — Cliente:** CRUD, CPF/CNPJ, endereços, histórico.
- **Tela 05 — Estoque:** Produtos, saldos, entradas/saídas, reservas e alertas.
- **Tela 06 — Relatório:** Filtros, KPIs, gráficos, PDF e CSV.
- **Tela 07 — Processamento de pagamento:** Estados do pagamento fictício e cenário de demonstração.
- **Tela 08 — Monitoramento:** Saúde, métricas, filas, tentativas e DLQ.
- **Tela 09 — Autenticação / acesso:** Perfis, permissões, sessões, administração.
- **Tela 10 — Rastreamento de status:** Linha do tempo dos eventos de vendas e pagamentos.
**Direção de UX:** painel visual premium, responsivo, com navegação clara, contraste e acessibilidade, estados de loading/erro/vazio, indicadores reais do banco, busca rápida e atalhos de teclado no caixa. Nada de números fictícios fora de seeds demonstrativos identificados.

## 4. Requisitos funcionais (19)
- **RF-V01 · Vendas:** Adicionar, editar e remover itens do carrinho.
- **RF-V02 · Vendas:** Reservar temporariamente estoque no checkout; liberar após timeout seguro.
- **RF-V03 · Vendas:** Fechar venda por Saga com faturamento interno e baixa definitiva.
- **RF-V04 · Vendas:** Cancelar venda com estorno fictício e reposição automática idempotente.
- **RF-V05 · Vendas:** Consultar histórico detalhado de compras com paginação eficiente.
- **RF-V06 · Vendas:** Emitir comprovante digital com identificador global único.
- **RF-E01 · Estoque:** Cadastrar, atualizar e inativar produtos e categorias.
- **RF-E02 · Estoque:** Registrar entradas e saídas em lote.
- **RF-E03 · Estoque:** Emitir alertas de estoque mínimo configurável.
- **RF-E04 · Estoque:** Sincronizar disponibilidade entre estoque e vitrine/PDV.
- **RF-C01 · Clientes:** Cadastrar clientes e garantir CPF/CNPJ único.
- **RF-C02 · Clientes:** Alterar dados cadastrais preservando histórico.
- **RF-C03 · Fornecedores:** Cadastrar fornecedores e associá-los a produtos.
- **RF-C04 · Fornecedores:** Registrar ordens de compra e atualizar entrega/status.
- **RF-R01 · Relatórios:** Exportar relatórios CSV e PDF.
- **RF-R02 · Relatórios:** Apurar vendas por região, produto e período.
- **RF-R03 · Relatórios:** Consolidar visões materializadas de estoque e vendas por agendamento.
- **RF-A01 · Administração:** Autenticar usuários e aplicar RBAC em todas as requisições.
- **RF-A02 · Administração:** Monitorar filas pendentes e sincronizações em interface administrativa.

## 5. Requisitos não funcionais (20)
- **RNF01 · Desempenho:** Catálogo/busca com p95 inferior a 200 ms sob carga média declarada.
- **RNF02 · Desempenho:** Suportar ao menos 100 consultas simultâneas de estoque.
- **RNF03 · Concorrência:** Trava otimista/versioning/ETag em escritas de estoque.
- **RNF04 · Dados analíticos:** Relatórios analíticos em leitura dedicada/réplica/CQRS; laboratório avançado.
- **RNF05 · Escalabilidade:** API stateless e múltiplas instâncias atrás de balanceador.
- **RNF06 · Mensageria:** Comunicação desacoplada API -> worker por RabbitMQ.
- **RNF07 · Tempo:** Sincronização de relógios e timestamps UTC, NTP no host/cluster.
- **RNF08 · Idempotência:** Identificador idempotente para toda mutação transacional.
- **RNF09 · Consistência:** Consistência eventual nas projeções de leitura do catálogo.
- **RNF10 · Consistência:** Consistência forte na reserva/baixa local; não prometer atomicidade global da Saga.
- **RNF11 · Resiliência:** Circuit breaker nas chamadas entre serviços quando houver comunicação síncrona.
- **RNF12 · Resiliência:** Failover automático de bancos: laboratório avançado com evidência.
- **RNF13 · Disponibilidade:** Meta de 99,5% de uptime; meta, não medição já alcançada.
- **RNF14 · Autenticação:** JWT assinado ou sessões opacas; não confundir assinatura com criptografia.
- **RNF15 · Transporte:** TLS 1.3 em tráfego externo e interno de ambiente de demonstração implantado.
- **RNF16 · Credenciais:** Senhas com Argon2id ou BCrypt e salt.
- **RNF17 · Observabilidade:** Trace ID e span ID propagados entre API, mensagens e worker.
- **RNF18 · Observabilidade:** Logs JSON estruturados e centralizados.
- **RNF19 · Entrega:** Migrações e deploys compatíveis com zero downtime, em perfil demonstrável.
- **RNF20 · Dados:** Backup automatizado diário e teste periódico de restauração.
**Gates de escopo:** P0/P1 compõem produto apresentável; P2 inclui laboratórios de infraestrutura avançada. Nenhum RNF é considerado implementado sem evidências verificáveis. Se algum RNF formal da disciplina for obrigatório na entrega, elevar a prioridade por refinamento com responsável acadêmico.

## 6. Atores e principais casos de uso
- **Vendedor/Operador:** realizar e cancelar venda; cadastrar, consultar e editar cliente; consultar produtos/estoque; consultar histórico; gerar comprovante.
- **Administrador:** gerenciar usuários/RBAC, catálogo, estoques, entradas/saídas, limites mínimos, fornecedores, ordens de compra, relatórios/exports, filas, sincronização e rastreamento.
- **Serviços técnicos:** autenticação (validar token, papéis), estoque (consultar, reservar, liberar, confirmar, devolver), pagamento simulado (processar, consultar, estornar) e RabbitMQ (publicar/consumir eventos).
- **Realizar Venda** inclui autenticar -> selecionar cliente -> carrinho -> consultar disponibilidade -> reservar -> processar pagamento fictício -> registrar -> baixar -> comprovante.
- **Cancelar Venda** inclui consultar venda -> registrar cancelamento -> estornar ficticiamente (quando aprovado) -> devolver estoque uma única vez.

## 7. Modelo de domínio e DER/MER
**Classes originais preservadas em escopo:** Usuario, Papel, Permissao, Cliente, Venda, ItemVenda, Pagamento, CategoriaProduto, Produto, EstoqueMovimentacao, EstoqueReserva, Fornecedor, OrdemCompra, ItemOrdemCompra. UUID nos agregados, Decimal/BigDecimal para valores, timestamps e exclusão lógica onde aplicável.
- **usuarios** (Usuario): id, nome, email UNIQUE, senha_hash, ativo, ultimo_acesso, created_at, updated_at.
- **papeis** (Papel): id, nome, descricao, timestamps.
- **usuarios_papeis** (UsuarioPapel): id, usuario_id FK, papel_id FK, created_at.
- **clientes** (Cliente): id, tipo_pessoa, cpf_cnpj UNIQUE, nome, telefone, email, localidade, ativo, timestamps.
- **enderecos_cliente** (EnderecoCliente): id, cliente_id FK, tipo, logradouro, numero, complemento, bairro, cidade, estado, cep, principal.
- **historico_compras** (Visão/consulta): Projeção derivada de vendas; evitar duplicação sem necessidade comprovada.
- **vendas** (Venda): id UUID, numero UNIQUE, cliente_id, usuario_id, status, totais Decimal, forma_pagamento, idempotency_key, timestamps.
- **itens_venda** (ItemVenda): id, venda_id, produto_id, quantidade, preco_unitario, desconto, subtotal.
- **pagamentos** (Pagamento): id, venda_id, metodo, status, valor, transacao_id, autorizacao, data_processamento, resposta.
- **estornos** (Estorno): id, pagamento_id, valor, motivo, status, data_estorno.
- **categorias_produtos** (CategoriaProduto): id, nome, descricao, ativo, timestamps.
- **produtos** (Produto): id, categoria_id, SKU UNIQUE, nome, descricao, preco_venda, custo, unidade, estoque_fisico, estoque_minimo, version, ativo.
- **estoque_movimentacoes** (EstoqueMovimentacao): id, produto_id, usuario_id, tipo, quantidade, estoque_anterior/posterior, referencia_tipo/id, observacao.
- **estoque_reservas** (EstoqueReserva): id, produto_id, venda_id, quantidade, expiracao, status, timestamps.
- **fornecedores** (Fornecedor): id, razao_social, nome_fantasia, CNPJ UNIQUE, contato, telefone, email, endereco, ativo.
- **ordens_compra** (OrdemCompra): id, fornecedor_id, status, datas, valor_total, observacao.
- **itens_ordem_compra** (ItemOrdemCompra): id, ordem_compra_id, produto_id, quantidade, preco_custo, subtotal.
- **logs_monitoramento** (LogMonitoramento): id, nivel, servico, mensagem, trace_id, contexto JSON, created_at.
- **rastreamento_status** (RastreamentoStatus): id, entidade_tipo, entidade_id, status_anterior/atual, usuario_id, observacao, created_at.
**Complementos necessários para robustez técnica (novas entidades a aprovar em ADR):**
- **permissoes / papeis_permissoes:** RBAC completo: recursos, ações e N:N papel-permissão.
- **outbox_eventos / inbox_eventos:** Publicação transacional e deduplicação de mensagens.
- **idempotencia_requisicoes:** Chave única escopada, fingerprint, status, resposta e TTL.
- **comprovantes:** Identificador global, número, snapshot de venda, URI/arquivo e geração.
- **controle_projecao:** Estado de sincronização/offset e reconciliação assíncrona.
**Relacionamentos-chave:** usuário N:N papel; papel N:N permissão; cliente 1:N endereço/venda; venda 1:N itens/pagamentos/reservas; produto 1:N itens/movimentos/reservas; fornecedor 1:N ordem; ordem 1:N item; pagamento 1:N estorno. Vínculo fornecedor-produto conforme decisão de cardinalidade.
**Enums de referência fornecidos:** TipoPessoa(FISICA,JURIDICA), FormaPagamento(CARTAO_CREDITO,CARTAO_DEBITO,PIX,BOLETO,DINHEIRO), MetodoPagamento(GATEWAY,PIX,BOLETO,CARTAO,DINHEIRO), TipoMovimentacao(ENTRADA,SAIDA,RESERVA,DEVOLUCAO,AJUSTE), StatusReserva(ATIVA,CONFIRMADA,LIBERADA,EXPIRADA), StatusOrdemCompra(EMITIDA,EM_TRANSITO,ENTREGUE,CANCELADA). StatusVenda e StatusPagamento exigem conciliação com diagrama de estados antes da implementação; métodos reais de pagamento serão apenas rótulos de simulação.

## 8. Fluxo sequencial de venda, eventos e falhas
- 1. Frontend envia token; API valida autenticação e autorização (RBAC) e consulta cliente.
- 2. API fornece catálogo e disponibilidade calculada com reservas existentes.
- 3. Checkout cria venda, itens e reserva sob uma transação atômica no PostgreSQL; idempotency-key é persistida.
- 4. API grava evento de solicitação de pagamento na outbox na mesma transação; publicador entrega ao RabbitMQ.
- 5. Worker processa pagamento simulado com resultado determinístico, grava sua decisão e publica evento; consumers usam inbox.
- 6. Se aprovado, orquestrador confirma reserva, baixa saldo físico uma única vez e conclui venda; emite comprovante interno.
- 7. Se recusado, registra tentativa e libera reserva; se timeout/resultado incerto, reconcilia antes de liberar ou confirmar.
- 8. No cancelamento pós-aprovação, Saga executa estorno fictício, devolução de estoque e rastreamento auditável.
- 9. Eventos atualizam projeções e painel de status; lag de leitura é esperado e deve ser explicitado.
- 10. Quedas, duplicações e reentregas têm retry, DLQ, idempotência, alertas e processamento manual seguro.

## 9. Estados do sistema
- **Venda:** RASCUNHO -> AGUARDANDO_PAGAMENTO -> PAGAMENTO_APROVADO -> CONCLUIDA; ramificações PAGAMENTO_RECUSADO, CANCELAMENTO_PENDENTE, CANCELADA; transição final somente após confirmação de estoque.
- **Pagamento:** CRIADO -> EM_PROCESSAMENTO -> APROVADO / RECUSADO / INDETERMINADO (timeout); estorno simulado como evento/status auditável, não confundir com cancelamento.
- **Produto:** CADASTRADO -> EM_REVISAO -> ATIVO <-> INATIVO; item inativo permanece no histórico.
- **Estoque:** DISPONIVEL -> RESERVADO -> BAIXADO, ou RESERVADO -> DISPONIVEL por liberação; indisponibilidade resulta de saldo, não substitui o ledger.
- **Cliente:** CADASTRADO -> ATIVO <-> BLOQUEADO / INATIVO; estado de bloqueio requer regra explícita.
- **Ordem de compra:** CRIADA -> ENVIADA -> RECEBIDA / CANCELADA; ao receber, movimentar estoque uma única vez.

## 10. Decisões e inconsistências para resolver em refinamento
- **Não existe gateway real:** Somente simulador local de pagamentos, sem Pix real, boletos cobrados, cartões, bancos ou NF-e.
- **Unificação dos estados:** Classe original (APROVADA) e diagrama (PAGAMENTO_APROVADO/CONCLUIDA) divergem; criar enum final via ADR antes da implementação.
- **Timeout não é recusa:** Se o resultado for desconhecido, reconciliar a simulação antes de liberar/baixar; nunca assumir falha de pagamento silenciosamente.
- **Estoque físico versus disponível:** Reservar não baixa físico: disponível = físico - reservas ativas, sob operação atômica no PostgreSQL.
- **Limites da Saga:** Transações ACID locais e eventos confiáveis; compensação para fluxos distribuídos, sem promessa de atomicidade global.
- **Histórico sem inconsistência:** Derivar histórico de compras de vendas e itens; só persistir projeção se houver caso demonstrado e sincronização clara.
- **Fornecedor e produto:** O modelo original usa produto.fornecedor_id (N:1); avaliar N:N se o requisito exigir múltiplos fornecedores.
- **RBAC incompleto no DER:** Incluir PERMISSOES e PAPEIS_PERMISSOES ausentes da lista das 19 entidades originais.
- **Laboratórios de HA e réplica:** Itens RNF04/12/13/15/19 exigem evidências de implantação; não marcar concluídos por simples documentação.
**Observação:** as imagens nomeadas image.png na fonte textual não estavam incorporadas a este pacote; o agente deve abrir os anexos reais do Notion e compará-los com esta descrição.

## 11. Modelo da board no Notion
**Título:** PDV | Roadmap e Execução. **Local:** dentro da página mestre do projeto, sem sobrescrever o conteúdo existente. Reutilizar a database se já existir.
**Campos:** Nome (title), ID PDV (texto único), Status (Backlog / Pronta / Em andamento / Em revisão / Em validação / Concluída), Fase (select), Módulo (select), Prioridade (P0/P1/P2), Escopo (MVP/Ampliação/Laboratório), Dependências (relação ou texto com IDs), Requisitos (texto), Repositório (URL), Branch (texto), Spec (texto/URL), PRs (URL/texto), Responsável (person somente identidade confirmada), Bloqueio (texto), Critérios de aceite (texto), Validação (texto), Última revisão da fonte (date).
**Vistas:** Board por Status; Tabela completa; Por Fase; Prioridades P0; Bloqueadas (filtro Bloqueio não vazio); Em revisão; Linha do tempo opcional só após datas acordadas.
**Status inicial:** Backlog para todas as 60 tarefas; não atribuir responsáveis, prazos, branches, PRs ou evidências inventadas.
**Escrita segura:** ler database antes de inserir, usar ID como chave de reconciliação, não duplicar itens, preservar campos editados por pessoas, criar uma tarefa por card e confirmar contagem/links.

## 12. Backlog de execução — 60 tarefas
### 00 · Governança
#### PDV-001 — Auditar Notion e definir baseline de escopo
**Prioridade:** P0 · **Dependências:** — · **Fontes:** Requisitos e diagramas
**Critério de aceite:** Ler página e anexos vivos, inventariar lacunas, registrar decisões e evitar duplicação de cards.
#### PDV-002 — Inicializar repositório, Gitflow e proteções
**Prioridade:** P0 · **Dependências:** 001 · **Fontes:** Gitflow
**Critério de aceite:** Bootstrap documentado do repositório vazio; main/develop e convenções; sem segredos; proteção após setup.
#### PDV-003 — Documentar arquitetura e ADRs
**Prioridade:** P0 · **Dependências:** 001 · **Fontes:** RNF04,05,06,08,10
**Critério de aceite:** ADRs explicam API+worker, PostgreSQL, RabbitMQ, Saga, consistência e limites acadêmicos.
#### PDV-004 — Criar Docker Compose e health checks
**Prioridade:** P0 · **Dependências:** 002,003 · **Fontes:** RNF05,07
**Critério de aceite:** PostgreSQL e RabbitMQ sobem com health checks; ambiente reproduzível e sem senhas versionadas.
#### PDV-005 — Criar scaffold da API Spring Boot
**Prioridade:** P0 · **Dependências:** 002,003 · **Fontes:** Backend
**Critério de aceite:** Java 21, Maven Wrapper, endpoints /actuator/health, camadas por domínio e build limpo.
#### PDV-006 — Criar scaffold do worker Java
**Prioridade:** P0 · **Dependências:** 004,005 · **Fontes:** RNF06
**Critério de aceite:** Processo independente consome/publica mensagens com contrato tipado e tratamento de erros.
#### PDV-007 — Definir OpenAPI e contratos de evento
**Prioridade:** P0 · **Dependências:** 003,005 · **Fontes:** API e eventos
**Critério de aceite:** DTOs, erros padronizados, versionamento de eventos e exemplos acessíveis à equipe.
#### PDV-008 — Implementar CI e qualidade
**Prioridade:** P0 · **Dependências:** 005,006 · **Fontes:** RNF19
**Critério de aceite:** Pipeline com build, unitários, lint/typecheck e teste de integração quando infraestrutura disponível.
#### PDV-009 — Configurar Flyway e migrações iniciais
**Prioridade:** P0 · **Dependências:** 004,005 · **Fontes:** Persistência
**Critério de aceite:** Migrations ordenadas, reversão testável por restauração/rebuild e testes contra PostgreSQL real.
#### PDV-010 — Criar AGENTS.md e workflow Notion da equipe
**Prioridade:** P0 · **Dependências:** 001,002 · **Fontes:** Processo
**Critério de aceite:** Instruções de leitura viva, DoR/DoD, Gitflow, evidências e limites de escrita versionadas.
### 01 · Segurança
#### PDV-011 — Modelar usuários, papéis e permissões
**Prioridade:** P0 · **Dependências:** 009 · **Fontes:** RF-A01
**Critério de aceite:** Tabelas users/roles/permissions com FKs, unicidade, seed apenas de desenvolvimento.
#### PDV-012 — Implementar login, tokens e hash de senhas
**Prioridade:** P0 · **Dependências:** 011 · **Fontes:** RNF14,16
**Critério de aceite:** Autenticação Spring Security, Argon2id/BCrypt, expiração, erros seguros e testes negativos.
#### PDV-013 — Aplicar RBAC a todos os endpoints
**Prioridade:** P0 · **Dependências:** 011,012 · **Fontes:** RF-A01
**Critério de aceite:** Matriz de permissões testada para operador e administrador; 401/403 corretos.
#### PDV-014 — Gerenciar usuários e perfis no admin
**Prioridade:** P1 · **Dependências:** 011,013 · **Fontes:** Casos de uso admin
**Critério de aceite:** Admin ativa/inativa usuário e define papéis; auditoria e validação de acesso.
#### PDV-015 — Construir login e sessão no frontend
**Prioridade:** P0 · **Dependências:** 007,012 · **Fontes:** Tela 01,09
**Critério de aceite:** Tela responsiva, estados de erro/loading, rota protegida e logout funcional.
### 02 · Cadastro
#### PDV-016 — Criar clientes com CPF/CNPJ único
**Prioridade:** P0 · **Dependências:** 009,013 · **Fontes:** RF-C01
**Critério de aceite:** Validação de documento e unicidade no banco; testes para duplicados e concorrência.
#### PDV-017 — Editar, consultar e inativar clientes
**Prioridade:** P0 · **Dependências:** 016 · **Fontes:** RF-C02
**Critério de aceite:** CRUD com filtros/paginação, histórico preservado, API testada e autorização.
#### PDV-018 — Modelar endereços e consulta regional
**Prioridade:** P1 · **Dependências:** 016 · **Fontes:** DER: enderecos_cliente
**Critério de aceite:** Cliente 1:N endereços, principal único por cliente e cidade/UF em relatórios.
#### PDV-019 — Criar telas de clientes
**Prioridade:** P0 · **Dependências:** 015,016,017 · **Fontes:** Tela 04
**Critério de aceite:** Lista, busca, formulário e validações acessíveis com feedback claro e estados vazios.
### 02 · Catálogo
#### PDV-020 — Criar categorias e produtos
**Prioridade:** P0 · **Dependências:** 009,013 · **Fontes:** RF-E01
**Critério de aceite:** SKU único, preço Decimal, categoria, cadastro/edição/inativação e testes de integridade.
#### PDV-021 — Consultar catálogo com filtros/paginação
**Prioridade:** P0 · **Dependências:** 020 · **Fontes:** RNF01
**Critério de aceite:** Busca e filtros indexados; medição p95 sob carga média documentada.
### 02 · Compras
#### PDV-022 — Cadastrar fornecedores e seus vínculos
**Prioridade:** P0 · **Dependências:** 009,013,020 · **Fontes:** RF-C03
**Critério de aceite:** CNPJ único, CRUD e associação produto/fornecedor; definir cardinalidade antes da migração.
#### PDV-023 — Implementar ordens e itens de compra
**Prioridade:** P1 · **Dependências:** 022 · **Fontes:** RF-C04
**Critério de aceite:** Criar/enviar/cancelar OCs, valor calculado, estados persistidos e testes de autorização.
#### PDV-024 — Receber compras e dar entrada em estoque
**Prioridade:** P1 · **Dependências:** 023,027 · **Fontes:** RF-C04
**Critério de aceite:** Recebimento idempotente gera movimentações; sem duplicar entrada; parcial se aprovado no refinamento.
### 02 · Cadastros
#### PDV-025 — Criar telas produtos, fornecedores e OCs
**Prioridade:** P1 · **Dependências:** 015,020,022,023 · **Fontes:** Telas 03,05
**Critério de aceite:** CRUDs com formulários, filtros, alertas, tabelas responsivas e permissões.
### 03 · Estoque
#### PDV-026 — Definir saldo físico/reservado/disponível
**Prioridade:** P0 · **Dependências:** 009,020 · **Fontes:** RF-E04,RNF10
**Critério de aceite:** Invariante disponível=físico-reservado; saldos nunca negativos; trilha auditável.
#### PDV-027 — Implementar entradas, saídas e ajustes atômicos
**Prioridade:** P0 · **Dependências:** 026 · **Fontes:** RF-E02
**Critério de aceite:** Transações com movimentos imutáveis, motivo e usuário; lote tudo-ou-nada ou erro por item definido.
#### PDV-028 — Implementar concorrência otimista e ETags
**Prioridade:** P0 · **Dependências:** 026,027 · **Fontes:** RNF03
**Critério de aceite:** Campo version/If-Match; conflito 409/412; dois escritores concorrentes não perdem atualização.
#### PDV-029 — Criar reserva com prazo de expiração
**Prioridade:** P0 · **Dependências:** 026,028 · **Fontes:** RF-V02
**Critério de aceite:** Reserva atômica e TTL; consulta evita overselling; renovação e expiração com testes.
#### PDV-030 — Liberar reservas expiradas com job idempotente
**Prioridade:** P0 · **Dependências:** 029,006 · **Fontes:** RF-V02
**Critério de aceite:** Worker libera no máximo uma vez, evita corrida com pagamento aprovado e gera status.
#### PDV-031 — Configurar alertas de estoque crítico
**Prioridade:** P1 · **Dependências:** 026,027 · **Fontes:** RF-E03
**Critério de aceite:** Limite por produto, evento/alerta sem duplicação e visualização no painel.
#### PDV-032 — Sincronizar disponibilidade via eventos
**Prioridade:** P1 · **Dependências:** 006,026,038 · **Fontes:** RF-E04,RNF09
**Critério de aceite:** Consumidores idempotentes, eventual consistency documentada e reconstrução de projeção.
#### PDV-033 — Criar telas de estoque e movimentações
**Prioridade:** P0 · **Dependências:** 025,027,029,031 · **Fontes:** Tela 05
**Critério de aceite:** Saldo discriminado, ajustes autorizados, histórico, reservas e alertas em UI responsiva.
### 04 · Vendas
#### PDV-034 — Implementar carrinho e itens de venda
**Prioridade:** P0 · **Dependências:** 016,020,026 · **Fontes:** RF-V01
**Critério de aceite:** Adicionar/alterar/remover itens; cálculo monetário correto; rejeitar inativo e quantidades inválidas.
#### PDV-035 — Criar checkout idempotente
**Prioridade:** P0 · **Dependências:** 029,034 · **Fontes:** RNF08
**Critério de aceite:** Idempotency-Key persistida com fingerprint e resposta; retry não gera segunda venda/reserva.
#### PDV-036 — Modelar Saga e máquina de estados de venda
**Prioridade:** P0 · **Dependências:** 003,035 · **Fontes:** RF-V03
**Critério de aceite:** Transições válidas/ilegais testadas; pagamento, reserva e confirmação reentrantes.
#### PDV-037 — Criar processador de pagamento simulado
**Prioridade:** P0 · **Dependências:** 006,036 · **Fontes:** RF-V03, Tela 07
**Critério de aceite:** Worker com cenários determinísticos APROVADO/RECUSADO/TIMEOUT; nenhuma API financeira externa.
#### PDV-038 — Implementar Outbox/Inbox e consumo confiável
**Prioridade:** P0 · **Dependências:** 006,009,036 · **Fontes:** RNF06,08
**Critério de aceite:** Publicação transacional via outbox, deduplicação inbox, retry e DLQ documentados/testados.
#### PDV-039 — Concluir venda, baixar estoque e emitir comprovante
**Prioridade:** P0 · **Dependências:** 029,036,037,038 · **Fontes:** RF-V03,V06
**Critério de aceite:** Após aprovação confirmada, baixa única e comprovante global; nunca concluir com reserva inválida.
#### PDV-040 — Tratar recusa, timeout e reconciliação
**Prioridade:** P0 · **Dependências:** 030,036,037,038 · **Fontes:** RF-V02,V03
**Critério de aceite:** Recusa libera reserva; timeout deixa estado inconclusivo até reconciliação, sem devolução prematura.
#### PDV-041 — Cancelar venda com compensação e estorno fictício
**Prioridade:** P0 · **Dependências:** 039,040 · **Fontes:** RF-V04
**Critério de aceite:** Estorno simulado e reposição idempotentes; falhas rastreáveis e reprocessáveis.
#### PDV-042 — Consultar histórico e comprovantes paginados
**Prioridade:** P1 · **Dependências:** 039,041 · **Fontes:** RF-V05,V06
**Critério de aceite:** Paginação keyset ou indexada, filtros cliente/período e download de comprovante seguro.
#### PDV-043 — Construir PDV e checkout no frontend
**Prioridade:** P0 · **Dependências:** 015,019,021,034,035 · **Fontes:** Tela 02
**Critério de aceite:** Busca ágil, carrinho, total em destaque, seleção cliente, teclado e responsividade.
#### PDV-044 — Construir tela de pagamento simulado
**Prioridade:** P0 · **Dependências:** 037,040,043 · **Fontes:** Tela 07
**Critério de aceite:** Operador escolhe cenário em demo; loading, pendência, recusa, aprovação e reconsulta.
#### PDV-045 — Exibir rastreamento do status da venda
**Prioridade:** P1 · **Dependências:** 036,039,040,044 · **Fontes:** Tela 10
**Critério de aceite:** Timeline exibe estados, hora/trace e falhas, sem expor segredos.
### 05 · Relatórios
#### PDV-046 — Gerar métricas por período/produto/região
**Prioridade:** P1 · **Dependências:** 039,018 · **Fontes:** RF-R02
**Critério de aceite:** Agregações validadas contra vendas reais e exclusão/tratamento de canceladas.
#### PDV-047 — Exportar relatórios CSV e PDF
**Prioridade:** P1 · **Dependências:** 046 · **Fontes:** RF-R01
**Critério de aceite:** Arquivos corretos, timezone BR, filtros preservados, encoding seguro e autorização.
#### PDV-048 — Criar snapshots e atualização programada
**Prioridade:** P2 · **Dependências:** 046,009 · **Fontes:** RF-R03,RNF04
**Critério de aceite:** View materializada atualizada sem travar escrita; documentar plano real de réplica em etapa avançada.
### 05 · Operações
#### PDV-049 — Criar métricas, tracing e logs JSON centralizados
**Prioridade:** P1 · **Dependências:** 005,006,038 · **Fontes:** RNF17,18
**Critério de aceite:** Trace ID atravessa API/worker/mensagem e logs sem dados sensíveis; dashboards locais.
#### PDV-050 — Criar painel de filas, retries e sincronização
**Prioridade:** P1 · **Dependências:** 038,049 · **Fontes:** RF-A02
**Critério de aceite:** Admin vê pendências, DLQ, última sincronização e reprocessa com segurança.
### 05 · Interfaces
#### PDV-051 — Criar dashboard e relatórios responsivos
**Prioridade:** P1 · **Dependências:** 033,046,047,050 · **Fontes:** Telas 06,08
**Critério de aceite:** KPIs reais, gráficos legíveis, exportações, empty states e acesso por papel.
### 06 · Qualidade
#### PDV-052 — Testar 100 consultas simultâneas ao estoque
**Prioridade:** P1 · **Dependências:** 021,027,033 · **Fontes:** RNF02
**Critério de aceite:** Teste com 100 concorrentes, sem erros inesperados, throughput e ambiente registrados.
#### PDV-053 — Validar p95 < 200 ms em catálogo
**Prioridade:** P1 · **Dependências:** 021,049 · **Fontes:** RNF01
**Critério de aceite:** Medir p95 e carga média definida; perfis, índices e resultados publicados com evidências.
### 06 · Resiliência
#### PDV-054 — Testar circuit breaker, retries e falhas parciais
**Prioridade:** P1 · **Dependências:** 037,038,049 · **Fontes:** RNF11
**Critério de aceite:** Falhas injetadas não bloqueiam API indefinidamente; DLQ e recuperação demonstradas.
### 06 · Segurança
#### PDV-055 — Realizar testes de segurança, TLS e segredos
**Prioridade:** P1 · **Dependências:** 012,013,049 · **Fontes:** RNF14,15,16
**Critério de aceite:** Acessos indevidos, rate limits, CSRF/CORS conforme modelo, HTTPS no perfil demo e secrets fora Git.
### 06 · Dados
#### PDV-056 — Criar backup, restauração e migrações seguras
**Prioridade:** P1 · **Dependências:** 009,039 · **Fontes:** RNF19,20
**Critério de aceite:** Backup incremental ou solução compatível explicitada; restore testado, RPO/RTO documentados.
### 07 · Avançado
#### PDV-057 — Laboratório de réplica de leitura e CQRS
**Prioridade:** P2 · **Dependências:** 046,048 · **Fontes:** RNF04,09
**Critério de aceite:** Consulta usa read replica; lag simulado/medido; fallback e consistência documentados.
#### PDV-058 — Laboratório de failover e disponibilidade
**Prioridade:** P2 · **Dependências:** 049,056 · **Fontes:** RNF12,13
**Critério de aceite:** Prova de failover automático e medição HA; se só projetado, marcar como não implementado.
#### PDV-059 — Rolling deploy, recuperação e ensaio de desastre
**Prioridade:** P2 · **Dependências:** 008,055,056 · **Fontes:** RNF05,07,19,20
**Critério de aceite:** Plano de migrações compatíveis, health gates e teste de restauração/switchover documentados.
### 08 · Entrega
#### PDV-060 — Executar testes e2e e montar demonstração final
**Prioridade:** P0 · **Dependências:** 041,044,045,050,051,052,053,054,055,056 · **Fontes:** Sistema completo
**Critério de aceite:** Demo do fluxo feliz e falhas; README, diagramas, vídeo/roteiro, testes e limitações honestas.

## 13. Protocolo obrigatório para o Codex por tarefa
- 1. Ler AGENTS.md raiz/submódulo, documentação, repositório afetado, status Git e alterações existentes. Nunca sobrescrever trabalho não relacionado.
- 2. Ler tarefa atual diretamente no Notion pelo ID PDV e confirmar critérios, dependências, bloqueios, escopo e repositório. Registry/snapshot local é somente ponteiro.
- 3. Satisfazer Definition of Ready: objetivo/fonte, aceites verificáveis, dependências resolvidas e nenhuma decisão impeditiva.
- 4. Criar spec em sdd/features/PDV-XXX-slug.md a partir de sdd/features/TEMPLATE.md com fontes, fronteiras, contrato API/eventos, critérios e validação.
- 5. Atualizar develop (apenas fast-forward), criar feature/PDV-XXX-slug. Para tarefa que envolve persistência: docker compose up -d postgres rabbitmq; validar health e executar migrações Flyway da API pelo Maven Wrapper.
- 6. Implementar incremento pequeno, testes JUnit/Testcontainers/Vitest/Playwright conforme escopo. Nunca alegar testes passando sem execução.
- 7. Para alterações de schema, exercitar migrate -> rollback seguro (ou restore/rebuild de snapshot) -> migrate; validar evolução sem perda e testes e2e PostgreSQL real.
- 8. Revisar diff e segredos, registrar evidência/limitações; commitar convencionalmente com escopo PDV-XXX; fazer push da feature ao origin apenas se remoto autorizado e configurado; abrir PR para develop.
- 9. Atualizar somente a própria tarefa no Notion, lendo estado atual antes de escrever; preencher evidências reais, links e bloqueios, sem inventar responsável ou aceite.
- 10. Mover tarefa apenas nos gates previstos, preservar branch feature no remoto e local; nunca mesclar automaticamente nem realizar deploy produtivo.
**Definition of Done:** revisão concluída, testes relevantes aprovados, migrations e fluxos integrados verificados, documentação e contratos atualizados, evidências anexadas e aceite real realizado; PR aberto não significa Concluída.
**Workflow:** Backlog -> Pronta -> Em andamento -> Em revisão -> Em validação -> Concluída. Retornos permitidos: revisão/validação -> Em andamento. Bloqueios vão em campo próprio, não simulam conclusão.
**Gitflow:** main liberada; develop integração; feature/PDV-XXX-descricao a partir de develop; hotfix/PDV-XXX-descricao a partir de main; release/versao desde develop. Nunca usar git flow feature finish, force-push main/develop ou --delete-branch. Commits: feat(PDV-034): implementa carrinho, test(PDV-052): valida concorrência, docs(PDV-003): registra ADR. PR: PDV-034 — resultado; corpo com URL da tarefa Notion, Spec, Testes, Evidências e Riscos.
**Bootstrap do repositório vazio:** há exceção inicial documentada para criar histórico base, main e develop; após bootstrap, toda alteração de implementação ocorre em feature branches e via PR.

## 14. Estratégia de verificação e demonstração
- Unitários: regras de cálculo monetário/BigDecimal, CPF/CNPJ, estados, TTL, RBAC e idempotência.
- Integração: Testcontainers com PostgreSQL e RabbitMQ reais para reservas concorrentes, consumidor duplicado, outbox/inbox, Saga e reconciliação.
- E2E: login, cliente, catálogo, estoque, venda aprovada, recusada e timeout, cancelamento/estorno fictício, comprovante, OC recebida, relatórios.
- Performance: 100 consultas simultâneas e p95 do catálogo sob ambiente e carga documentados; não inferir de teste unitário.
- Resiliência: desligar worker, atrasar RabbitMQ, repetir mensagem e verificar DLQ, recuperação e ausência de baixa/estorno duplicados.
- Portfólio: README com arquitetura, GIF/screenshots autorizados, diagrama C4 simples, DER, sequência, estados, roteiro de demo e relatório de testes.
- Acadêmico: apontar precisamente o que foi implementado, simulado, parcialmente atendido e não implementado para cada RNF.

## 15. Conexão Notion e segurança
O Codex deve usar o Notion MCP remoto com OAuth: `codex mcp add notion --url https://mcp.notion.com/mcp` e `codex mcp login notion`. A instalação da conexão no ChatGPT não significa conexão no Codex local. Não imprimir, solicitar em conversa aberta, salvar, versionar ou reutilizar tokens de integração expostos.
O agente deve validar acesso à página mestre e a eventuais bases ligadas antes de criar registros. Caso falte acesso de leitura/escrita, registrar impedimento sem inventar conteúdo. O GitHub deve ser inspecionado ao vivo antes de executar o bootstrap.
