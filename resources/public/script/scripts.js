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
    const stemmer = new Snowball('Finnish');
    stemmer.setCurrent(searchQuery);
    stemmer.stem();
    const stemQuery = stemmer.getCurrent();

    return searchDatabase.filter(article => article.stems.filter(s => s === stemQuery).length > 0);
};

const hideSearchresults = () => {
    const searchResultsElem = document.getElementById('search-results');
    searchResultsElem.classList.add('hide');
    const currentLiveResults = searchResultsElem.querySelectorAll('.live');
    if (currentLiveResults != null) {
        currentLiveResults.forEach((elem) => {
            elem.remove();
        });
    }
};

const showSearchResults = (results) => {
    const searchResultsElem = document.getElementById('search-results');

    const currentLiveResults = searchResultsElem.querySelectorAll('.live');
    if (currentLiveResults != null) {
        currentLiveResults.forEach((elem) => {
            elem.remove();
        });
    }

    const searchResultTemplate = document.getElementById('search-result-template');

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
    inputElem.addEventListener("keypress", (event) => {
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
                showSearchResults(renderResults);
            } else {
                hideSearchresults();
            }
        }, 1);
    });
};

const bindDomEventHandlers = () => {
    bindUserSearch(document.getElementById('search-input'));
};

document.addEventListener('DOMContentLoaded', bindDomEventHandlers, false);