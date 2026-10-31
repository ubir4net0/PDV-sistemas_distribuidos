# Clientes — API e persistência

Recorte do lote 016→017→018 aprovado explicitamente pelo proprietário: ADMIN/OPERATOR cadastram, consultam e editam clientes/endereços; só ADMIN ativa/inativa; sem exclusão física, com histórico e no máximo um endereço principal. Regras de checkout para inativos ficam na tarefa própria. Sem frontend neste lote.

## PDV-016

POST /customers exige ADMIN ou OPERATOR e customer.create. JSON: personType PF/PJ, document e name obrigatórios; phone, email e locality opcionais. Limites técnicos 18/200/32/320/160 caracteres respectivamente. Aceita documento puro ou máscara convencional; normaliza CNPJ para uppercase, preserva letras, valida dígitos. Não consulta situação cadastral nem serviços externos.

201 devolve cliente e Location /customers/{uuid}. 400 invalid_request para entrada inválida; 409 document_conflict para duplicidade, incluindo máscaras equivalentes; 401 sem token, 403 sem papel/concessão. Outros caminhos/métodos continuam negados até implementação própria. Nenhum payload de cliente aparece em erros ou logs SQL. Sem conta ou cliente padrão; somente development concede customer.create a ADMIN e OPERATOR.

JPA/EntityManager implementa CRUD conforme baseline. Flyway V4 cria tabela com UNIQUE de documento canônico e formato PF/PJ no PostgreSQL. Hibernate usa validate, nunca update/create, e open-in-view=false. Datas vêm do banco; entidades não são retornadas diretamente. Não há exclusão HTTP. Suite real testa constraints, disputa por documento e rebuild/replay; alterações/endereços pertencem aos próximos commits.

[Manual Receita Federal: DV do CNPJ](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf) · [Spring Boot JPA](https://docs.spring.io/spring-boot/reference/data/sql.html).

## PDV-017

GET /customers retorna {items,total,page,size}, com page 0 e size 20 padrão (máximo 100), filtros name (substring literal), document (normalizado) e active. Ordenação name/id fixa. GET /customers/{id} e /customers/{id}/history exigem customer.read e ADMIN/OPERATOR; histórico também é paginado. PUT /customers/{id} exige customer.update e JSON {customer: campos completos do cadastro, version: inteiro não negativo}. PATCH /customers/{id}/status recebe {active:boolean,version:inteiro} e exige ADMIN+customer.status. Sem DELETE; 404 customer_not_found, 409 version_conflict ou document_conflict, demais erros seguros anteriores preservados.

JPA bloqueia a linha e compara a versão antes de editar. V5 customer_changes registra ator do token, ação, snapshots antes/depois e instante UTC na mesma transação JPA/JDBC; falha de auditoria reverte a mutação. FK RESTRICT preserva cliente/ator referenciados; não há API para remover/regravar história. Não são fabricados eventos anteriores à V5. Cliente inativo mantém documento único e pode ser consultado/editado; regras de checkout não foram definidas aqui. Status já desejado com versão atual não grava nova mudança.

Somente development concede customer.read/update a ambos e customer.status a ADMIN. Mesmo com concessão acidental, OPERATOR não altera status. Testes incluem corrida de edições, rollback real por falha de auditoria, filtro/paginação, versões e autorização. Sem frontend ou endereços neste commit.
