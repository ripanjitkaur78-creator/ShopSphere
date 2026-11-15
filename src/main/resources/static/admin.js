'use strict';

Shop.startPage(async () => {
  if (!Shop.requireUser(true)) return;
  let products = [];
  const form = document.querySelector('#product-editor');
  const saveButton = document.querySelector('#save-product');

  function tableCell(row, text) {
    const cell = document.createElement('td');
    cell.textContent = text;
    row.append(cell);
    return cell;
  }

  function clearEditor() {
    form.reset();
    form.elements.id.value = '';
    document.querySelector('#editor-title').textContent = 'Add product';
    saveButton.textContent = 'Create product';
  }

  function editProduct(product) {
    form.elements.id.value = product.id;
    for (const field of ['name', 'description', 'price', 'stock', 'imageUrl']) {
      form.elements[field].value = product[field] ?? '';
    }
    form.elements.categoryId.value = product.category?.id || '';
    form.elements.featured.checked = !!product.featured;
    document.querySelector('#editor-title').textContent = 'Edit product';
    saveButton.textContent = 'Save changes';
    form.scrollIntoView({ block: 'start' });
    form.elements.name.focus();
  }

  async function loadProducts() {
    products = await Shop.api('/api/products');
    const rows = document.querySelector('#product-rows');
    rows.replaceChildren();
    for (const product of products) {
      const row = document.createElement('tr');
      tableCell(row, product.name);
      tableCell(row, product.category?.name || '');
      tableCell(row, Shop.money(product.price));
      tableCell(row, product.stock);
      const actions = tableCell(row, '');
      const edit = document.createElement('button');
      edit.type = 'button';
      edit.className = 'btn btn-secondary btn-sm';
      edit.textContent = 'Edit';
      edit.addEventListener('click', () => editProduct(product));
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'btn btn-outline-danger btn-sm';
      remove.textContent = 'Delete';
      remove.addEventListener('click', () => {
        if (!confirm('Delete "' + product.name + '" from the catalog? This cannot be undone.')) return;
        Shop.busy(remove, async () => {
          await Shop.api('/api/admin/products/' + encodeURIComponent(product.id), { method: 'DELETE' });
          if (form.elements.id.value === String(product.id)) clearEditor();
          await loadProducts();
          Shop.notify('Product deleted.');
        });
      });
      actions.append(edit, remove);
      rows.append(row);
    }
    if (!products.length) {
      const row = document.createElement('tr');
      tableCell(row, 'No products. Add a product using the form.').colSpan = 5;
      rows.append(row);
    }
  }

  function showCategories() {
    const selected = form.elements.categoryId.value;
    Shop.fillCategories(form.elements.categoryId, selected);
    const list = document.querySelector('#category-list');
    list.replaceChildren();
    for (const category of Shop.categories) {
      const item = document.createElement('li');
      item.textContent = category.name;
      list.append(item);
    }
  }

  async function loadOrders() {
    OrderList.show(await Shop.api('/api/admin/orders'), true, loadOrders);
  }

  async function loadUsers() {
    const users = await Shop.api('/api/admin/users');
    const rows = document.querySelector('#user-rows');
    rows.replaceChildren();
    for (const user of users) {
      const row = document.createElement('tr');
      [user.id, user.name, user.email, user.role].forEach(value => tableCell(row, value));
      rows.append(row);
    }
  }

  document.querySelector('#reset-editor').addEventListener('click', clearEditor);
  form.addEventListener('submit', event => {
    event.preventDefault();
    Shop.busy(saveButton, async () => {
      const values = Object.fromEntries(new FormData(form));
      const id = values.id;
      const data = {
        name: values.name.trim(),
        description: values.description.trim(),
        price: Number(values.price),
        stock: Number(values.stock),
        imageUrl: values.imageUrl.trim(),
        featured: form.elements.featured.checked,
        categoryId: Number(values.categoryId)
      };
      if (!data.name || !data.description) throw new Error('Enter a product name and description.');
      if (!Number.isInteger(data.stock) || data.stock < 0 || data.stock > 2147483647) throw new Error('Stock must be a non-negative whole number.');
      if (!Number.isInteger(data.categoryId) || data.categoryId < 1) throw new Error('Select a category.');
      if (data.price < 0.01 || data.price > 9999999999.99 || !/^\d+(\.\d{1,2})?$/.test(values.price)) {
        throw new Error('Enter a price from 0.01 to 9999999999.99 with up to two decimal places.');
      }
      if (data.imageUrl && !/^(https:\/\/[^\s]+|\/images\/[a-zA-Z0-9_./-]+)$/.test(data.imageUrl)) {
        throw new Error('Use an HTTPS image URL or a local /images/ path.');
      }
      await Shop.api('/api/admin/products' + (id ? '/' + encodeURIComponent(id) : ''), {
        method: id ? 'PUT' : 'POST', data
      });
      clearEditor();
      await loadProducts();
      Shop.notify(id ? 'Product updated.' : 'Product created.');
    });
  });

  const categoryForm = document.querySelector('#category-form');
  categoryForm.addEventListener('submit', event => {
    event.preventDefault();
    Shop.busy(categoryForm.querySelector('button'), async () => {
      const name = categoryForm.elements.name.value.trim();
      if (!name) throw new Error('Enter a category name.');
      await Shop.api('/api/admin/categories', { method: 'POST', data: { name } });
      Shop.categories = await Shop.api('/api/categories');
      showCategories();
      categoryForm.reset();
      Shop.notify('Category added.');
    });
  });

  showCategories();
  document.querySelector('#admin-content').hidden = false;
  // A failed section should not hide the other admin tools.
  await Promise.all([loadProducts, loadOrders, loadUsers].map(async load => {
    try {
      await load();
    } catch (error) {
      Shop.notify(error.message, true);
    }
  }));
});
