'use strict';

Shop.startPage(async () => {
  const query = Shop.params.get('q') || '';
  const category = document.querySelector('#category');
  const searchInput = document.querySelector('#query');
  const sort = document.querySelector('#sort');

  searchInput.value = query;

  Shop.fillCategories(category, Shop.params.get('category') || '');

  let products = [];

  async function loadProducts() {
    const params = new URLSearchParams({
      q: searchInput.value.trim()
    });

    if (category.value) {
      params.set('category', category.value);
    }

    products = await Shop.api('/api/products?' + params.toString());

    showSortedProducts();
  }

  function showSortedProducts() {
    const list = [...products];

    if (sort.value === 'low') {
      list.sort((a, b) => a.price - b.price);
    } else if (sort.value === 'high') {
      list.sort((a, b) => b.price - a.price);
    } else if (sort.value === 'name') {
      list.sort((a, b) => a.name.localeCompare(b.name));
    } else {
      list.sort((a, b) => Number(b.featured) - Number(a.featured));
    }

    Shop.showProducts(list);
  }

  category.addEventListener('change', loadProducts);

  sort.addEventListener('change', showSortedProducts);

  await loadProducts();
});

