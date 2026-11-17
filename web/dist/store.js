import { asciiDigits, customerLedger, MAX_PRICE_PAISA, sumLines } from "./money.js";

const DATABASE_NAME = "quick-bill";
const DATABASE_VERSION = 1;
const SHOP_KEY = "shop";
const ALL_STORES = ["settings", "favourites", "bills", "customers", "payments"];
const STORAGE_UNAVAILABLE = "Could not open local storage. / स्थानीय भण्डारण खोल्न सकिएन।";
const STORAGE_WRITE_FAILED = "Could not save your changes on this device. / यो उपकरणमा परिवर्तन सुरक्षित गर्न सकिएन।";
let databasePromise;

function openDatabase() {
  if (!databasePromise) {
    databasePromise = new Promise((resolve, reject) => {
      const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
      request.onupgradeneeded = () => {
        const database = request.result;
        database.createObjectStore("settings");
        database.createObjectStore("favourites", { keyPath: "id", autoIncrement: true });
        database.createObjectStore("customers", { keyPath: "id", autoIncrement: true });
        const bills = database.createObjectStore("bills", { keyPath: "billNumber", autoIncrement: true });
        bills.createIndex("byCustomer", "customerId");
        const payments = database.createObjectStore("payments", { keyPath: "id", autoIncrement: true });
        payments.createIndex("byCustomer", "customerId");
      };
      request.onsuccess = () => {
        const database = request.result;
        database.onversionchange = () => {
          database.close();
          databasePromise = undefined;
        };
        resolve(database);
      };
      request.onerror = () => reject(request.error);
    }).catch((error) => {
      databasePromise = undefined;
      throw new Error(STORAGE_UNAVAILABLE, { cause: error });
    });
  }
  return databasePromise;
}

async function transact(stores, mode, action) {
  const database = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(stores, mode);
    let value;
    let failure;
    const finish = (result) => { value = result; };
    const abort = (error) => {
      failure = error;
      transaction.abort();
    };

    transaction.oncomplete = () => resolve(value);
    transaction.onabort = () => reject(failure ?? new Error(
      mode === "readonly" ? STORAGE_UNAVAILABLE : STORAGE_WRITE_FAILED,
      { cause: transaction.error },
    ));

    // Keep dependent writes inside request callbacks so the transaction stays active in WebKit.
    function read(requests, callback) {
      const entries = Object.entries(requests);
      const results = {};
      let remaining = entries.length;
      for (const [key, request] of entries) {
        request.onsuccess = () => {
          results[key] = request.result;
          if (--remaining === 0) {
            try {
              callback(results);
            } catch (error) {
              abort(error);
            }
          }
        };
      }
    }

    try {
      action(transaction, read, finish);
    } catch (error) {
      abort(error);
    }
  });
}

export function readState() {
  return transact(ALL_STORES, "readonly", (transaction, read, finish) => {
    read({
      shop: transaction.objectStore("settings").get(SHOP_KEY),
      favourites: transaction.objectStore("favourites").getAll(),
      bills: transaction.objectStore("bills").getAll(),
      customers: transaction.objectStore("customers").getAll(),
      payments: transaction.objectStore("payments").getAll(),
    }, (state) => {
      state.shop ??= null;
      state.favourites.sort((a, b) => b.timesUsed - a.timesUsed || a.id - b.id);
      state.bills.sort((a, b) => b.billNumber - a.billNumber);
      state.payments.sort((a, b) => b.createdAtMillis - a.createdAtMillis || b.id - a.id);
      finish(state);
    });
  });
}

export async function saveShop({ name, pan }) {
  const shop = {
    name: nonemptyName(name, "Enter a shop name. / पसलको नाम लेख्नुहोस्।"),
    pan: asciiDigits(pan).trim(),
  };
  if (!/^[0-9]{9}$/.test(shop.pan)) throw new Error("PAN must have nine digits. / प्यानमा नौ अङ्क हुनुपर्छ।");
  return transact(["settings"], "readwrite", (transaction, _read, finish) => {
    transaction.objectStore("settings").put(shop, SHOP_KEY);
    finish(shop);
  });
}

export async function saveFavourite({ id, name, unitPricePaisa, photo }) {
  name = nonemptyName(name, "Enter an item name. / सामानको नाम लेख्नुहोस्।");
  if (!isPaisa(unitPricePaisa) || unitPricePaisa > MAX_PRICE_PAISA) {
    throw new RangeError("Enter a valid price. / मान्य मूल्य लेख्नुहोस्।");
  }
  if (photo != null && (typeof photo !== "string" || !/^data:image\/[\w.+-]+[;,]/i.test(photo))) {
    throw new Error("Choose an image file. / तस्बिर छान्नुहोस्।");
  }
  if (id != null) requireId(id);

  return transact(["favourites"], "readwrite", (transaction, read, finish) => {
    const store = transaction.objectStore("favourites");
    const fields = { name, unitPricePaisa };
    if (id == null) {
      const item = { ...fields, timesUsed: 0, photo: photo ?? null };
      read({ key: store.add(item) }, ({ key }) => finish({ ...item, id: key }));
      return;
    }
    read({ previous: store.get(id) }, ({ previous }) => {
      if (!previous) throw new Error("This item was removed. / यो सामान हटाइएको छ।");
      const item = { ...previous, ...fields, photo: photo === undefined ? previous.photo : photo };
      store.put(item);
      finish(item);
    });
  });
}

