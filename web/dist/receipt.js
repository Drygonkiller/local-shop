import { formatMoney } from "./money.js";

const nepalDateParts = new Intl.DateTimeFormat("en-GB", {
  timeZone: "Asia/Kathmandu",
  calendar: "gregory",
  numberingSystem: "latn",
  year: "numeric",
  month: "2-digit",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});
const adMonths = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const bsMonths = ["वैशाख", "जेठ", "असार", "साउन", "भदौ", "असोज", "कात्तिक", "मंसिर", "पुस", "माघ", "फागुन", "चैत"];
// Same Kathmandu Metropolitan City month lengths and confirmation range as the Android app.
// https://kathmandu.gov.np/en/calendar
const bsMonthLengths = [
  [31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30],
  [31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31],
  [31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30],
  [31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30],
  [31, 31, 32, 31, 31, 30, 30, 30, 29, 30, 30, 30],
  [31, 32, 31, 32, 30, 31, 30, 30, 29, 30, 30, 30],
  [30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30],
  [31, 31, 32, 31, 31, 31, 30, 29, 30, 30, 30, 30],
  [30, 31, 32, 32, 30, 31, 30, 30, 29, 30, 30, 30],
  [30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30],
  [30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 30, 30],
];
// Nepal Rastra Bank lists 1 Baisakh 2080 as 14 April 2023.
// https://www.nrb.org.np/contents/uploads/2023/03/Holiday-list-2080-Nepali.pdf
const firstBsDay = Date.UTC(2023, 3, 14);

function civilParts(millis) {
  return Object.fromEntries(nepalDateParts.formatToParts(millis).map(({ type, value }) => [type, value]));
}

function bikramSambat(year, month, day) {
  if (year < 2023 || year > 2034) return null;
  let remaining = (Date.UTC(year, month - 1, day) - firstBsDay) / 86_400_000;
  if (remaining < 0) return null;
  for (const [yearIndex, months] of bsMonthLengths.entries()) {
    for (const [monthIndex, length] of months.entries()) {
      if (remaining < length) {
        return { year: 2080 + yearIndex, monthName: bsMonths[monthIndex], day: remaining + 1 };
      }
      remaining -= length;
    }
  }
  return null;
}

function formatDualDate(millis, withTime) {
  const { year, month, day, hour, minute } = civilParts(millis);
  const bs = bikramSambat(Number(year), Number(month), Number(day));
  const ad = `${day} ${adMonths[Number(month) - 1]} ${year}`;
  const date = bs ? `${bs.day} ${bs.monthName} ${bs.year} BS (${ad})` : ad;
  return [
    date,
    withTime ? `, ${hour}:${minute}` : "",
    bs?.year > 2083 ? " · Provisional / अनुमानित" : "",
  ].join("");
}

export function formatNepalDate(millis) {
  return formatDualDate(millis, true);
}

export function formatNepalDateOnly(millis) {
  return formatDualDate(millis, false);
}

export function formatNepalTime(millis) {
  const { hour, minute } = civilParts(millis);
  return `${hour}:${minute}`;
}

const smallNumbers = [
  "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
  "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
];
const tens = ["twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"];
const scales = [
  [100_000_000_000n, "kharab"], [1_000_000_000n, "arab"], [10_000_000n, "crore"],
  [100_000n, "lakh"], [1_000n, "thousand"], [100n, "hundred"],
];

function numberInWords(value) {
  if (value < 20n) return smallNumbers[Number(value)];
  if (value < 100n) {
    const tensWord = tens[Number(value / 10n) - 2];
    const units = value % 10n;
    return units ? `${tensWord}-${smallNumbers[Number(units)]}` : tensWord;
  }
  const [divisor, scale] = scales.find(([size]) => value >= size);
  const group = `${numberInWords(value / divisor)} ${scale}`;
  const remainder = value % divisor;
  return remainder ? `${group} ${numberInWords(remainder)}` : group;
}

