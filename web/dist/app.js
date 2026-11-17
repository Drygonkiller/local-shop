import {
  readState, saveShop, saveFavourite, deleteFavourite, saveBill, saveCustomer, deleteBill,
} from "./store.js";
import {
  asciiDigits, calculatorLines, calculateChange, customerLedger, formatMoney, MAX_QUANTITY,
  moneyInput, parseMoney, sumLines,
} from "./money.js";
import { createBillPdf, formatBillText, renderReceipt, sharePdf, tryShareText } from "./receipt.js";
import { renderManagement } from "./manage.js";

const main = document.querySelector("#view");
const dialog = document.querySelector("#dialog");
const dialogBody = document.querySelector("#dialog-body");
const notice = document.querySelector("#notice");
const installButton = document.querySelector("#install-button");
const connection = document.querySelector("#connection");
const routes = new Set(["favourites", "calculator", "today", "khata", "history", "shop", "bill"]);
const draft = new Map();
const paidCustomerNames = { favourites: "", calculator: "" };
let calculator = { amounts: [], entry: "" };
let state;
let favouriteQuery = "";
let cash = "";
let showCash = false;
let checkoutPending = false;
let noticeTimer;
let installPrompt;
let returnView = "favourites";

function element(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text != null) node.textContent = text;
  return node;
}

function bilingual(tag, en, ne, className) {
  const node = element(tag, className);
  const translation = element("small", "", ne);
  translation.lang = "ne";
  node.append(document.createTextNode(en), translation);
  return node;
}

function button(en, ne, onClick, type = "") {
  const node = bilingual("button", en, ne, ("button " + type).trim());
  node.type = "button";
  if (onClick) node.addEventListener("click", onClick);
  return node;
}

function blockTitle(en, ne) {
  const row = element("div", "heading");
  row.append(bilingual("h1", en, ne));
  return row;
}

function field(en, ne, options = {}) {
  const label = element("label");
  const input = element("input");
  Object.assign(input, options);
  label.append(bilingual("span", en, ne), input);
  return { label, input };
}

function showDialog(en, ne, content) {
  const title = bilingual("h2", en, ne);
  title.id = "dialog-title";
  dialogBody.replaceChildren(title, content);
  if (!dialog.open) dialog.showModal();
}

function openForm(en, ne, contents, submitLabel, submitNe, submit) {
  const form = element("form", "form");
  const error = element("p");
  error.role = "alert";
  error.hidden = true;
  const actions = element("div", "button-row");
  const cancel = button("Cancel", "रद्द गर्नुहोस्", () => dialog.close());
  const save = button(submitLabel, submitNe, null, "primary");
  save.type = "submit";
  actions.append(cancel, save);
  form.append(...contents, error, actions);
  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    if (save.disabled) return;
    error.hidden = true;
    save.disabled = cancel.disabled = true;
    try {
      await submit();
      if (form.isConnected && dialog.open) dialog.close();
    } catch (failure) {
      error.textContent = failure.message || "Could not save. / सुरक्षित गर्न सकिएन।";
      error.hidden = false;
    } finally {
      save.disabled = cancel.disabled = false;
    }
  });
  showDialog(en, ne, form);
}

function confirmAction(en, ne, description, label, labelNe, action) {
  openForm(en, ne, [element("p", "", description)], label, labelNe, action);
}

function notify(message, isError = false) {
  notice.textContent = message;
  notice.classList.toggle("error", isError);
  notice.hidden = false;
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => { notice.hidden = true; }, 3900);
}

function shareResult(result) {
  if (result === "copied") notify("Copied to clipboard. / प्रतिलिपि बनाइयो।");
  if (result === "downloaded") notify("File ready. / फाइल तयार भयो।");
  if (result === "unavailable") notify("Sharing is unavailable. / साझा गर्ने विकल्प उपलब्ध छैन।", true);
}

