import test from 'node:test';
import assert from 'node:assert/strict';
import { billMatchesQuery } from '../dist/manage.js';

test('history finds old bills by Arabic and Nepali numerals', () => {
    const oldBill = { billNumber: 123 };
    for (const query of ['12', '२३', '# १२३', 'Bill #१२३', 'बिल नं. १२३', ' ']) {
        assert.equal(billMatchesQuery(oldBill, query), true, query);
    }
    assert.equal(billMatchesQuery(oldBill, '124'), false);
    assert.equal(billMatchesQuery(oldBill, 'Anonymous'), false);
});

test('history finds paid and udharo bills by customer name', () => {
    const paid = { billNumber: 5, customerId: null, customerName: 'Sushila   Tamang' };
    const udharo = { billNumber: 25, customerId: 10, customerName: 'सुशीला तामाङ' };
    assert.equal(billMatchesQuery(paid, '  SUSHILA TAM '), true);
    assert.equal(billMatchesQuery(udharo, 'तामाङ'), true);
    assert.equal(billMatchesQuery(udharo, 'Ward 25'), false);
});
