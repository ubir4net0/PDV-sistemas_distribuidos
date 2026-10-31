# PDV-021 — Busca, filtros e paginação do catálogo

## Estado, fonte e aceite original

Leitura viva em 31/10/2026: https://app.notion.com/p/4092c9a9991781c4a796e2fbe8e288cb. Backlog; responsável/prazo não confirmados. RNF01: p95 inferior a 200 ms sob carga média declarada. Aceite original: “Busca e filtros indexados; medição p95 sob carga média documentada.”

## Contexto, dependências e Definition of Ready

PDV-020 relida ao vivo, entregue no commit c23b634df4f4c2b6551d1f13878f60e5b2d7743e, 149 testes API locais e CI Backend quality 41169264397/Planning integrity 41169264402 SUCCESS. Continua Em validação, sem aceite inferido. Preflight desta tarefa: main sincronizada, serviços PDV saudáveis, Migrate V7 SUCCESS. O proprietário confirmou a carga abaixo nesta sessão (“Usar a carga proposta”). Não declarar RNF01 satisfeita antes da medição real.

## Contrato planejado e carga confirmada

ADMIN e OPERATOR consultam produtos com catalog.read. GET /products com q de prefixo literal sem distinguir caixa por nome/SKU, categoryId UUID e status opcional (quatro estados aprovados), page=0/size=20, máximo 100 e offset inteiro seguro; ordenação determinística por nome/ID. %, _ e ! são literais, não curingas. GET /categories paginado com filtro Boolean active. V8 fornece índices de expressão lower(name)/text_pattern_ops, SKU/varchar_pattern_ops e índices de categoria/estado/ordenação. Consultas JPA parametrizadas constroem somente filtros presentes, sem OR de parâmetros nulos. Dados inativos são informativos, sem afirmar disponibilidade para venda. Paginação não congela um snapshot entre requisições concorrentes.

Ensaio confirmado: 10.000 produtos sintéticos, 10 clientes simultâneos, 10 s de aquecimento + 60 s de medição, requisições HTTP autenticadas alternando quatro rotas de busca/filtros/páginas. Modelo closed-loop sem think-time; dez workers compartilham token válido de operador e HttpClient concorrente. Fixtures de massa não executam transições de negócio; só existem no banco descartável do Verify. Publicar amostras, throughput, erros e p50/p95/p99, versões/recursos do ambiente e distribuição das requisições. Latência de cliente inclui HTTP, autenticação/guardas, consulta, leitura e parsing da resposta; não é só tempo SQL. Meta p95 < 200 ms, asserção real no teste CatalogLoadIT; registrar resultado inclusive se falhar. A carga caracteriza este laboratório, sem promessa de produção ou equivalência a outros ambientes. Relatório técnico target/catalog-load.json não versionado; resumo sintético na spec/Notion. EXPLAIN ANALYZE/BUFFERS sem forçar planner comprova uso dos índices de prefixo nas 10.000 linhas.

## Fronteiras e invariantes

Ensaio local usa cliente HTTP e servidor no mesmo container/JVM Java 21 via loopback e PostgreSQL em container separado na rede Docker. VM Docker compartilha recursos com outros serviços locais preservados; não mede rede externa, TLS, carga distribuída, latência de produção ou capacidade máxima. O resumo registra recursos observados e as limitações, sem extrapolação.

Não alterar mutações/estados aprovados da PDV-020. Sem saldo disponível fabricado, frontend, projeção assíncrona, estoque, vendas ou desempenho de outros endpoints. V8 apenas para índices se necessários, preservando migrations aplicadas. Limites de página/entrada evitam consulta ilimitada; buscas não interpretam curingas enviados pelo usuário.

## Plano de teste e migrações

Filtros combinados, paginação estável, Unicode/curingas literais, limites, autenticação/revogação e compatibilidade. PostgreSQL real com índices/EXPLAIN, upgrade preservando dados, rebuild/replay se houver V8. Verify completo, ensaio de carga real, Migrate/Validate/Info quando aplicável, planejamento/links/diff/segredos. Autorrevisão, commit e CI próprios antes da PDV-022.

## Evidências, revisão e aceite

Leitura viva, spec, confirmação da carga e preflight executados. Verify Java 21/PostgreSQL 16.15 SUCCESS em 7m02s: **21 unitários + 140 IT (161)**, zero falhas/erros/skips e lint zero. Dez HTTP novos cobrem prefixos literais, filtros combinados, desempate/paginação, perfis/revogação e entradas inválidas; IT de upgrade V7→V8 preserva produto/Decimal e testa rebuild/replay dos índices. Todas as regressões passaram e o banco próprio foi removido. Fontes técnicas consultadas: [classes de operadores PostgreSQL 16](https://www.postgresql.org/docs/16/indexes-opclass.html) e [índices de expressão](https://www.postgresql.org/docs/16/indexes-expressional.html).

Ensaio local executado: Java 21.0.12.1/Linux amd64, 12 CPUs disponíveis à JVM, PostgreSQL 16.15 Alpine em VM Docker com 7,622 GiB compartilhados. 10.000 produtos, 10 clientes, 10s aquecimento + 60s medidos: **69.190 requisições, zero erros, 1.153,17 req/s; p50 7,407 ms, p95 17,312 ms, p99 24,782 ms**. Quatro rotas round-robin com 17.296/17.298/17.300/17.296 amostras e p95 16,492/18,634/16,867/16,976 ms respectivamente. Meta local p95<200 ms atendida neste ensaio. EXPLAIN ANALYZE/BUFFERS escolheu Bitmap Index Scan de products_name_prefix_idx e products_sku_prefix_idx sem forçar planner, 100 linhas por busca e tempos SQL de 0,098/0,070 ms; esses tempos não são a latência HTTP. Limites do laboratório acima permanecem.

Primeiro Verify parou no Checkstyle por import java.util.Collections sem uso; removido e suíte completa repetida com sucesso. Nenhum teste foi ignorado nem gate reduzido. Migrate/Validate/Info V8 SUCCESS, JAR V1–V8 sem fixtures. Planejamento/fontes, 18 links locais/UTF-8 e staging/credenciais aprovados. Autorrevisão: parâmetros nomeados/escape literal, índices/planos reais, bounds seguros, métodos protegidos e relatório sem segredos. Commit/CI próprios ligados à tarefa viva após publicação, sem PR. Em validação aguardará aceite real; nenhuma predecessora aceita automaticamente.
