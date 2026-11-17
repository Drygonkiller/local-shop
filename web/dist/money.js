export const MAX_PRICE_PAISA = 9_999_999_999;
export const MAX_QUANTITY = 9_999;

const amountPattern = /^(?:[0-9]+(?:\.[0-9]{0,2})?|\.[0-9]{1,2})$/;
const rupeeFormatter = new Intl.NumberFormat("en-US");
const nepalDateFormatter = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Kathmandu",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

export function asciiDigits(value) {
  return String(value ?? "").replace(/[०-९]/g, (digit) => String(digit.charCodeAt(0) - 0x0966));
}

export function parseMoney(value, maxPaisa = MAX_PRICE_PAISA) {
  const text = asciiDigits(value).trim();
  if (!amountPattern.test(text) || !Number.isSafeInteger(maxPaisa) || maxPaisa < 0) return null;

  const [whole, fraction = ""] = text.split(".");
  const rupees = Number(whole);
  if (rupees > Math.floor(maxPaisa / 100)) return null;

  const paisa = rupees * 100 + Number(fraction.padEnd(2, "0"));
  return paisa <= maxPaisa ? paisa : null;
}

export function moneyInput(paisa) {
  const { sign, whole, fraction } = splitPaisa(paisa);
  return `${sign}${whole}${fraction}`;
}

export function formatMoney(paisa) {
  const { sign, whole, fraction } = splitPaisa(paisa);
  return `Rs. ${sign}${rupeeFormatter.format(whole)}${fraction}`;
}

function splitPaisa(paisa) {
  if (typeof paisa !== "bigint" && !Number.isSafeInteger(paisa)) {
    throw new RangeError("Amount must be a whole number of paisa. / रकम पूरा पैसा हुनुपर्छ।");
  }
  const value = BigInt(paisa);
  const sign = value < 0n ? "-" : "";
  const absolute = value < 0n ? -value : value;
  const remainder = absolute % 100n;
  return {
    sign,
    whole: absolute / 100n,
    fraction: remainder === 0n ? "" : `.${String(remainder).padStart(2, "0")}`,
  };
}

export function sumLines(lines) {
  let total = 0;
  for (const { unitPricePaisa, quantity } of lines) {
    if (!Number.isSafeInteger(unitPricePaisa) || unitPricePaisa < 0 || unitPricePaisa > MAX_PRICE_PAISA ||
        !Number.isInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY) {
      throw new RangeError("Check the item price and quantity. / सामानको मूल्य र सङ्ख्या जाँच गर्नुहोस्।");
    }
    total += unitPricePaisa * quantity;
    if (!Number.isSafeInteger(total)) {
      throw new RangeError("This bill exceeds the amount limit. / यो बिलको रकम सीमा नाघ्यो।");
    }
  }
  return total;
}

export function calculateChange(totalPaisa, cashPaisa) {
  if (!Number.isSafeInteger(totalPaisa) || totalPaisa < 0 || !Number.isSafeInteger(cashPaisa) || cashPaisa < totalPaisa) {
    return null;
  }
  return cashPaisa - totalPaisa;
}

export function calculatorLines(amounts) {
  const name = amounts.length === 1 ? "Total / जम्मा" : "Item / सामान";
  return amounts.map((unitPricePaisa) => ({ favouriteId: null, name, unitPricePaisa, quantity: 1 }));
}

export function nepalDayKey(createdAtMillis = Date.now()) {
  const parts = new Map(nepalDateFormatter.formatToParts(createdAtMillis).map(({ type, value }) => [type, value]));
  return `${parts.get("year")}-${parts.get("month")}-${parts.get("day")}`;
}

export function customerLedger(customerId, bills, payments) {
  const entries = [];
  let balance = 0n;
  for (const bill of bills) {
    if (bill.customerId !== customerId) continue;
    const { createdAtMillis, totalPaisa: amountPaisa } = bill;
    balance += BigInt(amountPaisa);
    entries.push({ kind: "bill", bill, createdAtMillis, amountPaisa });
  }
  for (const payment of payments) {
    if (payment.customerId !== customerId) continue;
    const { createdAtMillis, amountPaisa } = payment;
    balance -= BigInt(amountPaisa);
    entries.push({ kind: "payment", payment, createdAtMillis, amountPaisa });
  }
  entries.sort((left, right) => {
    const byTime = right.createdAtMillis - left.createdAtMillis;
    if (byTime) return byTime;
    if (left.kind !== right.kind) return left.kind === "payment" ? -1 : 1;
    return left.kind === "payment"
      ? right.payment.id - left.payment.id
      : right.bill.billNumber - left.bill.billNumber;
  });
  return {
    duePaisa: balance > 0n ? balance : 0n,
    advancePaisa: balance < 0n ? -balance : 0n,
    lastActivityAtMillis: entries[0]?.createdAtMillis ?? null,
    entries,
  };
}

export function dailySales(bills, dayKey = nepalDayKey()) {
  const dayBills = bills.filter(({ createdAtMillis }) => nepalDayKey(createdAtMillis) === dayKey)
    .sort((a, b) => b.createdAtMillis - a.createdAtMillis || b.billNumber - a.billNumber);
  const items = new Map();
  let totalPaisa = 0n;
  let udharoTotalPaisa = 0n;
  let udharoBillCount = 0;

  for (const bill of dayBills) {
    totalPaisa += BigInt(bill.totalPaisa);
    if (bill.customerId != null) {
      udharoBillCount++;
      udharoTotalPaisa += BigInt(bill.totalPaisa);
    }
    for (const line of bill.lines) {
      if (line.favouriteId == null) continue;
      const { favouriteId, name, unitPricePaisa, quantity } = line;
      const item = items.get(favouriteId) ?? { favouriteId, name, totalPaisa: 0n };
      item.totalPaisa += BigInt(unitPricePaisa) * BigInt(quantity);
      items.set(favouriteId, item);
    }
  }

  const billCount = dayBills.length;
  const count = BigInt(billCount);
  const topItems = [...items.values()].sort((a, b) => {
    if (a.totalPaisa === b.totalPaisa) return a.favouriteId - b.favouriteId;
    return a.totalPaisa > b.totalPaisa ? -1 : 1;
  }).slice(0, 5);

  return {
    dayKey,
    bills: dayBills,
    totalPaisa,
    averagePaisa: count === 0n ? 0 : Number((totalPaisa + count / 2n) / count),
    topItems,
    billCount,
    udharoTotalPaisa,
    udharoBillCount,
  };
}
