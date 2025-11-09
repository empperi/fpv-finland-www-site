const showBurgerMenu = () => {
    document.getElementById("nav-burger-list").classList.remove('hide');
    document.getElementById("burger-menu-closed").classList.add('hide');
    document.getElementById("burger-menu-open").classList.remove('hide');
};

const hideBurgerMenu = () => {
    document.getElementById("nav-burger-list").classList.add('hide');
    document.getElementById("burger-menu-closed").classList.remove('hide');
    document.getElementById("burger-menu-open").classList.add('hide');
};

const isBurgerMenuVisible = () => {
    return !document.getElementById("nav-burger-list").classList.contains('hide');
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
            const stemmer = new Snowball('Finnish');
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
    const searchResultsElem = inputElem.parentNode.querySelector('.search-results');
    searchResultsElem.classList.add('hide');
    const currentLiveResults = searchResultsElem.querySelectorAll('.live');
    if (currentLiveResults != null) {
        currentLiveResults.forEach((elem) => {
            elem.remove();
        });
    }
};

const showSearchResults = (inputElem, results) => {
    const searchResultsElem = inputElem.parentNode.querySelector('.search-results');

    const currentLiveResults = searchResultsElem.querySelectorAll('.live');
    if (currentLiveResults != null) {
        currentLiveResults.forEach((elem) => {
            elem.remove();
        });
    }

    const searchResultTemplate = searchResultsElem.querySelector('.search-result-template');

    results.map(result => {
        const clone = searchResultTemplate.cloneNode(true);
        clone.removeAttribute('id');
        clone.classList.remove('hide');
        clone.classList.add('live');
        clone.querySelector('a').href = result.link;
        clone.querySelector('.title').textContent = result.title;
        clone.querySelector('.date').textContent = result.date.toLocaleDateString('fi');
        searchResultTemplate.insertAdjacentElement('afterend', clone);
    });

    searchResultsElem.classList.remove('hide');
};

const bindUserSearch = (inputElem) => {
    inputElem.addEventListener("keydown", (event) => {
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
                showSearchResults(inputElem, renderResults);
            } else {
                hideSearchresults(inputElem);
            }
        }, 1);
    });
};

const bindDomEventHandlers = () => {
    document.querySelectorAll('.search-input').forEach(bindUserSearch);
};

document.addEventListener('DOMContentLoaded', bindDomEventHandlers, false);