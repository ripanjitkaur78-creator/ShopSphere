'use strict';

// Shared API and page helpers. Page-specific behavior lives in separate files.
const Shop = {
  user: null,
  categories: [],
  csrf: null,
  params: new URLSearchParams(location.search),
  placeholder: 'data:image/svg+xml,' + encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect width="100%" height="100%" fill="#f1f3f5"/><text x="50%" y="50%" text-anchor="middle" fill="#59636e" font-family="Arial" font-size="18">No image</text></svg>'),

  async api(path, options = {}) {
    // Never send session or CSRF credentials to a different origin.
    const url = new URL(path, location.origin);
    if (url.origin !== location.origin || !url.pathname.startsWith('/api/')) {
      throw new Error('Invalid API address.');
    }
    const method = options.method || 'GET';
    const headers = { Accept: 'application/json' };
    if (!['GET', 'HEAD'].includes(method)) {
      if (!this.csrf) {
        const response = await fetch('/api/csrf', { credentials: 'same-origin' });
        if (!response.ok) throw new Error('Could not secure the request. Refresh and try again.');
        this.csrf = await response.json();
      }
      headers[this.csrf.headerName || 'X-CSRF-TOKEN'] = this.csrf.token;
      if (options.data !== undefined) {
        headers['Content-Type'] = options.form
          ? 'application/x-www-form-urlencoded'
          : 'application/json';
      }
    }
    const response = await fetch(url.href, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.data === undefined ? undefined : options.form
        ? new URLSearchParams(options.data).toString()
        : JSON.stringify(options.data)
    });
    const text = await response.text();
    let result;
    try {
      result = text ? JSON.parse(text) : null;
    } catch {
      throw new Error('Unexpected server response. Please try again.');
    }
    if (response.status === 401 && options.allow401) return null;
    if (!response.ok) {
      if (response.status === 403) this.csrf = null;
      const error = new Error(result?.message || `Request failed (${response.status}). Please try again.`);
      error.status = response.status;
      throw error;
    }
    return result;
  },

  notify(message, error = false) {
    const notice = document.createElement('div');
    notice.className = error ? 'alert alert-danger' : 'alert';
    notice.setAttribute('role', error ? 'alert' : 'status');
    const close = document.createElement('button');
    close.type = 'button';
    close.textContent = 'Dismiss';
    close.addEventListener('click', () => notice.remove());
    notice.append(document.createTextNode(message), close);
    document.querySelector('#notice').append(notice);
  },

  async busy(button, action) {
    if (button.disabled) return;
    const original = button.textContent;
    button.disabled = true;
    button.textContent = 'Please wait...';
    try {
      await action();
    } catch (error) {
      this.notify(error.message || 'Request failed. Please try again.', true);
    } finally {
      if (button.isConnected) {
        button.disabled = false;
        if (button.textContent === 'Please wait...') button.textContent = original;
      }
    }
  },

  money(value) {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(Number(value) || 0);
  },

  setImage(image, url, name) {
    image.alt = name;
    image.src = this.placeholder;
    try {
      const parsed = new URL(url || '', location.origin);
      if (url && (parsed.protocol === 'https:' || (parsed.origin === location.origin && parsed.pathname.startsWith('/images/')))) {
        image.src = parsed.href;
      }
    } catch {
      // Invalid or missing images use the local placeholder.
    }
    image.addEventListener('error', () => { image.src = this.placeholder; }, { once: true });
  },

  safeNext() {
    const next = this.params.get('next');
    return next && /^(index|products|product|cart|orders|admin)\.html(?:\?[^#]*)?$/.test(next)
      ? next : 'index.html';
  },

  login() {
    const next = location.pathname.split('/').pop() + location.search;
    location.href = 'login.html?next=' + encodeURIComponent(next);
  },

  requireUser(admin = false) {
    if (!this.user) {
      this.login();
      return false;
    }
    if (admin && !this.isAdmin()) {
      document.querySelector('#main').textContent = 'Admin access is required to view this page.';
      return false;
    }
    return true;
  },

  isAdmin() {
    return this.user && ['ADMIN', 'ROLE_ADMIN'].includes(this.user.role);
  },

  updateCartCount(cart) {
    document.querySelector('#cart-count').textContent = cart.items.reduce((total, item) => total + item.quantity, 0);
  },

  async addToCart(productId, quantity) {
    if (!this.user) return this.login();
    const cart = await this.api('/api/cart/items', { method: 'POST', data: { productId, quantity } });
    this.updateCartCount(cart);
    this.notify('Added to cart.');
  },

  fillCategories(select, selected = '') {
    // Preserve the page's initial empty option.
    select.length = 1;
    for (const category of this.categories) {
      select.add(new Option(category.name, String(category.id)));
    }
    select.value = String(selected);
  },

  showProducts(products) {
    const list = document.querySelector('#product-list');
    list.replaceChildren();
    document.querySelector('#result-count').textContent = products.length + (products.length === 1 ? ' product' : ' products');
    if (!products.length) {
      list.textContent = 'No products found. Try a different search or category.';
      return;
    }
    for (const product of products) {
      const card = document.querySelector('#product-card-template').content.cloneNode(true);
      // Server data is inserted as text, never HTML.
      card.querySelectorAll('[data-product-link]').forEach(link => {
        link.href = 'product.html?id=' + encodeURIComponent(product.id);
      });
      this.setImage(card.querySelector('img'), product.imageUrl, product.name);
      card.querySelector('[data-category]').textContent = product.category?.name || '';
      card.querySelector('[data-name]').textContent = product.name;
      card.querySelector('[data-price]').textContent = this.money(product.price);
      card.querySelector('[data-stock]').textContent = product.stock > 0 ? 'In stock' : 'Out of stock';
      const button = card.querySelector('button');
      button.disabled = product.stock <= 0;
      button.addEventListener('click', () => this.busy(button, () => this.addToCart(product.id, 1)));
      list.append(card);
    }
  },

  async startPage(action) {
    await this.ready;
    try {
      await action();
    } catch (error) {
      this.notify(error.message || 'Could not load the page. Refresh to try again.', true);
    }
  },

  async initialize() {
    const results = await Promise.allSettled([
      this.api('/api/auth/me', { allow401: true }),
      this.api('/api/categories')
    ]);
    if (results[0].status === 'fulfilled') this.user = results[0].value;
    else this.notify('Could not check login status. ' + results[0].reason.message, true);
    if (results[1].status === 'fulfilled') this.categories = results[1].value;
    else this.notify('Could not load categories. ' + results[1].reason.message, true);
    document.querySelectorAll('[data-signed-in]').forEach(element => { element.hidden = !this.user; });
    document.querySelectorAll('[data-signed-out]').forEach(element => { element.hidden = !!this.user; });
    document.querySelector('#admin-link').hidden = !this.isAdmin();
    document.querySelector('#account-name').textContent = this.user?.name || '';
    document.querySelector('#logout').addEventListener('click', event => {
      this.busy(event.currentTarget, async () => {
        await this.api('/api/auth/logout', { method: 'POST' });
        this.csrf = null;
        location.href = 'index.html';
      });
    });
    if (this.user) {
      try {
        this.updateCartCount(await this.api('/api/cart'));
      } catch (error) {
        this.notify('Could not load cart. ' + error.message, true);
      }
    }
  }
};

Shop.ready = Shop.initialize();
