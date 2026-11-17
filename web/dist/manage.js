import { addPayment, clearBills, saveCustomer, saveShop } from './store.js';
import {
    asciiDigits,
    customerLedger,
    dailySales,
    formatMoney,
    moneyInput,
    nepalDayKey,
    parseMoney,
} from './money.js';
import {
    createBillPdf,
    formatBillText,
    formatNepalDate,
    formatNepalDateOnly,
    sharePdf,
    tryShareText,
} from './receipt.js';

let historyQuery = '';
let openedCustomerId = null;

function normalizeSearch(value) {
    return asciiDigits(value).normalize('NFC').trim().replace(/\s+/g, ' ').toLowerCase();
}

export function billMatchesQuery(bill, query) {
    const text = normalizeSearch(query);
    const number = /^(?:(?:bill(?: no\.)?|बिल नं\.) )?#? *(\d+)$/.exec(text)?.[1];
    return !text || (number != null && String(bill.billNumber).includes(number)) ||
        normalizeSearch(bill.customerName).includes(text);
}

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    return node;
}

function bilingual(tag, en, ne, className) {
    const node = el(tag, className);
    node.append(el('span', 'mg-lang-en', en), el('span', 'mg-lang-ne', ne));
    node.lastElementChild.lang = 'ne';
    return node;
}

function button(en, ne, action, variant = 'outline') {
    const node = bilingual('button', en, ne, 'button ' + variant + ' mg-button');
    node.type = 'button';
    if (action) node.addEventListener('click', action);
    return node;
}

function heading(en, ne, action) {
    const row = el('header', 'mg-heading');
    row.append(bilingual('h1', en, ne, 'mg-title'));
    if (action) row.append(action);
    return row;
}

function field(en, ne, value = '', attributes = {}) {
    const label = el('label', 'mg-field');
    const caption = bilingual('span', en, ne, 'mg-field-label');
    const input = el('input', 'mg-input');
    Object.assign(input, { value, ...attributes });
    label.append(caption, input);
    return { label, input };
}

function empty(en, ne) {
    return bilingual('p', en, ne, 'mg-empty');
}

function openForm(title, titleNe, fields, action, actionNe, submit, api, variant = 'primary') {
    const dialog = el('dialog', 'mg-dialog');
    const form = el('form', 'mg-form');
    const error = el('p', 'mg-error');
    error.setAttribute('role', 'alert');
    error.hidden = true;
    const cancel = button('Cancel', 'रद्द गर्नुहोस्', () => dialog.close());
    const confirm = button(action, actionNe, null, variant);
    confirm.type = 'submit';
    const footer = el('footer', 'mg-actions');
    footer.append(cancel, confirm);
    form.append(bilingual('h2', title, titleNe, 'mg-dialog-title'), ...fields, error, footer);
    dialog.append(form);
    document.body.append(dialog);
    dialog.addEventListener('close', () => dialog.remove(), { once: true });
    dialog.addEventListener('cancel', (event) => {
        if (confirm.disabled) event.preventDefault();
    });
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (confirm.disabled) return;
        confirm.disabled = true;
        cancel.disabled = true;
        error.hidden = true;
        try {
            await submit();
            dialog.close();
        } catch (failure) {
            error.textContent = failure?.message || 'Could not save. / सुरक्षित गर्न सकिएन।';
            error.hidden = false;
            api.notify?.(error.textContent, true);
        } finally {
            confirm.disabled = false;
            cancel.disabled = false;
        }
    });
    dialog.showModal();
    return dialog;
}

async function shareAction(action, api, control) {
    if (control.disabled) return;
    control.disabled = true;
    control.setAttribute('aria-busy', 'true');
    try {
        const result = await action();
        if (result === 'copied') api.notify('Copied to clipboard. / प्रतिलिपि बनाइयो।');
        if (result === 'downloaded') api.notify('Download ready. / डाउनलोड तयार भयो।');
        if (result === 'unavailable') api.notify('Sharing is unavailable. / साझा गर्ने विकल्प उपलब्ध छैन।', true);
    } catch {
        api.notify('Could not open sharing. / साझा गर्ने विकल्प खोल्न सकिएन।', true);
    } finally {
        control.disabled = false;
        control.removeAttribute('aria-busy');
    }
}

