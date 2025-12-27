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
    const searchParent = findParentWithClass(inputElem, 'search');
    const searchResultsElem = searchParent.querySelector('.search-results');
    const searchClickTargetElem = searchParent.querySelector('.search-click-target')


    searchClickTargetElem.classList.add('hide');
    searchResultsElem.classList.add('hide');
    const currentLiveResults = searchResultsElem.querySelectorAll('.live');
    if (currentLiveResults != null) {
        currentLiveResults.forEach((elem) => {
            elem.remove();
        });
    }
};

const showSearchResults = (inputElem, results, searchQuery) => {
    const searchParent = findParentWithClass(inputElem, 'search');
    const searchResultsElem = searchParent.querySelector('.search-results');
    const searchClickTargetElem = searchParent.querySelector('.search-click-target');

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
        clone.classList.remove('search-result-template');
        clone.classList.remove('hide');
        clone.classList.add('live');
        clone.querySelector('a').href = result.link + "#:~:text=" + searchQuery;
        clone.querySelector('.title').textContent = result.title;
        clone.querySelector('.date').textContent = result.date.toLocaleDateString('fi');
        clone.addEventListener('click', (event) => {
           setTimeout(() => {
              inputElem.value = '';
              hideSearchresults(inputElem);
           }, 500);
        });
        searchResultTemplate.insertAdjacentElement('afterend', clone);
    });

    searchResultsElem.classList.remove('hide');
    searchClickTargetElem.classList.remove('hide');
};

const bindUserSearch = (inputElem) => {
    const searchParent = findParentWithClass(inputElem, 'search');
    const searchClickTargetElem = searchParent.querySelector('.search-click-target');

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
                showSearchResults(inputElem, renderResults, value.toLowerCase());
            } else {
                hideSearchresults(inputElem);
            }
        }, 1);
    });

    searchClickTargetElem.addEventListener("click", (event) => {
        hideSearchresults(inputElem);
    });
};

const bindDomEventHandlers = () => {
    document.querySelectorAll('.search-input').forEach(bindUserSearch);
};

document.addEventListener('DOMContentLoaded', bindDomEventHandlers, false);