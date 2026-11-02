import { test, expect, type Page } from '@playwright/test';
import { randomInt, randomUUID } from 'node:crypto';
function cpf() { let s = String(randomInt(100000000,999999999)); for (let n=9;n<11;n++) { let sum=0; for(let i=0;i<n;i++) sum+=Number(s[i])*(n+1-i); const r=sum%11; s+=r<2?'0':String(11-r); } return s; }
async function login(page: Page, admin=false) {
  await page.goto('/login'); await page.getByLabel('E-mail', {exact:true}).fill((admin?process.env.PDV_E2E_ADMIN_EMAIL:process.env.PDV_E2E_EMAIL)!); await page.getByLabel('Senha',{exact:true}).fill(process.env.PDV_E2E_PASSWORD!);
  await page.getByRole('button',{name:'Entrar na minha conta'}).click(); await page.getByRole('link',{name:'Clientes',exact:true}).click(); await expect(page.getByRole('heading',{name:'Clientes',exact:true})).toBeVisible();
}
test('empty, validation, create, duplicate, edit and operator restrictions', async ({page},info) => {
  await login(page); await expect(page.getByRole('heading',{name:'Nenhum cliente encontrado'})).toBeVisible();
  await page.getByRole('button',{name:'Novo cliente'}).click(); await page.getByRole('button',{name:'Salvar cadastro'}).click(); await expect(page.getByRole('alert')).toContainText('nome');
  const name=`Cliente ${randomUUID().slice(0,8)}`, doc=cpf(); await page.getByLabel('Nome',{exact:true}).last().fill(name); await page.getByLabel('CPF ou CNPJ',{exact:true}).fill('11111111111');
  await page.getByRole('button',{name:'Salvar cadastro'}).click(); await expect(page.getByRole('alert')).toContainText('válido'); await page.getByLabel('CPF ou CNPJ',{exact:true}).fill(doc);
  await page.getByRole('button',{name:'Salvar cadastro'}).click(); await expect(page.getByRole('button',{name:`Abrir ${name}`,exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Novo cliente'}).click(); await page.getByLabel('Nome',{exact:true}).last().fill('Duplicado sintético'); await page.getByLabel('CPF ou CNPJ',{exact:true}).fill(doc);
  await page.getByRole('button',{name:'Salvar cadastro'}).click(); await expect(page.getByRole('alert')).toContainText('já está cadastrado'); await expect(page.getByRole('button',{name:'Salvar cadastro'})).toBeEnabled(); await page.getByRole('button',{name:'Fechar',exact:true}).click();
  await page.getByRole('button',{name:`Abrir ${name}`,exact:true}).click(); await expect(page.getByRole('button',{name:'Inativar cliente'})).toHaveCount(0);
  await page.getByLabel('Nome',{exact:true}).last().fill(`${name} editado`); await page.getByRole('button',{name:'Salvar cadastro'}).click(); await expect(page.getByRole('button',{name:`Abrir ${name} editado`,exact:true})).toBeVisible();
  await page.screenshot({path:info.outputPath('customers-desktop.png')}); await page.setViewportSize({width:390,height:844}); expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true); await page.screenshot({path:info.outputPath('customers-mobile.png')});
});
test('admin status and stale version preserve edits until explicit reload',async({page,request})=>{
  let bearer=''; page.on('request',r=>{if(r.url().includes('/customers'))bearer=r.headers()['authorization']??bearer;}); await login(page,true);
  const row=page.getByRole('button',{name:/^Abrir /}).first(); await row.click(); await expect(page.getByRole('button',{name:'Inativar cliente'})).toBeVisible(); await page.getByRole('button',{name:'Inativar cliente'}).click(); await expect(page.getByRole('heading',{name:'Detalhes do cliente'})).toHaveCount(0);
  await page.getByRole('button',{name:/^Abrir /}).first().click(); const field=page.getByLabel('Nome',{exact:true}).last(); await field.fill('Edição preservada');
  const list=await request.get(`${process.env.PDV_E2E_API}/customers`,{headers:{Authorization:bearer}}); const data=await list.json(); const c=data.items[0];
  const update=await request.put(`${process.env.PDV_E2E_API}/customers/${c.id}`,{headers:{Authorization:bearer},data:{customer:{personType:c.personType,document:c.document,name:'Concorrente sintético',phone:c.phone,email:c.email,locality:c.locality},version:c.version}}); expect(update.status()).toBe(200);
  await page.getByRole('button',{name:'Salvar cadastro'}).click(); await expect(page.getByRole('alert')).toContainText('mudou'); await expect(field).toHaveValue('Edição preservada'); await expect(page.getByRole('button',{name:'Salvar cadastro'})).toBeDisabled();
  await page.getByRole('button',{name:'Descartar edição e recarregar'}).click(); await expect(field).toHaveValue('Concorrente sintético');
});
test('search, pagination and controlled read failure with retry',async({page,request})=>{
  let bearer=''; page.on('request',r=>{if(r.url().includes('/customers'))bearer=r.headers()['authorization']??bearer;}); await login(page);
  for(let i=0;i<21;i++){const r=await request.post(`${process.env.PDV_E2E_API}/customers`,{headers:{Authorization:bearer},data:{personType:'PF',document:cpf(),name:`Lote sintético ${String(i).padStart(2,'0')}`}});expect(r.status()).toBe(201);}
  await page.getByLabel('Nome',{exact:true}).fill('Lote sintético'); await page.getByRole('button',{name:'Buscar',exact:true}).click(); await expect(page.getByText('21 cliente(s)')).toBeVisible(); await page.getByRole('button',{name:'Próxima'}).click(); await expect(page.getByText(/Página 2/)).toBeVisible();
  await page.route('**/customers?*',r=>r.abort('failed')); await page.getByRole('button',{name:'Buscar',exact:true}).click(); await expect(page.getByRole('alert')).toContainText('conexão'); await page.unroute('**/customers?*'); await page.getByRole('button',{name:'Tentar novamente'}).click(); await expect(page.getByText('21 cliente(s)')).toBeVisible();
});