export function formatMoneyInWords(totalPaisa) {
  const amount = BigInt(totalPaisa);
  if (amount < 0n) throw new RangeError("Amount must be nonnegative");
  const rupees = amount / 100n;
  const paisa = amount % 100n;
  const rupeeWord = rupees === 1n ? "rupee" : "rupees";
  const paisaWords = paisa ? ` and ${numberInWords(paisa)} paisa` : "";
  const text = `${numberInWords(rupees)} ${rupeeWord}${paisaWords} only`;
  return text[0].toUpperCase() + text.slice(1);
}

function receiptParts(bill) {
  const draft = bill.billNumber == null;
  const details = [
    `PAN / प्यान ${bill.shopPan || "—"}`,
    `Bill no. / बिल नं. ${bill.billNumber ?? "—"}`,
    `Date / मिति ${formatNepalDate(bill.createdAtMillis)}`,
  ];
  if (bill.customerName) {
    const label = bill.customerId == null ? "Customer / ग्राहक" : "Udharo / उधारो";
    details.push(`${label}: ${bill.customerName}`);
  }
  const lines = bill.lines.map(({ name, quantity, unitPricePaisa }) => ({
    name,
    quantityPrice: `${quantity} × ${formatMoney(unitPricePaisa)}`,
    total: formatMoney(BigInt(unitPricePaisa) * BigInt(quantity)),
  }));
  return {
    draft,
    name: bill.shopName || "Quick Bill / छिटो बिल",
    details,
    lines,
    totalLabel: "Total / जम्मा",
    total: formatMoney(bill.totalPaisa),
    words: `In words / अक्षरमा: ${formatMoneyInWords(bill.totalPaisa)}`,
  };
}

export function formatBillText(bill) {
  const receipt = receiptParts(bill);
  return [
    ...(receipt.draft ? ["Draft / मस्यौदा"] : []),
    receipt.name,
    ...receipt.details,
    "",
    ...receipt.lines.flatMap((line) => [line.name, `${line.quantityPrice}  ${line.total}`, ""]),
    `${receipt.totalLabel}  ${receipt.total}`,
    receipt.words,
  ].join("\n");
}

function makeElement(tag, className, text) {
  const element = document.createElement(tag);
  element.className = className;
  if (text != null) element.textContent = text;
  return element;
}

function receiptPair(left, right, className = "") {
  const row = makeElement("div", `receipt-pair${className ? ` ${className}` : ""}`);
  row.append(makeElement("span", "receipt-left", left), makeElement("strong", "receipt-amount", right));
  return row;
}

export function renderReceipt(bill) {
  const receipt = receiptParts(bill);
  const paper = makeElement("article", "receipt-paper");
  if (receipt.draft) paper.append(makeElement("p", "receipt-meta receipt-draft", "Draft / मस्यौदा"));
  paper.append(
    makeElement("h2", "receipt-heading", receipt.name),
    ...receipt.details.map((detail) => makeElement("p", "receipt-meta", detail)),
    makeElement("hr", "receipt-divider"),
  );
  for (const line of receipt.lines) {
    const item = makeElement("div", "receipt-line");
    item.append(makeElement("p", "receipt-line-name", line.name), receiptPair(line.quantityPrice, line.total));
    paper.append(item);
  }
  paper.append(
    makeElement("hr", "receipt-divider"),
    receiptPair(receipt.totalLabel, receipt.total, "receipt-total"),
    makeElement("p", "receipt-words", receipt.words),
  );
  return paper;
}

const paperWidth = 384;
const paperMargin = 18;
const textWidth = paperWidth - 2 * paperMargin;
const maxPageHeight = 1700;
const fontStack = '"Quick Bill Devanagari", system-ui, sans-serif';
const titleStyle = { size: 30, weight: 700, height: 42, align: "center" };
const detailStyle = { size: 20, weight: 400, height: 30, align: "left" };
const bodyStyle = { size: 23, weight: 400, height: 34, align: "left" };
const amountStyle = { size: 21, weight: 700, height: 32, align: "right" };
const totalStyle = { size: 26, weight: 700, height: 38, align: "left" };

function applyFont(context, style) {
  context.font = `${style.weight} ${style.size}px ${fontStack}`;
  context.textAlign = style.align;
}

