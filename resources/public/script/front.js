// Front page: upcoming events list and the goggles OSD in the hero.
document.addEventListener("DOMContentLoaded", () => {
    const E = window.FpvEvents;
    const reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
    const upcoming = E.upcoming();

    const list = document.getElementById("upcoming-list");
    if (list) {
        upcoming.slice(0, 3).forEach(evt => list.append(E.row(evt)));
        document.getElementById(upcoming.length ? "upcoming-more" : "upcoming-empty").classList.remove("hide");
        list.classList.toggle("hide", upcoming.length === 0);
    }

    const hero = document.querySelector(".hero");
    if (!hero) return;

    const video = hero.querySelector("video");
    const toggle = hero.querySelector(".hero-toggle");
    const setPlaying = (playing) => {
        video.muted = true;
        if (playing) {
            video.play().catch(() => {});
        } else {
            video.pause();
            if (video.currentTime > 0 && reduceMotion.matches) video.currentTime = 0;
        }
        toggle.classList.toggle("is-paused", !playing);
        toggle.setAttribute("aria-label", playing ? "Pysäytä video" : "Toista video");
    };
    // Autoplays as usual; only when the OS asks for reduced motion do we start paused on the first frame.
    setPlaying(!reduceMotion.matches);
    toggle.addEventListener("click", () => setPlaying(video.paused));
    reduceMotion.addEventListener("change", () => setPlaying(!reduceMotion.matches));

    const next = upcoming[0];
    const dateEl = document.getElementById("osd-date");
    const titleEl = document.getElementById("osd-title");
    const countEl = document.getElementById("osd-countdown");
    const labelDate = (d) => `${E.WEEKDAYS_SHORT[d.getDay()]} ${E.formatFinnish(d)}`;

    if (!next) {
        dateEl.textContent = labelDate(new Date());
        titleEl.textContent = "Ei tulevia tapahtumia";
        countEl.classList.add("hide");
        hero.classList.add("no-countdown");
        return;
    }

    const target = E.parseDate(next.date);
    dateEl.textContent = labelDate(target);
    titleEl.textContent = next.title;

    const tick = () => {
        const diff = target - new Date();
        if (diff <= 0) {
            countEl.textContent = "TÄNÄÄN";
            return;
        }
        const s = Math.floor(diff / 1000);
        const days = Math.floor(s / 86400);
        const hh = E.pad(Math.floor(s % 86400 / 3600));
        const mm = E.pad(Math.floor(s % 3600 / 60));
        const ss = E.pad(s % 60);
        const clock = reduceMotion.matches ? `${hh}:${mm}` : `${hh}:${mm}:${ss}`;
        countEl.textContent = days > 0 ? `T-${days}d ${clock}` : `T-${clock}`;
    };

    let timer;
    const schedule = () => {
        clearInterval(timer);
        tick();
        timer = setInterval(tick, reduceMotion.matches ? 60000 : 1000);
    };
    schedule();
    reduceMotion.addEventListener("change", schedule);
});