function billRecord(bill, api, shareActions = false) {
    const card = el('article', 'mg-record');
    const row = el('div', 'mg-record-row');
    const primary = el('div', 'mg-record-content');
    primary.append(
        el('strong', null, 'Bill #' + bill.billNumber + ' / बिल नं. ' + bill.billNumber),
        el('span', 'mg-meta', formatNepalDate(bill.createdAtMillis)),
    );
    if (bill.customerName) {
        const label = bill.customerId == null ? 'Customer / ग्राहक: ' : 'Udharo / उधारो: ';
        primary.append(el('span', 'mg-meta', label + bill.customerName));
    }
    row.append(primary, el('strong', 'mg-record-amount', formatMoney(bill.totalPaisa)));
    if (!api) {
        card.append(row);
        return card;
    }

    const open = el('button', 'mg-record-open');
    open.type = 'button';
    open.addEventListener('click', () => api.openBill(bill.billNumber));
    open.append(row);
    card.append(open);
    if (shareActions) {
        const actions = el('div', 'mg-actions mg-record-actions');
        const share = button('Share', 'साझा गर्नुहोस्', () => {
            shareAction(() => tryShareText(formatBillText(bill), 'Share bill / बिल साझा गर्नुहोस्'), api, share);
        });
        const pdf = button('Share PDF', 'PDF सेयर गर्नुहोस्', () => {
            shareAction(async () => sharePdf(await createBillPdf(bill)), api, pdf);
        });
        actions.append(share, pdf);
        card.append(actions);
    }
    return card;
}

function confirmClear(api) {
    openForm(
        'Clear history?', 'बिल इतिहास मेटाउने?',
        [bilingual('p', "This removes saved bills from History and today's report.", 'यसले सुरक्षित बिल इतिहास र आजको रिपोर्टबाट हटाउँछ।')],
        'Continue', 'अगाडि बढ्नुहोस्',
        () => queueMicrotask(() => openForm(
            'Delete ALL bills?', 'सबै बिल मेटाउने?',
            [bilingual('p', 'This cannot be undone.', 'फिर्ता हुँदैन।')],
            'Clear all', 'सबै मेटाउनुहोस्',
            async () => {
                await clearBills();
                historyQuery = '';
                await api.refresh();
            },
            api,
            'danger',
        )),
        api,
    );
}

function historyView(api) {
    const section = el('section', 'mg-view');
    const bills = [...api.state.bills].sort((a, b) => b.billNumber - a.billNumber);
    const clear = bills.length ? button('Clear all', 'सबै मेटाउनुहोस्', () => confirmClear(api), 'danger') : null;
    section.append(heading('History', 'बिलको इतिहास', clear));
    const search = field('Bill number or customer name', 'बिल नं. वा ग्राहकको नाम', historyQuery, { type: 'search' });
    const list = el('div', 'mg-list');
    let visible = 40;
    const draw = () => {
        const matches = bills.filter((bill) => billMatchesQuery(bill, historyQuery));
        list.replaceChildren(...matches.slice(0, visible).map((bill) => billRecord(bill, api, true)));
        if (!matches.length) {
            list.append(bills.length ? empty('No matching bills', 'मिल्ने बिल भेटिएन') : empty('No saved bills', 'सुरक्षित बिल छैन'));
        }
        if (matches.length > visible) {
            list.append(button('Show more', 'थप हेर्नुहोस्', () => {
                visible += 40;
                draw();
            }));
        }
    };
    search.input.addEventListener('input', () => {
        historyQuery = search.input.value;
        visible = 40;
        draw();
    });
    section.append(search.label, list);
    draw();
    return section;
}

function stat(en, ne, value, className = '') {
    const node = el('div', 'mg-stat ' + className);
    node.append(bilingual('span', en, ne, 'mg-stat-label'), el('strong', 'mg-stat-value', value));
    return node;
}

