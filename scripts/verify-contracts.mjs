import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const read=(p)=>fs.readFileSync(path.join(root,p),'utf8');
const json=(p)=>JSON.parse(read(p));
execFileSync(process.execPath,['scripts/build-contracts.mjs','--check'],{cwd:root,stdio:'inherit'});
const api=json('contracts/openapi.json'), schemas=api.components.schemas;
const ajv=new Ajv2020({allErrors:true,strict:true,multipleOfPrecision:8});addFormats(ajv);ajv.addKeyword({keyword:'x-java-record',valid:true});
const definitions=JSON.parse(JSON.stringify(schemas).replaceAll('#/components/schemas/','#/$defs/'));
const validators=Object.fromEntries(Object.keys(schemas).map(name=>[name,ajv.compile({$defs:definitions,$ref:'#/$defs/'+name})]));
// Parse only the bounded record header, respecting annotation/generic commas.
function components(source,name){
 const at=source.search(new RegExp('\\brecord\\s+'+name+'(?:<[^>]+>)?\\s*\\('));assert(at>=0,'Record absent: '+name);
 let start=source.indexOf('(',at),depth=1,angle=0,quoted=false,part='',out=[];
 for(let i=start+1;i<source.length;i++){const c=source[i];if(c==='"'&&source[i-1]!=='\\')quoted=!quoted;
  if(!quoted){if(c==='(')depth++;if(c===')'){depth--;if(depth===0){out.push(part);break;}}if(c==='<')angle++;if(c==='>')angle--;if(c===','&&depth===1&&angle===0){out.push(part);part='';continue;}}
  part+=c;
 }
 return out.map(v=>v.trim().match(/([A-Za-z_][A-Za-z0-9_]*)\s*$/)?.[1]);
}
let records=0;
for(const [name,s]of Object.entries(schemas))if(s['x-java-record']){const link=s['x-java-record'];assert.deepEqual(components(read(link.file),link.record).sort(),Object.keys(s.properties).sort(),'DTO field drift: '+name);records++;}
function javaFiles(dir){return fs.readdirSync(path.join(root,dir),{withFileTypes:true}).flatMap(e=>e.isDirectory()?javaFiles(dir+'/'+e.name):e.name.endsWith('.java')?[dir+'/'+e.name]:[]);}
const actual=[];
for(const file of javaFiles('backend/api/src/main/java').filter(x=>x.endsWith('Controller.java'))){const source=read(file);const prefix=source.match(/@RequestMapping\("([^"]+)"\)/)?.[1]??'';for(const m of source.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping(?:\("([^"]*)"\))?/g))actual.push(m[1].toLowerCase()+' '+prefix+(m[2]??''));}
const documented=Object.entries(api.paths).flatMap(([url,ops])=>Object.keys(ops).filter(x=>['get','post','put','patch','delete'].includes(x)).map(method=>method+' '+url));
const health=['/actuator/health','/actuator/health/liveness','/actuator/health/readiness'].map(p=>'get '+p);
assert.deepEqual(documented.sort(),[...actual,...health].sort(),'Route inventory drift');
assert.equal(api.paths['/auth/login'].post.security.length,0);assert.equal(api.components.securitySchemes.bearerAuth.bearerFormat,'opaque');
assert(api.paths['/sales/{id}/reconcile'].post.responses[202]);assert(api.paths['/inventory/{productId}'].patch.responses[428]);
assert(api.paths['/carts/{id}/checkout'].post.parameters.some(p=>p.name==='Idempotency-Key'&&p.required));
const eventSchema=json('contracts/business-events.v1.schema.json'),event=ajv.compile(eventSchema),probe=ajv.compile(json('contracts/probe.v1.schema.json'));
const envelopeSource=read('backend/worker/src/main/java/dev/pdv/worker/business/BusinessEnvelope.java');
assert.deepEqual([...envelopeSource.matchAll(/public static final String \w+ = "(pdv\.[^"]+)";/g)].map(m=>m[1]).sort(),eventSchema.oneOf.map(v=>v.properties.type.const).sort(),'Business types drift');
const examples=json('contracts/examples.json');let positive=0,negative=0;
for(const row of examples.http){assert(validators[row.schema](row.value),row.schema+': '+ajv.errorsText(validators[row.schema].errors));positive++;}
for(const row of examples.events){assert(event(row),ajv.errorsText(event.errors));assert(Buffer.byteLength(JSON.stringify(row))<=4096);positive++;}
for(const row of examples.probes){assert(probe(row));positive++;}
for(const value of [{...examples.events[0],eventId:'invalid'},{...examples.events[0],outcome:'APROVADO'},{...examples.events[1],outcome:'TIMEOUT'},{...examples.events[1],type:'pdv.payment.result.v2'},{...examples.events[0],extra:true},{...examples.events[0],paymentId:null}]){assert(!event(value),'Invalid event accepted');negative++;}
for(const [type,value]of [['LoginRequest',{email:'bad',password:'x'}],['LoginRequest',{email:'a@example.test',password:'x',role:'ADMIN'}],['Checkout',{version:-1,method:'PIX',scenario:'APROVADO'}],['Reconcile',{requestId:examples.events[0].eventId,outcome:'APROVADO'}],['Resolve',{requestId:examples.events[0].eventId,outcome:'INDETERMINADO',reason:'synthetic'}]]){assert(!validators[type](value),'Invalid DTO accepted');negative++;}
console.log(`PASS: ${documented.length} HTTP operations, ${records} Java DTO field sets, ${Object.keys(schemas).length} compiled schemas, ${positive} valid/${negative} invalid examples; five business types + diagnostic contract.`);
