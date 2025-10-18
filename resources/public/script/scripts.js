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