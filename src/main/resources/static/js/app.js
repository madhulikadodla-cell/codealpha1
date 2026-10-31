(() => {
  const state = { csrf: null, user: null, chart: null, editingId: null, records: [], attempts: [], modal: null, toast: null, searchTimer: null };
  const $ = (selector, root = document) => root.querySelector(selector);
  const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
  const escapeHtml = (value = '') => String(value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));

  async function getCsrf() {
    const response = await fetch('/api/csrf', { credentials: 'same-origin' });
    if (!response.ok) throw new Error('Unable to start a secure session. Refresh and try again.');
    state.csrf = (await response.json()).token;
  }

  async function api(url, options = {}) {
    const method = (options.method || 'GET').toUpperCase();
    if (method !== 'GET' && !state.csrf) await getCsrf();
    const headers = { ...(options.headers || {}) };
    if (options.body && !(options.body instanceof FormData)) headers['Content-Type'] = 'application/json';
    if (method !== 'GET' && state.csrf) headers['X-XSRF-TOKEN'] = state.csrf;
    const response = await fetch(url, { ...options, method, headers, credentials: 'same-origin' });
    const contentType = response.headers.get('content-type') || '';
    const data = contentType.includes('application/json') ? await response.json() : null;
    if (!response.ok) {
      if (response.status === 401) showAuth();
      const message = data?.message || (response.status === 403 ? 'Administrator access is required for this view.' : 'The request could not be completed.');
      const error = new Error(message);
      error.status = response.status;
      error.data = data;
      throw error;
    }
    return data;
  }

  function showAuth(message = '') {
    state.user = null;
    $('#auth-view').classList.remove('d-none');
    $('#app-view').classList.add('d-none');
    setAuthMessage(message, message ? 'danger' : '');
  }

  function setAuthMessage(message, type = 'danger') {
    const box = $('#auth-message');
    box.className = `alert alert-${type} ${message ? '' : 'd-none'}`;
    box.textContent = message;
  }

  function setRecordMessage(message = '', type = 'danger') {
    const box = $('#record-message');
    box.className = `alert alert-${type} ${message ? '' : 'd-none'}`;
    box.textContent = message;
  }

  function showToast(message) {
    $('#toast-message').textContent = message;
    state.toast.show();
  }

  function activateAuthTab(register) {
    $('#login-form').classList.toggle('d-none', register);
    $('#register-form').classList.toggle('d-none', !register);
    $('#login-tab').classList.toggle('active', !register);
    $('#register-tab').classList.toggle('active', register);
    $('#login-tab').setAttribute('aria-selected', String(!register));
    $('#register-tab').setAttribute('aria-selected', String(register));
    $('#auth-heading').textContent = register ? 'Create your workspace account' : 'Sign in to your workspace';
    $('.auth-subtitle').textContent = register ? 'Accounts are created with standard user access.' : 'Use your account credentials to continue.';
    setAuthMessage('');
  }

  function showApp(user) {
    state.user = user;
    $('#sidebar').classList.remove('open');
    $('.main-shell').classList.remove('menu-open');
    $('#auth-view').classList.add('d-none');
    $('#app-view').classList.remove('d-none');
    $('#user-name').textContent = user.username;
    $('#user-avatar').textContent = user.username.slice(0, 1).toUpperCase();
    $('#user-role').textContent = user.role;
    const admin = user.role === 'ADMIN';
    $('#topbar-logout').classList.toggle('d-none', admin);
    $('#app-view').classList.toggle('user-mode', !admin);
    $('#sidebar').classList.toggle('d-none', !admin);
    $$('.admin-only').forEach((element) => element.classList.toggle('d-none', !admin));
    $('#admin-dashboard').classList.toggle('d-none', !admin);
    $('#user-portal').classList.toggle('d-none', admin);
    $('#export-report').classList.toggle('d-none', !admin);
    $('#add-record').classList.toggle('d-none', !admin);
    if (admin) {
      $('#topbar-section').textContent = 'Overview';
      loadDashboard().catch(handleError);
    } else {
      renderUserPortal();
    }
  }

  function renderUserPortal() {
    const portal = $('#user-portal');
    portal.innerHTML = `<section class="user-portal"><div class="portal-head"><div><p class="eyebrow">SECURE SUBMISSION</p><h1>Submit a data record</h1><p>Each submission is validated against saved data before it is accepted.</p></div><span class="validation-note"><i class="bi bi-shield-check"></i> Duplicate prevention active</span></div><div class="panel portal-panel"><form id="portal-record-form" novalidate><div class="row g-3"><div class="col-md-6"><label class="form-label" for="portal-id">Unique ID <span class="required-mark">*</span></label><input id="portal-id" class="form-control" maxlength="80" required placeholder="e.g. CUST-1048"></div><div class="col-md-6"><label class="form-label" for="portal-category">Category <span class="required-mark">*</span></label><input id="portal-category" class="form-control" maxlength="60" required placeholder="e.g. Customer"></div><div class="col-md-6"><label class="form-label" for="portal-name">Full name <span class="required-mark">*</span></label><input id="portal-name" class="form-control" maxlength="120" required></div><div class="col-md-6"><label class="form-label" for="portal-email">Email</label><input id="portal-email" class="form-control" type="email" maxlength="160" placeholder="name@example.com"></div><div class="col-12"><label class="form-label" for="portal-details">Record details <span class="required-mark">*</span></label><textarea id="portal-details" class="form-control" maxlength="500" rows="3" required></textarea></div></div><button class="btn btn-primary mt-3" type="submit"><i class="bi bi-shield-check me-1"></i> Validate and submit</button><div id="portal-result" class="portal-result" aria-live="polite"></div></form></div></section>`;
    $('#topbar-section').textContent = 'Submit data';
    $('#portal-record-form').addEventListener('submit', submitPortalRecord);
  }

  async function loadDashboard() {
    const [stats, records] = await Promise.all([api('/api/dashboard'), api('/api/records')]);
    state.records = records;
    state.attempts = stats.recentAttempts || [];
    $('#total-records').textContent = stats.totalRecords;
    $('#unique-records').textContent = stats.uniqueRecords;
    $('#duplicate-count').textContent = stats.duplicatesDetected;
    const rate = stats.totalRecords + stats.duplicatesDetected === 0 ? 100 : Math.round(stats.uniqueRecords / (stats.totalRecords + stats.duplicatesDetected) * 100);
    $('#integrity-rate').innerHTML = `${rate}<span class="metric-suffix">%</span>`;
    $('#nav-record-count').textContent = stats.totalRecords;
    $('#nav-duplicate-count').textContent = stats.duplicatesDetected;
    renderCategoryChart(stats.categoryCounts || {});
    renderRecentActivity(state.attempts);
    renderRecords(records);
    updateCategoryFilter(stats.categoryCounts || {});
  }

  function renderCategoryChart(counts) {
    const labels = Object.keys(counts);
    const values = Object.values(counts);
    $('#chart-empty').classList.toggle('d-none', labels.length > 0);
    if (state.chart) state.chart.destroy();
    state.chart = new Chart($('#category-chart'), {
      type: 'doughnut',
      data: { labels: labels.length ? labels : ['No records'], datasets: [{ data: labels.length ? values : [1], backgroundColor: ['#27785a', '#79c69b', '#e7aa6e', '#79b5c8', '#d07158', '#8eaa67', '#5a7770'], borderWidth: 3, borderColor: '#ffffff', hoverOffset: 4 }] },
      options: { maintainAspectRatio: false, cutout: '67%', plugins: { legend: { position: 'bottom', labels: { color: '#66766e', usePointStyle: true, pointStyle: 'circle', boxWidth: 7, boxHeight: 7, padding: 14, font: { family: 'DM Sans', size: 10 } } }, tooltip: { enabled: labels.length > 0 } } }
    });
  }

  function renderRecentActivity(attempts) {
    const container = $('#recent-activity');
    if (!attempts.length) {
      container.innerHTML = '<div class="activity-empty">No duplicate attempts recorded. Your data is looking clean.</div>';
      return;
    }
    container.innerHTML = attempts.slice(0, 5).map((attempt) => `<div class="activity-row"><span class="activity-icon"><i class="bi bi-intersect"></i></span><div class="activity-copy"><strong>${escapeHtml(attempt.externalId)} blocked</strong><span>${escapeHtml(attempt.reason === 'EXTERNAL_ID' ? 'Existing unique ID' : 'Matching record content')} · by ${escapeHtml(attempt.submittedBy)}</span></div><time class="activity-time">${formatTime(attempt.attemptedAt)}</time></div>`).join('');
  }

  function updateCategoryFilter(counts) {
    const select = $('#category-filter');
    const selected = select.value;
    select.innerHTML = '<option value="">All categories</option>' + Object.keys(counts).sort().map((category) => `<option value="${escapeHtml(category)}">${escapeHtml(category)}</option>`).join('');
    select.value = selected;
  }

  function renderRecords(records) {
    state.records = records;
    const body = $('#records-body');
    const admin = state.user?.role === 'ADMIN';
    body.innerHTML = records.map((record) => `<tr><td><div class="record-cell"><span class="record-avatar">${escapeHtml(record.name.slice(0, 1).toUpperCase())}</span><span><span class="record-title">${escapeHtml(record.name)}</span><span class="record-subtitle">${escapeHtml(record.externalId)}${record.email ? ` · ${escapeHtml(record.email)}` : ''}</span></span></div></td><td><span class="category-tag">${escapeHtml(record.category)}</span></td><td class="detail-cell" title="${escapeHtml(record.details)}">${escapeHtml(record.details)}</td><td>${escapeHtml(record.createdBy)}</td><td>${formatDate(record.createdAt)}</td><td class="${admin ? '' : 'd-none'}"><div class="action-buttons"><button class="icon-button" type="button" data-action="edit" data-id="${record.id}" title="Edit record" aria-label="Edit ${escapeHtml(record.externalId)}"><i class="bi bi-pencil"></i></button><button class="icon-button delete" type="button" data-action="delete" data-id="${record.id}" title="Delete record" aria-label="Delete ${escapeHtml(record.externalId)}"><i class="bi bi-trash3"></i></button></div></td></tr>`).join('');
    $('#records-empty').classList.toggle('d-none', records.length > 0);
    $('#record-summary').textContent = `Showing ${records.length} ${records.length === 1 ? 'record' : 'records'}`;
  }

  async function filterRecords() {
    const params = new URLSearchParams();
    if ($('#record-search').value.trim()) params.set('q', $('#record-search').value.trim());
    if ($('#category-filter').value) params.set('category', $('#category-filter').value);
    const records = await api(`/api/records${params.size ? `?${params}` : ''}`);
    renderRecords(records);
  }

  async function submitPortalRecord(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const result = $('#portal-result');
    result.innerHTML = '';
    if (!form.reportValidity()) return;
    const payload = { externalId: $('#portal-id').value, name: $('#portal-name').value, email: $('#portal-email').value, category: $('#portal-category').value, details: $('#portal-details').value };
    try {
      const data = await api('/api/records', { method: 'POST', body: JSON.stringify(payload) });
      if (data.duplicate) {
        result.innerHTML = `<div class="portal-message duplicate"><strong>Duplicate blocked.</strong> ${data.reason === 'EXTERNAL_ID' ? 'That unique ID is already in the database.' : 'The submitted content matches an existing record.'} The original record was not changed.</div>`;
      } else {
        form.reset();
        result.innerHTML = '<div class="portal-message success"><strong>Record accepted.</strong> It passed validation and is now stored as a unique record.</div>';
      }
    } catch (error) {
      result.innerHTML = `<div class="portal-message error">${escapeHtml(error.message)}</div>`;
    }
  }

  function openRecordModal(record = null) {
    state.editingId = record?.id || null;
    $('#record-modal-title').textContent = record ? 'Update data record' : 'Add data record';
    $('#save-record').innerHTML = record ? '<i class="bi bi-check2"></i> Save changes' : '<i class="bi bi-check2"></i> Validate and save';
    $('#record-form').reset();
    setRecordMessage('');
    $('#details-count').textContent = '0 / 500';
    if (record) {
      $('#record-external-id').value = record.externalId;
      $('#record-name').value = record.name;
      $('#record-email').value = record.email || '';
      $('#record-category').value = record.category;
      $('#record-details').value = record.details;
      $('#details-count').textContent = `${record.details.length} / 500`;
    }
    state.modal.show();
  }

  async function saveRecord(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!form.reportValidity()) return;
    const payload = { externalId: $('#record-external-id').value, name: $('#record-name').value, email: $('#record-email').value, category: $('#record-category').value, details: $('#record-details').value };
    const button = $('#save-record');
    button.disabled = true;
    try {
      const data = state.editingId
        ? await api(`/api/records/${state.editingId}`, { method: 'PUT', body: JSON.stringify(payload) })
        : await api('/api/records', { method: 'POST', body: JSON.stringify(payload) });
      if (data.duplicate) {
        setRecordMessage(data.reason === 'EXTERNAL_ID' ? 'That unique ID already exists. The record was not saved.' : 'The same record content already exists. The record was not saved.', 'warning');
        await loadDashboard();
        return;
      }
      state.modal.hide();
      showToast(state.editingId ? 'Record updated successfully.' : 'Record validated and saved.');
      await loadDashboard();
    } catch (error) {
      setRecordMessage(error.message, 'danger');
    } finally {
      button.disabled = false;
    }
  }

  async function loadDuplicateLog() {
    try {
      state.attempts = await api('/api/duplicate-attempts');
      const body = $('#duplicates-body');
      body.innerHTML = state.attempts.map((attempt) => `<tr><td>#${attempt.id}</td><td><strong>${escapeHtml(attempt.externalId)}</strong></td><td>${escapeHtml(attempt.submittedBy)}</td><td><span class="match-tag">${escapeHtml(attempt.reason === 'EXTERNAL_ID' ? 'UNIQUE ID' : 'CONTENT MATCH')}</span></td><td>${attempt.matchedRecordId ? `#${attempt.matchedRecordId}` : '—'}</td><td>${formatDate(attempt.attemptedAt)} ${formatClock(attempt.attemptedAt)}</td></tr>`).join('');
      $('#duplicates-empty').classList.toggle('d-none', state.attempts.length > 0);
      $('#duplicate-summary').textContent = `${state.attempts.length} recent ${state.attempts.length === 1 ? 'attempt' : 'attempts'} (maximum 250 shown)`;
    } catch (error) { handleError(error); }
  }

  function setSection(section) {
    const isDuplicates = section === 'duplicates';
    $('#record-section').classList.toggle('d-none', isDuplicates);
    $('#duplicate-section').classList.toggle('d-none', !isDuplicates);
    $('#topbar-section').textContent = isDuplicates ? 'Duplicate log' : section === 'records' ? 'Data records' : 'Overview';
    $$('.sidebar-link').forEach((link) => link.classList.toggle('active', link.dataset.nav === section || (section === 'overview' && link.dataset.nav === 'overview')));
    if (isDuplicates) loadDuplicateLog();
    if (section === 'records') $('#record-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
    else if (isDuplicates) $('#duplicate-section').scrollIntoView({ behavior: 'smooth', block: 'start' });
    else window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  async function exportReport() {
    try {
      const response = await fetch('/api/reports/duplicates.csv', { credentials: 'same-origin' });
      if (!response.ok) throw new Error('The report could not be downloaded.');
      const blob = await response.blob();
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = 'duplicate-report.csv';
      link.click();
      URL.revokeObjectURL(link.href);
    } catch (error) { showToast(error.message); }
  }

  function formatDate(value) { return new Intl.DateTimeFormat(undefined, { year: 'numeric', month: 'short', day: 'numeric' }).format(new Date(value)); }
  function formatClock(value) { return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(new Date(value)); }
  function formatTime(value) {
    const minutes = Math.max(0, Math.floor((Date.now() - new Date(value).getTime()) / 60000));
    if (minutes < 1) return 'just now';
    if (minutes < 60) return `${minutes}m ago`;
    if (minutes < 1440) return `${Math.floor(minutes / 60)}h ago`;
    return `${Math.floor(minutes / 1440)}d ago`;
  }

  function handleError(error) {
    if (error.status === 401) return;
    showToast(error.message || 'Something went wrong.');
  }

  function bindEvents() {
    $('#current-year').textContent = new Date().getFullYear();
    $('#login-tab').addEventListener('click', () => activateAuthTab(false));
    $('#register-tab').addEventListener('click', () => activateAuthTab(true));
    $('#login-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!event.currentTarget.reportValidity()) return;
      try {
        const user = await api('/api/auth/login', { method: 'POST', body: JSON.stringify({ username: $('#login-username').value, password: $('#login-password').value }) });
        showApp(user);
      } catch (error) { setAuthMessage(error.message); }
    });
    $('#register-form').addEventListener('submit', async (event) => {
      event.preventDefault();
      if (!event.currentTarget.reportValidity()) return;
      try {
        const user = await api('/api/auth/register', { method: 'POST', body: JSON.stringify({ username: $('#register-username').value, password: $('#register-password').value }) });
        activateAuthTab(false);
        $('#login-username').value = user.username;
        $('#login-password').focus();
        setAuthMessage('Account created. Sign in to submit a record.', 'success');
      } catch (error) { setAuthMessage(error.message); }
    });
    const signOut = async () => {
      try { await api('/api/auth/logout', { method: 'POST' }); } catch (_) { }
      state.csrf = null;
      showAuth();
      getCsrf().catch(handleError);
    };
    $('#logout-button').addEventListener('click', signOut);
    $('#topbar-logout').addEventListener('click', signOut);
    $('#add-record').addEventListener('click', () => openRecordModal());
    $('#record-form').addEventListener('submit', saveRecord);
    $('#record-details').addEventListener('input', (event) => { $('#details-count').textContent = `${event.target.value.length} / 500`; });
    $('#record-search').addEventListener('input', () => {
      clearTimeout(state.searchTimer);
      state.searchTimer = setTimeout(() => filterRecords().catch(handleError), 180);
    });
    $('#category-filter').addEventListener('change', () => filterRecords().catch(handleError));
    $('#records-body').addEventListener('click', async (event) => {
      const button = event.target.closest('[data-action]');
      if (!button) return;
      const record = state.records.find((item) => item.id === Number(button.dataset.id));
      if (button.dataset.action === 'edit' && record) openRecordModal(record);
      if (button.dataset.action === 'delete' && record && window.confirm(`Delete record ${record.externalId}? This cannot be undone.`)) {
        try { await api(`/api/records/${record.id}`, { method: 'DELETE' }); showToast('Record deleted.'); await loadDashboard(); }
        catch (error) { handleError(error); }
      }
    });
    $('#export-report').addEventListener('click', exportReport);
    $('#export-report-secondary').addEventListener('click', exportReport);
    $$('[data-nav]').forEach((link) => link.addEventListener('click', (event) => {
      event.preventDefault();
      const section = link.dataset.nav;
      if (state.user?.role !== 'ADMIN') return;
      setSection(section);
      $('#sidebar').classList.remove('open');
      $('.main-shell').classList.remove('menu-open');
    }));
    $('#mobile-menu').addEventListener('click', () => {
      $('#sidebar').classList.toggle('open');
      $('.main-shell').classList.toggle('menu-open');
    });
    $('.main-shell').addEventListener('click', (event) => {
      if ($('.main-shell').classList.contains('menu-open') && event.target.closest('.page-content')) {
        $('#sidebar').classList.remove('open');
        $('.main-shell').classList.remove('menu-open');
      }
    });
  }

  async function start() {
    state.modal = new bootstrap.Modal($('#record-modal'));
    state.toast = new bootstrap.Toast($('#app-toast'), { delay: 3200 });
    bindEvents();
    try {
      await getCsrf();
      const response = await fetch('/api/auth/me', { credentials: 'same-origin' });
      if (response.status === 204) {
        showAuth();
        return;
      }
      if (!response.ok) throw new Error('Unable to verify this session. Please sign in again.');
      showApp(await response.json());
    } catch (error) {
      showAuth();
      if (error.status !== 401) setAuthMessage(error.message);
    }
  }

  document.addEventListener('DOMContentLoaded', start);
})();