function route() {
  const [name, id] = location.hash.slice(1).split("/");
  const billNumber = Number(id);
  if (name === "bill" && Number.isSafeInteger(billNumber) && billNumber > 0) return { name, billNumber };
  return { name: routes.has(name) && name !== "bill" ? name : "favourites" };
}

function go(name, billNumber) {
  const previous = route().name;
  if (name === "bill" && previous !== "bill") returnView = previous;
  const hash = name === "bill" ? "#bill/" + billNumber : "#" + name;
  if (location.hash !== hash) history.pushState(null, "", hash);
  render();
  window.scrollTo(0, 0);
  main.focus({ preventScroll: true });
}

async function refresh() {
  state = await readState();
  render();
}

function shopRequired(action) {
  if (state.shop) { action(); return; }
  const name = field("Shop name", "पसलको नाम", { required: true, maxLength: 120, autocomplete: "organization" });
  const pan = field("PAN number", "प्यान नम्बर", { required: true, inputMode: "numeric", maxLength: 9 });
  openForm("Shop details", "पसलको विवरण", [name.label, pan.label], "Save and continue", "सुरक्षित गरी अगाडि बढ्नुहोस्", async () => {
    state.shop = await saveShop({ name: name.input.value, pan: pan.input.value });
    dialog.close();
    action();
  });
}

function imageData(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      try {
        const scale = Math.min(1, 480 / Math.max(image.naturalWidth, image.naturalHeight));
        const canvas = document.createElement("canvas");
        canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
        canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
        const ctx = canvas.getContext("2d", { alpha: false });
        ctx.fillStyle = "#ffffff";
        ctx.fillRect(0, 0, canvas.width, canvas.height);
        ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL("image/jpeg", .82));
      } catch (error) { reject(error); }
      finally { URL.revokeObjectURL(url); }
    };
    image.onerror = () => { URL.revokeObjectURL(url); reject(new Error("Invalid image")); };
    image.src = url;
  });
}

function itemEditor(item) {
  const name = field("Item name", "सामानको नाम", { value: item?.name ?? "", required: true, maxLength: 120 });
  const price = field("Price (Rs.)", "मूल्य (रु.)", {
    value: item ? moneyInput(item.unitPricePaisa) : "", inputMode: "decimal", required: true, maxLength: 16,
  });
  const contents = [name.label, price.label];
  let photo = item?.photo ?? null;
  let loadingImage = false;
  if (item) {
    const photoActions = element("div", "button-row");
    const photoStatus = element("small", "", photo ? "Photo saved / फोटो सुरक्षित छ" : "Photo optional / फोटो वैकल्पिक");
    for (const source of ["camera", "gallery"]) {
      const input = element("input");
      input.type = "file";
      input.accept = "image/*";
      input.hidden = true;
      if (source === "camera") input.setAttribute("capture", "environment");
      const labels = source === "camera" ? ["Take photo", "फोटो खिच्नुहोस्"] : ["Gallery", "ग्यालरी"];
      photoActions.append(input, button(...labels, () => input.click()));
      input.addEventListener("change", async () => {
        const file = input.files[0];
        if (!file) return;
        loadingImage = true;
        try {
          photo = await imageData(file);
          photoStatus.textContent = "Photo selected / फोटो छानियो";
        } catch {
          notify("Could not open this photo. / यो फोटो खोल्न सकिएन।", true);
        } finally { loadingImage = false; }
      });
    }
    photoActions.append(button("Remove photo", "फोटो हटाउनुहोस्", () => {
      photo = null;
      photoStatus.textContent = "Photo removed / फोटो हटाइयो";
    }));
    contents.push(photoActions, photoStatus, button("Delete item", "सामान मेटाउनुहोस्", () => {
      confirmAction("Delete this item?", "यो सामान मेटाउने?", item.name, "Delete item", "सामान मेटाउनुहोस्", async () => {
        await deleteFavourite(item.id);
        await refresh();
      });
    }, "danger"));
  }
  openForm(item ? "Edit item" : "Add item", item ? "सामान सच्याउनुहोस्" : "सामान थप्नुहोस्",
    contents, "Save item", "सामान सुरक्षित गर्नुहोस्", async () => {
      if (loadingImage) throw new Error("Photo is loading. / फोटो खुल्दैछ।");
      const unitPricePaisa = parseMoney(price.input.value);
      if (unitPricePaisa == null) throw new Error("Enter a valid price. / मान्य मूल्य लेख्नुहोस्।");
      const saved = await saveFavourite({ id: item?.id, name: name.input.value, unitPricePaisa, photo });
      const line = draft.get(saved.id);
      if (line) draft.set(saved.id, { ...line, name: saved.name, unitPricePaisa: saved.unitPricePaisa });
      await refresh();
    });
}