function reportContent(report, api) {
    const content = el('div', 'mg-report-content');
    const stats = el('div', 'mg-stats');
    stats.append(
        stat('Total sales', 'कुल बिक्री', formatMoney(report.totalPaisa), 'mg-stat-primary'),
        stat('Bills', 'बिल', report.billCount),
        stat('Average bill', 'औसत बिल', formatMoney(report.averagePaisa)),
        stat('Udharo today', 'आजको उधारो', formatMoney(report.udharoTotalPaisa), 'mg-stat-credit'),
    );
    stats.lastElementChild.append(el('span', 'mg-meta', report.udharoBillCount + ' bills / बिल'));
    content.append(stats);
    if (report.topItems.length) {
        const top = el('section', 'mg-card');
        top.append(bilingual('h2', 'Top items', 'धेरै बिक्री भएका सामान', 'mg-subtitle'));
        for (const item of report.topItems) {
            const row = el('div', 'mg-item-row');
            row.append(el('span', null, item.name), el('strong', null, formatMoney(item.totalPaisa)));
            top.append(row);
        }
        content.append(top);
    }
    const bills = el('section', 'mg-list');
    bills.append(bilingual('h2', "Today's bills", 'आजका बिल', 'mg-subtitle'));
    if (report.bills.length) bills.append(...report.bills.map((bill) => billRecord(bill, api)));
    else bills.append(empty('No bills today', 'आज बिल छैन'));
    content.append(bills);
    return content;
}

function printToday(report, date, shop) {
    document.getElementById('qb-print-report')?.remove();
    const sheet = el('section', 'mg-print-sheet');
    sheet.id = 'qb-print-report';
    if (shop) sheet.append(el('h2', null, shop.name), el('p', null, 'PAN / प्यान: ' + shop.pan));
    sheet.append(bilingual('h1', 'Daily sales', 'दैनिक बिक्री', 'mg-title'), el('p', null, date), reportContent(report));
    document.body.append(sheet);
    document.body.classList.add('printing-report');
    window.addEventListener('afterprint', () => {
        sheet.remove();
        document.body.classList.remove('printing-report');
    }, { once: true });
    window.print();
}

function todayView(api) {
    const today = Date.now();
    const date = formatNepalDateOnly(today);
    const report = dailySales(api.state.bills, nepalDayKey(today));
    const section = el('section', 'mg-view');
    section.append(
        heading('Today', 'आज', button('Print report', 'रिपोर्ट छाप्नुहोस्', () => printToday(report, date, api.state.shop))),
        el('p', 'mg-date', date),
        reportContent(report, api),
    );
    return section;
}

function customerEditor(existing, api) {
    const name = field('Name', 'नाम', existing?.name ?? '', { required: true, autocomplete: 'name' });
    const phone = field('Phone (optional)', 'फोन (ऐच्छिक)', existing?.phone ?? '', {
        type: 'tel', autocomplete: 'tel',
    });
    openForm(
        existing ? 'Edit customer' : 'New customer',
        existing ? 'ग्राहकको विवरण बदल्नुहोस्' : 'नयाँ ग्राहक',
        [name.label, phone.label],
        'Save', 'सुरक्षित गर्नुहोस्',
        async () => {
            const saved = await saveCustomer({ id: existing?.id, name: name.input.value, phone: phone.input.value });
            if (saved?.id != null) openedCustomerId = saved.id;
            await api.refresh();
        },
        api,
    );
}

