import { isLosslessNumber } from 'lossless-json';
import { ApiError } from './api';
import { integer, record, text } from './transport';
export interface Product { id: string; name: string; sku: string; price: string; status: string }
export interface Line { productId: string; quantity: string; unitPrice: string; subtotal: string }
export interface Cart { id: string; customerId: string; version: string; total: string; items: Line[] }
export interface Sale extends Cart { number: string; cartId: string; paymentId: string; status: string; paymentStatus: string; method: string; scenario: string }
export interface CheckoutAttempt { cartId: string; key: string; version: string; method: string; scenario: string }
export interface SalesMemory { cart: Cart | null; attempt: CheckoutAttempt | null; sale: Sale | null; create: { requestId: string; customerId: string } | null; busy: boolean; needsReload: boolean; locked: boolean; products: Record<string,Product> }
export const emptySales = (): SalesMemory => ({ cart:null, attempt:null, sale:null, create:null, busy:false, needsReload:false, locked:false, products:{} });
export function cents(raw: string): bigint {
  const m = /^(\d+)(?:\.(\d*))?(?:[eE]([+-]?\d+))?$/.exec(raw);
  if (!m || Math.abs(Number(m[3] ?? 0)) > 100) throw new ApiError(502, 'Valor monetário inválido.');
  const digits = BigInt(m[1] + (m[2] ?? '')); const power = 2 + Number(m[3] ?? 0) - (m[2]?.length ?? 0);
  if (power >= 0) return digits * 10n ** BigInt(power);
  const divisor = 10n ** BigInt(-power); if (digits % divisor !== 0n) throw new ApiError(502, 'Valor monetário exige arredondamento.');
  return digits / divisor;
}
export function money(v: unknown): string {
  if (!isLosslessNumber(v)) throw new ApiError(502, 'Valor monetário inválido.');
  const c = cents(v.value); return `${c / 100n}.${String(c % 100n).padStart(2,'0')}`;
}
export function brl(raw: string) { const c = cents(raw); return `R$ ${(c/100n).toLocaleString('pt-BR')},${String(c%100n).padStart(2,'0')}`; }
export function quantity(raw: string): boolean { return /^\d+$/.test(raw) && BigInt(raw)>0n && BigInt(raw)<=9223372036854775807n; }
export function readProduct(v: unknown): Product { const r=record(v); return {id:text(r.id),name:text(r.name),sku:text(r.sku),price:money(r.salePrice),status:text(r.status)}; }
export function readCart(v: unknown): Cart {
  const r=record(v); if(!Array.isArray(r.items)) throw new ApiError(502,'Carrinho inválido.');
  return { id:text(r.id),customerId:text(r.customerId),version:integer(r.version),total:money(r.total),items:r.items.map(v=>{const l=record(v);return {productId:text(l.productId),quantity:integer(l.quantity),unitPrice:money(l.unitPrice),subtotal:money(l.subtotal)};}) };
}
export function readSale(v: unknown): Sale {
  const r=record(v); return {...readCart(v),number:integer(r.number),cartId:text(r.cartId),paymentId:text(r.paymentId),status:text(r.status),paymentStatus:text(r.paymentStatus),method:text(r.method),scenario:text(r.scenario)};
}