function favouriteLines() {
  return [...draft.values()];
}

function calcLines() {
  const amount = parseMoney(calculator.entry);
  const amounts = calculator.amounts.map((line) => line.amount);
  if (amount != null) amounts.push(amount);
  return calculatorLines(amounts);
}

function addItem(item, delta = 1) {
  const existing = draft.get(item.id);
  const quantity = (existing?.quantity ?? 0) + delta;
  if (quantity < 1) draft.delete(item.id);
  else if (quantity <= MAX_QUANTITY) {
    draft.set(item.id, {
      favouriteId: item.id, name: item.name, unitPricePaisa: item.unitPricePaisa, quantity,
    });
    try { sumLines(favouriteLines()); } catch (error) {
      existing ? draft.set(item.id, existing) : draft.delete(item.id);
      notify(error.message, true);
    }
  }
}

function renderFavourites() {
  const workspace = element("div", "workspace");
  const catalog = element("section", "panel catalog");
  const title = blockTitle("Favourites", "मनपर्ने सामान");
  title.append(button("Add item", "सामान थप्नुहोस्", () => itemEditor(), "primary"));
  const search = element("input");
  search.type = "search";
  search.value = favouriteQuery;
  search.placeholder = "Search items / सामान खोज्नुहोस्";
  search.setAttribute("aria-label", search.placeholder);
  const searchBox = element("div", "search-box");
  const grid = element("div", "item-grid");
  const cart = element("aside", "panel cart");
  function showItems() {
    const query = favouriteQuery.trim().toLocaleLowerCase();
    const matches = state.favourites.filter((item) => item.name.toLocaleLowerCase().includes(query));
    if (!matches.length) {
      const empty = element("div", "empty");
      if (state.favourites.length) empty.append(bilingual("strong", "No matching items", "मिल्ने सामान भेटिएन"));
      else empty.append(bilingual("strong", "Add your first item", "पहिलो सामान थप्नुहोस्"));
      grid.replaceChildren(empty);
      grid.classList.add("grid-empty");
      return;
    }
    grid.classList.remove("grid-empty");
    const tiles = matches.map((item) => {
      const tile = element("div", "item-tile");
      const add = element("button", "item-add");
      add.type = "button";
      if (item.photo) {
        const photo = element("img", "item-photo");
        photo.src = item.photo;
        photo.alt = "";
        add.append(photo);
      }
      add.append(element("strong", "", item.name), element("span", "item-price", formatMoney(item.unitPricePaisa)));
      add.addEventListener("click", () => { addItem(item); drawCart(); });
      add.addEventListener("contextmenu", (event) => { event.preventDefault(); itemEditor(item); });
      const manage = element("button", "item-more", "Edit / सच्याउनुहोस्");
      manage.type = "button";
      manage.setAttribute("aria-label", "Edit / सच्याउनुहोस् " + item.name);
      manage.addEventListener("click", () => itemEditor(item));
      tile.append(add, manage);
      return tile;
    });
    grid.replaceChildren(...tiles);
  }
  function drawCart() { renderCart(cart, "favourites", drawCart); }
  search.addEventListener("input", () => { favouriteQuery = search.value; showItems(); });
  searchBox.append(search, button("Clear", "मेटाउनुहोस्", () => {
    search.value = favouriteQuery = "";
    showItems();
    search.focus();
  }, "compact"));
  catalog.append(title, searchBox, grid);
  workspace.append(catalog, cart);
  main.append(workspace);
  showItems();
  drawCart();
}