function addPaymentDialog(customer, due, api) {
    const amount = field('Amount (Rs.)', 'रकम (रु.)', '', { inputMode: 'decimal', required: true });
    const full = button('Pay full', 'पूरा तिर्नुहोस्', () => {
        amount.input.value = moneyInput(due);
        amount.input.focus();
    });
    full.disabled = BigInt(due) > BigInt(Number.MAX_SAFE_INTEGER);
    openForm(
        'Add payment', 'भुक्तानी थप्नुहोस्',
        [bilingual('p', 'Balance due', 'तिर्न बाँकी'), el('strong', null, formatMoney(due)), amount.label, full],
        'Save', 'सुरक्षित गर्नुहोस्',
        async () => {
            const value = parseMoney(amount.input.value, Number.MAX_SAFE_INTEGER);
            if (value == null || value <= 0 || BigInt(value) > BigInt(due)) {
                throw new Error('Enter an amount above zero and within the balance. / शून्यभन्दा बढी र बाँकी रकमभित्रको रकम हाल्नुहोस्।');
            }
            await addPayment(customer.id, value);
            await api.refresh();
        },
        api,
    );
}

function khataEntryLabel(entry) {
    return entry.kind === 'bill'
        ? 'Udharo bill #' + entry.bill.billNumber + ' / उधारो बिल नं. ' + entry.bill.billNumber
        : 'Payment received / भुक्तानी प्राप्त';
}

function ledgerText(customer, ledger, shop) {
    const lines = [
        shop?.name ?? 'Quick Bill',
        'Customer / ग्राहक: ' + customer.name,
        'Balance due / तिर्न बाँकी: ' + formatMoney(ledger.duePaisa),
    ];
    if (ledger.advancePaisa > 0) lines.push('Advance / अग्रिम: ' + formatMoney(ledger.advancePaisa));
    if (ledger.entries.length) {
        lines.push('', 'Recent transactions / हालको कारोबार');
        for (const entry of ledger.entries.slice(0, 10)) {
            lines.push(khataEntryLabel(entry) + ' · ' + formatNepalDate(entry.createdAtMillis)
                + ' · ' + formatMoney(entry.amountPaisa));
        }
    }
    if (ledger.duePaisa > 0) lines.push('', 'Please pay the outstanding balance. / कृपया बाँकी रकम तिर्नुहोस्।');
    return lines.join('\n');
}

function khataDetail(customer, ledger, api, back) {
    const section = el('section', 'mg-view');
    section.append(heading('Customer khata', 'ग्राहकको खाता', button('Back', 'पछाडि', back)));
    const summary = el('div', 'mg-card mg-khata-summary');
    summary.append(el('h2', null, customer.name));
    if (customer.phone) summary.append(el('p', 'mg-meta', customer.phone));
    summary.append(stat('Balance due', 'तिर्न बाँकी', formatMoney(ledger.duePaisa)));
    if (ledger.advancePaisa > 0) summary.append(el('p', 'mg-advance', 'Advance / अग्रिम: ' + formatMoney(ledger.advancePaisa)));
    const actions = el('div', 'mg-actions');
    const pay = button('Add payment', 'भुक्तानी थप्नुहोस्', () => addPaymentDialog(customer, ledger.duePaisa, api), 'primary');
    pay.disabled = ledger.duePaisa <= 0;
    const share = button('Share khata', 'खाता साझा गर्नुहोस्', () => {
        shareAction(() => tryShareText(ledgerText(customer, ledger, api.state.shop), 'Share khata / खाता साझा गर्नुहोस्'), api, share);
    });
    actions.append(pay, share, button('Edit customer', 'ग्राहकको विवरण बदल्नुहोस्', () => customerEditor(customer, api)));
    summary.append(actions);
    section.append(summary);
    const entries = el('section', 'mg-list');
    entries.append(bilingual('h2', 'Transactions', 'कारोबार', 'mg-subtitle'));
    if (!ledger.entries.length) entries.append(empty('No transactions', 'कारोबार छैन'));
    for (const entry of ledger.entries) {
        const row = el('article', 'mg-record');
        const detail = el('div', 'mg-record-row');
        const content = el('div', 'mg-record-content');
        content.append(el('strong', null, khataEntryLabel(entry)), el('span', 'mg-meta', formatNepalDate(entry.createdAtMillis)));
        detail.append(content, el('strong', 'mg-record-amount', formatMoney(entry.kind === 'payment' ? -entry.amountPaisa : entry.amountPaisa)));
        if (entry.kind === 'bill') {
            const open = el('button', 'mg-record-open');
            open.type = 'button';
            open.append(detail);
            open.addEventListener('click', () => api.openBill(entry.bill.billNumber));
            row.append(open);
        } else row.append(detail);
        entries.append(row);
    }
    section.append(entries);
    return section;
}

