# PDV-034 — Carrinho e itens

## Estado, fonte e aceite

Leitura viva em 01/11/2026: https://app.notion.com/p/4092c9a999178116b1b0f11f0151607e. RF-V01; responsável/prazo ausentes. Original: “Adicionar/alterar/remover itens; cálculo monetário correto; rejeitar inativo e quantidades inválidas.” Dependências PDV-016/020/026 entregues Em validação, sem aceite automático. Preflight/leitura viva próprios após PDV-030.

## Recorte confirmado

ADMIN/OPERATOR gerenciam próprios carrinhos, cliente ativo obrigatório, produtos ATIVOS/quantidades inteiras positivas. Preço do catálogo copiado ao adicionar, preservado ao alterar quantidade. Descontos não permitidos; totais calculados no servidor com BigDecimal. Carrinho não reserva/baixa físico; checkout futuro revalida preço e disponibilidade. Sem frontend/Saga/venda/pagamento nem regra financeira inventada.

## Contrato planejado

POST /carts {requestId,customerId}; GET /carts paginado, GET /carts/{id}. PUT /carts/{id}/items/{productId} {quantity,version} define quantidade absoluta: ausente adiciona com preço atual, existente preserva preço; DELETE na mesma URI requer version por query. Versão do agregado protege concorrência. Erros seguros 400/401/403/404/409/503; propriedade sempre no SQL, permissões cart.read/write e papéis explícitos. Itens únicos por produto, dinheiro decimal sem arredondamento; preço/subtotal/total não aceitos do cliente. Auditoria transacional imutável. Migration própria após V12, sem editar scripts aplicados.

## Verificação planejada e aceite

HTTP/PG reais, criar/adicionar/alterar/remover/replay de criação, cálculo e preço preservado, rejeição de cliente/produto inativos, quantidade/fração, propriedade/revogação, dois escritores e rollback em falha da trilha. Upgrade preserva estoque/reservas, rebuild/replay, Verify/lint/JAR/carga, links/planejamento/segredos; testes/commit/CI próprios. Sem código iniciado ou aceite inferido.

## DoR e desenho antes do código

Tarefa e dependências016/020/026 relidas ao vivo, artefatos JPA/validação/estados/saldos disponíveis Em validação; nenhum aceite inferido. Refinamento confirmado pelo proprietário. Main c436cce sincronizada, serviços saudáveis, Migrate V12 SUCCESS no preflight.

V13 cria carts/cart_items/cart_operations/cart_changes. CRUD JPA com @Version e lock do agregado; JDBC somente para locks de leitura de cliente/produto, idempotência/auditoria compartilhando a mesma transação JPA. Preço NUMERIC(19,2) copiado do catálogo; quantidade BIGINT positiva; subtotal/total calculados com BigDecimal sem persistir cópia nem usar double, preservando precisão inclusive no limite Long. DTOs de dinheiro seguem números decimais do catálogo. GET/list em REPEATABLE_READ para versão/itens/totais coerentes. Alterações exigem versão do agregado; dois escritores não perdem atualização.

Adicionar/alterar exige cliente e produto ativos, mas remoção de item obsoleto/inativo permanece permitida para limpar o carrinho. Quantidade absoluta em PUT; preço de item existente preservado; remover e adicionar novamente captura o preço atual. DELETE com version obrigatório/validado, idempotente sem efeito repetido: após uma aplicação cliente deve reconsultar antes de outra mutação. Histórico de antes/depois imutável inclui remoções; trilha e dados revertem juntos. Carrinho não é venda; checkout futuro precisa validar/congelar o agregado e realizar reserva/preço/disponibilidade na própria transação. Não há cálculo de desconto ou taxa inventados.

## Primeira verificação e correções

A primeira rodada Verify (7m22s) executou247 testes e encontrou duas falhas de asserção: expectativa antiga de1 migration após V11 (agora V12+V13) e parser JsonNode padrão convertendo o total decimal extremo para double no teste. A fonte já havia sido ajustada durante a compilação, porém o bytecode daquela rodada ainda era anterior. Reiniciado clean verify completo com os testes estabilizados: contagem usa MigrationBaseline.COUNT e leitura monetária habilita USE_BIG_DECIMAL_FOR_FLOATS. Nenhum script aplicado ou regra de cálculo foi alterado para ocultar a falha; resultado final será registrado após a repetição.

Referência técnica do parser consultada: [Jackson3 DeserializationFeature](https://github.com/FasterXML/jackson-databind/blob/3.x/src/main/java/tools/jackson/databind/DeserializationFeature.java). O teste extremo lê JSON com BigDecimal; a API já calcula/serializa BigDecimal e nenhum double foi introduzido na regra de negócio.

## Resultado final executado e autorevisão

Segunda rodada clean Verify SUCCESS em9m12s:21unitários+226integrações=247, zero falhas/erros/skips/lint. Os12 novos HTTP+um PG passaram: criar/replay/propriedade/revogação; adicionar/alterar/remover e preço preservado/reinclusão; quantidades/frações/campos extras; cliente/produto inativos/remoção de obsoleto; concorrência200/409; valor decimal extremo×Long.MAX_VALUE exato/limite de versão; auditoria imutável e rollback JPA/JDBC; upgrade V12→V13 preservando saldo/contrato de reserva, rebuild/replay. PostgreSQL16.15/Java21.0.12.1 reais, banco Verify removido. JAR13 migrations/zero classes ou recursos de teste.

Carga regressiva desta rodada:82.364requests/0erros/p95 14,059579ms, 10.000 produtos/10clientes/10s aquecimento+60s medição. Laboratório, sem inferir produção. As duas asserções da primeira rodada foram corrigidas e verificadas novamente, sem editar scripts aplicados ou relaxar regras. Autorevisão confirma fronteira de carrinho sem checkout/reserva/desconto/Saga; publicação/CI próprios serão vinculados no card após os checks restantes.

Migrate/Validate locais V13 SUCCESS. Smoke do JAR development sem bootstrap: readiness UP, GET anônimo /carts401, users/products/reservations/carts/items todos zero,27 grants de desenvolvimento esperados; runner exit0/container removido. Seis documentos UTF-8/79links, planejamento/fontes e diff PASS.

Autorevisão concluída; staged paths/credenciais PASS. Commit direto em main e push normal, com CI do SHA vinculada na tarefa viva; entrega disponível para aceite após esses gates.
