'use strict';

// Shared by the customer's order history and the admin order list.
const OrderList = {
  transitions: {
    PLACED: ['PROCESSING', 'CANCELLED'],
    PROCESSING: ['SHIPPED', 'CANCELLED'],
    SHIPPED: ['DELIVERED'],
    DELIVERED: [],
    CANCELLED: []
  },

  show(orders, admin = false, onUpdate = null) {
    const list = document.querySelector('#order-list');
    list.replaceChildren();
    document.querySelector('#orders-empty').hidden = orders.length > 0;
    for (const order of orders) {
      const card = document.querySelector('#order-template').content.cloneNode(true);
      card.querySelector('[data-order-title]').textContent = 'Order #' + order.id;
      const date = new Date(order.createdAt);
      card.querySelector('[data-date]').textContent = Number.isNaN(date.getTime()) ? 'Date unavailable' : date.toLocaleString('en-IN');
      card.querySelector('[data-status]').textContent = order.status;
      card.querySelector('[data-total]').textContent = 'Total: ' + Shop.money(order.total);
      card.querySelector('[data-address]').textContent = order.shippingName + '\n' + order.shippingAddress + '\n' + order.phone;
      const items = card.querySelector('[data-items]');
      for (const item of order.items || []) {
        const line = document.createElement('li');
        line.textContent = item.productName + ' × ' + item.quantity + ' — ' + Shop.money(item.unitPrice * item.quantity);
        items.append(line);
      }
      const form = card.querySelector('.status-form');
      const nextStatuses = this.transitions[order.status] || [];
      if (admin && nextStatuses.length) {
        form.hidden = false;
        const select = form.elements.status;
        select.add(new Option(order.status, order.status));
        nextStatuses.forEach(status => select.add(new Option(status, status)));
        form.addEventListener('submit', event => {
          event.preventDefault();
          Shop.busy(form.querySelector('button'), async () => {
            if (select.value === order.status) return;
            await Shop.api('/api/admin/orders/' + encodeURIComponent(order.id) + '/status', {
              method: 'PUT', data: { status: select.value }
            });
            await onUpdate();
            Shop.notify('Order status updated.');
          });
        });
      }
      list.append(card);
    }
  }
};

if (document.body.dataset.page === 'orders') {
  Shop.startPage(async () => {
    if (!Shop.requireUser()) return;
    OrderList.show(await Shop.api('/api/orders'));
    if (Shop.params.has('placed')) {
      Shop.notify('Order placed. Pay cash when it arrives.');
      history.replaceState(null, '', 'orders.html');
    }
  });
}
