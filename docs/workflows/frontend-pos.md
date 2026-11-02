# PDV e checkout na interface — PDV-043

Abrir PDV em `/app/pos`. Operador/administrador precisam de customer.read, catalog.read e cart.read/write; finalizar também exige sale.checkout. Só carrinhos próprios; API é autoridade. Buscar cliente ativo por nome (até20 resultados por busca) e iniciar atendimento. Busca de produtos ATIVOS por prefixo de nome/SKU tem debounce250ms, cancelamento de resposta antiga e páginas20. Catálogo não anuncia saldo/disponibilidade inventado.

Incluir produto soma uma unidade à quantidade já conhecida; alterar define quantidade absoluta int64 positiva. Versão acompanha todas as mutações. Erro de escrita bloqueia novas intenções até reconsultar o carrinho; não reaplica incremento às cegas. Remoção/reinclusão captura preço atual conforme [API034](carts.md). Preço/subtotal/total vêm exclusivamente do servidor. JSON lossless e centavos BigInt preservam decimais/extremos; sem Number/double, arredondamento, desconto ou total enviado pelo cliente.

F2 foca busca de produto; F4 busca de cliente antes de iniciar; Alt+Enter submete checkout com as mesmas validações do botão. Controles são nativos, rotulados e operáveis por teclado. Layout adapta catálogo/carrinho a desktop/mobile; não é certificação integral de acessibilidade ou meta de carga medida.

Método fictício e cenário APROVADO/RECUSADO/TIMEOUT são escolhas explícitas por atendimento. Checkout não coleta cartão, chave Pix, dinheiro real nem usa serviço externo. Falha/perda de resposta conserva versão/método/cenário/Idempotency-Key e bloqueia edição; “Repetir checkout com segurança” recupera a mesma operação. Abertura de carrinho conserva requestId/cliente durante resultado desconhecido. Esses contextos ficam em memória no shell autenticado, inclusive ao navegar para Clientes e voltar, e são descartados no logout/recarga.

409 de preço exige reconsulta e remoção/reinclusão explícita do item. Não se reprecifica automaticamente. Carrinho convertido não pode ser editado; o DTO Cart atual não informa conversão, então retomada pode receber409 ao tentar mutar. Servidor impede segunda venda. Após recarga durante resultado desconhecido, sem código da venda, não há endpoint atual para recuperar venda pelo carrinho; não prometer essa recuperação. Preserve o código recebido. Nenhum token/dado de cliente é salvo em URL/storage/log.

Venda criada exibe snapshot/código e não significa pagamento aprovado. A [interface044](frontend-payments.md) consulta progresso/reconciliação; comprovante/cancelamento continuam próprios. [API de checkout](checkout.md), [spec](../../sdd/features/PDV-043-pdv-checkout-frontend.md), [frontend](../../frontend/README.md).

E2E usa produtos/categoria/saldos sintéticos SQL somente no PostgreSQL UUID. Fixtures não comprovam operação de entrada de estoque; HTTP real verifica carrinho/checkout/replay/conflito. Comandos typecheck/test/build/e2e no frontend; CI repete os gates e a suíte backend independente.