function wrapRows(context, text, style) {
  applyFont(context, style);
  const lines = [];
  let line = "";
  for (const word of String(text).trim().split(/\s+/u)) {
    const candidate = line ? `${line} ${word}` : word;
    if (line && context.measureText(candidate).width > textWidth) {
      lines.push({ text: line, style });
      line = word;
    } else {
      line = candidate;
    }
  }
  if (line) lines.push({ text: line, style });
  return lines;
}

function pairRows(context, left, right, leftStyle, rightStyle) {
  applyFont(context, leftStyle);
  const leftWidth = context.measureText(left).width;
  applyFont(context, rightStyle);
  const rightWidth = context.measureText(right).width;
  if (leftWidth + rightWidth + 12 > textWidth) {
    return [...wrapRows(context, left, leftStyle), ...wrapRows(context, right, rightStyle)];
  }
  return [{ text: left, style: leftStyle, right, rightStyle }];
}

const ruleRow = { rule: true, height: 17 };
const spaceRow = { height: 7 };

function rowHeight(row) {
  return row.height ?? Math.max(row.style.height, row.rightStyle?.height ?? 0);
}

function pageRows(context, receipt) {
  const header = [
    ...wrapRows(context, receipt.name, titleStyle),
    ...receipt.details.flatMap((detail) => wrapRows(context, detail, detailStyle)),
    ruleRow,
  ];
  const groups = receipt.lines.map((line) => [
    ...wrapRows(context, line.name, bodyStyle),
    ...pairRows(context, line.quantityPrice, line.total, detailStyle, amountStyle),
    spaceRow,
  ]);
  groups.push([
    ruleRow,
    ...pairRows(context, receipt.totalLabel, receipt.total, totalStyle, { ...totalStyle, align: "right" }),
    ...wrapRows(context, receipt.words, detailStyle),
  ]);

  const headerHeight = header.reduce((total, row) => total + rowHeight(row), 2 * paperMargin);
  const pages = [];
  let rows = [...header];
  let height = headerHeight;
  for (const group of groups) {
    const nextHeight = group.reduce((total, row) => total + rowHeight(row), 0);
    if (rows.length > header.length && height + nextHeight > maxPageHeight) {
      pages.push({ rows, height });
      rows = [...header];
      height = headerHeight;
    }
    rows.push(...group);
    height += nextHeight;
  }
  pages.push({ rows, height });
  return pages;
}

function drawRowText(context, text, y, style) {
  applyFont(context, style);
  const x = style.align === "center" ? paperWidth / 2 : style.align === "right" ? paperWidth - paperMargin : paperMargin;
  context.fillText(text, x, y + Math.ceil(style.height * 0.75), textWidth);
}

function drawPage(canvas, page) {
  canvas.width = paperWidth;
  canvas.height = page.height;
  const context = canvas.getContext("2d", { alpha: false });
  context.fillStyle = "#fff";
  context.fillRect(0, 0, paperWidth, page.height);
  context.fillStyle = "#111";
  context.textBaseline = "alphabetic";
  context.strokeStyle = "#111";
  let y = paperMargin;
  for (const row of page.rows) {
    if (row.rule) {
      context.beginPath();
      context.moveTo(paperMargin, y + 7.5);
      context.lineTo(paperWidth - paperMargin, y + 7.5);
      context.stroke();
    } else if (row.style) {
      drawRowText(context, row.text, y, row.style);
      if (row.right) drawRowText(context, row.right, y, row.rightStyle);
    }
    y += rowHeight(row);
  }
}

const encode = new TextEncoder();
const receiptWidthPoints = 58 * 72 / 25.4;

