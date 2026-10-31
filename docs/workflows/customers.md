# Clientes — API e persistência

Recorte do lote 016→017→018 aprovado explicitamente pelo proprietário: ADMIN/OPERATOR cadastram, consultam e editam clientes/endereços; só ADMIN ativa/inativa; sem exclusão física, com histórico e no máximo um endereço principal. Regras de checkout para inativos ficam na tarefa própria. Sem frontend neste lote.

## PDV-016

POST /customers exige ADMIN ou OPERATOR e customer.create. JSON: personType PF/PJ, document e name obrigatórios; phone, email e locality opcionais. Limites técnicos 18/200/32/320/160 caracteres respectivamente. Aceita documento puro ou máscara convencional; normaliza CNPJ para uppercase, preserva letras, valida dígitos. Não consulta situação cadastral nem serviços externos.

201 devolve cliente e Location /customers/{uuid}. 400 invalid_request para entrada inválida; 409 document_conflict para duplicidade, incluindo máscaras equivalentes; 401 sem token, 403 sem papel/concessão. Outros caminhos/métodos continuam negados até implementação própria. Nenhum payload de cliente aparece em erros ou logs SQL. Sem conta ou cliente padrão; somente development concede customer.create a ADMIN e OPERATOR.

JPA/EntityManager implementa CRUD conforme baseline. Flyway V4 cria tabela com UNIQUE de documento canônico e formato PF/PJ no PostgreSQL. Hibernate usa validate, nunca update/create, e open-in-view=false. Datas vêm do banco; entidades não são retornadas diretamente. Não há exclusão HTTP. Suite real testa constraints, disputa por documento e rebuild/replay; alterações/endereços pertencem aos próximos commits.

[Manual Receita Federal: DV do CNPJ](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf) · [Spring Boot JPA](https://docs.spring.io/spring-boot/reference/data/sql.html).