function renderCalculator() {
  const workspace = element("div", "workspace");
  const controls = element("section", "panel calculator");
  controls.append(blockTitle("Calculator", "क्याल्कुलेटर"));
  const display = element("div", "calc-display");
  display.setAttribute("aria-live", "polite");
  const grid = element("div", "calc-grid");
  const cart = element("aside", "panel cart");
  function draw() {
    const entry = calculator.entry.startsWith(".") ? "0" + calculator.entry : calculator.entry;
    display.replaceChildren(
      element("span", "", calculator.amounts.length ? calculator.amounts.map(({ amount }) => moneyInput(amount)).join(" + ") + " +" : "Amount / रकम"),
      element("strong", "", entry || "0"),
    );
    renderCart(cart, "calculator", draw);
  }
  const keys = [
    "7", "8", "9", ["+", "Add amount / रकम जोड्नुहोस्"], "4", "5", "6", ["⌫", "Backspace / पछाडि मेटाउनुहोस्"],
    "1", "2", "3", ["Clear", "Clear / खाली गर्नुहोस्"], ".", "0", "00",
  ];
  for (const key of keys) {
    const [value, label] = Array.isArray(key) ? key : [key, key];
    const control = element("button", "button" + (Array.isArray(key) ? " op" : ""), value);
    control.type = "button";
    control.setAttribute("aria-label", label);
    control.addEventListener("click", () => { calculatorKey(value); draw(); });
    grid.append(control);
  }
  controls.append(display, grid);
  workspace.append(controls, cart);
  main.append(workspace);
  draw();
}

function calculatorKey(key) {
  const { amounts, entry } = calculator;
  if (key === "Clear") {
    calculator = { amounts: [], entry: "" };
    paidCustomerNames.calculator = "";
  } else if (key === "⌫") {
    if (entry) calculator.entry = entry.slice(0, -1);
    else calculator.entry = amounts.pop()?.entry ?? "";
  } else if (key === "+") {
    const amount = parseMoney(entry);
    if (amount != null) { amounts.push({ entry, amount }); calculator.entry = ""; }
  } else if (key === ".") {
    if (!entry.includes(".")) calculator.entry = entry + ".";
  } else {
    const next = (entry === "0" ? "" : entry) + key;
    const amount = parseMoney(next);
    if (amount != null && Number.isSafeInteger(amounts.reduce((total, line) => total + line.amount, 0) + amount)) {
      calculator.entry = next;
    }
  }
}