export async function deleteFavourite(id) {
  requireId(id);
  return transact(["favourites"], "readwrite", (transaction) => {
    transaction.objectStore("favourites").delete(id);
  });
}

export async function saveCustomer({ id, name, phone }) {
  const details = {
    name: nonemptyName(name, "Enter a customer name. / ग्राहकको नाम लेख्नुहोस्।"),
    phone: String(phone ?? "").trim() || null,
  };
  if (id != null) requireId(id);
  return transact(["customers"], "readwrite", (transaction, read, finish) => {
    const store = transaction.objectStore("customers");
    if (id == null) {
      read({ key: store.add(details) }, ({ key }) => finish({ ...details, id: key }));
      return;
    }
    read({ previous: store.get(id) }, ({ previous }) => {
      if (!previous) throw new Error("Customer unavailable. / ग्राहक उपलब्ध छैन।");
      const customer = { id, ...details };
      store.put(customer);
      finish(customer);
    });
  });
}

export async function saveBill(lines, customerId = null, customerName = "") {
  if (!lines.length) throw new Error("Add an item to the bill. / बिलमा सामान थप्नुहोस्।");
  if (customerId != null) requireId(customerId);
  const nameOnPaidBill = customerId == null ? String(customerName ?? "").trim().replace(/\s+/gu, " ") : "";
  if (nameOnPaidBill.length > 120) {
    throw new RangeError("Customer name must be 120 characters or fewer. / ग्राहकको नाम १२० अक्षरभित्र हुनुपर्छ।");
  }
  const snapshot = lines.map(({ favouriteId, name, unitPricePaisa, quantity }) => {
    if (favouriteId != null) requireId(favouriteId);
    return {
      favouriteId: favouriteId ?? null,
      name: nonemptyName(name, "Enter an item name. / सामानको नाम लेख्नुहोस्।"),
      unitPricePaisa,
      quantity,
    };
  });
  const totalPaisa = sumLines(snapshot);
  const usage = new Map();
  for (const { favouriteId, quantity } of snapshot) {
    if (favouriteId != null) usage.set(favouriteId, (usage.get(favouriteId) ?? 0) + quantity);
  }

  return transact(["settings", "customers", "favourites", "bills"], "readwrite", (transaction, read, finish) => {
    const favourites = transaction.objectStore("favourites");
    const requests = { shop: transaction.objectStore("settings").get(SHOP_KEY) };
    if (customerId != null) requests.customer = transaction.objectStore("customers").get(customerId);
    for (const id of usage.keys()) requests[id] = favourites.get(id);

    read(requests, (data) => {
      if (!data.shop) throw new Error("Enter shop details first. / पहिले पसलको विवरण भर्नुहोस्।");
      if (customerId != null && !data.customer) throw new Error("Customer unavailable. / ग्राहक उपलब्ध छैन।");
      const bill = {
        shopName: data.shop.name,
        shopPan: data.shop.pan,
        createdAtMillis: Date.now(),
        totalPaisa,
        customerId,
        customerName: data.customer?.name ?? (nameOnPaidBill || null),
        lines: snapshot,
      };
      for (const [id, quantity] of usage) {
        const item = data[id];
        if (item) favourites.put({ ...item, timesUsed: item.timesUsed + quantity });
      }
      // IndexedDB's key generator never reuses a key, even after clearing the store.
      read({ billNumber: transaction.objectStore("bills").add(bill) }, ({ billNumber }) => {
        finish({ ...bill, billNumber });
      });
    });
  });
}

export async function addPayment(customerId, amountPaisa) {
  requireId(customerId);
  if (!isPaisa(amountPaisa) || amountPaisa === 0) {
    throw new RangeError("Enter a payment above zero. / शून्यभन्दा बढी भुक्तानी लेख्नुहोस्।");
  }
  return transact(["customers", "bills", "payments"], "readwrite", (transaction, read, finish) => {
    const payments = transaction.objectStore("payments");
    read({
      customer: transaction.objectStore("customers").get(customerId),
      bills: transaction.objectStore("bills").index("byCustomer").getAll(customerId),
      payments: payments.index("byCustomer").getAll(customerId),
    }, (data) => {
      if (!data.customer) throw new Error("Customer unavailable. / ग्राहक उपलब्ध छैन।");
      if (BigInt(amountPaisa) > customerLedger(customerId, data.bills, data.payments).duePaisa) {
        throw new RangeError("Payment exceeds the balance due. / भुक्तानी बाँकी रकमभन्दा बढी छ।");
      }
      const payment = { customerId, amountPaisa, createdAtMillis: Date.now() };
      read({ id: payments.add(payment) }, ({ id }) => finish({ ...payment, id }));
    });
  });
}

export async function deleteBill(billNumber) {
  requireId(billNumber);
  return transact(["bills"], "readwrite", (transaction) => {
    transaction.objectStore("bills").delete(billNumber);
  });
}

export function clearBills() {
  return transact(["bills"], "readwrite", (transaction) => {
    transaction.objectStore("bills").clear();
  });
}

function isPaisa(value) {
  return Number.isSafeInteger(value) && value >= 0;
}

function requireId(id) {
  if (!Number.isSafeInteger(id) || id < 1) throw new Error("Record unavailable. / विवरण उपलब्ध छैन।");
}

function nonemptyName(value, message) {
  const name = String(value ?? "").trim();
  if (!name) throw new Error(message);
  return name;
}
