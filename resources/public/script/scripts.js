const SEARCH_LINK_MAX_TEXT_LENGTH = 32;

const showBurgerMenu = () => {
    document.getElementById("nav-burger-list").classList.remove("hide");
    document.getElementById("burger-menu-closed").classList.add("hide");
    document.getElementById("burger-menu-open").classList.remove("hide");
};

const hideBurgerMenu = () => {
    document.getElementById("nav-burger-list").classList.add("hide");
    document.getElementById("burger-menu-closed").classList.remove("hide");
    document.getElementById("burger-menu-open").classList.add("hide");
};

const findParentWithClass = (elem, cssClass) => {
    const parent = elem.parentNode;

    if (parent == null) {
        return null;
    }

    for (var i = 0; i < parent.classList.length; i++) {
        if (parent.classList[i] === cssClass) {
            return parent;
        }
    }
    return findParentWithClass(parent, cssClass);
}

const isBurgerMenuVisible = () => {
    return !document.getElementById("nav-burger-list").classList.contains("hide");
};

const toggleBurgerMenu = () => {
    if (isBurgerMenuVisible()) {
        hideBurgerMenu();
    } else {
        showBurgerMenu();
    }
};

const searchFromDatabase = (searchQuery) => {
    const stemMatcher = searchQuery.split(/[ ]+/)
        .filter(s => s.length > 0)
        .map(querypart => {
            const stemmer = new Snowball("Finnish");
            stemmer.setCurrent(querypart);
            stemmer.stem();
            return stemmer.getCurrent();
        })
        .reduce((f, q) => (strs) => {
            return strs.find(s => s === q) !== undefined && f(strs);
        }, (strs) => true);

    return searchDatabase.filter(article => stemMatcher(article.stems));
};

const hideSearchresults = (inputElem) => {
    const searchParent = findParentWithClass(inputElem, "search");
    const searchResultsElem = searchParent.querySelector(".search-results");
    const searchClickTargetElem = searchParent.querySelector(".search-click-target")

    searchClickTargetElem.classList.add("hide");
    searchResultsElem.classList.add("hide");
    const currentLiveResults = searchResultsElem.querySelectorAll(".live");
    if (currentLiveResults != null) {
        currentLiveResults.forEach((elem) => {
            elem.remove();
        });
    }
};

const shortenString = (str, maxChars) => {
    if (str.length <= maxChars) {
        return str;
    } else {
        return str.substr(0, maxChars) + "…";
    }
};

const showSearchResults = (inputElem, results, searchQuery) => {
    const searchParent = findParentWithClass(inputElem, "search");
    const searchResultsElem = searchParent.querySelector(".search-results");
    const searchClickTargetElem = searchParent.querySelector(".search-click-target");

    const currentLiveResults = searchResultsElem.querySelectorAll(".live");
    if (currentLiveResults != null) {
        currentLiveResults.forEach((elem) => {
            elem.remove();
        });
    }

    const searchResultTemplate = searchResultsElem.querySelector(".search-result-template");

    results.map(result => {
        const clone = searchResultTemplate.cloneNode(true);
        clone.removeAttribute("id");
        clone.classList.remove("search-result-template");
        clone.classList.remove("hide");
        clone.classList.add("live");
        clone.querySelector("a").href = result.link + "#:~:text=" + searchQuery;
        clone.querySelector(".title").textContent = shortenString(result.title, SEARCH_LINK_MAX_TEXT_LENGTH);
        clone.querySelector(".date").textContent = result.date.toLocaleDateString("fi");
        clone.addEventListener("click", (event) => {
           setTimeout(() => {
              inputElem.value = "";
              hideSearchresults(inputElem);
           }, 500);
        });
        searchResultTemplate.insertAdjacentElement("afterend", clone);
    });

    searchResultsElem.classList.remove("hide");
    searchClickTargetElem.classList.remove("hide");
};

const bindUserSearch = (inputElem) => {
    const searchParent = findParentWithClass(inputElem, "search");
    const searchClickTargetElem = searchParent.querySelector(".search-click-target");

    inputElem.addEventListener("input", (event) => {
        // wait until value is applied
        setTimeout(() => {
            const value = inputElem.value;

            let renderResults = [];

            if (value != null && value.length > 2) {
                const dbResults = searchFromDatabase(value.toLowerCase());

                if (dbResults.length > 0) {
                    renderResults = renderResults.concat(dbResults.map(article => {
                        return {...article, date: new Date(article.date)};
                    })).sort((a, b) => b.date.getTime() - a.date.getTime());
                }
            }

            if (renderResults.length > 0) {
                showSearchResults(inputElem, renderResults, value.toLowerCase());
            } else {
                hideSearchresults(inputElem);
            }
        }, 1);
    });

    searchClickTargetElem.addEventListener("click", (event) => {
        inputElem.value = "";
        hideSearchresults(inputElem);
    });
};

const hideCookieConsent = () => {
    document.querySelectorAll(".cookies").forEach(e => {
        e.classList.add("hide");
    });
};

const showCookieConsent = () => {
    document.querySelectorAll("#cookie-consent").forEach(e => {
        e.classList.remove("hide");
    });
};

const showTrackingBlocked = () => {
    document.querySelectorAll("#tracking-blocked").forEach(e => {
        e.classList.remove("hide");
    });
};

const addCookieConsentBasedScripts = () => {
    console.info("Adding cookie consent based tracking");

    const gaTagId = "G-W8XH03FX5C";

    const script = document.createElement("script");
    script.src = `https://www.googletagmanager.com/gtag/js?id=${gaTagId}`;
    script.async = true;
    script.onerror = () => {
        console.error("GA couldn't load, ad/tracker blocking?");
        showTrackingBlocked();
    };
    document.head.appendChild(script);

    window.dataLayer = window.dataLayer || [];
    function gtag(){window.dataLayer.push(arguments);}
    gtag("js", new Date());
    gtag("config", gaTagId);
};

const acceptCookies = () => {
    window.localStorage.setItem("cookieConsent", true);
    addCookieConsentBasedScripts();
    hideCookieConsent();
};

const declineCookies = () => {
    window.localStorage.setItem("cookieConsent", false);
    hideCookieConsent();
    console.info("Cookie consent not given, skipping analytics");
};

const checkCookieConsent = () => {
    const consent = window.localStorage.getItem("cookieConsent");
    if (consent == null) {
        return undefined;
    }
    return consent == "true";
};

const initializeCookieConsent = () => {
    const consent = checkCookieConsent();
    if (consent === undefined) {
        showCookieConsent();
    } else if (consent === true) {
        addCookieConsentBasedScripts();
    } else {
        console.info("Cookie consent not given, skipping analytics");
    }
};

const bindDomEventHandlers = () => {
    document.querySelectorAll(".search-input").forEach(bindUserSearch);
};

document.addEventListener("DOMContentLoaded", bindDomEventHandlers, false);
document.addEventListener("DOMContentLoaded", initializeCookieConsent, false);