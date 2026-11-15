'use strict';

Shop.startPage(async () => {
  Shop.fillCategories(document.querySelector('#category'));
  const products = await Shop.api('/api/products');
  // Show a few featured products; the full catalog stays on Products.
  const featured = products.filter(product => product.featured);
  Shop.showProducts((featured.length ? featured : products).slice(0, 8));
});