function encodeRasterPdf(images) {
  const chunks = [];
  const offsets = [0];
  let length = 0;
  function append(value) {
    const bytes = typeof value === "string" ? encode.encode(value) : value;
    chunks.push(bytes);
    length += bytes.length;
  }
  function writeObject(id, ...content) {
    offsets[id] = length;
    append(`${id} 0 obj\n`);
    content.forEach(append);
    append("\nendobj\n");
  }

  append("%PDF-1.4\n");
  append(Uint8Array.of(0x25, 0xe2, 0xe3, 0xcf, 0xd3, 0x0a));
  writeObject(1, "<< /Type /Catalog /Pages 2 0 R >>");
  const pages = images.map((_, index) => `${3 + index * 3} 0 R`).join(" ");
  writeObject(2, `<< /Type /Pages /Count ${images.length} /Kids [${pages}] >>`);
  images.forEach(({ bytes, height }, index) => {
    const page = 3 + index * 3;
    const widthPoints = receiptWidthPoints.toFixed(4);
    const heightPoints = (height * receiptWidthPoints / paperWidth).toFixed(4);
    const draw = encode.encode(`q ${widthPoints} 0 0 ${heightPoints} 0 0 cm /Receipt Do Q\n`);
    writeObject(page, `<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${widthPoints} ${heightPoints}] /Resources << /XObject << /Receipt ${page + 1} 0 R >> >> /Contents ${page + 2} 0 R >>`);
    writeObject(page + 1,
      `<< /Type /XObject /Subtype /Image /Width ${paperWidth} /Height ${height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${bytes.length} >>\nstream\n`,
      bytes, "\nendstream");
    writeObject(page + 2, `<< /Length ${draw.length} >>\nstream\n`, draw, "endstream");
  });
  const crossReference = length;
  append(`xref\n0 ${offsets.length}\n0000000000 65535 f \n`);
  for (const offset of offsets.slice(1)) append(`${String(offset).padStart(10, "0")} 00000 n \n`);
  append(`trailer\n<< /Size ${offsets.length} /Root 1 0 R >>\nstartxref\n${crossReference}\n%%EOF\n`);
  return chunks;
}

async function jpegBytes(canvas) {
  const blob = await new Promise((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.92));
  if (!blob || blob.type !== "image/jpeg") throw new Error("Receipt image could not be generated");
  return new Uint8Array(await blob.arrayBuffer());
}

export async function createBillPdf(bill) {
  if (!Number.isSafeInteger(bill.billNumber) || bill.billNumber <= 0) {
    throw new TypeError("A bill number is required");
  }
  // Wait for the bundled font before measuring and painting the shaped Devanagari text.
  await Promise.allSettled([400, 700].map((weight) =>
    document.fonts?.load(`${weight} 23px "Quick Bill Devanagari"`, "जम्मा प्यान मिति")));
  const canvas = document.createElement("canvas");
  const context = canvas.getContext("2d", { alpha: false });
  if (!context) throw new Error("Receipt preview is unavailable");
  const pages = pageRows(context, receiptParts(bill));
  const images = [];
  for (const page of pages) {
    drawPage(canvas, page);
    images.push({ bytes: await jpegBytes(canvas), height: page.height });
  }
  return new File(encodeRasterPdf(images), `QuickBill-${bill.billNumber}.pdf`, { type: "application/pdf" });
}

function downloadFile(blob, filename) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.hidden = true;
  document.body.append(anchor);
  try {
    anchor.click();
  } finally {
    anchor.remove();
    // Give mobile browsers time to hand the object URL to their download or preview UI.
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
  }
  return "downloaded";
}

export async function tryShareText(text, title) {
  if (typeof navigator.share === "function") {
    try {
      await navigator.share({ title, text });
      return "shared";
    } catch (error) {
      if (error?.name === "AbortError") return "cancelled";
    }
  }
  if (typeof navigator.clipboard?.writeText === "function") {
    try {
      await navigator.clipboard.writeText(text);
      return "copied";
    } catch {
      // Downloads also work when clipboard access is unavailable.
    }
  }
  try {
    return downloadFile(new Blob([text], { type: "text/plain;charset=utf-8" }), "QuickBill.txt");
  } catch {
    return "unavailable";
  }
}

export async function sharePdf(file) {
  if (!file) return "unavailable";
  const share = { title: "Share PDF / PDF सेयर गर्नुहोस्", files: [file] };
  if (typeof navigator.share === "function" && typeof navigator.canShare === "function") {
    try {
      if (navigator.canShare(share)) {
        await navigator.share(share);
        return "shared";
      }
    } catch (error) {
      if (error?.name === "AbortError") return "cancelled";
    }
  }
  try {
    return downloadFile(file, file.name);
  } catch {
    return "unavailable";
  }
}
