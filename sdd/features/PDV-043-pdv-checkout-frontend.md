# PDV-043 — PDV e checkout no frontend

## Fonte e aceite

[Tarefa viva](https://app.notion.com/p/4092c9a99917817f9b8aea419dd8b3c8), relida em 02/11/2026. Aceite: “Busca ágil, carrinho, total em destaque, seleção cliente, teclado e responsividade.” Tela02. Responsável/prazo não informados. PDV-015/019/021/034/035 consultadas ao vivo; implementação depende do commit/verificação da019 deste lote, sem aceitar predecessoras. Regras de carrinho/checkout/estados já aprovadas nas specs034/035 e ADR-008.

## Fronteiras e invariantes

Interface de operação com busca de produtos ATIVOS por nome/SKU, busca/seleção de cliente ativo, carrinho próprio, quantidades inteiras positivas, atualização/remoção e totais do servidor em destaque. Retomar carrinhos próprios com paginação; nenhuma exclusão de agregado. Dinheiro e int64 lidos exatamente, renderização em centavos BigInt sem double ou arredondamento. Cliente nunca envia preço/desconto/total. Teclas F2/F4 para busca e Alt+Enter para formulário de checkout; controles nativos e layout responsivo.

Checkout exige versão, método fictício e cenário explícito conforme contrato existente. Mesma chave/corpo preservados após resposta perdida; edição/opções bloqueadas enquanto resultado incerto. Criar carrinho também preserva requestId/cliente em retry. Erro de mutação exige reconsulta antes de nova intenção, sem reaplicar incremento às cegas.409 de preço pede remoção/reinclusão explícita para capturar catálogo atual. Estado em memória no shell autenticado conserva contexto ao navegar entre telas; logout/recarga o descarta. Servidor impede venda duplicada mesmo após perda do contexto, mas retomada de resultado desconhecido após recarga não é prometida sem código da venda. Não inferir que GET Cart informa se já foi convertido: DTO atual não traz esse campo.

Criar venda exibe snapshot AGUARDANDO_PAGAMENTO e código próprio, sem declarar pagamento aprovado. Consulta/progresso e reconciliação de pagamento são044; cancelamento/comprovante/gestão de catálogo/estoque são tarefas próprias. Sem regra financeira nova, gateway, mudança Java/DDL, deploy ou requisição em sistema real externo.

## Verificação planejada

Pre-flight executado após019: main1b77746 sincronizada, PG/Rabbit healthy, Migrate V18 sem pendências (9,506s). TypeScript/build/unitários de centavos/extremos/DTOs e browser real contra API/PG UUID. Fixtures privadas criam produto/categoria/saldo sintéticos somente no banco E2E. Cobrir cliente ativo, busca literal/páginas, incluir/alterar/remover, totais0,10+0,20, conflitos e preço modificado, checkout após resposta perdida com chave/corpo iguais e mesma venda, teclado e mobile/desktop. Regressões015/019 continuam. Sem migration nova.

## Evidências e revisão

Executado em 02/11/2026 após commit019: TypeScript,19 unitários e build Vite aprovados (JS304,05kB/95,38kB gzip). JSON exato/BigInt cobre centavos, expoente, precisão/extremos e int64 sem coerção. Browser final:12 testes aprovados em1,2min, zero falhas/skips, com API/PostgreSQL UUID reais e V1–V18 reconstruído. Três novos cenários verificam catálogo23/páginas/busca vazia/teclado, adicionar/quantidade inválida/alterar/remover/totais0,10+0,20; resposta perdida após201 real e replay201 com mesma chave/corpo/ID e header true após navegar Clientes→PDV;409 de preço, remoção/reinclusão e checkout revisado. Nove regressões de auth/clientes aprovadas. Layout1280px/390px e carrinho/total inspecionados visualmente, sem overflow; screenshots ignoradas.

Primeira rodada passou os três cenários novos e foi interrompida no TTL por edição/HMR durante execução; fontes congeladas para repetição. Rodada seguinte identificou corrida da fixture de paginação ao capturar token antes da primeira leitura; captura movida para /auth/me e toda a suíte repetida. Nenhum resultado parcial foi contado como12/12. Build Maven do runner tenta cache offline e usa dependências fixadas online quando ausentes, sem relaxar compilação/Checkstyle ou substituir HTTP/PG reais. Fixtures SQL não comprovam entrada de estoque. Recursos próprios removidos.

Planejamento60IDs/39requisitos/três hashes,327links/UTF8, diff e segredos conferidos antes do commit. Sem Java/DDL ou teste backend local adicional; CI própria repete backend/contratos/planejamento/frontend do SHA publicado e será vinculada no card043. Main, commit próprio sem PR; Em validação aguarda aceite do proprietário. Recuperação depois de recarregar durante resultado incerto e progresso de pagamento seguem limites acima e044; sem aceite implícito de predecessoras.
