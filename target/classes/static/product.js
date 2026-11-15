'use strict';

Shop.startPage(async () => {
  const id = Shop.params.get('id');
  if (!id) throw new Error('No product selected. Use the Products page to select a product.');
  const product = await Shop.api('/api/products/' + encodeURIComponent(id));
  document.title = product.name + ' | ShopSphere';
  Shop.setImage(document.querySelector('#product-image'), product.imageUrl, product.name);
  document.querySelector('#product-name').textContent = product.name;
  document.querySelector('#product-category').textContent = product.category?.name || '';
  document.querySelector('#product-price').textContent = Shop.money(product.price);
  document.querySelector('#product-description').textContent = product.description;
  document.querySelector('#product-stock').textContent = product.stock > 0
    ? product.stock + ' available' : 'Out of stock';
  const form = document.querySelector('#buy-form');
  const quantity = form.elements.quantity;
  quantity.max = Math.max(1, Math.min(99, product.stock));
  quantity.disabled = product.stock <= 0;
  const button = form.querySelector('button');
  button.disabled = product.stock <= 0;
  form.addEventListener('submit', event => {
    event.preventDefault();
    Shop.busy(button, async () => {
      const count = Number(quantity.value);
      if (!Number.isInteger(count) || count < 1 || count > 99 || count > product.stock) {
        throw new Error('Choose a quantity from 1 to 99 within the available stock.');
      }
      await Shop.addToCart(product.id, count);
    });
  });
  document.querySelector('#product-details').hidden = false;
});