function renderCart(cart, mode, draw) {
  const favouriteMode = mode === "favourites";
  const lines = favouriteMode ? favouriteLines() : calcLines();
  let total;
  try { total = sumLines(lines); } catch {
    cart.replaceChildren(bilingual("p", "Bill exceeds the amount limit.", "बिलको रकम सीमा नाघ्यो।"));
    return;
  }
  const actions = element("div", "button-row quick-checkout");
  const print = button("Print bill", "बिल छाप्नुहोस्", () => shopRequired(() => checkout(lines, mode)), "primary");
  const credit = button("Udharo", "उधारो", () => shopRequired(() => chooseCustomer(lines, mode)), "outline");
  const isEmpty = lines.length === 0;
  print.disabled = credit.disabled = isEmpty || checkoutPending;
  actions.append(credit, print);
  const list = element("div", "cart-lines");
  if (isEmpty) list.append(bilingual("p", favouriteMode ? "Tap an item to start." : "Enter an amount to start.",
    favouriteMode ? "सुरु गर्न सामान थिच्नुहोस्।" : "सुरु गर्न रकम लेख्नुहोस्।", "cart-empty"));
  else for (const line of lines) {
    const row = element("div", "cart-line");
    const info = element("div");
    info.append(element("strong", "", line.name), element("small", "", formatMoney(line.unitPricePaisa)));
    row.append(info);
    if (favouriteMode) {
      const qty = element("div", "qty");
      const item = { id: line.favouriteId, name: line.name, unitPricePaisa: line.unitPricePaisa };
      for (const delta of [-1, 1]) {
        const adjust = element("button", "button", delta === -1 ? "−" : "+");
        adjust.type = "button";
        adjust.setAttribute("aria-label", (delta === -1 ? "Decrease / घटाउनुहोस् " : "Increase / बढाउनुहोस् ") + line.name);
        adjust.addEventListener("click", () => { addItem(item, delta); draw(); });
        if (delta === 1) qty.append(element("span", "", line.quantity));
        qty.append(adjust);
      }
      row.append(qty);
    }
    list.append(row);
  }
  const summary = element("div", "cart-total");
  const viewBill = button("View bill", "बिल हेर्नुहोस्", () => cart.scrollIntoView({ block: "start" }), "view-bill");
  viewBill.hidden = isEmpty;
  summary.append(bilingual("span", "Total", "जम्मा"), element("strong", "", formatMoney(total)), viewBill);
  const subtitle = bilingual("h2", "Current bill", "हालको बिल");
  const customerName = field("Customer name (optional)", "ग्राहकको नाम (वैकल्पिक)", {
    value: paidCustomerNames[mode], autocomplete: "off", maxLength: 120,
  });
  customerName.label.className = "bill-customer";
  customerName.input.addEventListener("input", () => { paidCustomerNames[mode] = customerName.input.value; });
  cart.replaceChildren(subtitle, customerName.label, list);
  if (!isEmpty) {
    if (favouriteMode) cart.append(cashHelper(total));
    const tools = element("div", "button-row");
    if (favouriteMode) tools.append(button("Clear bill", "बिल खाली गर्नुहोस्", () => {
      confirmAction("Clear this bill?", "यो बिल खाली गर्ने?", "", "Clear bill", "बिल खाली गर्नुहोस्", () => {
        draft.clear(); paidCustomerNames.favourites = ""; cash = ""; showCash = false; draw();
      });
    }));
    tools.append(button("Share draft", "मस्यौदा सेयर", async () => {
      const preview = {
        shopName: state.shop?.name, shopPan: state.shop?.pan, lines,
        totalPaisa: total, createdAtMillis: Date.now(),
        customerName: paidCustomerNames[mode],
      };
      shareResult(await tryShareText(formatBillText(preview), "Share bill / बिल साझा गर्नुहोस्"));
    }));
    cart.append(tools);
  }
  const dock = element("div", "checkout-dock");
  dock.append(summary, actions);
  cart.append(dock);
}

function cashHelper(total) {
  const details = element("details", "cash-helper");
  details.open = showCash;
  details.addEventListener("toggle", () => { showCash = details.open; });
  const amount = field("Cash received (Rs.)", "प्राप्त नगद (रु.)", { value: cash, inputMode: "decimal" });
  const difference = element("p", "change-value");
  difference.setAttribute("aria-live", "polite");
  function showChange() {
    cash = amount.input.value;
    const change = calculateChange(total, parseMoney(cash, Number.MAX_SAFE_INTEGER));
    difference.hidden = change == null;
    if (change != null) difference.textContent = "Change / फिर्ता: " + formatMoney(change);
  }
  amount.input.addEventListener("input", showChange);
  const suggestions = element("div", "cash-options");
  for (const rupees of [100, 200, 500, 1000]) {
    const control = element("button", "button", "Rs. " + rupees);
    control.type = "button";
    control.addEventListener("click", () => { amount.input.value = String(rupees); showChange(); });
    suggestions.append(control);
  }
  details.append(element("summary", "", "Cash received / प्राप्त नगद"), suggestions, amount.label, difference);
  showChange();
  return details;
}

