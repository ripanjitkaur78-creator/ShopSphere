'use strict';

Shop.startPage(() => {
  const register = document.body.dataset.page === 'register';
  const form = document.querySelector('#auth-form');
  const errorMessage = document.querySelector('#auth-error');
  document.querySelector('#other-auth').href = (register ? 'login' : 'register')
    + '.html?next=' + encodeURIComponent(Shop.safeNext());
  if (Shop.params.has('registered')) Shop.notify('Account created. Login to continue.');

  form.addEventListener('submit', event => {
    event.preventDefault();
    errorMessage.textContent = '';
    Shop.busy(form.querySelector('button'), async () => {
      try {
        const email = form.elements.email.value.trim();
        const password = form.elements.password.value;
        if (register) {
          const name = form.elements.name.value.trim();
          if (!name) throw new Error('Enter your full name.');
          if (password.length < 10 || password.length > 72 || new TextEncoder().encode(password).length > 72) {
            throw new Error('Password must have 10–72 characters and no more than 72 UTF-8 bytes.');
          }
          if (password !== document.querySelector('#confirm-password').value) {
            throw new Error('Passwords do not match.');
          }
          await Shop.api('/api/auth/register', { method: 'POST', data: { name, email, password } });
          Shop.csrf = null;
          location.href = 'login.html?registered=1&next=' + encodeURIComponent(Shop.safeNext());
        } else {
          await Shop.api('/api/auth/login', { method: 'POST', form: true, data: { username: email, password } });
          // Authentication rotates the session and invalidates the previous CSRF token.
          Shop.csrf = null;
          const account = await Shop.api('/api/auth/me', { allow401: true });
          if (!account) throw new Error('Could not confirm login. Please try again.');
          location.href = Shop.safeNext();
        }
      } catch (error) {
        errorMessage.textContent = error.message;
      }
    });
  });
});
