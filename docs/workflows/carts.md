# Carrinho — PDV-034

Recorte confirmado pelo proprietário em 01/11/2026. ADMIN/OPERATOR com cart.read/write gerenciam somente seus carrinhos, inclusive ADMIN. Cliente ativo obrigatório para criar/adicionar/alterar; produtos ATIVOS e quantidades inteiras positivas. Carrinho não é venda, não reserva nem baixa estoque; checkout futuro revalidará preço/disponibilidade e congelará o agregado na própria transação.

| Rota | Contrato |
| --- | --- |
| POST /carts | {requestId,customerId} →201 Cart, Idempotent-Replay |
| GET /carts | Page própria, page0/size20/máximo100 |
| GET /carts/{id} | Cart próprio, alheio404 |
| PUT /carts/{id}/items/{productId} | {quantity,version} →200 Cart; quantidade absoluta |
| DELETE /carts/{id}/items/{productId}?version=N | →200 Cart; versão obrigatória |

Cart={id,customerId,version,total,items,createdAt,updatedAt}; Line={productId,quantity,unitPrice,subtotal}. Dinheiro usa números decimais como no catálogo: consumidor deve preservar precisão decimal. Servidor calcula BigDecimal sem double/arredondamento; subtotal=preço copiado×quantidade, total=soma. Preço NUMERIC(19,2) vem exclusivamente do catálogo na primeira inclusão; alterar quantidade conserva preço capturado. Remover e reincluir captura preço atual. Sem desconto/taxa/repreçamento automático; campos de preço/desconto/total/ator enviados pelo cliente são rejeitados.

CRUD JPA/V13 com @Version e lock do agregado, snapshots de leitura REPEATABLE_READ; cliente/produto bloqueados para revalidar ativo ao mutar. Dois escritores da mesma versão: um sucesso, outro409, sem perda. Remover item inativo/obsoleto continua permitido para limpar o carrinho; produto/cliente inativo impede novos itens/quantidades. Item ausente404. Repetição de mutação com versão antiga409, sem novo efeito; cliente consulta versão atual antes de tentar outra operação. Versão no limite409 sem wrap.

Criação usa chave por ator, fingerprint de cliente e resposta original persistida; replay retorna snapshot da criação, sem segundo carrinho ou estado atual inventado. Históricos cart_changes/cart_operations imutáveis, ator consistente com dono por FK; snapshots antes/depois por versão. Falhar trilha reverte itens/versão/totais. DELETE remove o item e conserva snapshot histórico; sem endpoint de exclusão física do agregado.

400 inválido/fração/campos extras/paginação/versão ausente,401/403 acesso,404 inexistente/alheio,409 estado/versão/chave,503 persistência. Sem frontend, checkout/venda/Saga/pagamento ou reserva inferidos. [Spec](../../sdd/features/PDV-034-carrinho-itens.md), [catálogo](catalog.md), [reservas](reservations.md), [migrations](database-migrations.md).