function chooseCustomer(lines, mode) {
  const body = element("div", "form");
  const search = field("Find customer", "ग्राहक खोज्नुहोस्", { type: "search" });
  const choices = element("div", "choices");
  const customers = state.customers.map((customer) => ({
    ...customer, ledger: customerLedger(customer.id, state.bills, state.payments),
  })).sort((a, b) => {
    const dueDifference = Number(b.ledger.duePaisa > 0n) - Number(a.ledger.duePaisa > 0n);
    return dueDifference || (b.ledger.entries[0]?.createdAtMillis ?? 0) - (a.ledger.entries[0]?.createdAtMillis ?? 0) || b.id - a.id;
  });
  function draw() {
    const query = search.input.value.trim().toLocaleLowerCase();
    choices.replaceChildren(...customers.filter(({ name, phone }) => (name + " " + (phone ?? "")).toLocaleLowerCase().includes(query))
      .map((customer) => button(customer.name, customer.ledger.duePaisa > 0n ? "Due / बाँकी " + formatMoney(customer.ledger.duePaisa) : "खाता",
        () => { dialog.close(); checkout(lines, mode, customer.id); })));
  }
  search.input.addEventListener("input", draw);
  const add = button("New customer", "नयाँ ग्राहक", () => {
    const name = field("Customer name", "ग्राहकको नाम", { required: true, maxLength: 120 });
    const phone = field("Phone (optional)", "फोन (वैकल्पिक)", { type: "tel", maxLength: 40 });
    openForm("New customer", "नयाँ ग्राहक", [name.label, phone.label], "Save and prepare bill", "सुरक्षित गरी बिल बनाउनुहोस्", async () => {
      const customer = await saveCustomer({ name: name.input.value, phone: phone.input.value });
      dialog.close();
      await checkout(lines, mode, customer.id);
    });
  }, "primary");
  body.append(search.label, choices, add, button("Cancel", "रद्द गर्नुहोस्", () => dialog.close()));
  showDialog("Udharo", "उधारो", body);
  draw();
}

async function checkout(lines, mode, customerId = null) {
  if (checkoutPending || lines.length === 0) return;
  checkoutPending = true;
  main.querySelectorAll(".quick-checkout button").forEach((control) => { control.disabled = true; });
  try {
    const bill = await saveBill(lines, customerId, paidCustomerNames[mode]);
    paidCustomerNames[mode] = "";
    if (mode === "favourites") { draft.clear(); cash = ""; showCash = false; }
    else calculator = { amounts: [], entry: "" };
    state.bills.unshift(bill);
    // Keep the saved receipt available even if a later refresh cannot read storage.
    try { state = await readState(); } catch { /* The sale has already been saved. */ }
    go("bill", bill.billNumber);
  } catch (error) {
    notify(error.message || "Could not save bill. / बिल सुरक्षित गर्न सकिएन।", true);
  } finally {
    checkoutPending = false;
    if (route().name !== "bill") render();
  }
}

function printReceipt() {
  document.body.dataset.print = "bill";
  window.print();
}