function khataView(container, api) {
    const { customers, bills, payments } = api.state;
    const customer = customers.find((item) => item.id === openedCustomerId);
    if (customer) {
        return khataDetail(customer, customerLedger(customer.id, bills, payments), api, () => {
            openedCustomerId = null;
            renderManagement(container, 'khata', api);
        });
    }

    const section = el('section', 'mg-view');
    section.append(heading('Khata', 'खाता', button('New customer', 'नयाँ ग्राहक', () => customerEditor(null, api), 'primary')));
    if (!customers.length) {
        section.append(empty('No customers yet', 'अहिलेसम्म ग्राहक थपिएका छैनन्'));
        return section;
    }
    const ordered = customers.map((item) => ({ customer: item, ledger: customerLedger(item.id, bills, payments) }))
        .sort((a, b) => {
            if (a.ledger.duePaisa !== b.ledger.duePaisa) return a.ledger.duePaisa > b.ledger.duePaisa ? -1 : 1;
            return (b.ledger.lastActivityAtMillis ?? 0) - (a.ledger.lastActivityAtMillis ?? 0) || b.customer.id - a.customer.id;
        });
    const list = el('div', 'mg-list');
    for (const { customer: item, ledger } of ordered) {
        const card = el('article', 'mg-record');
        const open = el('button', 'mg-record-open');
        open.type = 'button';
        const row = el('div', 'mg-record-row');
        const identity = el('div', 'mg-record-content');
        identity.append(el('strong', null, item.name));
        if (item.phone) identity.append(el('span', 'mg-meta', item.phone));
        row.append(identity, stat('Due', 'बाँकी', formatMoney(ledger.duePaisa), 'mg-stat-row'));
        open.append(row);
        open.addEventListener('click', () => {
            openedCustomerId = item.id;
            renderManagement(container, 'khata', api);
        });
        card.append(open);
        list.append(card);
    }
    section.append(list);
    return section;
}

function shopView(api) {
    const section = el('section', 'mg-view');
    section.append(heading('Shop settings', 'पसल सेटिङ'));
    const form = el('form', 'mg-card mg-shop-form');
    const name = field('Shop name', 'पसलको नाम', api.state.shop?.name ?? '', { required: true, autocomplete: 'organization' });
    const pan = field('PAN number', 'प्यान नम्बर', api.state.shop?.pan ?? '', { required: true, inputMode: 'numeric' });
    const submit = button('Save', 'सुरक्षित गर्नुहोस्', null, 'primary');
    submit.type = 'submit';
    const error = el('p', 'mg-error');
    error.setAttribute('role', 'alert');
    error.hidden = true;
    form.append(name.label, pan.label, error, submit);
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        if (submit.disabled) return;
        const normalized = asciiDigits(pan.input.value).trim();
        if (!/^[0-9]{9}$/.test(normalized)) {
            error.textContent = 'Enter a nine-digit PAN. / नौ अङ्कको प्यान नम्बर हाल्नुहोस्।';
            error.hidden = false;
            pan.input.focus();
            return;
        }
        submit.disabled = true;
        error.hidden = true;
        try {
            await saveShop({ name: name.input.value, pan: normalized });
            await api.refresh();
            api.notify('Shop details saved. / पसलको विवरण सुरक्षित भयो।');
        } catch (failure) {
            error.textContent = failure?.message || 'Could not save shop details. / पसलको विवरण सुरक्षित गर्न सकिएन।';
            error.hidden = false;
        } finally {
            submit.disabled = false;
        }
    });
    section.append(form);
    return section;
}

export function renderManagement(container, view, api) {
    const page = {
        history: () => historyView(api),
        today: () => todayView(api),
        khata: () => khataView(container, api),
        shop: () => shopView(api),
    }[view];
    if (!page) return;
    container.replaceChildren(page());
}
