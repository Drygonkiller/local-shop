import test from "node:test";
import assert from "node:assert/strict";
import {
  calculateChange, calculatorLines, customerLedger, dailySales, formatMoney, MAX_PRICE_PAISA,
  MAX_QUANTITY, moneyInput, nepalDayKey, parseMoney, sumLines,
} from "../dist/money.js";

test("parse Nepali numerals and decimal paisa without rounding", () => {
  assert.equal(parseMoney(" १२५५००.७५ "), 12_550_075);
  assert.equal(parseMoney(".७"), 70);
  assert.equal(parseMoney("0."), 0);
  assert.equal(parseMoney("99999999.99"), MAX_PRICE_PAISA);
  assert.equal(parseMoney("100000000"), null);
  assert.equal(parseMoney("90071992547409.91", Number.MAX_SAFE_INTEGER), Number.MAX_SAFE_INTEGER);
  assert.equal(parseMoney("90071992547409.92", Number.MAX_SAFE_INTEGER), null);
  for (const invalid of ["", ".", "-1", "1.234", "1e3", "1,000"]) assert.equal(parseMoney(invalid), null);
});

test("format fractional paisa and large history balances exactly", () => {
  assert.equal(formatMoney(12_550_075), "Rs. 125,500.75");
  assert.equal(formatMoney(100_100), "Rs. 1,001");
  assert.equal(formatMoney(900_719_925_474_099_101n), "Rs. 9,007,199,254,740,991.01");
  assert.equal(formatMoney(-1n), "Rs. -0.01");
  assert.equal(moneyInput(12_550_070), "125500.70");
});

test("calculator and express produce the same bill lines as checkout", () => {
  assert.deepEqual(calculatorLines([45_000]), [
    { favouriteId: null, name: "Total / जम्मा", unitPricePaisa: 45_000, quantity: 1 },
  ]);
  const lines = calculatorLines([12_000, 25_000, 8_000]);
  assert.equal(sumLines(lines), 45_000);
  assert.ok(lines.every(({ name }) => name === "Item / सामान"));
  assert.equal(sumLines(calculatorLines([1, 1, 1])), 3);
});

test("bill totals reject an unsafe amount and invalid quantities", () => {
  const line = { unitPricePaisa: MAX_PRICE_PAISA, quantity: MAX_QUANTITY };
  assert.equal(sumLines([line]), MAX_PRICE_PAISA * MAX_QUANTITY);
  assert.throws(() => sumLines(Array(100).fill(line)), RangeError);
  assert.throws(() => sumLines([{ unitPricePaisa: 100, quantity: 0 }]), RangeError);
});

test("cash change stays empty when underpaid and uses exact paisa", () => {
  assert.equal(calculateChange(5_099, 5_098), null);
  assert.equal(calculateChange(5_099, 5_099), 0);
  assert.equal(calculateChange(5_099, 6_000), 901);
  assert.equal(calculateChange(5_099, null), null);
});

test("Nepal day turns over at 18:15 UTC and daily sales exclude yesterday", () => {
  const before = Date.parse("2026-11-15T18:14:59.999Z");
  const after = before + 1;
  const today = nepalDayKey(after);
  assert.equal(nepalDayKey(before), "2026-11-15");
  assert.equal(today, "2026-11-16");

  const oldBill = { billNumber: 1, createdAtMillis: before, totalPaisa: 99, customerId: null, lines: [] };
  const oldTea = { favouriteId: 1, name: "Tea", unitPricePaisa: 50, quantity: 1 };
  const newTea = { favouriteId: 1, name: "Milk tea", unitPricePaisa: 50, quantity: 1 };
  const udharoBill = { billNumber: 2, createdAtMillis: after, totalPaisa: 50, customerId: 22, lines: [oldTea] };
  const cashBill = { billNumber: 3, createdAtMillis: after + 1, totalPaisa: 63, customerId: null, lines: [newTea] };
  const report = dailySales([oldBill, udharoBill, cashBill], today);

  assert.deepEqual(report.bills.map(({ billNumber }) => billNumber), [3, 2]);
  assert.equal(report.totalPaisa, 113n);
  assert.equal(report.averagePaisa, 57);
  assert.equal(report.billCount, 2);
  assert.equal(report.udharoTotalPaisa, 50n);
  assert.equal(report.udharoBillCount, 1);
  assert.deepEqual(report.topItems, [{ favouriteId: 1, name: "Milk tea", totalPaisa: 100n }]);
});

test("deleting a paid udharo bill preserves payments and turns the surplus into advance", () => {
  const customerId = 22;
  const charge = { customerId, billNumber: 1, createdAtMillis: 20, totalPaisa: 2_501 };
  const otherCharge = { customerId: 23, billNumber: 2, createdAtMillis: 21, totalPaisa: 10_000 };
  const payment = { customerId, id: 31, createdAtMillis: 20, amountPaisa: 500 };
  const ledger = customerLedger(customerId, [charge, otherCharge], [payment]);
  assert.equal(ledger.duePaisa, 2_001n);
  assert.equal(ledger.advancePaisa, 0n);
  assert.deepEqual(ledger.entries.map(({ kind }) => kind), ["payment", "bill"]);

  const afterDeletion = customerLedger(customerId, [otherCharge], [payment]);
  assert.equal(afterDeletion.duePaisa, 0n);
  assert.equal(afterDeletion.advancePaisa, 500n);
  assert.equal(afterDeletion.entries[0].payment, payment);
});
