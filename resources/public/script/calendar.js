// Events page: list view (default) and month calendar view.
document.addEventListener("DOMContentLoaded", () => {
    const E = window.FpvEvents;
    const $ = (id) => document.getElementById(id);
    const events = E.all();
    const VIEW_KEY = "eventsView";

    // ---------- View toggle ----------
    const views = {list: $("view-list"), month: $("view-month")};
    const buttons = {list: $("view-list-btn"), month: $("view-month-btn")};

    const setView = (name) => {
        Object.keys(views).forEach(key => {
            views[key].classList.toggle("hide", key !== name);
            buttons[key].setAttribute("aria-pressed", String(key === name));
        });
        try { window.localStorage.setItem(VIEW_KEY, name); } catch (e) { /* storage unavailable */ }
    };
    buttons.list.addEventListener("click", () => setView("list"));
    buttons.month.addEventListener("click", () => setView("month"));

    // ---------- List view ----------
    const renderGroups = (container, list, opts) => {
        let lastKey = null;
        let ul = null;
        list.forEach(evt => {
            const d = E.parseDate(evt.date);
            const key = `${d.getFullYear()}-${d.getMonth()}`;
            if (key !== lastKey) {
                lastKey = key;
                container.append(E.el("h2", "month-heading", `${E.MONTHS[d.getMonth()]} ${d.getFullYear()}`));
                ul = E.el("ul", "event-rows");
                container.append(ul);
            }
            ul.append(E.row(evt, {full: true, past: opts.past}));
        });
    };

    const upcoming = E.upcoming();
    renderGroups($("upcoming-groups"), upcoming, {past: false});
    $("no-upcoming").classList.toggle("hide", upcoming.length > 0);

    const pastBox = $("past-events");
    const pastBtn = $("toggle-past");
    const past = E.past();
    if (past.length === 0) {
        pastBtn.classList.add("hide");
    } else {
        renderGroups(pastBox, past, {past: true});
    }
    pastBtn.addEventListener("click", () => {
        const open = pastBox.classList.toggle("hide") === false;
        pastBtn.setAttribute("aria-expanded", String(open));
        pastBtn.textContent = open ? "Piilota menneet tapahtumat" : "Näytä menneet tapahtumat";
    });

    // ---------- Month view ----------
    const grid = $("calendar-days");
    const monthHeader = $("current-month-year");
    const detailsBox = $("event-details");
    const monthList = $("month-events");
    const current = new Date();
    current.setDate(1);

    const showDetails = (dateStr, dayEvents) => {
        $("detail-title").textContent = dayEvents.map(e => e.title).join(", ");
        $("detail-date").textContent = E.formatFinnish(E.parseDate(dateStr));
        $("detail-desc").innerHTML = dayEvents.map(e => e.desc).join("");
        detailsBox.classList.remove("hidden");
    };

    const renderCalendar = (date) => {
        grid.innerHTML = "";
        detailsBox.classList.add("hidden");
        const year = date.getFullYear();
        const month = date.getMonth();
        monthHeader.textContent = `${E.MONTHS[month]} ${year}`;

        // Monday first: JS getDay() Sun=0 -> index 6
        const startIndex = (new Date(year, month, 1).getDay() + 6) % 7;
        const totalDays = new Date(year, month + 1, 0).getDate();
        const prevMonthLastDay = new Date(year, month, 0).getDate();

        for (let i = startIndex; i > 0; i--) {
            const cell = E.el("div", "day other-month");
            cell.append(E.el("span", "date-number", prevMonthLastDay - i + 1));
            grid.append(cell);
        }

        const todayStr = E.isoDate(new Date());
        for (let i = 1; i <= totalDays; i++) {
            const dayStr = `${year}-${E.pad(month + 1)}-${E.pad(i)}`;
            const dayEvents = events.filter(e => e.date === dayStr);
            const cell = E.el("div", "day");
            if (dayStr === todayStr) cell.classList.add("today");
            cell.append(E.el("span", "date-number", i));
            dayEvents.forEach(evt => {
                const chip = E.el("div", "event-chip", evt.title);
                chip.title = evt.title;
                cell.append(chip);
            });
            if (dayEvents.length > 0) {
                cell.classList.add("has-events");
                cell.tabIndex = 0;
                cell.setAttribute("role", "button");
                cell.setAttribute("aria-label", `${i}. ${E.MONTHS[month].toLowerCase()}: ${dayEvents.map(e => e.title).join(", ")}`);
                const open = () => showDetails(dayStr, dayEvents);
                cell.addEventListener("click", open);
                cell.addEventListener("keydown", (ev) => {
                    if (ev.key === "Enter" || ev.key === " ") {
                        ev.preventDefault();
                        open();
                    }
                });
            }
            grid.append(cell);
        }

        // Month's events as rows beneath the grid (shown on small screens only, see CSS)
        monthList.innerHTML = "";
        const prefix = `${year}-${E.pad(month + 1)}-`;
        const monthEvents = events.filter(e => e.date.startsWith(prefix));
        monthEvents.forEach(evt => monthList.append(E.row(evt, {full: true})));
        if (monthEvents.length === 0) {
            monthList.append(E.el("li", "month-empty", "Ei tapahtumia tässä kuussa."));
        }
    };

    $("prev-month").addEventListener("click", () => {
        current.setMonth(current.getMonth() - 1);
        renderCalendar(current);
    });
    $("next-month").addEventListener("click", () => {
        current.setMonth(current.getMonth() + 1);
        renderCalendar(current);
    });
    $("close-details").addEventListener("click", () => detailsBox.classList.add("hidden"));

    renderCalendar(current);

    let saved = null;
    try { saved = window.localStorage.getItem(VIEW_KEY); } catch (e) { /* storage unavailable */ }
    setView(saved === "month" ? "month" : "list");
});
