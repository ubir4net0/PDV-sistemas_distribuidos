import { expect, it } from 'vitest';
import { cents,brl,quantity,readCart } from './sales-model';
import { exactParse } from './transport';
it('uses exact cents for decimal fractions and values beyond JS safe integer',()=>{
  expect(cents('0.10')+cents('0.20')).toBe(30n);expect(brl('99999999999999999.99')).toBe('R$ 99.999.999.999.999.999,99');
  expect(cents('1e2')).toBe(10000n);expect(()=>cents('0.001')).toThrow();expect(()=>cents('-1')).toThrow();
});
it('validates positive int64 quantities without coercion or overflow',()=>{
  expect(quantity('9223372036854775807')).toBe(true);for(const v of ['0','1.5','-1','NaN','9223372036854775808'])expect(quantity(v)).toBe(false);
});
it('reads server totals and versions without rounding or computing catalog prices',()=>{
  const c=readCart(exactParse('{"id":"cart","customerId":"customer","version":9223372036854775806,"total":922337203685477580.70,"items":[{"productId":"product","quantity":9223372036854775807,"unitPrice":0.10,"subtotal":922337203685477580.70}]}'));
  expect(c.total).toBe('922337203685477580.70');expect(c.version).toBe('9223372036854775806');expect(c.items[0].quantity).toBe('9223372036854775807');
});
