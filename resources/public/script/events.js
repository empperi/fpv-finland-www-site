// Shared helpers for the events data in window.calendarEvents.
// Dates are local-day dates ("YYYY-MM-DD"); an event is upcoming through the end of its day.
window.FpvEvents = (() => {
    const MONTHS = ["Tammikuu", "Helmikuu", "Maaliskuu", "Huhtikuu", "Toukokuu", "Kesäkuu",
        "Heinäkuu", "Elokuu", "Syyskuu", "Lokakuu", "Marraskuu", "Joulukuu"];
    const MONTHS_SHORT = ["tammi", "helmi", "maalis", "huhti", "touko", "kesä",
        "heinä", "elo", "syys", "loka", "marras", "joulu"];
    const WEEKDAYS_SHORT = ["SU", "MA", "TI", "KE", "TO", "PE", "LA"];

    const pad = (n) => String(n).padStart(2, "0");
    const parseDate = (iso) => {
        const [y, m, d] = iso.split("-").map(Number);
        return new Date(y, m - 1, d);
    };
    const isoDate = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
    const startOfToday = () => {
        const n = new Date();
        return new Date(n.getFullYear(), n.getMonth(), n.getDate());
    };
    const formatFinnish = (d) => `${pad(d.getDate())}.${pad(d.getMonth() + 1)}.${d.getFullYear()}`;

    const all = () => (window.calendarEvents || []).slice().sort((a, b) => a.date.localeCompare(b.date));
    const upcoming = () => {
        const today = startOfToday();
        return all().filter(e => parseDate(e.date) >= today);
    };
    const past = () => {
        const today = startOfToday();
        return all().filter(e => parseDate(e.date) < today).reverse();
    };

    const el = (tag, cls, text) => {
        const e = document.createElement(tag);
        if (cls) e.className = cls;
        if (text !== undefined) e.textContent = text;
        return e;
    };

    const plainText = (html) => {
        const spaced = html.replace(/<\/(p|h[1-6]|li|ul|ol|div)>/gi, " ");
        const doc = new DOMParser().parseFromString(spaced, "text/html");
        return doc.body.textContent.replace(/\s+/g, " ").trim();
    };

    // One list row: date block on the left, title and excerpt (or full description) on the right.
    const row = (evt, opts = {}) => {
        const d = parseDate(evt.date);
        const li = el("li", "event-row" + (opts.past ? " is-past" : ""));
        const date = el("time", "event-date");
        date.dateTime = evt.date;
        date.append(el("span", "event-day", d.getDate()), el("span", "event-month", MONTHS_SHORT[d.getMonth()]));
        if (d.getFullYear() !== new Date().getFullYear()) {
            date.append(el("span", "event-year", d.getFullYear()));
        }
        const body = el("div", "event-body");
        body.append(el("h3", "event-title", evt.title));
        if (opts.full) {
            const desc = el("div", "event-desc");
            desc.innerHTML = evt.desc;
            body.append(desc);
        } else {
            body.append(el("p", "event-excerpt", plainText(evt.desc)));
        }
        li.append(date, body);
        return li;
    };

    return {MONTHS, MONTHS_SHORT, WEEKDAYS_SHORT, pad, parseDate, isoDate, startOfToday, formatFinnish,
        all, upcoming, past, el, row};
})();