function showReceipt(number) {
  const bill = state.bills.find((entry) => entry.billNumber === number);
  const view = element("section", "receipt-view");
  const header = blockTitle("Bill preview", "बिलको पूर्वावलोकन");
  header.append(button("Back", "पछाडि", () => go(returnView), "outline"));
  view.append(header);
  if (!bill) {
    view.append(bilingual("p", "This bill is unavailable.", "यो बिल उपलब्ध छैन।", "empty"));
    main.append(view);
    return;
  }
  const toolbar = element("div", "receipt-toolbar");
  const actions = element("div", "button-row");
  let file;
  const share = button("Share PDF", "PDF सेयर गर्नुहोस्", async () => shareResult(await sharePdf(file)), "primary");
  share.disabled = true;
  actions.append(share, button("Print bill", "बिल छाप्नुहोस्", printReceipt, "outline"));
  const more = element("div", "button-row receipt-more");
  more.append(button("Share text", "बिल साझा गर्नुहोस्", async () => {
    shareResult(await tryShareText(formatBillText(bill), "Share bill / बिल साझा गर्नुहोस्"));
  }), button("New bill", "नयाँ बिल", () => {
    draft.clear();
    calculator = { amounts: [], entry: "" };
    paidCustomerNames.favourites = paidCustomerNames.calculator = "";
    cash = ""; showCash = false;
    go("favourites");
  }), button("Delete bill", "बिल मेटाउनुहोस्", () => {
    confirmAction("Delete this bill?", "यो बिल मेटाउने?",
      "This removes the bill from History and Today. / यो बिल इतिहास र आजको रिपोर्टबाट हट्नेछ।",
      "Delete bill", "बिल मेटाउनुहोस्", async () => { await deleteBill(number); await refresh(); go("history"); });
  }, "danger"));
  toolbar.append(actions);
  view.append(toolbar, renderReceipt(bill), more);
  main.append(view);
  createBillPdf(bill).then((pdf) => {
    file = pdf;
    if (share.isConnected) share.disabled = false;
  }).catch(() => {
    if (!share.isConnected) return;
    share.title = "PDF unavailable. Use Share text. / PDF उपलब्ध छैन। बिल साझा गर्नुहोस्।";
    notify(share.title, true);
  });
}

function render() {
  if (!state) return;
  const current = route();
  main.classList.toggle("checkout-view", current.name === "favourites" || current.name === "calculator");
  for (const control of document.querySelectorAll(".tab, .tool-button[data-view]")) {
    if (control.dataset.view === current.name) control.setAttribute("aria-current", "page");
    else control.removeAttribute("aria-current");
  }
  main.replaceChildren();
  if (current.name === "favourites") renderFavourites();
  else if (current.name === "calculator") renderCalculator();
  else if (current.name === "bill") showReceipt(current.billNumber);
  else renderManagement(main, current.name, { state, refresh, go, openBill: (number) => go("bill", number), notify });
}

function retryStorage() {
  refresh().catch((error) => {
    main.replaceChildren(
      element("p", "loading", error.message),
      button("Try again", "फेरि प्रयास गर्नुहोस्", retryStorage, "primary"),
    );
  });
}

async function install() {
  if (installPrompt) {
    const prompt = installPrompt;
    installPrompt = undefined;
    await prompt.prompt();
    return;
  }
  const choices = element("div", "form");
  choices.append(
    bilingual("p", "iPhone · In Safari, tap Share, then Add to Home Screen.", "iPhone · Safari मा Share थिचेर Add to Home Screen छान्नुहोस्।"),
    bilingual("p", "Android · In Chrome, open the menu and choose Install app or Add to Home screen.", "Android · Chrome को मेनुमा Install app वा Add to Home screen छान्नुहोस्।"),
    button("Done", "भयो", () => dialog.close(), "primary"),
  );
  showDialog("Install Quick Bill", "छिटो बिल स्थापना गर्नुहोस्", choices);
}

function updateConnection() {
  connection.textContent = navigator.serviceWorker?.controller
    ? "Offline ready / अफलाइन तयार" : navigator.onLine ? "" : "Offline / अफलाइन";
}

installButton.hidden = matchMedia("(display-mode: standalone)").matches || navigator.standalone === true;
installButton.addEventListener("click", install);
window.addEventListener("beforeinstallprompt", (event) => { event.preventDefault(); installPrompt = event; });
window.addEventListener("appinstalled", () => { installButton.hidden = true; });
for (const nav of document.querySelectorAll("[data-view]")) nav.addEventListener("click", () => go(nav.dataset.view));
window.addEventListener("popstate", render);
window.addEventListener("afterprint", () => { delete document.body.dataset.print; });
window.addEventListener("online", updateConnection);
window.addEventListener("offline", updateConnection);
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.addEventListener("controllerchange", updateConnection);
  navigator.serviceWorker.register("./sw.js").then(updateConnection).catch(() => { updateConnection(); });
}
retryStorage();
