import test from "node:test";
import assert from "node:assert/strict";
import { formatBillText } from "../dist/receipt.js";
import { saveBill } from "../dist/store.js";

const lines = [{ name: "Tea", quantity: 1, unitPricePaisa: 2500 }];
const bill = {
  billNumber: 3,
  shopName: "Tea Shop",
  createdAtMillis: Date.parse("2026-11-15T10:00:00Z"),
  totalPaisa: 2500,
  lines,
};

test("paid receipts and drafts show an optional customer name", () => {
  const named = { ...bill, customerId: null, customerName: "सीता राई" };
  const savedText = formatBillText(named);
  const draftText = formatBillText({ ...named, billNumber: undefined });
  assert.match(savedText, /^Customer \/ ग्राहक: सीता राई$/m);
  assert.match(draftText, /^Draft \/ मस्यौदा\n/);
  assert.match(draftText, /^Customer \/ ग्राहक: सीता राई$/m);
  assert.doesNotMatch(savedText, /^Udharo \/ उधारो:/m);
});

test("credit receipts keep the Udharo label and older unnamed bills omit the customer line", () => {
  const creditText = formatBillText({ ...bill, customerId: 2, customerName: "Sita Rai" });
  assert.match(creditText, /^Udharo \/ उधारो: Sita Rai$/m);
  assert.doesNotMatch(creditText, /^Customer \/ ग्राहक:/m);
  assert.doesNotMatch(formatBillText(bill), /^(?:Customer \/ ग्राहक|Udharo \/ उधारो):/m);
});

test("paid checkout rejects excessively long customer names before writing to storage", async () => {
  await assert.rejects(saveBill(lines, null, "A".repeat(121)), {
    name: "RangeError",
    message: /120 characters/,
  });
});
