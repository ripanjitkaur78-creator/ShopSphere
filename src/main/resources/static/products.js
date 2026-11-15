'use strict';

Shop.startPage(async () => {
  const query = Shop.params.get('q') || '';
  const category = Shop.params.get('category') || '';
  document.querySelector('#query').value = query;
  Shop.fillCategories(document.querySelector('#category'), category);
  const search = new URLSearchParams({ q: query });
  if (category) search.set('category', category);
  const products = await Shop.api('/api/products?' + search);
  const sort = document.querySelector('#sort');

  function showSortedProducts() {
    const list = [...products];
    if (sort.value === 'low') list.sort((a, b) => a.price - b.price);
    else if (sort.value === 'high') list.sort((a, b) => b.price - a.price);
    else if (sort.value === 'name') list.sort((a, b) => a.name.localeCompare(b.name));
    else list.sort((a, b) => Number(b.featured) - Number(a.featured));
    Shop.showProducts(list);
  }

  sort.addEventListener('change', showSortedProducts);
  showSortedProducts();
});
