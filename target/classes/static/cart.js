'use strict';

Shop.startPage(async () => {
  if (!Shop.requireUser()) return;
  const checkout = document.querySelector('#checkout');
  checkout.elements.shippingName.value = Shop.user.name;
  let cart;
  let changingCart = false;
  let orderPlaced = false;

  // Serialize cart edits and checkout so repeated clicks cannot place duplicate orders.
  async function changeCart(button, action) {
    if (changingCart || orderPlaced) return;
    changingCart = true;
    await Shop.busy(button, action);
    changingCart = false;
  }

  function showCart() {
    Shop.updateCartCount(cart);
    const empty = cart.items.length === 0;
    document.querySelector('#cart-empty').hidden = !empty;
    document.querySelector('#cart-content').hidden = empty;
    document.querySelector('#cart-total').textContent = 'Total: ' + Shop.money(cart.total);
    const list = document.querySelector('#cart-items');
    list.replaceChildren();
    for (const item of cart.items) {
      const row = document.querySelector('#cart-item-template').content.cloneNode(true);
      const link = row.querySelector('[data-product-link]');
      link.textContent = item.product.name;
      link.href = 'product.html?id=' + encodeURIComponent(item.product.id);
      row.querySelector('[data-price]').textContent = Shop.money(item.product.price) + ' each · Subtotal: ' + Shop.money(item.product.price * item.quantity);
      row.querySelector('[data-stock]').textContent = item.product.active === false || item.product.stock <= 0
        ? 'Unavailable. Remove this item before checkout.'
        : item.quantity > item.product.stock ? 'Stock changed. Update quantity before checkout.' : item.product.stock + ' available';
      const form = row.querySelector('form');
      const quantity = form.elements.quantity;
      quantity.value = item.quantity;
      quantity.max = Math.max(1, Math.min(99, item.product.stock));
      form.addEventListener('submit', event => {
        event.preventDefault();
        changeCart(form.querySelector('[type="submit"]'), async () => {
          const count = Number(quantity.value);
          if (!Number.isInteger(count) || count < 1 || count > 99 || count > item.product.stock) {
            throw new Error('Quantity must be from 1 to 99 and within available stock.');
          }
          cart = await Shop.api('/api/cart/items/' + encodeURIComponent(item.id), { method: 'PUT', data: { quantity: count } });
          showCart();
          Shop.notify('Cart updated.');
        });
      });
      const remove = row.querySelector('[data-remove]');
      remove.addEventListener('click', () => changeCart(remove, async () => {
        cart = await Shop.api('/api/cart/items/' + encodeURIComponent(item.id), { method: 'DELETE' });
        showCart();
        Shop.notify('Item removed.');
      }));
      list.append(row);
    }
  }

  checkout.addEventListener('submit', event => {
    event.preventDefault();
    changeCart(checkout.querySelector('button'), async () => {
      const data = Object.fromEntries(new FormData(checkout));
      data.shippingName = data.shippingName.trim();
      data.shippingAddress = data.shippingAddress.trim();
      data.phone = data.phone.trim();
      if (!data.shippingName || data.shippingName.length > 80) throw new Error('Enter a name of up to 80 characters.');
      if (data.shippingAddress.length < 10 || data.shippingAddress.length > 500) throw new Error('Address must have 10–500 characters.');
      if (!/^[+0-9 ()-]{7,25}$/.test(data.phone)) throw new Error('Enter a valid phone number of 7–25 characters.');
      await Shop.api('/api/orders', { method: 'POST', data });
      // Keep checkout locked while navigating away after a successful order.
      orderPlaced = true;
      location.replace('orders.html?placed=1');
    });
  });

  cart = await Shop.api('/api/cart');
  showCart();
});
