document.addEventListener('DOMContentLoaded', () => {
    // 1. MOCK DATA (In production, render this variable from your Clojure backend)
    // Format: YYYY-MM-DD
    const events = window.calendarEvents || [

    ];

    let currentDate = new Date();

    const monthNames = ["Tammikuu", "Helmikuu", "Maaliskuu", "Huhtikuu", "Toukokuu", "Kesäkuu",
        "Heinäkuu", "Elokuu", "Syyskuu", "Lokakuu", "Marraskuu", "Joulukuu"
    ];

    const grid = document.getElementById('calendar-days');
    const monthHeader = document.getElementById('current-month-year');

    // Helper: Format Date object to "YYYY-MM-DD" for comparison
    const formatDate = (d) => {
        return d.toISOString().split('T')[0];
    };

    function renderCalendar(date) {
        grid.innerHTML = ""; // Clear previous

        const year = date.getFullYear();
        const month = date.getMonth();

        // Set Header
        monthHeader.innerText = `${monthNames[month]} ${year}`;

        // Get first day of the month (0 = Sunday, 1 = Monday, etc.)
        const firstDay = new Date(year, month, 1);
        const lastDay = new Date(year, month + 1, 0); // Last day of current month

        // Adjust for Monday start (standard in Finland)
        // JS getDay(): Sun=0, Mon=1...Sat=6. We want Mon=0...Sun=6
        let startDayIndex = firstDay.getDay() - 1;
        if (startDayIndex === -1) startDayIndex = 6;

        const totalDays = lastDay.getDate();

        // 1. Padding Days (Previous Month)
        const prevMonthLastDay = new Date(year, month, 0).getDate();
        for (let i = startDayIndex; i > 0; i--) {
            const dayDiv = document.createElement('div');
            dayDiv.classList.add('day', 'other-month');
            dayDiv.innerHTML = `<span class="date-number">${prevMonthLastDay - i + 1}</span>`;
            grid.appendChild(dayDiv);
        }

        // 2. Actual Days
        const todayStr = formatDate(new Date());

        for (let i = 1; i <= totalDays; i++) {
            const dayDiv = document.createElement('div');
            dayDiv.classList.add('day');

            // Check if it's today
            const currentDayStr = `${year}-${String(month + 1).padStart(2, '0')}-${String(i).padStart(2, '0')}`;
            if (currentDayStr === todayStr) {
                dayDiv.classList.add('today');
            }

            // Add Date Number
            let html = `<span class="date-number">${i}</span>`;

            // Check for Events
            const daysEvents = events.filter(e => e.date === currentDayStr);
            daysEvents.forEach(evt => {
                html += `<div class="event-dot">${evt.title}</div>`;
            });

            dayDiv.innerHTML = html;

            // Click Handler
            dayDiv.addEventListener('click', () => {
                showDetails(currentDayStr, daysEvents);
            });

            grid.appendChild(dayDiv);
        }
    }

    const formatFinnishDate = (isoDateStr) => {
        const [year, month, day] = isoDateStr.split('-');
        return `${Number(day)}.${Number(month)}.${year}`;
    };

    function showDetails(dateStr, eventsList) {
        const detailsBox = document.getElementById('event-details');
        const titleEl = document.getElementById('detail-title');
        const dateEl = document.getElementById('detail-date');
        const descEl = document.getElementById('detail-desc');

        detailsBox.classList.remove('hidden');

        // Format the date header
        dateEl.innerText = formatFinnishDate(dateStr);

        if (eventsList.length > 0) {
            // Clear previous content
            titleEl.innerText = "";
            descEl.innerText = "";

            // Loop through all events for this day
            eventsList.forEach(evt => {
                const h4 = document.createElement('h4');
                h4.innerText = evt.title;
                h4.style.margin = "10px 0 5px 0"; // Slight spacing

                const p = document.createElement('p');
                p.innerText = evt.desc;

                // Append to the container (make sure titleEl/descEl are treated as containers or update HTML structure slightly)
                // *Simpler approach using existing HTML structure:*
                titleEl.innerText = evt.title;
                descEl.innerHTML = evt.desc;
                // If you have multiple events, you might want to join them or create elements dynamically.
                // For now, this matches your previous simple structure.
            });
        } else {
            titleEl.innerText = "Ei tapahtumia"; // "No events"
            descEl.innerText = "Klikkaa päivää nähdäksesi tiedot.";
        }
    }

    // Navigation Logic
    document.getElementById('prev-month').addEventListener('click', () => {
        currentDate.setMonth(currentDate.getMonth() - 1);
        renderCalendar(currentDate);
    });

    document.getElementById('next-month').addEventListener('click', () => {
        currentDate.setMonth(currentDate.getMonth() + 1);
        renderCalendar(currentDate);
    });

    document.getElementById('close-details').addEventListener('click', () => {
        document.getElementById('event-details').classList.add('hidden');
    });

    // Initial Render
    renderCalendar(currentDate);
});