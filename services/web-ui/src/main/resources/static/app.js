/* CardDemo Web UI —— 无框架单页应用，仅依赖浏览器原生能力。 */
'use strict';

const AUTH_KEY = 'carddemo.session';
const state = { session: null };

const appEl = () => document.getElementById('app');
const toastRoot = () => document.getElementById('toasts');

/* ---------------- 基础工具 ---------------- */

const esc = (value) => String(value ?? '').replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
}[ch]));

const money = (value) => value === null || value === undefined || value === ''
    ? '—'
    : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const dateText = (value) => (value ? String(value).slice(0, 10) : '—');

const dateTimeText = (value) => (value ? String(value).replace('T', ' ').slice(0, 19) : '—');

const statusBadge = (value) => (value === 'Y'
    ? '<span class="badge ok">正常</span>'
    : '<span class="badge bad">停用</span>');

const userTypeBadge = (value) => (value === 'A'
    ? '<span class="badge warn">管理员</span>'
    : '<span class="badge neutral">普通用户</span>');

const authBadge = (code) => (code === '00'
    ? '<span class="badge ok">批准</span>'
    : '<span class="badge bad">拒绝</span>');

function toast(message, type = 'info') {
    const el = document.createElement('div');
    el.className = `toast ${type}`;
    el.textContent = message;
    toastRoot().appendChild(el);
    setTimeout(() => el.remove(), 4500);
}

function errBox(err) {
    const details = err && err.details && err.details.length
        ? `<ul>${err.details.map((item) => `<li>${esc(item)}</li>`).join('')}</ul>`
        : '';
    return `<div class="alert error"><strong>${esc((err && err.code) || '请求失败')}</strong>`
        + `${err && err.message ? ` · ${esc(err.message)}` : ''}${details}</div>`;
}

function setBusy(button, busy, label = '处理中…') {
    if (!button) return;
    if (busy) {
        button.dataset.label = button.innerHTML;
        button.disabled = true;
        button.innerHTML = `<span class="spinner"></span>${esc(label)}`;
    } else {
        button.disabled = false;
        if (button.dataset.label) button.innerHTML = button.dataset.label;
    }
}

function formValues(form) {
    return Object.fromEntries(new FormData(form).entries());
}

/* ---------------- 会话与 API ---------------- */

function loadSession() {
    try {
        state.session = JSON.parse(sessionStorage.getItem(AUTH_KEY) || 'null');
    } catch (ignored) {
        state.session = null;
    }
}

function saveSession(session) {
    state.session = session;
    navOpenSection = null;
    sessionStorage.setItem(AUTH_KEY, JSON.stringify(session));
}

function clearSession() {
    state.session = null;
    navOpenSection = null;
    sessionStorage.removeItem(AUTH_KEY);
}

async function api(path, options = {}) {
    const headers = Object.assign({}, options.headers || {});
    if (options.body !== undefined) headers['Content-Type'] = 'application/json';
    if (state.session && state.session.token) headers['Authorization'] = `Bearer ${state.session.token}`;

    const response = await fetch(path, {
        method: options.method || 'GET',
        headers,
        body: options.body === undefined ? undefined : JSON.stringify(options.body)
    });

    const text = await response.text();
    let data = null;
    if (text) {
        try {
            data = JSON.parse(text);
        } catch (ignored) {
            data = { message: text };
        }
    }

    if (!response.ok) {
        const error = new Error((data && (data.message || data.error)) || `请求失败（HTTP ${response.status}）`);
        error.status = response.status;
        error.code = data && data.code;
        error.details = (data && data.details) || [];
        if (response.status === 401 && state.session) {
            clearSession();
            location.hash = '#/login';
            toast('登录已失效，请重新登录', 'error');
        }
        throw error;
    }
    return data;
}

/* ---------------- 布局 ---------------- */

const NAV_SECTIONS = [
    { key: 'menu', href: '#/menu', icon: '🏠', label: '首页', direct: true },
    {
        title: '账户与卡片',
        icon: '🗂️',
        children: [
            { key: 'portal', href: '#/portal', icon: '👤', label: '客户 360' },
            { key: 'accounts', href: '#/accounts', icon: '🧾', label: '账户视图' },
            { key: 'cards', href: '#/cards', icon: '💳', label: '信用卡' }
        ]
    },
    {
        title: '交易与支付',
        icon: '💳',
        children: [
            { key: 'transactions', href: '#/transactions', icon: '📄', label: '交易查询' },
            { key: 'transaction-new', href: '#/transactions/new', icon: '➕', label: '交易录入' },
            { key: 'bill-pay', href: '#/bill-pay', icon: '💸', label: '账单支付' },
            { key: 'payments', href: '#/payments', icon: '🏦', label: '支付' },
            { key: 'billing', href: '#/billing', icon: '📃', label: '账单' },
            { key: 'fx', href: '#/fx', icon: '💱', label: '汇率' },
            { key: 'postings', href: '#/postings', icon: '🧮', label: '过账' },
            { key: 'reports', href: '#/reports', icon: '📊', label: '交易报表' }
        ]
    },
    {
        title: '营销',
        icon: '🎁',
        children: [
            { key: 'premium-offers', href: '#/premium-offers', icon: '💎', label: '高端返现' },
            { key: 'premium-campaigns', href: '#/premium-campaigns', icon: '🎯', label: '返现活动', adminOnly: true }
        ]
    },
    {
        title: '信贷与风险',
        icon: '🛡️',
        children: [
            { key: 'lending', href: '#/lending', icon: '🏦', label: '贷款' },
            { key: 'authorizations', href: '#/authorizations', icon: '🛡️', label: '授权管理' },
            { key: 'risk', href: '#/risk', icon: '🚨', label: '风险', adminOnly: true }
        ]
    },
    {
        title: '运营管理',
        icon: '🛠️',
        adminOnly: true,
        children: [
            { key: 'ledger', href: '#/ledger', icon: '📚', label: '总账', adminOnly: true },
            { key: 'reconciliation', href: '#/reconciliation', icon: '⚖️', label: '对账与 EOD', adminOnly: true },
            { key: 'limits', href: '#/limits', icon: '🚦', label: '限额', adminOnly: true },
            { key: 'operations', href: '#/operations', icon: '🛠️', label: '运营', adminOnly: true },
            { key: 'users', href: '#/users', icon: '👥', label: '用户管理', adminOnly: true }
        ]
    }
];

let navOpenSection = null;

function visibleNavSections() {
    const isAdmin = state.session && state.session.user && state.session.user.userType === 'A';
    return NAV_SECTIONS
        .filter((section) => !section.adminOnly || isAdmin)
        .map((section) => section.children
            ? { ...section, children: section.children.filter((item) => !item.adminOnly || isAdmin) }
            : section)
        .filter((section) => section.direct || (section.children && section.children.length > 0));
}

function shell({ active, title, subtitle, content }) {
    const user = state.session ? state.session.user : null;
    const initials = user ? `${(user.firstName || '?').slice(0, 1)}${(user.lastName || '').slice(0, 1)}` : '?';
    const sections = visibleNavSections();
    const activeSection = sections.find((section) =>
        section.children && section.children.some((item) => item.key === active));
    if (activeSection) {
        navOpenSection = activeSection.title;
    } else if (active === 'menu') {
        navOpenSection = null;
    }

    const nav = `<div class="nav">${sections.map((section) => {
        if (section.direct) {
            return `
                <a href="${section.href}" class="nav-top ${active === section.key ? 'active' : ''}">
                    <span class="ico">${section.icon}</span>${esc(section.label)}
                </a>`;
        }
        const open = navOpenSection === section.title;
        const childActive = section.children.some((item) => item.key === active);
        return `
            <div class="nav-section ${open ? 'open' : ''}">
                <button type="button" class="nav-section-toggle ${childActive ? 'has-active' : ''}"
                        data-nav-section="${esc(section.title)}" aria-expanded="${open}">
                    <span class="ico">${section.icon}</span>
                    <span class="nav-label">${esc(section.title)}</span>
                    <span class="chev">${open ? '▾' : '▸'}</span>
                </button>
                <div class="nav-sub" ${open ? '' : 'hidden'}>
                    ${section.children.map((item) => `
                        <a href="${item.href}" class="${active === item.key ? 'active' : ''}">
                            <span class="ico">${item.icon}</span>${esc(item.label)}
                        </a>`).join('')}
                </div>
            </div>`;
    }).join('')}</div>`;

    appEl().innerHTML = `
        <div class="shell">
            <aside class="sidebar">
                <div class="brand">
                    <span class="dot"></span>
                    <div>CardDemo<small>信用卡核心系统</small></div>
                </div>
                ${nav}
                <div class="sidebar-foot">演示环境 · 数据为种子样例<br>CardDemo Java 微服务版</div>
            </aside>
            <main class="main">
                <div class="topbar">
                    <div>
                        <h2>${esc(title || '')}</h2>
                        <div class="sub">${esc(subtitle || '')}</div>
                    </div>
                    <div class="user-chip">
                        <div class="avatar">${esc(initials.toUpperCase())}</div>
                        <div>
                            <div><strong>${esc(user ? `${user.firstName} ${user.lastName}` : '')}</strong></div>
                            <div class="muted" style="font-size:11.5px">${esc(user ? user.userId : '')}
                                · ${user && user.userType === 'A' ? '管理员' : '普通用户'}</div>
                        </div>
                        <button class="btn ghost small" data-action="logout">退出</button>
                    </div>
                </div>
                <div class="content">${content}</div>
            </main>
        </div>`;

    const logout = document.querySelector('[data-action="logout"]');
    if (logout) {
        logout.addEventListener('click', () => {
            clearSession();
            location.hash = '#/login';
            toast('已退出登录');
        });
    }

    document.querySelectorAll('[data-nav-section]').forEach((button) => {
        button.addEventListener('click', () => {
            const section = button.closest('.nav-section');
            const willOpen = !section.classList.contains('open');
            document.querySelectorAll('.nav-section').forEach((other) => {
                other.classList.remove('open');
                const sub = other.querySelector('.nav-sub');
                if (sub) sub.hidden = true;
                const toggle = other.querySelector('[data-nav-section]');
                if (toggle) {
                    toggle.setAttribute('aria-expanded', 'false');
                    const chev = toggle.querySelector('.chev');
                    if (chev) chev.textContent = '▸';
                }
            });
            if (willOpen) {
                section.classList.add('open');
                const sub = section.querySelector('.nav-sub');
                if (sub) sub.hidden = false;
                button.setAttribute('aria-expanded', 'true');
                const chev = button.querySelector('.chev');
                if (chev) chev.textContent = '▾';
                navOpenSection = button.dataset.navSection;
            } else {
                navOpenSection = null;
            }
        });
    });
}

function pager(page, size, total, action = 'page') {
    const pages = Math.max(1, Math.ceil((total || 0) / size));
    return `
        <div class="pager">
            <span class="info">共 ${total} 条 · 第 ${Math.min(page + 1, pages)}/${pages} 页</span>
            <button type="button" class="btn ghost small" data-${action}="prev" ${page <= 0 ? 'disabled' : ''}>上一页</button>
            <button type="button" class="btn ghost small" data-${action}="next" ${page + 1 >= pages ? 'disabled' : ''}>下一页</button>
        </div>`;
}

/* ---------------- 页面：登录 ---------------- */

function pageLogin() {
    appEl().innerHTML = `
        <div class="login-wrap">
            <div class="login-card">
                <div class="login-brand">
                    <div class="mark">CD</div>
                    <div>
                        <h1>CardDemo 信用卡核心系统</h1>
                        <p>登录后进入账户、卡片、交易与授权管理</p>
                    </div>
                </div>
                <form id="login-form">
                    <div class="form-grid">
                        <label class="field">
                            <span>用户号</span>
                            <input id="login-user" name="userId" value="ADMIN001" maxlength="8"
                                   autocomplete="username" required>
                        </label>
                        <label class="field">
                            <span>密码</span>
                            <input id="login-pass" name="password" type="password" value="admin123"
                                   maxlength="64" autocomplete="current-password" required>
                        </label>
                    </div>
                    <div class="mt-16">
                        <button class="btn primary" type="submit" id="login-btn">登录</button>
                    </div>
                    <div id="login-error"></div>
                </form>
                <div class="demo-accounts">
                    <button type="button" class="chip" data-demo="admin">管理员 ADMIN001 / admin123</button>
                    <button type="button" class="chip" data-demo="user">用户 USER0001 / user1234</button>
                </div>
                <div class="login-foot">
                    演示账号已预填，可直接登录。管理员可额外访问“用户管理”。
                    本环境为 POC，接口尚未强制校验 token，请勿暴露到公网。
                </div>
            </div>
        </div>`;

    document.querySelectorAll('[data-demo]').forEach((chip) => {
        chip.addEventListener('click', () => {
            const admin = chip.dataset.demo === 'admin';
            document.getElementById('login-user').value = admin ? 'ADMIN001' : 'USER0001';
            document.getElementById('login-pass').value = admin ? 'admin123' : 'user1234';
            document.getElementById('login-error').innerHTML = '';
        });
    });

    const form = document.getElementById('login-form');
    const button = document.getElementById('login-btn');
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const errorBox = document.getElementById('login-error');
        errorBox.innerHTML = '';
        setBusy(button, true, '登录中…');
        try {
            const result = await api('/api/v1/auth/login', {
                method: 'POST',
                body: { userId: values.userId.trim(), password: values.password }
            });
            saveSession({ token: result.token, expiresAt: result.expiresAt, user: result.user });
            toast(`欢迎，${result.user.firstName}（${result.user.landingMenu === 'ADMIN' ? '管理员' : '普通用户'}）`, 'success');
            location.hash = '#/menu';
        } catch (error) {
            errorBox.innerHTML = errBox(error);
            setBusy(button, false);
        }
    });
}

/* ---------------- 页面：首页 ---------------- */

function pageMenu() {
    const user = state.session.user;
    const isAdmin = user.userType === 'A';
    const features = [
        { href: '#/accounts', icon: '🧾', title: '账户视图', text: '余额、额度、账期与客户资料一屏查看' },
        { href: '#/cards', icon: '💳', title: '信用卡管理', text: '按账户查询卡片、查看详情与维护卡状态' },
        { href: '#/transactions', icon: '📄', title: '交易查询', text: '按交易号/卡号/账户检索交易明细' },
        { href: '#/transactions/new', icon: '➕', title: '交易录入', text: '人工补录交易，支持类型与类别选择' },
        { href: '#/bill-pay', icon: '💸', title: '账单支付', text: '全额还款，实时更新账户余额' },
        { href: '#/payments', icon: '🏦', title: '支付', text: '受理支付指令、自动筛查，接受、清算与退汇' },
        { href: '#/billing', icon: '📃', title: '账单', text: '生成信用卡账单，查看利息与最低还款并全额支付' },
        { href: '#/fx', icon: '💱', title: '汇率', text: '维护汇率、获取 5 分钟有效报价并执行换汇' },
        { href: '#/postings', icon: '🧮', title: '过账', text: '按过账类型生成借贷分录，支持冲正与幂等重试' },
        { href: '#/reports', icon: '📊', title: '交易报表', text: '按日期区间生成交易明细与合计' },
        { href: '#/premium-offers', icon: '💎', title: '高端返现', text: '高端卡分期消费返现：查看可参与活动、报名并跟踪返现入账' },
        ...(isAdmin ? [{ href: '#/premium-campaigns', icon: '🎯', title: '返现活动', text: '创建活动、发布/暂停/结束并查看报名与返现转化效果' }] : []),
        { href: '#/authorizations', icon: '🛡️', title: '授权管理', text: '待授权汇总、明细与欺诈标记' },
        { href: '#/portal', icon: '👤', title: '客户 360', text: 'BFF 聚合额度位置、卡片、客户、最近交易与权益，并统一搜索' },
        { href: '#/lending', icon: '🏦', title: '贷款', text: '贷款申请、审批、放款、还款计划与还款' },
        ...(isAdmin ? [{ href: '#/risk', icon: '🚨', title: '风险', text: '欺诈评分与警报、AML 筛查、争议案件与逾期催收' }] : []),
        ...(isAdmin ? [{ href: '#/operations', icon: '🛠️', title: '运营', text: '批处理、报表、通知、文档、工作流、审计与参考数据' }] : []),
        ...(isAdmin ? [{ href: '#/limits', icon: '🚦', title: '限额', text: '客户/账户/渠道限额的创建、占用、释放与可用性检查' }] : []),
        ...(isAdmin ? [{ href: '#/ledger', icon: '📚', title: '总账', text: '科目表、日记账、试算平衡与期间关账' }] : []),
        ...(isAdmin ? [{ href: '#/reconciliation', icon: '⚖️', title: '对账与 EOD', text: '批次与总账对账、差异处理、EOD 作业' }] : []),
        ...(isAdmin ? [{ href: '#/users', icon: '👥', title: '用户管理', text: '维护登录用户与管理员账号' }] : [])
    ];

    shell({
        active: 'menu',
        title: `欢迎，${user.firstName}`,
        subtitle: `当前身份：${isAdmin ? '管理员' : '普通用户'} · ${user.userId}`,
        content: `
            <div class="card mb-16">
                <h3>今天的演示可以做什么</h3>
                <p class="muted" style="margin:0">
                    本界面连接 IAM、客户、卡片、账户、交易、授权、总账、对账、支付、账单、限额与汇率等微服务，
                    全部数据来自遗留 COBOL 系统的归一化种子。
                    可以先看账户 00000000001（额度 2020、欠款 1940、可用 80），再试一笔账单支付、支付指令或换汇报价。
                </p>
            </div>
            <div class="grid cols-3">
                ${features.map((feature) => `
                    <a class="feature" href="${feature.href}">
                        <div class="icon">${feature.icon}</div>
                        <h4>${esc(feature.title)}</h4>
                        <p>${esc(feature.text)}</p>
                    </a>`).join('')}
            </div>`
    });
}

/* ---------------- 钱路径工具 ---------------- */

function listOf(result) {
    if (Array.isArray(result)) return result;
    if (result && Array.isArray(result.items)) return result.items;
    return [];
}

const uuid = () => (typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `id-${Date.now()}-${Math.random().toString(16).slice(2, 10)}`);

const localDateTimeNow = () => {
    const now = new Date();
    const pad = (value) => String(value).padStart(2, '0');
    return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
        + `T${pad(now.getHours())}:${pad(now.getMinutes())}`;
};

const currentPeriod = () => localDateTimeNow().slice(0, 7);

/* <input type="datetime-local"> 的值是 yyyy-MM-ddTHH:mm，服务端要 LocalDateTime */
const isoLocalDateTime = (value) => (!value ? null : (value.length === 16 ? `${value}:00` : value));

/* ---------------- 页面：账户视图 ---------------- */

const acctUi = { accountId: '00000000001' };

const HOLD_STATUS_TEXT = { ACTIVE: '生效中', RELEASED: '已释放', EXPIRED: '已过期', CAPTURED: '已捕获' };

function holdStatusBadge(status) {
    const cls = status === 'ACTIVE' ? 'ok' : status === 'CAPTURED' ? 'warn' : 'neutral';
    return `<span class="badge ${cls}">${esc(HOLD_STATUS_TEXT[status] || status || '—')}</span>`;
}

function pageAccounts() {
    shell({
        active: 'accounts',
        title: '账户视图',
        subtitle: '账户 + 额度位置 + 冻结 + 客户 + 卡片组合视图（对应遗留 COACTVWC）',
        content: `
            <div class="card">
                <form class="inline-form" id="acct-form">
                    <label class="field grow">
                        <span>账户号（11 位）</span>
                        <input id="acct-id" name="accountId" value="${esc(acctUi.accountId)}" maxlength="11"
                               pattern="\\d{11}" required>
                    </label>
                    <button class="btn primary" type="submit">查询</button>
                </form>
                <div id="acct-result"></div>
            </div>`
    });

    const form = document.getElementById('acct-form');
    const box = document.getElementById('acct-result');
    const view = {
        accountId: acctUi.accountId,
        account: null,
        cards: [],
        customer: null,
        position: null,
        holds: [],
        product: null
    };

    const load = async (accountId) => {
        acctUi.accountId = accountId;
        view.accountId = accountId;
        box.innerHTML = '<div class="empty">加载中…</div>';
        const encoded = encodeURIComponent(accountId);
        try {
            const [account, cardsResult, position, holdsResult] = await Promise.all([
                api(`/api/v1/accounts/${encoded}`),
                api(`/api/v1/cards/by-account/${encoded}`),
                api(`/api/v1/accounts/${encoded}/credit-position`),
                api(`/api/v1/accounts/${encoded}/holds?status=ACTIVE`)
            ]);
            view.account = account;
            view.cards = cardsResult.items || [];
            view.position = position;
            view.holds = listOf(holdsResult);
            view.customer = view.cards.length > 0
                ? await api(`/api/v1/customers/${encodeURIComponent(view.cards[0].customerId)}`).catch(() => null)
                : null;
            view.product = position && position.productCode
                ? await api(`/api/v1/account-products/${encodeURIComponent(position.productCode)}`).catch(() => null)
                : null;
            render();
        } catch (error) {
            box.innerHTML = errBox(error);
        }
    };

    const render = () => {
        const { account, cards, customer, position, product, holds } = view;
        const available = Number(account.creditLimit) - Number(account.currentBalance);
        const aprText = product && product.apr !== null && product.apr !== undefined
            ? `${(Number(product.apr) * 100).toFixed(2)}%`
            : '—';
        box.innerHTML = `
            <div class="grid cols-3 mt-16">
                <div class="card">
                    <h3>账户 <span class="muted">${esc(account.accountId)}</span></h3>
                    <dl class="dl">
                        <dt>状态</dt><dd>${statusBadge(account.activeStatus)}</dd>
                        <dt>产品编码</dt><dd class="mono">${esc(position.productCode || account.productCode || '—')}</dd>
                        <dt>当前欠款余额</dt><dd class="mono">${money(account.currentBalance)}</dd>
                        <dt>信用额度</dt><dd class="mono">${money(account.creditLimit)}</dd>
                        <dt>可用额度</dt><dd class="mono"><strong>${money(available)}</strong></dd>
                        <dt>现金额度</dt><dd class="mono">${money(account.cashCreditLimit)}</dd>
                        <dt>本期贷 / 借</dt><dd class="mono">${money(account.currentCycleCredit)} / ${money(account.currentCycleDebit)}</dd>
                        <dt>开户 / 到期</dt><dd>${dateText(account.openDate)} / ${dateText(account.expirationDate)}</dd>
                        <dt>重发日</dt><dd>${dateText(account.reissueDate)}</dd>
                        <dt>账户组</dt><dd class="mono">${esc(account.groupId || '—')}</dd>
                    </dl>
                    <div class="hint mt-16">可用额度 = 信用额度 − 当前欠款 − 生效冻结合计（授权占用另计）。</div>
                </div>
                <div class="card">
                    <h3>额度位置 <span class="muted">CREDIT POSITION</span></h3>
                    <dl class="dl">
                        <dt>产品编码</dt><dd class="mono">${esc(position.productCode || '—')}</dd>
                        <dt>信用额度</dt><dd class="mono">${money(position.creditLimit)}</dd>
                        <dt>当前欠款</dt><dd class="mono">${money(position.currentBalance)}</dd>
                        <dt>冻结合计</dt><dd class="mono">${money(position.activeHoldAmount)}</dd>
                        <dt>可用额度</dt><dd class="mono"><strong>${money(position.availableCredit)}</strong></dd>
                    </dl>
                    ${product ? `
                    <h3 class="mt-16">产品 <span class="muted">${esc(product.productCode)}</span></h3>
                    <dl class="dl">
                        <dt>名称</dt><dd>${esc(product.name || '—')}</dd>
                        <dt>类别 / 币种</dt><dd>${esc(product.category || '—')} / ${esc(product.currency || '—')}</dd>
                        <dt>年利率 APR</dt><dd>${aprText}</dd>
                        <dt>月费</dt><dd class="mono">${money(product.monthlyFee)}</dd>
                        <dt>账单周期日</dt><dd>${product.statementCycleDay === null || product.statementCycleDay === undefined
                            ? '—' : esc(product.statementCycleDay)}</dd>
                    </dl>` : ''}
                </div>
                <div class="card">
                    <h3>客户</h3>
                    ${customer ? `
                        <dl class="dl">
                            <dt>客户号</dt><dd class="mono">${esc(customer.customerId)}</dd>
                            <dt>姓名</dt><dd>${esc(customer.firstName)} ${esc(customer.middleName || '')} ${esc(customer.lastName)}</dd>
                            <dt>地址</dt><dd>${esc(customer.addressLine1)}<br>${esc(customer.addressLine2 || '')}<br>${esc(customer.addressLine3)}</dd>
                            <dt>州 / 邮编</dt><dd>${esc(customer.stateCode)} / ${esc(customer.zipCode)}</dd>
                            <dt>电话</dt><dd>${esc(customer.phoneNumber1 || '—')}</dd>
                            <dt>SSN</dt><dd class="mono">${esc(customer.ssnMasked)}</dd>
                            <dt>出生日期</dt><dd>${dateText(customer.dateOfBirth)}</dd>
                            <dt>FICO</dt><dd>${esc(customer.ficoScore)}</dd>
                            <dt>主卡持有人</dt><dd>${customer.primaryCardHolder === 'Y' ? '是' : '否'}</dd>
                        </dl>`
                    : '<div class="empty">该账户暂无关联客户（未找到卡片映射）</div>'}
                </div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>生效中冻结 <span class="muted">${holds.length} 笔</span></h3>
                    <span class="hint">冻结占用可用额度，释放后立即归还</span>
                </div>
                <form class="inline-form" id="hold-form">
                    <label class="field"><span>金额 <span class="req">*</span></span>
                        <input name="amount" type="number" step="0.01" min="0.01" max="9999999999.99"
                               value="25.00" required></label>
                    <label class="field grow"><span>来源类型 <span class="req">*</span></span>
                        <input name="referenceType" value="AUTH" maxlength="20" required></label>
                    <label class="field grow"><span>来源 ID <span class="req">*</span></span>
                        <input name="referenceId" value="${esc(`ui-${uuid()}`)}" maxlength="64" required></label>
                    <label class="field"><span>到期时间（可选）</span>
                        <input name="expiresAt" type="datetime-local"></label>
                    <button class="btn primary" type="submit" id="hold-save">创建冻结</button>
                </form>
                <div id="hold-msg"></div>
                ${holds.length === 0 ? '<div class="empty">该账户当前没有生效中的冻结</div>' : `
                <div class="table-wrap mt-16"><table class="table">
                    <thead><tr><th>冻结号</th><th class="num">金额</th><th>状态</th><th>来源</th>
                        <th>创建时间</th><th>到期时间</th><th>操作</th></tr></thead>
                    <tbody>${holds.map((hold) => `
                        <tr>
                            <td class="mono">${esc(hold.holdId)}</td>
                            <td class="num mono">${money(hold.amount)}</td>
                            <td>${holdStatusBadge(hold.status)}</td>
                            <td>${esc(hold.referenceType)} · <span class="mono">${esc(hold.referenceId)}</span></td>
                            <td class="nowrap">${dateTimeText(hold.createdAt)}</td>
                            <td class="nowrap">${dateTimeText(hold.expiresAt)}</td>
                            <td class="actions">${hold.status === 'ACTIVE'
                                ? `<button class="btn danger small" data-release="${esc(hold.holdId)}">释放</button>`
                                : '—'}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
            </div>
            <div class="card mt-16">
                <div class="section-title"><h3>关联卡片</h3>
                    <a class="btn ghost small" href="#/cards">前往卡片管理</a></div>
                ${cards.length === 0 ? '<div class="empty">暂无卡片</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>卡号</th><th>卡面姓名</th><th>到期日</th><th>状态</th><th>操作</th></tr></thead>
                    <tbody>${cards.map((card) => `
                        <tr>
                            <td class="mono">${esc(card.cardNumber)}</td>
                            <td>${esc(card.embossedName)}</td>
                            <td>${dateText(card.expiryDate)}</td>
                            <td>${statusBadge(card.activeStatus)}</td>
                            <td><a class="btn ghost small" href="#/cards/${encodeURIComponent(card.cardNumber)}">详情 / 维护</a></td>
                        </tr>`).join('')}
                    </tbody>
                </table></div>`}
            </div>`;
        wire();
    };

    const wire = () => {
        const holdForm = document.getElementById('hold-form');
        holdForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const values = formValues(holdForm);
            const msg = document.getElementById('hold-msg');
            const button = document.getElementById('hold-save');
            msg.innerHTML = '';
            setBusy(button, true, '创建中…');
            try {
                const hold = await api(`/api/v1/accounts/${encodeURIComponent(view.accountId)}/holds`, {
                    method: 'POST',
                    body: {
                        amount: Number(values.amount),
                        referenceType: values.referenceType.trim(),
                        referenceId: values.referenceId.trim(),
                        expiresAt: isoLocalDateTime(values.expiresAt)
                    }
                });
                toast(`冻结已创建：${money(hold.amount)}（${hold.holdId}）`, 'success');
                await load(view.accountId);
            } catch (error) {
                msg.innerHTML = errBox(error);
                setBusy(button, false);
            }
        });

        box.querySelectorAll('[data-release]').forEach((button) => {
            button.addEventListener('click', async () => {
                const holdId = button.dataset.release;
                if (!window.confirm(`确认释放冻结 ${holdId}？释放后可用额度立即归还。`)) return;
                setBusy(button, true, '释放中…');
                try {
                    await api(
                        `/api/v1/accounts/${encodeURIComponent(view.accountId)}/holds/${encodeURIComponent(holdId)}`,
                        { method: 'DELETE' });
                    toast('冻结已释放', 'success');
                    await load(view.accountId);
                } catch (error) {
                    toast(`${error.code || '释放失败'}：${error.message}`, 'error');
                    setBusy(button, false);
                }
            });
        });
    };

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        load(document.getElementById('acct-id').value.trim());
    });
    load(acctUi.accountId);
}

/* ---------------- 页面：卡片列表 ---------------- */

const cardsUi = { accountId: '00000000001', customerId: '', page: 0 };

function pageCards() {
    shell({
        active: 'cards',
        title: '信用卡管理',
        subtitle: '按账户或客户查询卡片，维护卡面姓名与状态（对应遗留 COCRDLIC/UPC）',
        content: `
            <div class="card">
                <form class="inline-form" id="cards-form">
                    <label class="field grow"><span>账户号（可选，11 位）</span>
                        <input id="cards-account" value="${esc(cardsUi.accountId)}" maxlength="11" pattern="\\d{11}"></label>
                    <label class="field grow"><span>客户号（可选，9 位）</span>
                        <input id="cards-customer" value="${esc(cardsUi.customerId)}" maxlength="9" pattern="\\d{9}"></label>
                    <button class="btn primary" type="submit">查询</button>
                </form>
                <div id="cards-result"></div>
            </div>`
    });

    const form = document.getElementById('cards-form');
    const box = document.getElementById('cards-result');

    const load = async (page) => {
        cardsUi.page = Math.max(0, page);
        cardsUi.accountId = document.getElementById('cards-account').value.trim();
        cardsUi.customerId = document.getElementById('cards-customer').value.trim();
        box.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(cardsUi.page), size: '7' });
        if (cardsUi.accountId) params.set('accountId', cardsUi.accountId);
        if (cardsUi.customerId) params.set('customerId', cardsUi.customerId);
        try {
            const result = await api(`/api/v1/cards?${params.toString()}`);
            const items = result.items || [];
            box.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的卡片</div>' : `
                <div class="table-wrap mt-16"><table class="table">
                    <thead><tr><th>卡号</th><th>账户</th><th>客户</th><th>卡面姓名</th><th>到期日</th><th>状态</th><th>操作</th></tr></thead>
                    <tbody>${items.map((card) => `
                        <tr>
                            <td class="mono">${esc(card.cardNumber)}</td>
                            <td class="mono">${esc(card.accountId)}</td>
                            <td class="mono">${esc(card.customerId)}</td>
                            <td>${esc(card.embossedName)}</td>
                            <td>${dateText(card.expiryDate)}</td>
                            <td>${statusBadge(card.activeStatus)}</td>
                            <td class="actions"><a class="btn ghost small" href="#/cards/${encodeURIComponent(card.cardNumber)}">查看 / 维护</a></td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(cardsUi.page, result.size || 7, result.total || 0)}`;
            box.querySelectorAll('[data-page]').forEach((button) => {
                button.addEventListener('click', () => {
                    load(cardsUi.page + (button.dataset.page === 'next' ? 1 : -1));
                });
            });
        } catch (error) {
            box.innerHTML = errBox(error);
        }
    };

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        load(0);
    });
    load(cardsUi.page);
}

/* ---------------- 页面：卡片详情 / 维护 ---------------- */

async function pageCardDetail(cardNumber) {
    shell({
        active: 'cards',
        title: '卡片详情',
        subtitle: `卡号 ${cardNumber}`,
        content: '<div class="card" id="card-detail"><div class="empty">加载中…</div></div>'
    });
    const box = document.getElementById('card-detail');
    try {
        const card = await api(`/api/v1/cards/${encodeURIComponent(cardNumber)}`);
        const [year, month, day] = String(card.expiryDate).split('-');
        box.innerHTML = `
            <div class="grid cols-2">
                <div>
                    <h3>卡片信息</h3>
                    <dl class="dl">
                        <dt>卡号</dt><dd class="mono">${esc(card.cardNumber)}</dd>
                        <dt>所属账户</dt><dd class="mono">${esc(card.accountId)}</dd>
                        <dt>所属客户</dt><dd class="mono">${esc(card.customerId)}</dd>
                        <dt>卡面姓名</dt><dd>${esc(card.embossedName)}</dd>
                        <dt>到期日</dt><dd>${dateText(card.expiryDate)}（日 ${esc(day)} 不可修改）</dd>
                        <dt>状态</dt><dd>${statusBadge(card.activeStatus)}</dd>
                        <dt>版本号</dt><dd class="mono">${esc(card.version)}</dd>
                    </dl>
                    <div class="hint mt-16">CVV 仅存储于服务端，不对外返回，也不能通过本页面修改。</div>
                </div>
                <form id="card-form">
                    <h3>维护卡片</h3>
                    <div class="form-grid">
                        <label class="field span-2"><span>卡面姓名 <span class="req">*</span></span>
                            <input name="embossedName" value="${esc(card.embossedName)}" maxlength="50" required></label>
                        <label class="field"><span>卡状态 <span class="req">*</span></span>
                            <select name="activeStatus">
                                <option value="Y" ${card.activeStatus === 'Y' ? 'selected' : ''}>Y · 正常</option>
                                <option value="N" ${card.activeStatus === 'N' ? 'selected' : ''}>N · 停用</option>
                            </select></label>
                        <label class="field"><span>到期月 <span class="req">*</span></span>
                            <input name="expiryMonth" type="number" min="1" max="12" value="${esc(month)}" required></label>
                        <label class="field"><span>到期年 <span class="req">*</span></span>
                            <input name="expiryYear" type="number" min="1950" max="2099" value="${esc(year)}" required></label>
                    </div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="card-save">保存修改</button>
                        <a class="btn ghost" href="#/cards">返回列表</a>
                    </div>
                    <div id="card-msg"></div>
                </form>
            </div>`;

        const form = document.getElementById('card-form');
        const button = document.getElementById('card-save');
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const values = formValues(form);
            const msg = document.getElementById('card-msg');
            msg.innerHTML = '';
            setBusy(button, true, '保存中…');
            try {
                await api(`/api/v1/cards/${encodeURIComponent(cardNumber)}`, {
                    method: 'PUT',
                    body: {
                        embossedName: values.embossedName.trim(),
                        activeStatus: values.activeStatus,
                        expiryMonth: Number(values.expiryMonth),
                        expiryYear: Number(values.expiryYear)
                    }
                });
                toast('卡片已更新', 'success');
                await pageCardDetail(cardNumber);
            } catch (error) {
                msg.innerHTML = errBox(error);
                setBusy(button, false);
            }
        });
    } catch (error) {
        box.innerHTML = errBox(error);
    }
}

/* ---------------- 页面：交易列表 ---------------- */

const txnUi = { idPrefix: '', cardNumber: '', accountId: '', page: 0 };

function pageTransactions() {
    shell({
        active: 'transactions',
        title: '交易查询',
        subtitle: '按交易号前缀、卡号或账户查询（对应遗留 COTRN00C/01C）',
        content: `
            <div class="card">
                <form class="inline-form" id="txn-form">
                    <label class="field grow"><span>交易号前缀</span>
                        <input id="txn-id" value="${esc(txnUi.idPrefix)}" maxlength="16" placeholder="如 00000000000000"></label>
                    <label class="field grow"><span>卡号</span>
                        <input id="txn-card" value="${esc(txnUi.cardNumber)}" maxlength="16" pattern="\\d{16}"></label>
                    <label class="field grow"><span>账户号</span>
                        <input id="txn-account" value="${esc(txnUi.accountId)}" maxlength="11" pattern="\\d{11}"></label>
                    <button class="btn primary" type="submit">查询</button>
                    <a class="btn ghost" href="#/transactions/new">新增交易</a>
                </form>
                <div id="txn-result"></div>
            </div>`
    });

    const form = document.getElementById('txn-form');
    const box = document.getElementById('txn-result');

    const load = async (page) => {
        txnUi.page = Math.max(0, page);
        txnUi.idPrefix = document.getElementById('txn-id').value.trim();
        txnUi.cardNumber = document.getElementById('txn-card').value.trim();
        txnUi.accountId = document.getElementById('txn-account').value.trim();
        box.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(txnUi.page), size: '10' });
        if (txnUi.idPrefix) params.set('idPrefix', txnUi.idPrefix);
        if (txnUi.cardNumber) params.set('cardNumber', txnUi.cardNumber);
        if (txnUi.accountId) params.set('accountId', txnUi.accountId);
        try {
            const result = await api(`/api/v1/transactions?${params.toString()}`);
            const items = result.items || [];
            box.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的交易</div>' : `
                <div class="table-wrap mt-16"><table class="table">
                    <thead><tr><th>交易号</th><th>处理时间</th><th>卡号</th><th>类型 / 类别</th>
                        <th class="num">金额</th><th>描述</th><th>操作</th></tr></thead>
                    <tbody>${items.map((txn) => `
                        <tr>
                            <td class="mono">${esc(txn.transactionId)}</td>
                            <td class="nowrap">${dateTimeText(txn.processedTimestamp)}</td>
                            <td class="mono">${esc(txn.cardNumber)}</td>
                            <td>${esc(txn.typeDescription || txn.typeCode)} / ${esc(txn.categoryDescription || txn.categoryCode)}</td>
                            <td class="num mono">${money(txn.amount)}</td>
                            <td>${esc(txn.description)}</td>
                            <td class="actions"><a class="btn ghost small" href="#/transactions/${encodeURIComponent(txn.transactionId)}">详情</a></td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(txnUi.page, result.size || 10, result.total || 0)}`;
            box.querySelectorAll('[data-page]').forEach((button) => {
                button.addEventListener('click', () => load(txnUi.page + (button.dataset.page === 'next' ? 1 : -1)));
            });
        } catch (error) {
            box.innerHTML = errBox(error);
        }
    };

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        load(0);
    });
    load(txnUi.page);
}

/* ---------------- 页面：交易详情 ---------------- */

async function pageTransactionDetail(transactionId) {
    shell({
        active: 'transactions',
        title: '交易详情',
        subtitle: `交易号 ${transactionId}`,
        content: '<div class="card" id="txn-detail"><div class="empty">加载中…</div></div>'
    });
    const box = document.getElementById('txn-detail');
    try {
        const txn = await api(`/api/v1/transactions/${encodeURIComponent(transactionId)}`);
        box.innerHTML = `
            <dl class="dl">
                <dt>交易号</dt><dd class="mono">${esc(txn.transactionId)}</dd>
                <dt>账户 / 卡号</dt><dd class="mono">${esc(txn.accountId)} / ${esc(txn.cardNumber)}</dd>
                <dt>类型</dt><dd>${esc(txn.typeCode)} · ${esc(txn.typeDescription || '')}</dd>
                <dt>类别</dt><dd>${esc(txn.categoryCode)} · ${esc(txn.categoryDescription || '')}</dd>
                <dt>来源</dt><dd>${esc(txn.source)}</dd>
                <dt>金额</dt><dd class="mono"><strong>${money(txn.amount)}</strong></dd>
                <dt>描述</dt><dd>${esc(txn.description)}</dd>
                <dt>商户</dt><dd>${esc(txn.merchantName)}（${esc(txn.merchantId)}）<br>
                    ${esc(txn.merchantCity)} ${esc(txn.merchantZip)}</dd>
                <dt>原始时间</dt><dd>${dateTimeText(txn.originalTimestamp)}</dd>
                <dt>处理时间</dt><dd>${dateTimeText(txn.processedTimestamp)}</dd>
            </dl>
            <div class="form-actions">
                <a class="btn ghost" href="#/transactions">返回列表</a>
                <a class="btn ghost" href="#/bill-pay">去账单支付</a>
            </div>`;
    } catch (error) {
        box.innerHTML = errBox(error);
    }
}

/* ---------------- 页面：交易录入 ---------------- */

async function pageTransactionNew() {
    shell({
        active: 'transaction-new',
        title: '交易录入',
        subtitle: '人工补录一笔交易（对应遗留 COTRN02C）',
        content: '<div class="card" id="txn-new"><div class="empty">正在加载参照数据…</div></div>'
    });
    const box = document.getElementById('txn-new');
    try {
        const [types, categories] = await Promise.all([
            api('/api/v1/transaction-types'),
            api('/api/v1/transaction-categories')
        ]);
        const typeItems = types.items || [];
        const categoryItems = categories.items || [];
        const today = new Date().toISOString().slice(0, 10);

        box.innerHTML = `
            <form id="txn-new-form">
                <div class="form-grid">
                    <label class="field"><span>账户号 <span class="req">*</span></span>
                        <input name="accountId" value="00000000002" maxlength="11" pattern="\\d{11}" required></label>
                    <label class="field"><span>卡号（与账户一致）</span>
                        <input name="cardNumber" value="0683586198171516" maxlength="16" pattern="\\d{16}"></label>
                    <label class="field"><span>交易类型 <span class="req">*</span></span>
                        <select name="typeCode" id="txn-type">
                            ${typeItems.map((type) => `<option value="${esc(type.typeCode)}">${esc(type.typeCode)} · ${esc(type.description)}</option>`).join('')}
                        </select></label>
                    <label class="field"><span>交易类别 <span class="req">*</span></span>
                        <select name="categoryCode" id="txn-category"></select></label>
                    <label class="field"><span>来源 <span class="req">*</span></span>
                        <input name="source" value="POS TERM" maxlength="10" required></label>
                    <label class="field"><span>金额（-99999999.99） <span class="req">*</span></span>
                        <input name="amount" type="number" step="0.01" min="-99999999.99" max="99999999.99" value="25.99" required></label>
                    <label class="field"><span>原始日期 <span class="req">*</span></span>
                        <input name="originalDate" type="date" value="${today}" required></label>
                    <label class="field"><span>处理日期 <span class="req">*</span></span>
                        <input name="processedDate" type="date" value="${today}" required></label>
                    <label class="field span-2"><span>描述 <span class="req">*</span></span>
                        <input name="description" value="Purchase at Test Merchant" maxlength="100" required></label>
                    <label class="field"><span>商户号 <span class="req">*</span></span>
                        <input name="merchantId" value="800000123" maxlength="9" pattern="\\d{9}" required></label>
                    <label class="field"><span>商户名称 <span class="req">*</span></span>
                        <input name="merchantName" value="Test Merchant" maxlength="50" required></label>
                    <label class="field"><span>商户城市 <span class="req">*</span></span>
                        <input name="merchantCity" value="Springfield" maxlength="50" required></label>
                    <label class="field"><span>商户邮编 <span class="req">*</span></span>
                        <input name="merchantZip" value="10001" maxlength="10" required></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="txn-new-save">提交交易</button>
                    <a class="btn ghost" href="#/transactions">返回列表</a>
                </div>
                <div id="txn-new-msg"></div>
            </form>`;

        const form = document.getElementById('txn-new-form');
        const typeSelect = document.getElementById('txn-type');
        const categorySelect = document.getElementById('txn-category');

        const renderCategories = () => {
            const type = typeSelect.value;
            const filtered = categoryItems.filter((item) => item.typeCode === type);
            categorySelect.innerHTML = filtered.map((item) =>
                `<option value="${esc(item.categoryCode)}">${esc(item.categoryCode)} · ${esc(item.description)}</option>`).join('')
                || '<option value="">（该类型无类别）</option>';
        };
        typeSelect.addEventListener('change', renderCategories);
        renderCategories();

        const button = document.getElementById('txn-new-save');
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const values = formValues(form);
            const msg = document.getElementById('txn-new-msg');
            msg.innerHTML = '';
            setBusy(button, true, '提交中…');
            try {
                const created = await api('/api/v1/transactions', {
                    method: 'POST',
                    body: {
                        accountId: values.accountId.trim(),
                        cardNumber: values.cardNumber.trim() || null,
                        typeCode: values.typeCode,
                        categoryCode: values.categoryCode,
                        source: values.source.trim(),
                        description: values.description.trim(),
                        amount: Number(values.amount),
                        originalDate: values.originalDate,
                        processedDate: values.processedDate,
                        merchantId: values.merchantId.trim(),
                        merchantName: values.merchantName.trim(),
                        merchantCity: values.merchantCity.trim(),
                        merchantZip: values.merchantZip.trim()
                    }
                });
                toast(`交易已创建：${created.transactionId}`, 'success');
                location.hash = `#/transactions/${created.transactionId}`;
            } catch (error) {
                msg.innerHTML = errBox(error);
                setBusy(button, false);
            }
        });
    } catch (error) {
        box.innerHTML = errBox(error);
    }
}

/* ---------------- 页面：账单支付 ---------------- */

function pageBillPay() {
    shell({
        active: 'bill-pay',
        title: '账单支付',
        subtitle: '全额偿还账户当前欠款（对应遗留 COBIL00C）',
        content: `
            <div class="grid cols-2">
                <div class="card">
                    <h3>查询账户</h3>
                    <form class="inline-form" id="bill-lookup">
                        <label class="field grow"><span>账户号（11 位）</span>
                            <input id="bill-account" value="00000000001" maxlength="11" pattern="\\d{11}" required></label>
                        <button class="btn ghost" type="submit">查询余额</button>
                    </form>
                    <div id="bill-info"></div>
                </div>
                <div class="card">
                    <h3>确认支付</h3>
                    <p class="hint">支付金额恒等于账户当前欠款余额，支付后余额归零。余额 ≤ 0 时不可支付。</p>
                    <form id="bill-form">
                        <label class="checkbox"><input type="checkbox" name="confirm" id="bill-confirm"> 我确认执行全额还款</label>
                        <div class="form-actions">
                            <button class="btn primary" type="submit" id="bill-submit">执行支付</button>
                        </div>
                        <div id="bill-msg"></div>
                    </form>
                </div>
            </div>`
    });

    const info = document.getElementById('bill-info');
    let current = null;

    const lookup = async () => {
        const accountId = document.getElementById('bill-account').value.trim();
        info.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const account = await api(`/api/v1/accounts/${encodeURIComponent(accountId)}`);
            current = account;
            const available = Number(account.creditLimit) - Number(account.currentBalance);
            info.innerHTML = `
                <div class="grid cols-2 mt-16">
                    <div class="stat">
                        <div class="label">当前欠款余额</div>
                        <div class="value">${money(account.currentBalance)}</div>
                    </div>
                    <div class="stat">
                        <div class="label">可用额度</div>
                        <div class="value">${money(available)}</div>
                    </div>
                </div>
                <div class="hint mt-16">账户 ${esc(account.accountId)} · 额度 ${money(account.creditLimit)} · ${statusBadge(account.activeStatus)}</div>`;
        } catch (error) {
            current = null;
            info.innerHTML = errBox(error);
        }
    };

    document.getElementById('bill-lookup').addEventListener('submit', (event) => {
        event.preventDefault();
        lookup();
    });

    const form = document.getElementById('bill-form');
    const button = document.getElementById('bill-submit');
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const msg = document.getElementById('bill-msg');
        msg.innerHTML = '';
        if (!current) {
            msg.innerHTML = '<div class="alert error">请先查询账户余额</div>';
            return;
        }
        if (!document.getElementById('bill-confirm').checked) {
            msg.innerHTML = '<div class="alert error">请勾选确认后再提交</div>';
            return;
        }
        setBusy(button, true, '支付中…');
        try {
            const result = await api('/api/v1/bill-payments', {
                method: 'POST',
                body: { accountId: current.accountId, confirm: true }
            });
            msg.innerHTML = `
                <div class="alert success">
                    支付成功：交易号 <code>${esc(result.transactionId)}</code>，
                    金额 <strong>${money(result.amount)}</strong>，账户新余额 <strong>${money(result.newBalance)}</strong>。
                    <div class="mt-16"><a href="#/transactions/${encodeURIComponent(result.transactionId)}">查看交易</a></div>
                </div>`;
            toast('账单支付成功', 'success');
            await lookup();
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(button, false);
        }
    });

    lookup();
}

/* ---------------- 页面：交易报表 ---------------- */

function pageReports() {
    shell({
        active: 'reports',
        title: '交易报表',
        subtitle: '按处理日期闭区间统计交易明细与合计（对应遗留 CORPT00C / CBTRN03C）',
        content: `
            <div class="card">
                <form class="inline-form" id="report-form">
                    <label class="field grow"><span>开始日期</span>
                        <input id="report-start" type="date" value="2026-09-01" required></label>
                    <label class="field grow"><span>结束日期</span>
                        <input id="report-end" type="date" value="2026-09-30" required></label>
                    <label class="field grow"><span>卡号（可选）</span>
                        <input id="report-card" maxlength="16" pattern="\\d{16}"></label>
                    <button class="btn primary" type="submit">生成报表</button>
                </form>
                <div id="report-result"></div>
            </div>`
    });

    const form = document.getElementById('report-form');
    const box = document.getElementById('report-result');

    const run = async () => {
        const startDate = document.getElementById('report-start').value;
        const endDate = document.getElementById('report-end').value;
        const cardNumber = document.getElementById('report-card').value.trim();
        if (!startDate || !endDate) {
            box.innerHTML = '<div class="alert error">请选择开始与结束日期</div>';
            return;
        }
        const params = new URLSearchParams({ startDate, endDate, page: '0', size: '50' });
        if (cardNumber) params.set('cardNumber', cardNumber);
        box.innerHTML = '<div class="empty">统计中…</div>';
        try {
            const report = await api(`/api/v1/reports/transactions?${params.toString()}`);
            const items = report.items || [];
            box.innerHTML = `
                <div class="grid cols-3 mt-16">
                    <div class="stat"><div class="label">交易笔数</div><div class="value">${report.total}</div></div>
                    <div class="stat"><div class="label">区间合计</div><div class="value">${money(report.grandTotal)}</div></div>
                    <div class="stat"><div class="label">本页合计</div><div class="value">${money(report.pageTotal)}</div></div>
                </div>
                ${items.length === 0 ? '<div class="empty">该区间没有交易</div>' : `
                <div class="table-wrap mt-16"><table class="table">
                    <thead><tr><th>交易号</th><th>处理时间</th><th>卡号</th><th>类型 / 类别</th>
                        <th class="num">金额</th><th>描述</th></tr></thead>
                    <tbody>${items.map((txn) => `
                        <tr>
                            <td class="mono"><a href="#/transactions/${encodeURIComponent(txn.transactionId)}">${esc(txn.transactionId)}</a></td>
                            <td class="nowrap">${dateTimeText(txn.processedTimestamp)}</td>
                            <td class="mono">${esc(txn.cardNumber)}</td>
                            <td>${esc(txn.typeDescription || txn.typeCode)} / ${esc(txn.categoryDescription || txn.categoryCode)}</td>
                            <td class="num mono">${money(txn.amount)}</td>
                            <td>${esc(txn.description)}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${(report.accountTotals && report.accountTotals.length) ? `
                <div class="card mt-16">
                    <h3>按卡号合计</h3>
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>卡号</th><th class="num">合计</th></tr></thead>
                        <tbody>${report.accountTotals.map((row) => `
                            <tr><td class="mono">${esc(row.cardNumber)}</td><td class="num mono">${money(row.total)}</td></tr>`).join('')}
                        </tbody></table></div>
                </div>` : ''}`;
        } catch (error) {
            box.innerHTML = errBox(error);
        }
    };

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        run();
    });
    run();
}

/* ---------------- 页面：授权管理 ---------------- */

const authUi = { accountId: '00000000001', page: 0 };

async function pageAuthorizations() {
    shell({
        active: 'authorizations',
        title: '授权管理',
        subtitle: '待授权汇总、授权明细、欺诈标记与过期清理（对应遗留 COPAUS0C/1C/2C、CBPAUP0C）',
        content: `
            <div class="card">
                <form class="inline-form" id="auth-form">
                    <label class="field grow"><span>账户号（11 位）</span>
                        <input id="auth-account" value="${esc(authUi.accountId)}" maxlength="11" pattern="\\d{11}" required></label>
                    <button class="btn primary" type="submit">查询</button>
                </form>
                <div id="auth-result"></div>
            </div>`
    });

    const form = document.getElementById('auth-form');
    const box = document.getElementById('auth-result');

    const load = async (page) => {
        authUi.accountId = document.getElementById('auth-account').value.trim();
        authUi.page = Math.max(0, page);
        box.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const summary = await api(`/api/v1/pending-authorizations/${encodeURIComponent(authUi.accountId)}`)
                .catch((error) => (error.status === 404 ? null : Promise.reject(error)));
            const list = await api(`/api/v1/authorizations?accountId=${encodeURIComponent(authUi.accountId)}&page=${authUi.page}&size=10`);
            const items = list.items || [];

            box.innerHTML = `
                ${summary ? `
                <div class="grid cols-4 mt-16">
                    <div class="stat"><div class="label">信用额度</div><div class="value">${money(summary.creditLimit)}</div></div>
                    <div class="stat"><div class="label">待授权占用</div><div class="value">${money(summary.creditBalance)}</div></div>
                    <div class="stat"><div class="label">批准笔数</div><div class="value">${summary.approvedCount}<small>${money(summary.approvedAmount)}</small></div></div>
                    <div class="stat"><div class="label">拒绝笔数</div><div class="value">${summary.declinedCount}<small>${money(summary.declinedAmount)}</small></div></div>
                </div>` : '<div class="alert info mt-16">该账户暂无待授权汇总；新授权批准后会自动创建。</div>'}

                ${items.length === 0 ? '<div class="empty">暂无授权记录</div>' : `
                <div class="table-wrap mt-16"><table class="table">
                    <thead><tr><th>授权时间</th><th>卡号</th><th class="num">金额</th><th>结果</th>
                        <th>原因码</th><th>商户</th><th>欺诈</th><th>操作</th></tr></thead>
                    <tbody>${items.map((item) => `
                        <tr>
                            <td class="nowrap">${dateTimeText(item.authTimestamp)}</td>
                            <td class="mono">${esc(item.cardNumber)}</td>
                            <td class="num mono">${money(item.transactionAmount)}</td>
                            <td>${authBadge(item.responseCode)}</td>
                            <td class="mono">${esc(item.reasonCode)}</td>
                            <td>${esc(item.merchantName || '—')}</td>
                            <td>${item.fraudFlag === 'Y' ? '<span class="badge bad">已标记</span>' : '<span class="badge neutral">正常</span>'}</td>
                            <td>${item.fraudFlag === 'Y' ? '—' : `<button class="btn danger small" data-fraud="${esc(item.authorizationId)}" data-card="${esc(item.cardNumber)}">标记欺诈</button>`}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(authUi.page, list.size || 10, list.total || 0)}

                <div class="card mt-16">
                    <h3>过期授权清理</h3>
                    <p class="hint">删除早于“今天 − 过期天数”的授权明细，并回退其占用的信用额度。</p>
                    <form class="inline-form" id="purge-form">
                        <label class="field"><span>过期天数</span>
                            <input id="purge-days" type="number" min="1" max="365" value="5" style="width:120px"></label>
                        <button class="btn ghost" type="submit">执行清理</button>
                    </form>
                    <div id="purge-msg"></div>
                </div>`;

            box.querySelectorAll('[data-fraud]').forEach((button) => {
                button.addEventListener('click', async () => {
                    const id = button.dataset.fraud;
                    if (!window.confirm(`确认将授权 ${id} 标记为欺诈？该卡后续授权将被拒绝（5100）。`)) return;
                    setBusy(button, true, '处理中…');
                    try {
                        await api(`/api/v1/authorizations/${encodeURIComponent(id)}/fraud`, { method: 'POST' });
                        toast('已标记欺诈，卡号 ' + button.dataset.card + ' 后续授权将被拒绝', 'success');
                        await load(authUi.page);
                    } catch (error) {
                        toast(error.message, 'error');
                        setBusy(button, false);
                    }
                });
            });

            box.querySelectorAll('[data-page]').forEach((button) => {
                button.addEventListener('click', () => load(authUi.page + (button.dataset.page === 'next' ? 1 : -1)));
            });

            const purgeForm = document.getElementById('purge-form');
            purgeForm.addEventListener('submit', async (event) => {
                event.preventDefault();
                const days = Number(document.getElementById('purge-days').value);
                if (!window.confirm(`确认清理 ${days} 天前的授权明细？`)) return;
                const msg = document.getElementById('purge-msg');
                msg.innerHTML = '';
                try {
                    const result = await api('/api/v1/authorization-purges', {
                        method: 'POST',
                        body: { expiryDays: days }
                    });
                    msg.innerHTML = `<div class="alert success">
                        明细删除 ${result.detailsDeleted} 条，汇总删除 ${result.summariesDeleted} 个，
                        回退占用额度 ${money(result.creditBalanceReleased)}。</div>`;
                    await load(0);
                } catch (error) {
                    msg.innerHTML = errBox(error);
                }
            });
        } catch (error) {
            box.innerHTML = errBox(error);
        }
    };

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        load(0);
    });
    load(authUi.page);
}

/* ---------------- 页面：过账 ---------------- */

const postingsUi = { page: 0 };

const BATCH_STATUS_TEXT = { POSTED: '已过账', REVERSED: '已冲正' };

function batchStatusBadge(status) {
    const cls = status === 'POSTED' ? 'ok' : status === 'REVERSED' ? 'neutral' : 'warn';
    return `<span class="badge ${cls}">${esc(BATCH_STATUS_TEXT[status] || status || '—')}</span>`;
}

function directionBadge(direction) {
    return direction === 'DR'
        ? '<span class="badge warn">借 DR</span>'
        : '<span class="badge neutral">贷 CR</span>';
}

function legsTable(legs) {
    const rows = legs || [];
    if (rows.length === 0) {
        return '<div class="empty">无分录行</div>';
    }
    return `
        <div class="table-wrap"><table class="table">
            <thead><tr><th>行号</th><th>账户角色</th><th>方向</th><th>总账科目</th><th class="num">金额</th></tr></thead>
            <tbody>${rows.map((leg) => `
                <tr>
                    <td class="mono">${esc(leg.legNo)}</td>
                    <td>${esc(leg.accountRole || '—')}</td>
                    <td>${directionBadge(leg.direction)}</td>
                    <td class="mono">${esc(leg.ledgerAccountCode || leg.accountCode || '—')}</td>
                    <td class="num mono">${money(leg.amount)}</td>
                </tr>`).join('')}
            </tbody></table></div>`;
}

function renderBatch(box, batch) {
    box.innerHTML = `
        <h3>提交结果 <span class="muted">${esc(batch.batchId)}</span></h3>
        <dl class="dl">
            <dt>批次号</dt><dd class="mono">${esc(batch.batchId)}</dd>
            <dt>幂等键</dt><dd class="mono">${esc(batch.idempotencyKey)}</dd>
            <dt>类型</dt><dd>${esc(batch.typeCode)}${batch.typeName ? ` · ${esc(batch.typeName)}` : ''}</dd>
            <dt>账户</dt><dd class="mono">${esc(batch.accountId)}</dd>
            <dt>金额</dt><dd class="mono"><strong>${money(batch.amount)}</strong> ${esc(batch.currency || '')}</dd>
            <dt>状态</dt><dd>${batchStatusBadge(batch.status)}</dd>
            <dt>过账时间</dt><dd>${dateTimeText(batch.createdAt)}</dd>
            <dt>冲正时间</dt><dd>${dateTimeText(batch.reversedAt)}</dd>
            ${batch.reversalOf ? `<dt>冲正原批次</dt><dd class="mono">${esc(batch.reversalOf)}</dd>` : ''}
            ${batch.ledgerEntryId ? `<dt>总账分录</dt><dd class="mono">${esc(batch.ledgerEntryId)}</dd>` : ''}
            <dt>描述</dt><dd>${esc(batch.description || '—')}</dd>
        </dl>
        <h3 class="mt-16">分录行</h3>
        ${legsTable(batch.legs)}`;
}

function pagePostings() {
    shell({
        active: 'postings',
        title: '过账',
        subtitle: '按过账类型生成复式分录并驱动账户余额，幂等批次可冲正',
        content: `
            <div class="grid cols-2">
                <div class="card">
                    <h3>新建过账批次</h3>
                    <form id="posting-form">
                        <div class="form-grid">
                            <label class="field span-2"><span>幂等键 <span class="req">*</span></span>
                                <input name="idempotencyKey" value="${esc(`post-${uuid()}`)}" maxlength="64" required></label>
                            <label class="field"><span>过账类型 <span class="req">*</span></span>
                                <select name="typeCode" id="posting-type" required>
                                    <option value="">加载中…</option>
                                </select></label>
                            <label class="field"><span>账户号 <span class="req">*</span></span>
                                <input name="accountId" value="00000000001" maxlength="11" pattern="\\d{11}" required></label>
                            <label class="field"><span>金额 <span class="req">*</span></span>
                                <input name="amount" type="number" step="0.01" min="0.01" value="25.00" required></label>
                            <label class="field"><span>币种 <span class="req">*</span></span>
                                <input name="currency" value="USD" maxlength="3" required></label>
                            <label class="field span-2"><span>描述</span>
                                <input name="description" value="Purchase at Corner Grocery" maxlength="200"></label>
                        </div>
                        <div class="form-actions">
                            <button class="btn primary" type="submit" id="posting-save">提交过账</button>
                        </div>
                        <div class="hint mt-16">
                            同一幂等键重复提交只产生一个批次；类型决定借贷方向与总账科目。
                        </div>
                        <div id="posting-msg"></div>
                    </form>
                </div>
                <div class="card" id="posting-result">
                    <h3>提交结果</h3>
                    <div class="empty">提交后会显示批次信息与借贷分录行</div>
                </div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>最近批次</h3>
                    <span class="hint">按创建时间倒序，已过账批次可冲正</span>
                </div>
                <div id="postings-list"></div>
            </div>`
    });

    const form = document.getElementById('posting-form');
    const typeSelect = document.getElementById('posting-type');
    const button = document.getElementById('posting-save');
    const resultBox = document.getElementById('posting-result');
    const listBox = document.getElementById('postings-list');

    api('/api/v1/posting-types')
        .then((result) => {
            const types = listOf(result);
            typeSelect.innerHTML = types.length === 0
                ? '<option value="">（无可用过账类型）</option>'
                : types.map((type) => `
                    <option value="${esc(type.typeCode)}">${esc(type.typeCode)}${type.name ? ` · ${esc(type.name)}` : ''}</option>`
                ).join('');
        })
        .catch((error) => {
            typeSelect.innerHTML = '<option value="">（类型加载失败）</option>';
            toast(`${error.code || '加载失败'}：${error.message}`, 'error');
        });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('posting-msg');
        msg.innerHTML = '';
        setBusy(button, true, '过账中…');
        try {
            const batch = await api('/api/v1/posting-batches', {
                method: 'POST',
                body: {
                    idempotencyKey: values.idempotencyKey.trim(),
                    typeCode: values.typeCode,
                    accountId: values.accountId.trim(),
                    amount: Number(values.amount),
                    currency: values.currency.trim().toUpperCase(),
                    description: values.description ? values.description.trim() : null
                }
            });
            toast(`批次已过账：${batch.batchId}`, 'success');
            renderBatch(resultBox, batch);
            form.querySelector('[name="idempotencyKey"]').value = `post-${uuid()}`;
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(button, false);
        }
    });

    const loadList = async (page) => {
        postingsUi.page = Math.max(0, page);
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/posting-batches?page=${postingsUi.page}&size=10`);
            const items = listOf(result);
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">还没有过账批次</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>批次号</th><th>类型</th><th>账户</th><th class="num">金额</th>
                        <th>状态</th><th>时间</th><th>操作</th></tr></thead>
                    <tbody>${items.map((batch) => `
                        <tr>
                            <td class="mono">${esc(batch.batchId)}</td>
                            <td>${esc(batch.typeCode)}${batch.typeName ? `<div class="muted">${esc(batch.typeName)}</div>` : ''}</td>
                            <td class="mono">${esc(batch.accountId)}</td>
                            <td class="num mono">${money(batch.amount)}<div class="muted">${esc(batch.currency || '')}</div></td>
                            <td>${batchStatusBadge(batch.status)}</td>
                            <td class="nowrap">${dateTimeText(batch.createdAt)}</td>
                            <td class="actions">${batch.status === 'POSTED' && !batch.reversalOf
                                ? `<button class="btn danger small" data-reverse="${esc(batch.batchId)}">冲正</button>`
                                : '—'}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(postingsUi.page, result.size || 10, result.total || 0)}`;

            listBox.querySelectorAll('[data-page]').forEach((pagerButton) => {
                pagerButton.addEventListener('click', () => loadList(
                    postingsUi.page + (pagerButton.dataset.page === 'next' ? 1 : -1)));
            });
            listBox.querySelectorAll('[data-reverse]').forEach((reverseButton) => {
                reverseButton.addEventListener('click', async () => {
                    const batchId = reverseButton.dataset.reverse;
                    if (!window.confirm(`确认冲正批次 ${batchId}？将生成方向相反的分录并回退账户余额。`)) return;
                    setBusy(reverseButton, true, '冲正中…');
                    try {
                        const reversal = await api(
                            `/api/v1/posting-batches/${encodeURIComponent(batchId)}/reversal`,
                            { method: 'POST' });
                        toast(`已冲正，冲正批次 ${reversal.batchId}`, 'success');
                        renderBatch(resultBox, reversal);
                        await loadList(postingsUi.page);
                    } catch (error) {
                        toast(`${error.code || '冲正失败'}：${error.message}`, 'error');
                        setBusy(reverseButton, false);
                    }
                });
            });
        } catch (error) {
            listBox.innerHTML = errBox(error);
        }
    };

    loadList(postingsUi.page);
}

/* ---------------- 页面：总账 ---------------- */

const ledgerUi = { page: 0, accounts: [] };

const LEDGER_ACCOUNT_TYPE_TEXT = {
    ASSET: '资产',
    LIABILITY: '负债',
    EQUITY: '权益',
    REVENUE: '收入',
    EXPENSE: '费用'
};
const JOURNAL_STATUS_TEXT = { POSTED: '已记账', REVERSED: '已冲正' };
const PERIOD_STATUS_TEXT = { OPEN: '开放', CLOSED: '已关账' };

function journalStatusBadge(status) {
    const cls = status === 'REVERSED' ? 'neutral' : 'ok';
    return `<span class="badge ${cls}">${esc(JOURNAL_STATUS_TEXT[status] || status || '—')}</span>`;
}

function periodStatusBadge(status) {
    const cls = status === 'CLOSED' ? 'neutral' : 'ok';
    return `<span class="badge ${cls}">${esc(PERIOD_STATUS_TEXT[status] || status || '—')}</span>`;
}

function ledgerAccountOptions(accounts, selected) {
    if (accounts.length === 0) return '<option value="">（无科目）</option>';
    return accounts.map((account) => `
        <option value="${esc(account.accountCode)}" ${account.accountCode === selected ? 'selected' : ''}>
            ${esc(account.accountCode)} · ${esc(account.name)}
        </option>`).join('');
}

async function pageLedger() {
    if (!requireAdmin('ledger', '总账')) return;
    shell({
        active: 'ledger',
        title: '总账',
        subtitle: '科目表、日记账、试算平衡与期间关账（管理员）',
        content: `
            <div class="grid cols-2">
                <div class="card">
                    <div class="section-title">
                        <h3>科目表</h3>
                        <span class="hint" id="ledger-account-count"></span>
                    </div>
                    <div id="ledger-accounts"><div class="empty">加载中…</div></div>
                </div>
                <div class="card">
                    <div class="section-title">
                        <h3>会计期间</h3>
                        <span class="hint">关账后该期间不可再记账</span>
                    </div>
                    <div id="ledger-periods"><div class="empty">加载中…</div></div>
                </div>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>试算平衡</h3>
                    <form class="inline-form" id="trial-form">
                        <label class="field"><span>期间（YYYY-MM）</span>
                            <input id="trial-period" value="${esc(currentPeriod())}" maxlength="7"
                                   pattern="\\d{4}-\\d{2}" placeholder="2026-10" required></label>
                        <button class="btn primary" type="submit">计算</button>
                    </form>
                </div>
                <div id="trial-result"><div class="empty">选择期间后点击“计算”</div></div>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>日记账</h3>
                    <span class="hint">按记账时间倒序，含过账批次同步的分录</span>
                </div>
                <div id="journal-list"></div>
            </div>
            <div class="card">
                <h3>手工记账 <span class="muted">sourceType = MANUAL</span></h3>
                <form id="manual-form">
                    <div class="form-grid">
                        <label class="field"><span>来源类型</span>
                            <input value="MANUAL" disabled></label>
                        <label class="field"><span>来源 ID（自动生成）</span>
                            <input name="sourceId" value="${esc(`manual-${uuid()}`)}" maxlength="64" required></label>
                        <label class="field"><span>记账时间 postedAt</span>
                            <input name="postedAt" type="datetime-local" value="${esc(localDateTimeNow())}" required></label>
                        <label class="field"><span>币种</span>
                            <input name="currency" value="USD" maxlength="3" required></label>
                        <label class="field span-2"><span>描述</span>
                            <input name="description" value="Manual journal entry" maxlength="200"></label>
                    </div>
                    <div class="table-wrap mt-16"><table class="table">
                        <thead><tr><th>方向</th><th>科目</th><th class="num">金额</th></tr></thead>
                        <tbody>
                            <tr>
                                <td><span class="badge warn">借 DR</span></td>
                                <td><select name="debitAccount" id="manual-debit" required><option value="">加载中…</option></select></td>
                                <td class="num"><input name="debitAmount" type="number" step="0.01" min="0.01"
                                                       value="100.00" required></td>
                            </tr>
                            <tr>
                                <td><span class="badge neutral">贷 CR</span></td>
                                <td><select name="creditAccount" id="manual-credit" required><option value="">加载中…</option></select></td>
                                <td class="num"><input name="creditAmount" type="number" step="0.01" min="0.01"
                                                       value="100.00" required></td>
                            </tr>
                        </tbody></table></div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="manual-save">提交分录</button>
                    </div>
                    <div class="hint mt-16">两行借贷金额必须相等，否则服务端返回 UNBALANCED_JOURNAL。</div>
                    <div id="manual-msg"></div>
                </form>
            </div>`
    });

    const accountsBox = document.getElementById('ledger-accounts');
    const periodsBox = document.getElementById('ledger-periods');
    const trialBox = document.getElementById('trial-result');
    const journalBox = document.getElementById('journal-list');
    const debitSelect = document.getElementById('manual-debit');
    const creditSelect = document.getElementById('manual-credit');

    const loadAccounts = async () => {
        accountsBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api('/api/v1/ledger-accounts');
            const items = listOf(result);
            ledgerUi.accounts = items;
            document.getElementById('ledger-account-count').textContent = `${items.length} 个科目`;
            accountsBox.innerHTML = items.length === 0 ? '<div class="empty">暂无科目</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>科目</th><th>名称</th><th>类型</th><th>币种</th><th>状态</th></tr></thead>
                    <tbody>${items.map((account) => `
                        <tr>
                            <td class="mono">${esc(account.accountCode)}</td>
                            <td>${esc(account.name)}</td>
                            <td>${esc(LEDGER_ACCOUNT_TYPE_TEXT[account.accountType] || account.accountType || '—')}</td>
                            <td>${esc(account.currency || '—')}</td>
                            <td>${account.active === false
                                ? '<span class="badge bad">停用</span>'
                                : '<span class="badge ok">可用</span>'}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`;
            debitSelect.innerHTML = ledgerAccountOptions(items, '1100');
            creditSelect.innerHTML = ledgerAccountOptions(items, '2100');
        } catch (error) {
            accountsBox.innerHTML = errBox(error);
            const failure = '<option value="">（科目加载失败）</option>';
            debitSelect.innerHTML = failure;
            creditSelect.innerHTML = failure;
        }
    };

    const loadPeriods = async () => {
        periodsBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api('/api/v1/accounting-periods');
            const items = listOf(result);
            periodsBox.innerHTML = items.length === 0 ? '<div class="empty">暂无会计期间</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>期间</th><th>状态</th><th>关账时间</th><th>操作</th></tr></thead>
                    <tbody>${items.map((period) => `
                        <tr>
                            <td class="mono">${esc(period.period)}</td>
                            <td>${periodStatusBadge(period.status)}</td>
                            <td class="nowrap">${dateTimeText(period.closedAt)}</td>
                            <td class="actions">${period.status === 'CLOSED' ? '—'
                                : `<button class="btn danger small" data-close="${esc(period.period)}">关账</button>`}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`;

            periodsBox.querySelectorAll('[data-close]').forEach((closeButton) => {
                closeButton.addEventListener('click', async () => {
                    const period = closeButton.dataset.close;
                    if (!window.confirm(`确认关闭会计期间 ${period}？关账后该期间不可再记账。`)) return;
                    setBusy(closeButton, true, '关账中…');
                    try {
                        await api(`/api/v1/accounting-periods/${encodeURIComponent(period)}/close`,
                            { method: 'POST' });
                        toast(`期间 ${period} 已关账`, 'success');
                        await loadPeriods();
                    } catch (error) {
                        toast(`${error.code || '关账失败'}：${error.message}`, 'error');
                        setBusy(closeButton, false);
                    }
                });
            });
        } catch (error) {
            periodsBox.innerHTML = errBox(error);
        }
    };

    const runTrial = async (period) => {
        if (!period) {
            trialBox.innerHTML = '<div class="alert error">请填写期间（YYYY-MM）</div>';
            return;
        }
        trialBox.innerHTML = '<div class="empty">计算中…</div>';
        try {
            const report = await api(`/api/v1/trial-balance?period=${encodeURIComponent(period)}`);
            const items = listOf(report);
            const balanced = typeof report.balanced === 'boolean'
                ? report.balanced
                : Number(report.totalDebit) === Number(report.totalCredit);
            trialBox.innerHTML = `
                <div class="grid cols-3 mt-16">
                    <div class="stat"><div class="label">借方合计</div><div class="value">${money(report.totalDebit)}</div></div>
                    <div class="stat"><div class="label">贷方合计</div><div class="value">${money(report.totalCredit)}</div></div>
                    <div class="stat"><div class="label">是否平衡</div>
                        <div class="value">${balanced
                            ? '<span class="badge ok">平衡</span>'
                            : '<span class="badge bad">不平衡</span>'}</div></div>
                </div>
                ${items.length === 0 ? '<div class="empty">该期间没有日记账分录</div>' : `
                <div class="table-wrap mt-16"><table class="table">
                    <thead><tr><th>科目</th><th>名称</th><th class="num">借方合计</th>
                        <th class="num">贷方合计</th><th class="num">净额</th></tr></thead>
                    <tbody>${items.map((item) => {
                        const debit = item.debitTotal === undefined ? (item.totalDebit || 0) : item.debitTotal;
                        const credit = item.creditTotal === undefined ? (item.totalCredit || 0) : item.creditTotal;
                        const net = item.netBalance === undefined
                            ? Number(debit) - Number(credit)
                            : item.netBalance;
                        return `
                        <tr>
                            <td class="mono">${esc(item.accountCode)}</td>
                            <td>${esc(item.accountName || item.name || '—')}</td>
                            <td class="num mono">${money(debit)}</td>
                            <td class="num mono">${money(credit)}</td>
                            <td class="num mono">${money(net)}</td>
                        </tr>`;
                    }).join('')}
                    </tbody></table></div>`}`;
        } catch (error) {
            trialBox.innerHTML = errBox(error);
        }
    };

    const loadJournal = async (page) => {
        ledgerUi.page = Math.max(0, page);
        journalBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/journal-entries?page=${ledgerUi.page}&size=10`);
            const items = listOf(result);
            journalBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">暂无日记账</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>分录号</th><th>来源</th><th>描述</th><th class="num">借方合计</th>
                        <th class="num">贷方合计</th><th>状态</th><th>记账时间</th></tr></thead>
                    <tbody>${items.map((entry) => {
                        const lines = entry.lines || [];
                        const debit = lines.filter((line) => line.direction === 'DR')
                            .reduce((sum, line) => sum + Number(line.amount || 0), 0);
                        const credit = lines.filter((line) => line.direction === 'CR')
                            .reduce((sum, line) => sum + Number(line.amount || 0), 0);
                        return `
                        <tr>
                            <td class="mono">${esc(entry.entryId)}</td>
                            <td>${esc(entry.sourceType)}
                                <div class="muted mono">${esc(entry.sourceId || '')}</div></td>
                            <td>${esc(entry.description || '—')}</td>
                            <td class="num mono">${lines.length === 0 ? '—' : money(debit)}</td>
                            <td class="num mono">${lines.length === 0 ? '—' : money(credit)}</td>
                            <td>${journalStatusBadge(entry.status)}</td>
                            <td class="nowrap">${dateTimeText(entry.postedAt)}</td>
                        </tr>`;
                    }).join('')}
                    </tbody></table></div>`}
                ${pager(ledgerUi.page, result.size || 10, result.total || 0)}`;

            journalBox.querySelectorAll('[data-page]').forEach((pagerButton) => {
                pagerButton.addEventListener('click', () => loadJournal(
                    ledgerUi.page + (pagerButton.dataset.page === 'next' ? 1 : -1)));
            });
        } catch (error) {
            journalBox.innerHTML = errBox(error);
        }
    };

    const trialForm = document.getElementById('trial-form');
    trialForm.addEventListener('submit', (event) => {
        event.preventDefault();
        runTrial(document.getElementById('trial-period').value.trim());
    });

    const manualForm = document.getElementById('manual-form');
    const manualButton = document.getElementById('manual-save');
    manualForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(manualForm);
        const msg = document.getElementById('manual-msg');
        msg.innerHTML = '';
        if (values.debitAccount === values.creditAccount) {
            msg.innerHTML = '<div class="alert error">借方与贷方科目不能相同</div>';
            return;
        }
        if (Number(values.debitAmount) !== Number(values.creditAmount)) {
            msg.innerHTML = '<div class="alert error">两行金额必须相等，否则借贷不平衡</div>';
            return;
        }
        setBusy(manualButton, true, '提交中…');
        try {
            const entry = await api('/api/v1/journal-entries', {
                method: 'POST',
                body: {
                    sourceType: 'MANUAL',
                    sourceId: values.sourceId.trim(),
                    description: values.description ? values.description.trim() : null,
                    postedAt: isoLocalDateTime(values.postedAt),
                    currency: values.currency.trim().toUpperCase(),
                    lines: [
                        { accountCode: values.debitAccount, direction: 'DR', amount: Number(values.debitAmount) },
                        { accountCode: values.creditAccount, direction: 'CR', amount: Number(values.creditAmount) }
                    ]
                }
            });
            toast(`分录已记账：${entry.entryId}`, 'success');
            manualForm.querySelector('[name="sourceId"]').value = `manual-${uuid()}`;
            await loadJournal(0);
            await runTrial(document.getElementById('trial-period').value.trim());
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(manualButton, false);
        }
    });

    await loadAccounts();
    loadPeriods();
    runTrial(document.getElementById('trial-period').value.trim());
    loadJournal(ledgerUi.page);
}

/* ---------------- 页面：对账与 EOD ---------------- */

const reconUi = { businessDate: localDateTimeNow().slice(0, 10), page: 0, eodPage: 0 };

const RUN_STATUS_TEXT = { COMPLETED: '已完成', RUNNING: '执行中', FAILED: '失败' };
const RECON_CHECK_TYPE_TEXT = { POSTING_VS_JOURNAL: '批次 vs 日记账', TRIAL_BALANCE: '试算平衡' };
const RECON_ITEM_STATUS_TEXT = {
    MATCH: '匹配',
    MISSING_JOURNAL: '缺日记账',
    MISSING_BATCH: '缺批次',
    AMOUNT_MISMATCH: '金额不一致',
    UNBALANCED: '试算不平衡'
};

function runStatusBadge(status) {
    const cls = status === 'COMPLETED' ? 'ok' : status === 'RUNNING' ? 'warn' : status === 'FAILED' ? 'bad' : 'neutral';
    return `<span class="badge ${cls}">${esc(RUN_STATUS_TEXT[status] || status || '—')}</span>`;
}

function reconItemStatusBadge(status) {
    const cls = status === 'MATCH' ? 'ok' : status ? 'bad' : 'neutral';
    return `<span class="badge ${cls}">${esc(RECON_ITEM_STATUS_TEXT[status] || status || '—')}</span>`;
}

function matchedBadge(matched) {
    if (matched === null || matched === undefined) return '<span class="badge neutral">—</span>';
    return matched
        ? '<span class="badge ok">匹配</span>'
        : '<span class="badge bad">不匹配</span>';
}

function resolvedBadge(resolved) {
    return resolved
        ? '<span class="badge ok">已处理</span>'
        : '<span class="badge warn">待处理</span>';
}

function pageReconciliation() {
    if (!requireAdmin('reconciliation', '对账与 EOD')) return;
    if (!reconUi.businessDate) reconUi.businessDate = localDateTimeNow().slice(0, 10);

    shell({
        active: 'reconciliation',
        title: '对账与 EOD',
        subtitle: '按业务日期对账过账批次与总账日记账，登记差异处理并查看 EOD 作业（管理员）',
        content: `
            <div class="card">
                <div class="section-title">
                    <h3>执行对账 / EOD</h3>
                    <span class="hint">对账只读业务与总账，不修改任何账务数据</span>
                </div>
                <form class="inline-form" id="recon-form">
                    <label class="field grow"><span>业务日期</span>
                        <input id="recon-date" type="date" value="${esc(reconUi.businessDate)}" required></label>
                    <button class="btn primary" type="submit" id="recon-run">执行对账</button>
                    <button class="btn ghost" type="button" id="eod-run">执行 EOD</button>
                </form>
                <div class="hint mt-16">
                    EOD 当前步骤为对账：按同一业务日期触发对账，并把 EOD 运行与对账运行关联起来。
                </div>
                <div id="recon-action-msg"></div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>对账历史</h3>
                    <span class="hint">点击运行号查看摘要与差异明细</span>
                </div>
                <div id="recon-runs"></div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>EOD 历史</h3>
                    <span class="hint">最近 5 次 EOD 作业，含关联对账运行号</span>
                </div>
                <div id="eod-runs"></div>
            </div>`
    });

    const form = document.getElementById('recon-form');
    const runButton = document.getElementById('recon-run');
    const eodButton = document.getElementById('eod-run');
    const msg = document.getElementById('recon-action-msg');
    const runsBox = document.getElementById('recon-runs');
    const eodBox = document.getElementById('eod-runs');

    const businessDate = () => {
        reconUi.businessDate = document.getElementById('recon-date').value;
        return reconUi.businessDate;
    };

    const loadRuns = async (page) => {
        reconUi.page = Math.max(0, page);
        runsBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/reconciliation-runs?page=${reconUi.page}&size=10`);
            const items = listOf(result);
            runsBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">还没有对账运行</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>运行号</th><th>业务日期</th><th>期间</th><th>状态</th><th>是否匹配</th>
                        <th>批次 / 日记账</th><th class="num">差异数</th><th>开始时间</th></tr></thead>
                    <tbody>${items.map((run) => `
                        <tr>
                            <td class="mono"><a href="#/reconciliation/${encodeURIComponent(run.runId)}">${esc(run.runId)}</a></td>
                            <td class="nowrap">${dateText(run.businessDate)}</td>
                            <td class="mono">${esc(run.period || '—')}</td>
                            <td>${runStatusBadge(run.status)}</td>
                            <td>${matchedBadge(run.matched)}</td>
                            <td class="mono">${esc(run.batchCount ?? '—')} / ${esc(run.journalCount ?? '—')}</td>
                            <td class="num mono">${esc(run.mismatchCount ?? '—')}</td>
                            <td class="nowrap">${dateTimeText(run.startedAt)}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(reconUi.page, result.size || 10, result.total || 0)}`;

            runsBox.querySelectorAll('[data-page]').forEach((button) => {
                button.addEventListener('click', () => loadRuns(
                    reconUi.page + (button.dataset.page === 'next' ? 1 : -1)));
            });
        } catch (error) {
            runsBox.innerHTML = errBox(error);
        }
    };

    const loadEodRuns = async (page) => {
        reconUi.eodPage = Math.max(0, page);
        eodBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/eod-runs?page=${reconUi.eodPage}&size=5`);
            const items = listOf(result);
            eodBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">还没有 EOD 运行</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>运行号</th><th>业务日期</th><th>状态</th><th>对账运行号</th>
                        <th>开始时间</th><th>完成时间</th><th>消息</th></tr></thead>
                    <tbody>${items.map((run) => `
                        <tr>
                            <td class="mono">${esc(run.runId)}</td>
                            <td class="nowrap">${dateText(run.businessDate)}</td>
                            <td>${runStatusBadge(run.status)}</td>
                            <td class="mono">${run.reconciliationRunId
                                ? `<a href="#/reconciliation/${encodeURIComponent(run.reconciliationRunId)}">${esc(run.reconciliationRunId)}</a>`
                                : '—'}</td>
                            <td class="nowrap">${dateTimeText(run.startedAt)}</td>
                            <td class="nowrap">${dateTimeText(run.completedAt)}</td>
                            <td class="muted">${esc(run.message || '—')}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(reconUi.eodPage, result.size || 5, result.total || 0)}`;

            eodBox.querySelectorAll('[data-page]').forEach((button) => {
                button.addEventListener('click', () => loadEodRuns(
                    reconUi.eodPage + (button.dataset.page === 'next' ? 1 : -1)));
            });
        } catch (error) {
            eodBox.innerHTML = errBox(error);
        }
    };

    const runReconciliation = async (button) => {
        const date = businessDate();
        msg.innerHTML = '';
        if (!date) {
            msg.innerHTML = '<div class="alert error mt-16">请选择业务日期</div>';
            return;
        }
        setBusy(button, true, '对账中…');
        try {
            const run = await api('/api/v1/reconciliation-runs', {
                method: 'POST',
                body: { businessDate: date }
            });
            toast(`对账已执行：${run.runId}`, 'success');
            msg.innerHTML = `
                <div class="alert success mt-16">
                    对账运行 <a class="mono" href="#/reconciliation/${encodeURIComponent(run.runId)}">${esc(run.runId)}</a>
                    ${runStatusBadge(run.status)} ${matchedBadge(run.matched)}
                    · 差异 <strong>${esc(run.mismatchCount ?? 0)}</strong> 项
                </div>`;
            await loadRuns(0);
            await loadEodRuns(reconUi.eodPage);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(button, false);
        }
    };

    const runEod = async (button) => {
        const date = businessDate();
        msg.innerHTML = '';
        if (!date) {
            msg.innerHTML = '<div class="alert error mt-16">请选择业务日期</div>';
            return;
        }
        setBusy(button, true, '执行中…');
        try {
            const run = await api('/api/v1/eod-runs', {
                method: 'POST',
                body: { businessDate: date }
            });
            toast(`EOD 已执行：${run.runId}`, 'success');
            msg.innerHTML = `
                <div class="alert success mt-16">
                    EOD 运行 <span class="mono">${esc(run.runId)}</span> ${runStatusBadge(run.status)}
                    ${run.reconciliationRunId
                        ? ` · 对账运行 <a class="mono" href="#/reconciliation/${encodeURIComponent(run.reconciliationRunId)}">${esc(run.reconciliationRunId)}</a>`
                        : ''}
                    ${run.message ? ` · ${esc(run.message)}` : ''}
                </div>`;
            await loadEodRuns(0);
            await loadRuns(reconUi.page);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(button, false);
        }
    };

    form.addEventListener('submit', (event) => {
        event.preventDefault();
        runReconciliation(runButton);
    });
    eodButton.addEventListener('click', () => runEod(eodButton));

    loadRuns(reconUi.page);
    loadEodRuns(reconUi.eodPage);
}

async function pageReconciliationRun(runId) {
    if (!requireAdmin('reconciliation', '对账运行详情')) return;
    shell({
        active: 'reconciliation',
        title: '对账运行详情',
        subtitle: `运行号 ${runId}`,
        content: '<div class="card" id="recon-detail"><div class="empty">加载中…</div></div>'
    });
    const box = document.getElementById('recon-detail');

    const resolveItem = async (itemId, button) => {
        const input = window.prompt('请输入差异处理说明（记录在对账明细上，例如：已确认总账同步延迟，手工补录）');
        if (input === null) return;
        const note = input.trim();
        if (!note) {
            toast('处理说明不能为空', 'error');
            return;
        }
        setBusy(button, true, '登记中…');
        try {
            await api(`/api/v1/reconciliation-items/${encodeURIComponent(itemId)}/resolve`, {
                method: 'POST',
                body: { note }
            });
            toast('差异处理说明已登记', 'success');
            await load();
        } catch (error) {
            toast(`${error.code || '登记失败'}：${error.message}`, 'error');
            setBusy(button, false);
        }
    };

    const load = async () => {
        box.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const run = await api(`/api/v1/reconciliation-runs/${encodeURIComponent(runId)}`);
            const items = Array.isArray(run.items) ? run.items : listOf(run);
            box.innerHTML = `
                <div class="section-title">
                    <h3>运行 <span class="muted">${esc(run.runId || runId)}</span></h3>
                    <div class="actions">
                        ${runStatusBadge(run.status)} ${matchedBadge(run.matched)}
                        <a class="btn ghost small" href="#/reconciliation">返回</a>
                    </div>
                </div>
                <div class="grid cols-4">
                    <div class="stat"><div class="label">批次数量</div>
                        <div class="value">${esc(run.batchCount ?? '—')}</div></div>
                    <div class="stat"><div class="label">批次金额</div>
                        <div class="value">${money(run.batchAmount)}</div></div>
                    <div class="stat"><div class="label">日记账数量</div>
                        <div class="value">${esc(run.journalCount ?? '—')}</div></div>
                    <div class="stat"><div class="label">差异数</div>
                        <div class="value">${esc(run.mismatchCount ?? '—')}</div></div>
                </div>
                <dl class="dl mt-16">
                    <dt>业务日期 / 期间</dt>
                        <dd>${dateText(run.businessDate)} / <span class="mono">${esc(run.period || '—')}</span></dd>
                    <dt>状态</dt><dd>${runStatusBadge(run.status)}</dd>
                    <dt>是否匹配</dt><dd>${matchedBadge(run.matched)}</dd>
                    <dt>批次金额</dt><dd class="mono">${money(run.batchAmount)}</dd>
                    <dt>日记账借方合计</dt><dd class="mono">${money(run.journalDebitTotal)}</dd>
                    <dt>日记账贷方合计</dt><dd class="mono">${money(run.journalCreditTotal)}</dd>
                    <dt>匹配项 / 未匹配项</dt>
                        <dd class="mono">${esc(run.matchCount ?? '—')} / ${esc(run.mismatchCount ?? '—')}</dd>
                    <dt>开始时间</dt><dd>${dateTimeText(run.startedAt)}</dd>
                    <dt>完成时间</dt><dd>${dateTimeText(run.completedAt)}</dd>
                    ${run.errorMessage ? `<dt>错误信息</dt><dd>${esc(run.errorMessage)}</dd>` : ''}
                </dl>
                <div class="section-title mt-16">
                    <h3>差异明细 <span class="muted">${items.length} 项</span></h3>
                    <span class="hint">未匹配项可登记处理说明，刷新后保留</span>
                </div>
                ${items.length === 0 ? '<div class="empty">该运行没有对账明细</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>检查类型</th><th>引用</th><th>状态</th><th class="num">应记金额</th>
                        <th class="num">实际金额</th><th class="num">差异</th><th>处理状态</th><th>详情</th>
                        <th>操作</th></tr></thead>
                    <tbody>${items.map((item) => {
                        const resolvable = item.status !== 'MATCH' && !item.resolved;
                        return `
                        <tr>
                            <td>${esc(RECON_CHECK_TYPE_TEXT[item.checkType] || item.checkType || '—')}</td>
                            <td class="mono">${esc(item.referenceId || '—')}</td>
                            <td>${reconItemStatusBadge(item.status)}</td>
                            <td class="num mono">${money(item.expectedAmount)}</td>
                            <td class="num mono">${money(item.actualAmount)}</td>
                            <td class="num mono">${money(item.difference)}</td>
                            <td>${resolvedBadge(item.resolved)}
                                ${item.resolutionNote ? `<div class="muted">${esc(item.resolutionNote)}</div>` : ''}</td>
                            <td class="muted">${esc(item.detail || '—')}</td>
                            <td class="actions">${resolvable
                                ? `<button class="btn ghost small" data-resolve="${esc(item.itemId)}">登记处理</button>`
                                : '—'}</td>
                        </tr>`;
                    }).join('')}
                    </tbody></table></div>`}`;

            box.querySelectorAll('[data-resolve]').forEach((button) => {
                button.addEventListener('click', () => resolveItem(button.dataset.resolve, button));
            });
        } catch (error) {
            box.innerHTML = errBox(error);
        }
    };

    await load();
}

/* ---------------- 页面：用户管理 ---------------- */

const usersUi = { page: 0 };

function requireAdmin(active = 'users', title = '用户管理') {
    if (state.session.user.userType !== 'A') {
        shell({
            active,
            title,
            subtitle: '仅管理员可访问',
            content: '<div class="alert error">当前账号不是管理员，无法访问该页面。请使用 ADMIN001 登录。</div>'
        });
        return false;
    }
    return true;
}

function pageUsers() {
    if (!requireAdmin()) return;
    shell({
        active: 'users',
        title: '用户管理',
        subtitle: '维护登录用户与管理员（对应遗留 COUSR00C–03C）',
        content: `
            <div class="card">
                <div class="section-title">
                    <h3>用户列表</h3>
                    <a class="btn primary small" href="#/users/new">新增用户</a>
                </div>
                <div id="users-result"></div>
            </div>`
    });

    const box = document.getElementById('users-result');
    const load = async (page) => {
        usersUi.page = Math.max(0, page);
        box.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/users?page=${usersUi.page}&size=10`);
            const items = result.items || [];
            box.innerHTML = `
                ${items.length === 0 ? '<div class="empty">暂无用户</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>用户号</th><th>姓名</th><th>类型</th><th>操作</th></tr></thead>
                    <tbody>${items.map((user) => `
                        <tr>
                            <td class="mono">${esc(user.userId)}</td>
                            <td>${esc(user.firstName)} ${esc(user.lastName)}</td>
                            <td>${userTypeBadge(user.userType)}</td>
                            <td class="actions">
                                <a class="btn ghost small" href="#/users/${encodeURIComponent(user.userId)}">编辑</a>
                                <button class="btn danger small" data-delete="${esc(user.userId)}">删除</button>
                            </td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(usersUi.page, result.size || 10, result.total || 0)}`;

            box.querySelectorAll('[data-page]').forEach((button) => {
                button.addEventListener('click', () => load(usersUi.page + (button.dataset.page === 'next' ? 1 : -1)));
            });
            box.querySelectorAll('[data-delete]').forEach((button) => {
                button.addEventListener('click', async () => {
                    const userId = button.dataset.delete;
                    if (!window.confirm(`确认删除用户 ${userId}？`)) return;
                    setBusy(button, true, '删除中…');
                    try {
                        await api(`/api/v1/users/${encodeURIComponent(userId)}`, {
                            method: 'DELETE',
                            headers: { 'X-User-Id': state.session.user.userId }
                        });
                        toast('用户已删除', 'success');
                        await load(usersUi.page);
                    } catch (error) {
                        toast(`${error.code || '删除失败'}：${error.message}`, 'error');
                        setBusy(button, false);
                    }
                });
            });
        } catch (error) {
            box.innerHTML = errBox(error);
        }
    };
    load(usersUi.page);
}

function pageUserNew() {
    if (!requireAdmin()) return;
    shell({
        active: 'users',
        title: '新增用户',
        subtitle: '用户号 8 位字母数字，密码 8–64 位，类型 A/U',
        content: `
            <div class="card">
                <form id="user-form">
                    <div class="form-grid">
                        <label class="field"><span>用户号 <span class="req">*</span></span>
                            <input name="userId" value="USER0002" maxlength="8" pattern="[A-Za-z0-9]{8}" required></label>
                        <label class="field"><span>用户类型 <span class="req">*</span></span>
                            <select name="userType">
                                <option value="U">U · 普通用户</option>
                                <option value="A">A · 管理员</option>
                            </select></label>
                        <label class="field"><span>名 <span class="req">*</span></span>
                            <input name="firstName" value="Ada" maxlength="20" required></label>
                        <label class="field"><span>姓 <span class="req">*</span></span>
                            <input name="lastName" value="Lovelace" maxlength="20" required></label>
                        <label class="field span-2"><span>密码 <span class="req">*</span></span>
                            <input name="password" type="password" value="pass1234" minlength="8" maxlength="64" required></label>
                    </div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="user-save">创建用户</button>
                        <a class="btn ghost" href="#/users">返回列表</a>
                    </div>
                    <div id="user-msg"></div>
                </form>
            </div>`
    });

    const form = document.getElementById('user-form');
    const button = document.getElementById('user-save');
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('user-msg');
        msg.innerHTML = '';
        setBusy(button, true, '创建中…');
        try {
            await api('/api/v1/users', {
                method: 'POST',
                body: {
                    userId: values.userId.trim().toUpperCase(),
                    firstName: values.firstName.trim(),
                    lastName: values.lastName.trim(),
                    password: values.password,
                    userType: values.userType
                }
            });
            toast('用户已创建', 'success');
            location.hash = '#/users';
        } catch (error) {
            msg.innerHTML = errBox(error);
            setBusy(button, false);
        }
    });
}

async function pageUserEdit(userId) {
    if (!requireAdmin()) return;
    shell({
        active: 'users',
        title: '编辑用户',
        subtitle: `用户号 ${userId}`,
        content: '<div class="card" id="user-edit"><div class="empty">加载中…</div></div>'
    });
    const box = document.getElementById('user-edit');
    try {
        const user = await api(`/api/v1/users/${encodeURIComponent(userId)}`);
        box.innerHTML = `
            <form id="user-form">
                <div class="form-grid">
                    <label class="field"><span>用户号</span>
                        <input value="${esc(user.userId)}" disabled></label>
                    <label class="field"><span>用户类型 <span class="req">*</span></span>
                        <select name="userType">
                            <option value="U" ${user.userType === 'U' ? 'selected' : ''}>U · 普通用户</option>
                            <option value="A" ${user.userType === 'A' ? 'selected' : ''}>A · 管理员</option>
                        </select></label>
                    <label class="field"><span>名 <span class="req">*</span></span>
                        <input name="firstName" value="${esc(user.firstName)}" maxlength="20" required></label>
                    <label class="field"><span>姓 <span class="req">*</span></span>
                        <input name="lastName" value="${esc(user.lastName)}" maxlength="20" required></label>
                    <label class="field span-2"><span>新密码（留空则不修改）</span>
                        <input name="password" type="password" minlength="8" maxlength="64" placeholder="至少 8 位"></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="user-save">保存修改</button>
                    <a class="btn ghost" href="#/users">返回列表</a>
                </div>
                <div id="user-msg"></div>
            </form>`;

        const form = document.getElementById('user-form');
        const button = document.getElementById('user-save');
        form.addEventListener('submit', async (event) => {
            event.preventDefault();
            const values = formValues(form);
            const body = {
                firstName: values.firstName.trim(),
                lastName: values.lastName.trim(),
                userType: values.userType
            };
            if (values.password) body.password = values.password;
            const msg = document.getElementById('user-msg');
            msg.innerHTML = '';
            setBusy(button, true, '保存中…');
            try {
                await api(`/api/v1/users/${encodeURIComponent(userId)}`, { method: 'PUT', body });
                toast('用户已更新', 'success');
                location.hash = '#/users';
            } catch (error) {
                msg.innerHTML = errBox(error);
                setBusy(button, false);
            }
        });
    } catch (error) {
        box.innerHTML = errBox(error);
    }
}

/* ---------------- 页面：支付 ---------------- */

const paymentsUi = { page: 0, status: '', accountId: '' };

const PAYMENT_STATUS_TEXT = {
    RECEIVED: '已受理',
    SCREENED: '已筛查',
    ACCEPTED: '已接受',
    SETTLED: '已清算',
    RETURNED: '已退汇',
    REJECTED: '已拒绝'
};

const PAYMENT_TYPE_OPTIONS = [
    ['ACH', 'ACH · 美国自动清算'],
    ['SEPA', 'SEPA · 欧洲'],
    ['WIRE', 'WIRE · 电汇'],
    ['INSTANT', 'INSTANT · 即时']
];

function paymentStatusBadge(status) {
    const cls = status === 'ACCEPTED' || status === 'SETTLED'
        ? 'ok'
        : status === 'REJECTED' ? 'bad' : status === 'SCREENED' ? 'warn' : 'neutral';
    return `<span class="badge ${cls}">${esc(PAYMENT_STATUS_TEXT[status] || status || '—')}</span>`;
}

function renderPayment(box, payment) {
    if (!payment) {
        box.innerHTML = '<h3>支付详情</h3><div class="empty">点击列表中的“详情”查看单据</div>';
        return;
    }
    box.innerHTML = `
        <h3>支付详情 <span class="muted">${esc(payment.paymentId)}</span></h3>
        <dl class="dl">
            <dt>支付号</dt><dd class="mono">${esc(payment.paymentId)}</dd>
            <dt>幂等键</dt><dd class="mono">${esc(payment.idempotencyKey)}</dd>
            <dt>付款账户</dt><dd class="mono">${esc(payment.debtorAccountId)}</dd>
            <dt>收款人</dt><dd>${esc(payment.creditorName || '—')}</dd>
            <dt>收款账号</dt><dd class="mono">${esc(payment.creditorAccountNumber || '—')}</dd>
            <dt>收款银行</dt><dd class="mono">${esc(payment.creditorBankCode || '—')}</dd>
            <dt>金额</dt><dd class="mono"><strong>${money(payment.amount)}</strong> ${esc(payment.currency || '')}</dd>
            <dt>支付类型</dt><dd>${esc(payment.paymentType || '—')}</dd>
            <dt>状态</dt><dd>${paymentStatusBadge(payment.status)}</dd>
            <dt>参考号</dt><dd>${esc(payment.reference || '—')}</dd>
            <dt>描述</dt><dd>${esc(payment.description || '—')}</dd>
            <dt>过账批次</dt><dd class="mono">${esc(payment.transactionBatchId || '—')}</dd>
            ${payment.rejectReason ? `<dt>拒绝原因</dt><dd>${esc(payment.rejectReason)}</dd>` : ''}
            <dt>创建时间</dt><dd>${dateTimeText(payment.createdAt)}</dd>
            <dt>清算时间</dt><dd>${dateTimeText(payment.settledAt)}</dd>
            <dt>退汇时间</dt><dd>${dateTimeText(payment.returnedAt)}</dd>
        </dl>`;
}

function pagePayments() {
    shell({
        active: 'payments',
        title: '支付',
        subtitle: '受理支付指令、自动筛查、接受、清算与退汇（设计 13 §2）',
        content: `
            <div class="card">
                <h3>创建支付指令</h3>
                <form id="payment-form">
                    <div class="form-grid">
                        <label class="field span-2"><span>幂等键 <span class="req">*</span></span>
                            <input name="idempotencyKey" value="${esc(`pay-${uuid()}`)}" maxlength="64" required></label>
                        <label class="field"><span>付款账户（11 位） <span class="req">*</span></span>
                            <input name="debtorAccountId" value="00000000006" maxlength="11"
                                   pattern="\\d{11}" required></label>
                        <label class="field"><span>收款人 <span class="req">*</span></span>
                            <input name="creditorName" value="Acme Utility" maxlength="60" required></label>
                        <label class="field"><span>收款账号</span>
                            <input name="creditorAccountNumber" value="1234567890123456" maxlength="34"></label>
                        <label class="field"><span>收款银行代码</span>
                            <input name="creditorBankCode" value="BOFAUS3N" maxlength="11"></label>
                        <label class="field"><span>金额 <span class="req">*</span></span>
                            <input name="amount" type="number" step="0.01" min="0.01" value="120.00" required></label>
                        <label class="field"><span>币种 <span class="req">*</span></span>
                            <input name="currency" value="USD" maxlength="3" required></label>
                        <label class="field"><span>支付类型 <span class="req">*</span></span>
                            <select name="paymentType">
                                ${PAYMENT_TYPE_OPTIONS.map(([code, label]) => `
                                    <option value="${code}">${esc(label)}</option>`).join('')}
                            </select></label>
                        <label class="field"><span>参考号</span>
                            <input name="reference" value="INV-2026-10" maxlength="35"></label>
                        <label class="field span-2"><span>描述</span>
                            <input name="description" value="Utility bill" maxlength="100"></label>
                    </div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="payment-save">提交支付</button>
                    </div>
                    <div class="hint mt-16">
                        提交后自动筛查：收款人非空且不在拒绝名单 → 已筛查（可接受）；否则直接拒绝。
                        同一幂等键重复提交返回原单据。
                    </div>
                    <div id="payment-msg"></div>
                </form>
            </div>
            <div class="card mt-16" id="payment-detail">
                <h3>支付详情</h3>
                <div class="empty">点击列表中的“详情”查看单据</div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>支付指令</h3>
                    <form class="inline-form" id="payments-filter">
                        <label class="field"><span>状态</span>
                            <select id="payments-status">
                                <option value="">全部</option>
                                ${Object.entries(PAYMENT_STATUS_TEXT).map(([code, label]) => `
                                    <option value="${code}">${esc(label)}</option>`).join('')}
                            </select></label>
                        <label class="field"><span>账户号</span>
                            <input id="payments-account" value="${esc(paymentsUi.accountId)}" maxlength="11"
                                   placeholder="可选"></label>
                        <button class="btn ghost" type="submit">查询</button>
                    </form>
                </div>
                <div id="payments-result"></div>
            </div>`
    });

    const form = document.getElementById('payment-form');
    const button = document.getElementById('payment-save');
    const detailBox = document.getElementById('payment-detail');
    const listBox = document.getElementById('payments-result');
    let current = null;

    const loadDetail = async (paymentId) => {
        try {
            current = await api(`/api/v1/payment-orders/${encodeURIComponent(paymentId)}`);
            renderPayment(detailBox, current);
        } catch (error) {
            detailBox.innerHTML = errBox(error);
        }
    };

    const loadList = async (page) => {
        paymentsUi.page = Math.max(0, page);
        paymentsUi.status = document.getElementById('payments-status').value;
        paymentsUi.accountId = document.getElementById('payments-account').value.trim();
        const params = new URLSearchParams({ page: String(paymentsUi.page), size: '10' });
        if (paymentsUi.status) params.set('status', paymentsUi.status);
        if (paymentsUi.accountId) params.set('accountId', paymentsUi.accountId);
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/payment-orders?${params.toString()}`);
            const items = listOf(result);
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">暂无支付指令</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>支付号</th><th>账户</th><th>收款人</th><th class="num">金额</th>
                        <th>类型</th><th>状态</th><th>时间</th><th>操作</th></tr></thead>
                    <tbody>${items.map((payment) => `
                        <tr>
                            <td class="mono">${esc(payment.paymentId)}</td>
                            <td class="mono">${esc(payment.debtorAccountId)}</td>
                            <td>${esc(payment.creditorName || '—')}
                                ${payment.rejectReason ? `<div class="muted">${esc(payment.rejectReason)}</div>` : ''}</td>
                            <td class="num mono">${money(payment.amount)}
                                <div class="muted">${esc(payment.currency || '')}</div></td>
                            <td>${esc(payment.paymentType || '—')}</td>
                            <td>${paymentStatusBadge(payment.status)}</td>
                            <td class="nowrap">${dateTimeText(payment.createdAt)}</td>
                            <td class="actions">
                                <button class="btn ghost small" data-pay-detail="${esc(payment.paymentId)}">详情</button>
                                ${payment.status === 'SCREENED'
                                    ? `<button class="btn primary small" data-pay-action="accept"
                                               data-pay-id="${esc(payment.paymentId)}">接受</button>` : ''}
                                ${payment.status === 'ACCEPTED'
                                    ? `<button class="btn primary small" data-pay-action="settle"
                                               data-pay-id="${esc(payment.paymentId)}">清算</button>` : ''}
                                ${payment.status === 'ACCEPTED' || payment.status === 'SETTLED'
                                    ? `<button class="btn danger small" data-pay-action="return"
                                               data-pay-id="${esc(payment.paymentId)}">退汇</button>` : ''}
                            </td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(paymentsUi.page, result.size || 10, result.total || 0)}`;

            listBox.querySelectorAll('[data-page]').forEach((pagerButton) => {
                pagerButton.addEventListener('click', () => loadList(
                    paymentsUi.page + (pagerButton.dataset.page === 'next' ? 1 : -1)));
            });
            listBox.querySelectorAll('[data-pay-detail]').forEach((detailButton) => {
                detailButton.addEventListener('click', () => loadDetail(detailButton.dataset.payDetail));
            });
            listBox.querySelectorAll('[data-pay-action]').forEach((actionButton) => {
                actionButton.addEventListener('click', async () => {
                    const action = actionButton.dataset.payAction;
                    const paymentId = actionButton.dataset.payId;
                    if (action === 'return'
                        && !window.confirm(`确认对支付 ${paymentId} 退汇？已清算的支付会先冲正过账。`)) return;
                    setBusy(actionButton, true, '处理中…');
                    try {
                        const updated = await api(
                            `/api/v1/payment-orders/${encodeURIComponent(paymentId)}/${action}`,
                            { method: 'POST' });
                        toast(`${PAYMENT_STATUS_TEXT[updated.status] || updated.status}：${paymentId}`, 'success');
                        if (current && current.paymentId === paymentId) {
                            current = updated;
                            renderPayment(detailBox, updated);
                        }
                        await loadList(paymentsUi.page);
                    } catch (error) {
                        toast(`${error.code || '操作失败'}：${error.message}`, 'error');
                        setBusy(actionButton, false);
                    }
                });
            });
        } catch (error) {
            listBox.innerHTML = errBox(error);
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('payment-msg');
        msg.innerHTML = '';
        setBusy(button, true, '提交中…');
        try {
            const created = await api('/api/v1/payment-orders', {
                method: 'POST',
                body: {
                    idempotencyKey: values.idempotencyKey.trim(),
                    debtorAccountId: values.debtorAccountId.trim(),
                    creditorName: values.creditorName.trim(),
                    creditorAccountNumber: values.creditorAccountNumber
                        ? values.creditorAccountNumber.trim() : null,
                    creditorBankCode: values.creditorBankCode ? values.creditorBankCode.trim() : null,
                    amount: Number(values.amount),
                    currency: values.currency.trim().toUpperCase(),
                    paymentType: values.paymentType,
                    reference: values.reference ? values.reference.trim() : null,
                    description: values.description ? values.description.trim() : null
                }
            });
            toast(`${PAYMENT_STATUS_TEXT[created.status] || created.status}：${created.paymentId}`,
                created.status === 'REJECTED' ? 'error' : 'success');
            current = created;
            renderPayment(detailBox, created);
            form.querySelector('[name="idempotencyKey"]').value = `pay-${uuid()}`;
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(button, false);
        }
    });

    document.getElementById('payments-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList(0);
    });

    loadList(paymentsUi.page);
}

/* ---------------- 页面：账单 ---------------- */

const billingUi = { page: 0, accountId: '00000000001' };

const STATEMENT_STATUS_TEXT = { GENERATED: '已生成', PAID: '已支付', OVERDUE: '已逾期' };

function statementStatusBadge(status) {
    const cls = status === 'PAID' ? 'ok' : status === 'OVERDUE' ? 'bad' : 'warn';
    return `<span class="badge ${cls}">${esc(STATEMENT_STATUS_TEXT[status] || status || '—')}</span>`;
}

function renderStatement(box, statement) {
    if (!statement) {
        box.innerHTML = '<h3>账单详情</h3><div class="empty">生成或查看账单后显示明细</div>';
        return;
    }
    box.innerHTML = `
        <h3>账单详情 <span class="muted">${esc(statement.period)}</span></h3>
        <dl class="dl">
            <dt>账单号</dt><dd class="mono">${esc(statement.statementId)}</dd>
            <dt>账户</dt><dd class="mono">${esc(statement.accountId)}</dd>
            <dt>期间</dt><dd>${esc(statement.period)}</dd>
            <dt>账期日</dt><dd>${dateText(statement.cycleDate)}</dd>
            <dt>期初 / 期末</dt>
                <dd class="mono">${money(statement.openingBalance)} / <strong>${money(statement.closingBalance)}</strong></dd>
            <dt>消费 / 还款</dt><dd class="mono">${money(statement.purchases)} / ${money(statement.payments)}</dd>
            <dt>费用 / 利息</dt><dd class="mono">${money(statement.fees)} / ${money(statement.interest)}</dd>
            <dt>最低还款</dt><dd class="mono">${money(statement.minimumPayment)}</dd>
            <dt>到期日</dt><dd>${dateText(statement.dueDate)}</dd>
            <dt>状态</dt><dd>${statementStatusBadge(statement.status)}</dd>
            <dt>生成时间</dt><dd>${dateTimeText(statement.generatedAt)}</dd>
            <dt>支付时间</dt><dd>${dateTimeText(statement.paidAt)}</dd>
        </dl>`;
}

function pageBilling() {
    shell({
        active: 'billing',
        title: '账单',
        subtitle: '生成账单、查看利息与最低还款、到期日与全额支付（设计 13 §3）',
        content: `
            <div class="grid cols-2">
                <div class="card">
                    <h3>生成账单</h3>
                    <form id="billing-form">
                        <div class="form-grid">
                            <label class="field"><span>账户号（11 位） <span class="req">*</span></span>
                                <input name="accountId" value="${esc(billingUi.accountId)}" maxlength="11"
                                       pattern="\\d{11}" required></label>
                            <label class="field"><span>账单期间 <span class="req">*</span></span>
                                <input name="period" value="${esc(currentPeriod())}" maxlength="7"
                                       pattern="\\d{4}-\\d{2}" placeholder="YYYY-MM" required></label>
                        </div>
                        <div class="form-actions">
                            <button class="btn primary" type="submit" id="billing-generate">生成账单</button>
                        </div>
                        <div class="hint mt-16">同账户 + 同期间只生成一张账单，重复生成返回已有账单。</div>
                        <div id="billing-msg"></div>
                    </form>
                </div>
                <div class="card" id="billing-detail">
                    <h3>账单详情</h3>
                    <div class="empty">生成或查看账单后显示明细</div>
                </div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>账单查询</h3>
                    <form class="inline-form" id="billing-filter">
                        <label class="field"><span>账户号</span>
                            <input id="billing-account" value="${esc(billingUi.accountId)}" maxlength="11"
                                   placeholder="可选"></label>
                        <button class="btn ghost" type="submit">查询</button>
                    </form>
                </div>
                <div id="billing-result"></div>
            </div>`
    });

    const form = document.getElementById('billing-form');
    const button = document.getElementById('billing-generate');
    const detailBox = document.getElementById('billing-detail');
    const listBox = document.getElementById('billing-result');

    const loadList = async (page) => {
        billingUi.page = Math.max(0, page);
        billingUi.accountId = document.getElementById('billing-account').value.trim();
        const params = new URLSearchParams({ page: String(billingUi.page), size: '10' });
        if (billingUi.accountId) params.set('accountId', billingUi.accountId);
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/statements?${params.toString()}`);
            const items = listOf(result);
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">暂无账单</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>期间</th><th>账户</th><th class="num">期初 / 期末</th><th class="num">消费</th>
                        <th class="num">还款</th><th class="num">利息</th><th class="num">最低还款</th>
                        <th>到期日</th><th>状态</th><th>操作</th></tr></thead>
                    <tbody>${items.map((statement) => `
                        <tr>
                            <td class="mono">${esc(statement.period)}</td>
                            <td class="mono">${esc(statement.accountId)}</td>
                            <td class="num mono">${money(statement.openingBalance)}<div class="muted">${money(statement.closingBalance)}</div></td>
                            <td class="num mono">${money(statement.purchases)}</td>
                            <td class="num mono">${money(statement.payments)}</td>
                            <td class="num mono">${money(statement.interest)}</td>
                            <td class="num mono">${money(statement.minimumPayment)}</td>
                            <td class="nowrap">${dateText(statement.dueDate)}</td>
                            <td>${statementStatusBadge(statement.status)}</td>
                            <td class="actions">
                                <button class="btn ghost small" data-stmt-detail="${esc(statement.statementId)}">详情</button>
                                ${statement.status === 'GENERATED'
                                    ? `<button class="btn primary small" data-stmt-pay="${esc(statement.statementId)}">全额支付</button>`
                                    : ''}
                            </td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(billingUi.page, result.size || 10, result.total || 0)}`;

            listBox.querySelectorAll('[data-page]').forEach((pagerButton) => {
                pagerButton.addEventListener('click', () => loadList(
                    billingUi.page + (pagerButton.dataset.page === 'next' ? 1 : -1)));
            });
            listBox.querySelectorAll('[data-stmt-detail]').forEach((detailButton) => {
                detailButton.addEventListener('click', async () => {
                    setBusy(detailButton, true, '加载中…');
                    try {
                        renderStatement(detailBox, await api(
                            `/api/v1/statements/${encodeURIComponent(detailButton.dataset.stmtDetail)}`));
                    } catch (error) {
                        detailBox.innerHTML = errBox(error);
                    } finally {
                        setBusy(detailButton, false);
                    }
                });
            });
            listBox.querySelectorAll('[data-stmt-pay]').forEach((payButton) => {
                payButton.addEventListener('click', async () => {
                    const statementId = payButton.dataset.stmtPay;
                    if (!window.confirm(`确认全额支付账单 ${statementId}？将调用账户支付扣款。`)) return;
                    setBusy(payButton, true, '支付中…');
                    try {
                        const paid = await api(
                            `/api/v1/statements/${encodeURIComponent(statementId)}/pay`,
                            { method: 'POST' });
                        toast(`账单已支付：${paid.period}`, 'success');
                        renderStatement(detailBox, paid);
                        await loadList(billingUi.page);
                    } catch (error) {
                        toast(`${error.code || '支付失败'}：${error.message}`, 'error');
                        setBusy(payButton, false);
                    }
                });
            });
        } catch (error) {
            listBox.innerHTML = errBox(error);
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('billing-msg');
        msg.innerHTML = '';
        setBusy(button, true, '生成中…');
        try {
            const statement = await api('/api/v1/statements/generate', {
                method: 'POST',
                body: { accountId: values.accountId.trim(), period: values.period.trim() }
            });
            toast(`账单已生成：${statement.period}`, 'success');
            renderStatement(detailBox, statement);
            document.getElementById('billing-account').value = statement.accountId;
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(button, false);
        }
    });

    document.getElementById('billing-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList(0);
    });

    loadList(billingUi.page);
}

/* ---------------- 页面：限额 ---------------- */

const limitsUi = { page: 0, scopeType: '', scopeId: '' };

const LIMIT_SCOPE_TEXT = { CUSTOMER: '客户', ACCOUNT: '账户', CHANNEL: '渠道' };
const LIMIT_TYPE_TEXT = { PURCHASE: '消费', CASH: '取现', DAILY: '日累计', TRANSFER: '转账' };
const LIMIT_SCOPE_OPTIONS = ['CUSTOMER', 'ACCOUNT', 'CHANNEL'];
const LIMIT_TYPE_OPTIONS = ['PURCHASE', 'CASH', 'DAILY', 'TRANSFER'];

function limitActiveBadge(active) {
    return active
        ? '<span class="badge ok">启用</span>'
        : '<span class="badge neutral">停用</span>';
}

function limitAllowBadge(allowed) {
    return allowed
        ? '<span class="badge ok">允许</span>'
        : '<span class="badge bad">不允许</span>';
}

function limitAmountPrompt(title, defaultValue) {
    const raw = window.prompt(title, defaultValue);
    if (raw === null) return null;
    const amount = Number(raw);
    if (!Number.isFinite(amount) || amount <= 0) {
        toast('金额必须大于零', 'error');
        return null;
    }
    return amount;
}

function pageLimits() {
    if (!requireAdmin('limits', '限额')) return;
    shell({
        active: 'limits',
        title: '限额',
        subtitle: '客户/账户/渠道限额的创建、占用、释放、调整与可用性检查（设计 13 §4）',
        content: `
            <div class="grid cols-2">
                <div class="card">
                    <h3>创建限额</h3>
                    <form id="limit-form">
                        <div class="form-grid">
                            <label class="field"><span>范围类型 <span class="req">*</span></span>
                                <select name="scopeType">
                                    ${LIMIT_SCOPE_OPTIONS.map((code) => `
                                        <option value="${code}" ${code === 'ACCOUNT' ? 'selected' : ''}>${code} · ${LIMIT_SCOPE_TEXT[code]}</option>`).join('')}
                                </select></label>
                            <label class="field"><span>范围 ID <span class="req">*</span></span>
                                <input name="scopeId" value="00000000001" maxlength="64" required></label>
                            <label class="field"><span>限额类型 <span class="req">*</span></span>
                                <select name="limitType">
                                    ${LIMIT_TYPE_OPTIONS.map((code) => `
                                        <option value="${code}">${code} · ${LIMIT_TYPE_TEXT[code]}</option>`).join('')}
                                </select></label>
                            <label class="field"><span>币种 <span class="req">*</span></span>
                                <input name="currency" value="USD" maxlength="3" pattern="[A-Za-z]{3}" required></label>
                            <label class="field span-2"><span>额度 <span class="req">*</span></span>
                                <input name="amount" type="number" step="0.01" min="0.01" value="1000.00" required></label>
                        </div>
                        <div class="form-actions">
                            <button class="btn primary" type="submit" id="limit-save">创建限额</button>
                        </div>
                        <div class="hint mt-16">
                            同一范围 + 类型 + 币种只允许一条限额；调整额度不得低于已用金额。
                        </div>
                        <div id="limit-msg"></div>
                    </form>
                </div>
                <div class="card">
                    <h3>可用性检查</h3>
                    <form id="limit-check-form">
                        <div class="form-grid">
                            <label class="field"><span>范围类型 <span class="req">*</span></span>
                                <select name="scopeType">
                                    ${LIMIT_SCOPE_OPTIONS.map((code) => `
                                        <option value="${code}" ${code === 'ACCOUNT' ? 'selected' : ''}>${code} · ${LIMIT_SCOPE_TEXT[code]}</option>`).join('')}
                                </select></label>
                            <label class="field"><span>范围 ID <span class="req">*</span></span>
                                <input name="scopeId" value="00000000001" maxlength="64" required></label>
                            <label class="field"><span>限额类型 <span class="req">*</span></span>
                                <select name="limitType">
                                    ${LIMIT_TYPE_OPTIONS.map((code) => `
                                        <option value="${code}">${code} · ${LIMIT_TYPE_TEXT[code]}</option>`).join('')}
                                </select></label>
                            <label class="field"><span>币种 <span class="req">*</span></span>
                                <input name="currency" value="USD" maxlength="3" pattern="[A-Za-z]{3}" required></label>
                            <label class="field span-2"><span>交易金额 <span class="req">*</span></span>
                                <input name="amount" type="number" step="0.01" min="0.01" value="100.00" required></label>
                        </div>
                        <div class="form-actions">
                            <button class="btn ghost" type="submit">检查限额</button>
                        </div>
                        <div id="limit-check-result"></div>
                    </form>
                </div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>限额列表</h3>
                    <form class="inline-form" id="limits-filter">
                        <label class="field"><span>范围类型</span>
                            <select id="limits-scope-type">
                                <option value="">全部</option>
                                ${LIMIT_SCOPE_OPTIONS.map((code) => `
                                    <option value="${code}">${code} · ${LIMIT_SCOPE_TEXT[code]}</option>`).join('')}
                            </select></label>
                        <label class="field"><span>范围 ID</span>
                            <input id="limits-scope-id" value="${esc(limitsUi.scopeId)}" maxlength="64"
                                   placeholder="可选"></label>
                        <button class="btn ghost" type="submit">查询</button>
                    </form>
                </div>
                <div id="limits-result"></div>
            </div>`
    });

    const form = document.getElementById('limit-form');
    const button = document.getElementById('limit-save');
    const listBox = document.getElementById('limits-result');

    const loadList = async (page) => {
        limitsUi.page = Math.max(0, page);
        limitsUi.scopeType = document.getElementById('limits-scope-type').value;
        limitsUi.scopeId = document.getElementById('limits-scope-id').value.trim();
        const params = new URLSearchParams({ page: String(limitsUi.page), size: '10' });
        if (limitsUi.scopeType) params.set('scopeType', limitsUi.scopeType);
        if (limitsUi.scopeId) params.set('scopeId', limitsUi.scopeId);
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/limits?${params.toString()}`);
            const items = listOf(result);
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">暂无限额定义</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>范围</th><th>类型</th><th class="num">额度</th><th class="num">已用</th>
                        <th class="num">剩余</th><th>状态</th><th>操作</th></tr></thead>
                    <tbody>${items.map((limit) => `
                        <tr>
                            <td>${esc(LIMIT_SCOPE_TEXT[limit.scopeType] || limit.scopeType)}
                                <div class="mono muted">${esc(limit.scopeId)}</div></td>
                            <td>${esc(LIMIT_TYPE_TEXT[limit.limitType] || limit.limitType)}
                                <div class="muted">${esc(limit.currency)}</div></td>
                            <td class="num mono">${money(limit.amount)}</td>
                            <td class="num mono">${money(limit.usedAmount)}</td>
                            <td class="num mono">${money(limit.remainingAmount)}</td>
                            <td>${limitActiveBadge(limit.active)}</td>
                            <td class="actions">
                                <button class="btn ghost small" data-limit-usage="${esc(limit.limitId)}"
                                        data-limit-remaining="${esc(limit.remainingAmount)}">占用</button>
                                <button class="btn ghost small" data-limit-release="${esc(limit.limitId)}"
                                        data-limit-used="${esc(limit.usedAmount)}">释放</button>
                                <button class="btn ghost small" data-limit-adjust="${esc(limit.limitId)}"
                                        data-limit-amount="${esc(limit.amount)}">调整</button>
                            </td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(limitsUi.page, result.size || 10, result.total || 0)}`;

            listBox.querySelectorAll('[data-page]').forEach((pagerButton) => {
                pagerButton.addEventListener('click', () => loadList(
                    limitsUi.page + (pagerButton.dataset.page === 'next' ? 1 : -1)));
            });
            listBox.querySelectorAll('[data-limit-usage]').forEach((usageButton) => {
                usageButton.addEventListener('click', async () => {
                    const amount = limitAmountPrompt('占用金额', Number(usageButton.dataset.limitRemaining || 0).toFixed(2));
                    if (amount === null) return;
                    setBusy(usageButton, true, '处理中…');
                    try {
                        const updated = await api(
                            `/api/v1/limits/${encodeURIComponent(usageButton.dataset.limitUsage)}/usage`,
                            { method: 'POST', body: { amount } });
                        toast(`已占用，剩余 ${money(updated.remainingAmount)}`, 'success');
                        await loadList(limitsUi.page);
                    } catch (error) {
                        toast(`${error.code || '占用失败'}：${error.message}`, 'error');
                        setBusy(usageButton, false);
                    }
                });
            });
            listBox.querySelectorAll('[data-limit-release]').forEach((releaseButton) => {
                releaseButton.addEventListener('click', async () => {
                    const amount = limitAmountPrompt('释放金额', Number(releaseButton.dataset.limitUsed || 0).toFixed(2));
                    if (amount === null) return;
                    setBusy(releaseButton, true, '处理中…');
                    try {
                        const updated = await api(
                            `/api/v1/limits/${encodeURIComponent(releaseButton.dataset.limitRelease)}/release`,
                            { method: 'POST', body: { amount } });
                        toast(`已释放，剩余 ${money(updated.remainingAmount)}`, 'success');
                        await loadList(limitsUi.page);
                    } catch (error) {
                        toast(`${error.code || '释放失败'}：${error.message}`, 'error');
                        setBusy(releaseButton, false);
                    }
                });
            });
            listBox.querySelectorAll('[data-limit-adjust]').forEach((adjustButton) => {
                adjustButton.addEventListener('click', async () => {
                    const amount = limitAmountPrompt('新的额度', Number(adjustButton.dataset.limitAmount || 0).toFixed(2));
                    if (amount === null) return;
                    setBusy(adjustButton, true, '处理中…');
                    try {
                        const updated = await api(
                            `/api/v1/limits/${encodeURIComponent(adjustButton.dataset.limitAdjust)}`,
                            { method: 'PUT', body: { amount } });
                        toast(`额度已调整为 ${money(updated.amount)}`, 'success');
                        await loadList(limitsUi.page);
                    } catch (error) {
                        toast(`${error.code || '调整失败'}：${error.message}`, 'error');
                        setBusy(adjustButton, false);
                    }
                });
            });
        } catch (error) {
            listBox.innerHTML = errBox(error);
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('limit-msg');
        msg.innerHTML = '';
        setBusy(button, true, '创建中…');
        try {
            const created = await api('/api/v1/limits', {
                method: 'POST',
                body: {
                    scopeType: values.scopeType,
                    scopeId: values.scopeId.trim(),
                    limitType: values.limitType,
                    currency: values.currency.trim().toUpperCase(),
                    amount: Number(values.amount)
                }
            });
            toast(`限额已创建：${created.limitType} / ${created.scopeId}`, 'success');
            document.getElementById('limits-scope-type').value = created.scopeType;
            document.getElementById('limits-scope-id').value = created.scopeId;
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(button, false);
        }
    });

    document.getElementById('limits-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList(0);
    });

    const checkForm = document.getElementById('limit-check-form');
    checkForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(checkForm);
        const resultBox = document.getElementById('limit-check-result');
        const params = new URLSearchParams({
            scopeType: values.scopeType,
            scopeId: values.scopeId.trim(),
            limitType: values.limitType,
            currency: values.currency.trim().toUpperCase(),
            amount: String(Number(values.amount))
        });
        resultBox.innerHTML = '<div class="empty">检查中…</div>';
        try {
            const check = await api(`/api/v1/limits/check?${params.toString()}`);
            resultBox.innerHTML = `
                <div class="alert ${check.allowed ? 'success' : 'error'}">
                    ${check.allowed
                        ? `额度充足：剩余 ${money(check.remainingAmount)}，本笔 ${money(check.amount)} 可执行。`
                        : '额度不足或没有匹配的限额定义，本笔交易将被拒绝。'}
                </div>
                <dl class="dl">
                    <dt>结果</dt><dd>${limitAllowBadge(check.allowed)}</dd>
                    <dt>限额号</dt><dd class="mono">${esc(check.limitId || '—')}</dd>
                    <dt>范围</dt><dd>${esc(`${LIMIT_SCOPE_TEXT[check.scopeType] || check.scopeType || '—'} · ${check.scopeId || '—'}`)}</dd>
                    <dt>类型 / 币种</dt><dd>${esc(`${LIMIT_TYPE_TEXT[check.limitType] || check.limitType || '—'} · ${check.currency || '—'}`)}</dd>
                    <dt>额度</dt><dd class="mono">${money(check.amount)}</dd>
                    <dt>已用</dt><dd class="mono">${money(check.usedAmount)}</dd>
                    <dt>剩余</dt><dd class="mono"><strong>${money(check.remainingAmount)}</strong></dd>
                </dl>`;
        } catch (error) {
            resultBox.innerHTML = errBox(error);
        }
    });

    loadList(limitsUi.page);
}

/* ---------------- 页面：汇率与换汇 ---------------- */

const fxUi = { ratePage: 0, conversionPage: 0 };

const fxRateText = (value) => (value === null || value === undefined || value === ''
    ? '—'
    : Number(value).toFixed(6));

function fxActiveBadge(active) {
    return active
        ? '<span class="badge ok">启用</span>'
        : '<span class="badge neutral">停用</span>';
}

function pageFx() {
    shell({
        active: 'fx',
        title: '汇率',
        subtitle: '汇率维护、5 分钟有效报价、单次消费的换汇执行与换汇记录（设计 13 §5）',
        content: `
            <div class="grid cols-2">
                <div class="card">
                    <h3>维护汇率</h3>
                    <form id="fx-rate-form">
                        <div class="form-grid">
                            <label class="field"><span>基础币种 <span class="req">*</span></span>
                                <input name="baseCurrency" value="USD" maxlength="3" pattern="[A-Za-z]{3}" required></label>
                            <label class="field"><span>报价币种 <span class="req">*</span></span>
                                <input name="quoteCurrency" value="CNY" maxlength="3" pattern="[A-Za-z]{3}" required></label>
                            <label class="field span-2"><span>汇率（6 位小数） <span class="req">*</span></span>
                                <input name="rate" type="number" step="0.000001" min="0.000001"
                                       value="7.100000" required></label>
                        </div>
                        <div class="form-actions">
                            <button class="btn primary" type="submit" id="fx-rate-save">保存汇率</button>
                        </div>
                        <div class="hint mt-16">按币种对 upsert：同一基础/报价币种只保留一条汇率。</div>
                        <div id="fx-rate-msg"></div>
                    </form>
                </div>
                <div class="card">
                    <h3>报价与换汇</h3>
                    <form id="fx-quote-form">
                        <div class="form-grid">
                            <label class="field"><span>卖出币种 <span class="req">*</span></span>
                                <input name="fromCurrency" value="USD" maxlength="3" pattern="[A-Za-z]{3}" required></label>
                            <label class="field"><span>买入币种 <span class="req">*</span></span>
                                <input name="toCurrency" value="CNY" maxlength="3" pattern="[A-Za-z]{3}" required></label>
                            <label class="field span-2"><span>金额 <span class="req">*</span></span>
                                <input name="amount" type="number" step="0.01" min="0.01" value="100.00" required></label>
                        </div>
                        <div class="form-actions">
                            <button class="btn primary" type="submit" id="fx-quote-save">获取报价</button>
                        </div>
                        <div class="hint mt-16">报价 5 分钟内有效；执行换汇后同一报价不可重复消费。</div>
                        <div id="fx-quote-msg"></div>
                    </form>
                    <div id="fx-quote-result"></div>
                </div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>汇率表</h3>
                    <span class="hint">按基础币种 / 报价币种排序</span>
                </div>
                <div id="fx-rates"></div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>换汇记录</h3>
                    <span class="hint">按创建时间倒序</span>
                </div>
                <div id="fx-conversions"></div>
            </div>`
    });

    const ratesBox = document.getElementById('fx-rates');
    const conversionsBox = document.getElementById('fx-conversions');
    const quoteResult = document.getElementById('fx-quote-result');

    const loadRates = async (page) => {
        fxUi.ratePage = Math.max(0, page);
        ratesBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/fx/rates?page=${fxUi.ratePage}&size=20`);
            const items = listOf(result);
            ratesBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">暂无汇率，请先维护</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>币种对</th><th class="num">汇率</th><th>生效时间</th><th>状态</th></tr></thead>
                    <tbody>${items.map((rate) => `
                        <tr>
                            <td class="mono">${esc(rate.baseCurrency)} / ${esc(rate.quoteCurrency)}</td>
                            <td class="num mono">${fxRateText(rate.rate)}</td>
                            <td class="nowrap">${dateTimeText(rate.effectiveAt)}</td>
                            <td>${fxActiveBadge(rate.active)}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(fxUi.ratePage, result.size || 20, result.total || 0)}`;

            ratesBox.querySelectorAll('[data-page]').forEach((pagerButton) => {
                pagerButton.addEventListener('click', () => loadRates(
                    fxUi.ratePage + (pagerButton.dataset.page === 'next' ? 1 : -1)));
            });
        } catch (error) {
            ratesBox.innerHTML = errBox(error);
        }
    };

    const loadConversions = async (page) => {
        fxUi.conversionPage = Math.max(0, page);
        conversionsBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/fx/conversions?page=${fxUi.conversionPage}&size=10`);
            const items = listOf(result);
            conversionsBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">暂无换汇记录</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>记录号</th><th>报价号</th><th>币种对</th><th class="num">卖出金额</th>
                        <th class="num">汇率</th><th class="num">买入金额</th><th>时间</th></tr></thead>
                    <tbody>${items.map((conversion) => `
                        <tr>
                            <td class="mono">${esc(conversion.conversionId)}</td>
                            <td class="mono">${esc(conversion.quoteId)}</td>
                            <td class="mono">${esc(conversion.fromCurrency)} / ${esc(conversion.toCurrency)}</td>
                            <td class="num mono">${money(conversion.sourceAmount)}</td>
                            <td class="num mono">${fxRateText(conversion.rate)}</td>
                            <td class="num mono"><strong>${money(conversion.convertedAmount)}</strong></td>
                            <td class="nowrap">${dateTimeText(conversion.createdAt)}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`}
                ${pager(fxUi.conversionPage, result.size || 10, result.total || 0)}`;

            conversionsBox.querySelectorAll('[data-page]').forEach((pagerButton) => {
                pagerButton.addEventListener('click', () => loadConversions(
                    fxUi.conversionPage + (pagerButton.dataset.page === 'next' ? 1 : -1)));
            });
        } catch (error) {
            conversionsBox.innerHTML = errBox(error);
        }
    };

    const renderQuote = (quote) => {
        quoteResult.innerHTML = `
            <div class="alert info mt-16">
                报价 <code>${esc(quote.quoteId)}</code>：${money(quote.sourceAmount)} ${esc(quote.fromCurrency)}
                → <strong>${money(quote.convertedAmount)} ${esc(quote.toCurrency)}</strong>
            </div>
            <dl class="dl">
                <dt>报价号</dt><dd class="mono">${esc(quote.quoteId)}</dd>
                <dt>汇率</dt><dd class="mono">${fxRateText(quote.rate)}</dd>
                <dt>换得金额</dt>
                <dd class="mono"><strong>${money(quote.convertedAmount)}</strong> ${esc(quote.toCurrency)}</dd>
                <dt>失效时间</dt><dd>${dateTimeText(quote.expiresAt)}</dd>
                <dt>状态</dt><dd>${quote.consumed
                    ? '<span class="badge neutral">已消费</span>'
                    : '<span class="badge ok">可执行</span>'}</dd>
            </dl>
            <div class="form-actions">
                <button class="btn primary" type="button" id="fx-execute"
                        ${quote.consumed ? 'disabled' : ''}>执行换汇</button>
            </div>`;

        const executeButton = document.getElementById('fx-execute');
        if (!executeButton) return;
        executeButton.addEventListener('click', async () => {
            setBusy(executeButton, true, '换汇中…');
            try {
                const conversion = await api(
                    `/api/v1/fx/quotes/${encodeURIComponent(quote.quoteId)}/execute`,
                    { method: 'POST' });
                toast(`换汇完成：${money(conversion.convertedAmount)} ${conversion.toCurrency}`, 'success');
                renderQuote({ ...quote, consumed: true });
                quoteResult.insertAdjacentHTML('afterbegin', `
                    <div class="alert success">
                        换汇成功：记录 <code>${esc(conversion.conversionId)}</code>，
                        ${money(conversion.sourceAmount)} ${esc(conversion.fromCurrency)}
                        → <strong>${money(conversion.convertedAmount)} ${esc(conversion.toCurrency)}</strong>，
                        汇率 ${fxRateText(conversion.rate)}。
                    </div>`);
                await loadConversions(0);
            } catch (error) {
                toast(`${error.code || '换汇失败'}：${error.message}`, 'error');
                setBusy(executeButton, false);
            }
        });
    };

    const rateForm = document.getElementById('fx-rate-form');
    const rateButton = document.getElementById('fx-rate-save');
    rateForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(rateForm);
        const msg = document.getElementById('fx-rate-msg');
        msg.innerHTML = '';
        setBusy(rateButton, true, '保存中…');
        try {
            const rate = await api('/api/v1/fx/rates', {
                method: 'POST',
                body: {
                    baseCurrency: values.baseCurrency.trim().toUpperCase(),
                    quoteCurrency: values.quoteCurrency.trim().toUpperCase(),
                    rate: Number(values.rate)
                }
            });
            toast(`汇率已保存：${rate.baseCurrency}/${rate.quoteCurrency} ${fxRateText(rate.rate)}`, 'success');
            await loadRates(fxUi.ratePage);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(rateButton, false);
        }
    });

    const quoteForm = document.getElementById('fx-quote-form');
    const quoteButton = document.getElementById('fx-quote-save');
    quoteForm.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(quoteForm);
        const msg = document.getElementById('fx-quote-msg');
        msg.innerHTML = '';
        setBusy(quoteButton, true, '报价中…');
        try {
            const quote = await api('/api/v1/fx/quotes', {
                method: 'POST',
                body: {
                    fromCurrency: values.fromCurrency.trim().toUpperCase(),
                    toCurrency: values.toCurrency.trim().toUpperCase(),
                    amount: Number(values.amount)
                }
            });
            renderQuote(quote);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(quoteButton, false);
        }
    });

    loadRates(fxUi.ratePage);
    loadConversions(fxUi.conversionPage);
}

/* ---------------- 扩展页面公共工具（P3/P4/P5） ---------------- */

const todayDate = () => localDateTimeNow().slice(0, 10);

const tomorrowDate = () => {
    const date = new Date();
    date.setDate(date.getDate() + 1);
    const pad = (value) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
};

const STATUS_OK = ['APPROVED', 'DISBURSED', 'COMPLETED', 'SENT', 'CONFIRMED', 'CLEARED', 'CLEAR', 'WON',
    'RESOLVED', 'ACTIVE', 'PAID', 'APPROVE'];
const STATUS_BAD = ['REJECTED', 'FAILED', 'LOST', 'BLOCKED', 'REJECT', 'CLOSED'];
const STATUS_WARN = ['SUBMITTED', 'RUNNING', 'PENDING', 'OPEN', 'PTP', 'REVIEW', 'UNDER_REVIEW',
    'PROVISIONAL_CREDIT', 'CHARGEBACK', 'POTENTIAL_MATCH', 'ESCALATED'];

function statusPill(status) {
    const kind = STATUS_OK.includes(status) ? 'ok'
        : STATUS_BAD.includes(status) ? 'bad'
            : STATUS_WARN.includes(status) ? 'warn' : 'neutral';
    return `<span class="badge ${kind}">${esc(status || '—')}</span>`;
}

function selectOptions(values, selected, allLabel) {
    const all = allLabel === undefined ? '' : `<option value="">${esc(allLabel)}</option>`;
    return all + values.map((value) => `<option value="${esc(value)}"${value === selected ? ' selected' : ''}>${esc(value)}</option>`).join('');
}

function wirePager(container, action, onPage) {
    container.querySelectorAll(`[data-${action}]`).forEach((pagerButton) => {
        pagerButton.addEventListener('click', () => onPage(pagerButton.dataset[action] === 'next' ? 1 : -1));
    });
}

function requireTextInput(label, defaultValue) {
    const raw = window.prompt(label, defaultValue === undefined ? '' : defaultValue);
    if (raw === null) return null;
    const value = raw.trim();
    if (!value) {
        toast(`${label}不能为空`, 'error');
        return null;
    }
    return value;
}

function requireAmountInput(label, defaultValue) {
    const raw = window.prompt(label, defaultValue === undefined ? '' : defaultValue);
    if (raw === null) return null;
    const amount = Number(raw);
    if (!Number.isFinite(amount) || amount <= 0) {
        toast('金额必须大于零', 'error');
        return null;
    }
    return Math.round(amount * 100) / 100;
}

/* 文档内容端点返回 text/plain，不能走 JSON 解析的 api() */
async function apiText(path) {
    const headers = {};
    if (state.session && state.session.token) headers['Authorization'] = `Bearer ${state.session.token}`;
    const response = await fetch(path, { headers });
    const text = await response.text();
    if (!response.ok) {
        const error = new Error(text || `请求失败（HTTP ${response.status}）`);
        error.status = response.status;
        throw error;
    }
    return text;
}

function tabsBar(group, tabs, activeKey) {
    return `
        <div class="tabs" id="${group}-tabs">
            ${tabs.map((tab) => `
                <button type="button" class="tab${tab.key === activeKey ? ' active' : ''}"
                        data-tab-key="${esc(tab.key)}">${esc(tab.label)}</button>`).join('')}
        </div>`;
}

function tabsPanels(group, tabs) {
    return `
        <div id="${group}-panels" class="mt-16">
            ${tabs.map((tab) => `<div class="tab-panel hidden" data-panel="${esc(tab.key)}"></div>`).join('')}
        </div>`;
}

function mountTabs(group, activeKey, renderers, onActivate) {
    const bar = document.getElementById(`${group}-tabs`);
    const panels = document.getElementById(`${group}-panels`);
    const rendered = new Set();

    const activate = (key) => {
        bar.querySelectorAll('[data-tab-key]').forEach((tabButton) => {
            tabButton.classList.toggle('active', tabButton.dataset.tabKey === key);
        });
        panels.querySelectorAll('[data-panel]').forEach((panel) => {
            panel.classList.toggle('hidden', panel.dataset.panel !== key);
        });
        if (onActivate) onActivate(key);
        const panel = panels.querySelector(`[data-panel="${key}"]`);
        /* 首次激活才加载；加载失败的 Tab 在再次切回时重试 */
        if (panel && renderers[key] && (!rendered.has(key) || panel.querySelector('.alert.error'))) {
            rendered.add(key);
            renderers[key](panel);
        }
    };

    bar.querySelectorAll('[data-tab-key]').forEach((tabButton) => {
        tabButton.addEventListener('click', () => activate(tabButton.dataset.tabKey));
    });
    activate(activeKey);
    return activate;
}

/* ---------------- 页面：客户 360（BFF 聚合 + 统一搜索） ---------------- */

const portalUi = { accountId: '00000000001', q: '', type: 'ALL' };

function pagePortal() {
    shell({
        active: 'portal',
        title: '客户 360',
        subtitle: 'BFF 聚合额度位置、卡片、客户、最近交易与权益，并提供客户/交易统一搜索（设计 16 §2–§3）',
        content: `
            <div class="card">
                <form class="inline-form" id="portal-form">
                    <label class="field grow"><span>账户号（11 位）</span>
                        <input id="portal-account" value="${esc(portalUi.accountId)}" maxlength="11"
                               pattern="\\d{11}" required></label>
                    <button class="btn primary" type="submit" id="portal-load">查询</button>
                </form>
                <div id="portal-result"><div class="empty">输入账户号后点击查询</div></div>
            </div>
            <div class="card mt-16">
                <div class="section-title">
                    <h3>统一搜索</h3>
                    <span class="hint">客户按客户号/姓名匹配；交易支持交易号、11 位账户号、16 位卡号</span>
                </div>
                <form class="inline-form" id="portal-search-form">
                    <label class="field grow"><span>关键词 q</span>
                        <input id="portal-q" value="${esc(portalUi.q)}" maxlength="64"
                               placeholder="如 Immanuel 或 000000001" required></label>
                    <label class="field"><span>类型</span>
                        <select id="portal-type">
                            ${['ALL', 'CUSTOMER', 'TRANSACTION'].map((value) => `
                                <option value="${value}"${portalUi.type === value ? ' selected' : ''}>${value}</option>`).join('')}
                        </select></label>
                    <button class="btn ghost" type="submit" id="portal-search">搜索</button>
                </form>
                <div id="portal-search-result"></div>
            </div>`
    });

    const box = document.getElementById('portal-result');
    const searchBox = document.getElementById('portal-search-result');
    const loadButton = document.getElementById('portal-load');
    const searchButton = document.getElementById('portal-search');

    const renderOverview = (overview) => {
        const degraded = Array.isArray(overview.degraded) ? overview.degraded : [];
        const position = overview.position || null;
        const cards = overview.cards || [];
        const customer = overview.customer || null;
        const transactions = overview.recentTransactions || [];
        box.innerHTML = `
            ${degraded.length === 0 ? '' : `
                <div class="alert warn">
                    <strong>下游降级</strong>：${degraded.map((name) => esc(name)).join('、')}
                    暂不可用，相关区块可能为空，其余数据仍然可用。
                </div>`}
            <div class="grid cols-2 mt-16">
                <div>
                    <h3>额度位置 <span class="muted">${esc(overview.accountId || '')}</span></h3>
                    ${position ? `
                        <dl class="dl">
                            <dt>产品编码</dt><dd class="mono">${esc(position.productCode || '—')}</dd>
                            <dt>信用额度</dt><dd class="mono">${money(position.creditLimit)}</dd>
                            <dt>当前欠款</dt><dd class="mono">${money(position.currentBalance)}</dd>
                            <dt>冻结合计</dt><dd class="mono">${money(position.activeHoldAmount)}</dd>
                            <dt>可用额度</dt><dd class="mono"><strong>${money(position.availableCredit)}</strong></dd>
                        </dl>`
                    : '<div class="empty">未返回额度位置（账户可能不存在）</div>'}
                </div>
                <div>
                    <h3>客户</h3>
                    ${customer ? `
                        <dl class="dl">
                            <dt>客户号</dt><dd class="mono">${esc(customer.customerId)}</dd>
                            <dt>姓名</dt><dd>${esc(customer.firstName || '')} ${esc(customer.lastName || '')}</dd>
                            <dt>SSN</dt><dd class="mono">${esc(customer.ssnMasked || '—')}</dd>
                            <dt>FICO</dt><dd>${esc(customer.ficoScore ?? '—')}</dd>
                        </dl>`
                    : '<div class="empty">未返回客户资料</div>'}
                </div>
            </div>
            <div class="grid cols-2 mt-16">
                <div>
                    <h3>卡片 <span class="muted">${cards.length} 张</span></h3>
                    ${cards.length === 0 ? '<div class="empty">暂无卡片</div>' : `
                        <div class="table-wrap"><table class="table">
                            <thead><tr><th>卡号</th><th>卡面姓名</th><th>到期日</th><th>状态</th></tr></thead>
                            <tbody>${cards.map((card) => `
                                <tr>
                                    <td class="mono">${esc(card.cardNumber)}</td>
                                    <td>${esc(card.embossedName || '—')}</td>
                                    <td>${dateText(card.expiryDate)}</td>
                                    <td>${statusBadge(card.activeStatus)}</td>
                                </tr>`).join('')}
                            </tbody></table></div>`}
                </div>
                <div>
                    <h3>最近交易 <span class="muted">${transactions.length} 笔</span></h3>
                    ${transactions.length === 0 ? '<div class="empty">暂无最近交易</div>' : `
                        <div class="table-wrap"><table class="table">
                            <thead><tr><th>交易号</th><th>时间</th><th class="num">金额</th><th>描述</th></tr></thead>
                            <tbody>${transactions.map((txn) => `
                                <tr>
                                    <td class="mono">${esc(txn.transactionId)}</td>
                                    <td class="nowrap">${dateTimeText(txn.processedTimestamp)}</td>
                                    <td class="num mono">${money(txn.amount)}</td>
                                    <td>${esc(txn.description || '—')}</td>
                                </tr>`).join('')}
                            </tbody></table></div>`}
                </div>
            </div>`;
    };

    const loadOverview = async (accountId) => {
        portalUi.accountId = accountId;
        box.innerHTML = '<div class="empty">加载中…</div>';
        setBusy(loadButton, true, '查询中…');
        try {
            const overview = await api(`/api/v1/bff/customer-overview?accountId=${encodeURIComponent(accountId)}`);
            renderOverview(overview);
        } catch (error) {
            box.innerHTML = `${errBox(error)}
                <div class="hint">BFF 服务（8107）或其下游未启动时无法聚合；账户不存在返回 404。</div>`;
        } finally {
            setBusy(loadButton, false);
        }
    };

    const runSearch = async () => {
        const q = document.getElementById('portal-q').value.trim();
        const type = document.getElementById('portal-type').value;
        portalUi.q = q;
        portalUi.type = type;
        if (!q) {
            searchBox.innerHTML = '<div class="alert error">请输入搜索关键词</div>';
            return;
        }
        searchBox.innerHTML = '<div class="empty">搜索中…</div>';
        setBusy(searchButton, true, '搜索中…');
        try {
            const result = await api(`/api/v1/search?q=${encodeURIComponent(q)}&type=${encodeURIComponent(type)}&limit=10`);
            const items = result.items || [];
            const degraded = Array.isArray(result.degraded) ? result.degraded : [];
            searchBox.innerHTML = `
                ${degraded.length === 0 ? '' : `
                    <div class="alert warn">
                        <strong>搜索降级</strong>：${degraded.map((name) => esc(name)).join('、')} 暂不可用。
                    </div>`}
                ${items.length === 0 ? '<div class="empty">没有匹配结果</div>' : `
                    <div class="table-wrap mt-16"><table class="table">
                        <thead><tr><th>类型</th><th>ID</th><th>标题</th><th>说明</th>
                            <th class="num">金额</th><th>引用</th></tr></thead>
                        <tbody>${items.map((item) => `
                            <tr>
                                <td>${statusPill(item.type)}</td>
                                <td class="mono">${esc(item.id)}</td>
                                <td>${esc(item.title || '—')}</td>
                                <td>${esc(item.subtitle || '—')}</td>
                                <td class="num mono">${item.amount === null || item.amount === undefined
                                    ? '—' : money(item.amount)}</td>
                                <td class="mono">${esc(item.reference || '—')}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                <div class="hint mt-16">共 ${esc(result.total ?? items.length)} 条结果（最多返回 10 条）。</div>`;
        } catch (error) {
            searchBox.innerHTML = `${errBox(error)}<div class="hint">搜索服务（8108）可能未启动。</div>`;
        } finally {
            setBusy(searchButton, false);
        }
    };

    document.getElementById('portal-form').addEventListener('submit', (event) => {
        event.preventDefault();
        loadOverview(document.getElementById('portal-account').value.trim());
    });
    document.getElementById('portal-search-form').addEventListener('submit', (event) => {
        event.preventDefault();
        runSearch();
    });

    loadOverview(portalUi.accountId);
    if (portalUi.q) runSearch();
}

/* ---------------- 页面：贷款（申请/审批/放款/还款） ---------------- */

const lendingUi = { page: 0, status: '', loanMap: {}, loanId: '', loan: null, schedule: [] };

const LOAN_STATUSES = ['SUBMITTED', 'APPROVED', 'REJECTED', 'DISBURSED', 'CLOSED'];

const formatRate = (value) => (value === null || value === undefined || value === ''
    ? '—'
    : `${(Number(value) * 100).toFixed(2)}%`);

function pageLending() {
    shell({
        active: 'lending',
        title: '贷款',
        subtitle: '贷款申请、审批、放款、还款计划与还款（设计 14 §2）',
        content: `
            <div class="card">
                <h3>提交贷款申请</h3>
                <form id="lending-form">
                    <div class="form-grid">
                        <label class="field"><span>客户号 <span class="req">*</span></span>
                            <input name="customerId" value="000000001" maxlength="9" pattern="\\d{9}" required></label>
                        <label class="field"><span>产品编码 <span class="req">*</span></span>
                            <input name="productCode" value="PERSONAL" maxlength="40" required></label>
                        <label class="field"><span>本金 <span class="req">*</span></span>
                            <input name="principal" type="number" step="0.01" min="0.01" value="12000.00" required></label>
                        <label class="field"><span>期限（月） <span class="req">*</span></span>
                            <input name="termMonths" type="number" min="1" max="360" value="12" required></label>
                        <label class="field"><span>年利率（0–1） <span class="req">*</span></span>
                            <input name="annualRate" type="number" step="0.0001" min="0" max="0.9999"
                                   value="0.1800" required></label>
                    </div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="lending-save">提交申请</button>
                    </div>
                    <div class="hint mt-16">审批通过后按等额本息生成还款计划；放款需要申请对应的 loanId。</div>
                    <div id="lending-msg"></div>
                </form>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>申请列表</h3>
                    <form class="inline-form" id="lending-filter">
                        <label class="field"><span>状态</span>
                            <select id="lending-status">
                                ${selectOptions(LOAN_STATUSES, lendingUi.status, '全部')}
                            </select></label>
                        <button class="btn ghost" type="submit">查询</button>
                    </form>
                </div>
                <div id="lending-list"></div>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>贷款详情与还款</h3>
                    <form class="inline-form" id="lending-loan-form">
                        <label class="field grow"><span>贷款号 loanId</span>
                            <input id="lending-loan-id" value="${esc(lendingUi.loanId)}" maxlength="36"
                                   placeholder="审批/放款响应中的 loan.loanId"></label>
                        <button class="btn ghost" type="submit">查询贷款</button>
                    </form>
                </div>
                <div id="lending-loan"><div class="empty">审批或放款后可在此查看贷款与还款计划</div></div>
            </div>`
    });

    const listBox = document.getElementById('lending-list');
    const loanBox = document.getElementById('lending-loan');
    const form = document.getElementById('lending-form');
    const saveButton = document.getElementById('lending-save');

    const renderLoan = () => {
        const loan = lendingUi.loan;
        const schedule = lendingUi.schedule;
        const next = schedule.find((item) => item.status === 'PENDING');
        loanBox.innerHTML = `
            <div class="grid cols-2">
                <div>
                    <dl class="dl">
                        <dt>贷款号</dt><dd class="mono">${esc(loan.loanId)}</dd>
                        <dt>申请号</dt><dd class="mono">${esc(loan.applicationId)}</dd>
                        <dt>客户号</dt><dd class="mono">${esc(loan.customerId)}</dd>
                        <dt>本金</dt><dd class="mono">${money(loan.principal)}</dd>
                        <dt>剩余本金</dt><dd class="mono"><strong>${money(loan.outstandingPrincipal)}</strong></dd>
                        <dt>期限 / 年利率</dt><dd>${esc(loan.termMonths)} 月 / ${formatRate(loan.annualRate)}</dd>
                        <dt>状态</dt><dd>${statusPill(loan.status)}</dd>
                        <dt>放款时间</dt><dd>${dateTimeText(loan.disbursedAt)}</dd>
                    </dl>
                </div>
                <form id="repay-form">
                    <h3>还款</h3>
                    <div class="form-grid">
                        <label class="field span-2"><span>还款金额 <span class="req">*</span></span>
                            <input name="amount" type="number" step="0.01" min="0.01"
                                   value="${next ? esc(next.totalDue) : ''}" required></label>
                    </div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="repay-save">提交还款</button>
                    </div>
                    <div class="hint mt-16">
                        金额须等于下一 PENDING 期次合计${next
                            ? `（第 ${esc(next.installmentNo)} 期 ${money(next.totalDue)}）` : ''}，
                        或剩余全部期次总额（提前结清），否则服务端返回 409。
                    </div>
                    <div id="repay-msg"></div>
                </form>
            </div>
            <div class="section-title mt-16">
                <h3>还款计划 <span class="muted">${schedule.length} 期</span></h3>
                <span class="hint">等额本息，按到期日排序</span>
            </div>
            ${schedule.length === 0 ? '<div class="empty">暂无还款计划</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>期次</th><th>到期日</th><th class="num">本金</th><th class="num">利息</th>
                        <th class="num">应还合计</th><th>状态</th><th>还款时间</th></tr></thead>
                    <tbody>${schedule.map((item) => `
                        <tr>
                            <td class="mono">${esc(item.installmentNo)}</td>
                            <td class="nowrap">${dateText(item.dueDate)}</td>
                            <td class="num mono">${money(item.principalDue)}</td>
                            <td class="num mono">${money(item.interestDue)}</td>
                            <td class="num mono">${money(item.totalDue)}</td>
                            <td>${statusPill(item.status)}</td>
                            <td class="nowrap">${dateTimeText(item.paidAt)}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`}`;

        const repayForm = document.getElementById('repay-form');
        const repayButton = document.getElementById('repay-save');
        repayForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            const values = formValues(repayForm);
            const msg = document.getElementById('repay-msg');
            msg.innerHTML = '';
            setBusy(repayButton, true, '还款中…');
            try {
                const result = await api(`/api/v1/loans/${encodeURIComponent(loan.loanId)}/repayments`, {
                    method: 'POST',
                    body: { amount: Number(values.amount) }
                });
                toast(`还款成功：利息与本金的期次已冲抵`, 'success');
                await loadLoan(loan.loanId);
                loanBox.insertAdjacentHTML('afterbegin', `
                    <div class="alert success">
                        已还 <strong>${money(result.amount)}</strong>，冲抵期次
                        ${(result.paidInstallments || []).map((no) => `第 ${esc(no)} 期`).join('、') || '—'}，
                        剩余本金 <strong>${money(result.outstandingPrincipal)}</strong>，
                        贷款状态 ${statusPill(result.loanStatus)}。
                    </div>`);
            } catch (error) {
                msg.innerHTML = errBox(error);
                setBusy(repayButton, false);
            }
        });
    };

    const loadLoan = async (loanId) => {
        lendingUi.loanId = loanId;
        loanBox.innerHTML = '<div class="empty">加载中…</div>';
        const encoded = encodeURIComponent(loanId);
        try {
            const [loan, schedule] = await Promise.all([
                api(`/api/v1/loans/${encoded}`),
                api(`/api/v1/loans/${encoded}/schedule`)
            ]);
            lendingUi.loan = loan;
            lendingUi.schedule = listOf(schedule);
            renderLoan();
            const field = document.getElementById('lending-loan-id');
            if (field) field.value = loanId;
        } catch (error) {
            lendingUi.loan = null;
            lendingUi.schedule = [];
            loanBox.innerHTML = `${errBox(error)}
                <div class="hint">贷款服务（8095）可能未启动，或贷款号不存在。</div>`;
        }
    };

    const loanIdFor = (applicationId) => lendingUi.loanMap[applicationId]
        || requireTextInput('该申请未记录 loanId，请填写审批响应中的 loan.loanId', lendingUi.loanId);

    const openLoan = (applicationId) => {
        const loanId = loanIdFor(applicationId);
        if (!loanId) return;
        loadLoan(loanId);
    };

    const disburse = async (applicationId, actionButton) => {
        const loanId = loanIdFor(applicationId);
        if (!loanId) return;
        setBusy(actionButton, true, '放款中…');
        try {
            const loan = await api(`/api/v1/loans/${encodeURIComponent(loanId)}/disburse`, { method: 'POST' });
            lendingUi.loanMap[loan.applicationId || applicationId] = loan.loanId;
            toast(`已放款：贷款号 ${loan.loanId}`, 'success');
            await loadList(lendingUi.page);
            await loadLoan(loan.loanId);
        } catch (error) {
            toast(`${error.code || '放款失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const applicationActions = (application) => {
        const id = esc(application.applicationId);
        if (application.status === 'SUBMITTED') {
            return `
                <button class="btn primary small" data-approve="${id}">通过</button>
                <button class="btn danger small" data-reject="${id}">拒绝</button>`;
        }
        if (application.status === 'APPROVED') {
            return `<button class="btn primary small" data-disburse="${id}">放款</button>`;
        }
        if (application.status === 'DISBURSED' || application.status === 'CLOSED') {
            return `<button class="btn ghost small" data-loan="${id}">贷款详情</button>`;
        }
        return application.rejectReason
            ? `<span class="muted">${esc(application.rejectReason)}</span>`
            : '—';
    };

    const loadList = async (page) => {
        lendingUi.page = Math.max(0, page);
        lendingUi.status = document.getElementById('lending-status').value;
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(lendingUi.page), size: '10' });
        if (lendingUi.status) params.set('status', lendingUi.status);
        try {
            const result = await api(`/api/v1/loan-applications?${params.toString()}`);
            const items = result.items || [];
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的贷款申请</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>申请号</th><th>客户</th><th>产品</th><th class="num">本金</th><th>期限</th>
                            <th class="num">年利率</th><th>状态</th><th>提交时间</th><th>操作</th></tr></thead>
                        <tbody>${items.map((application) => `
                            <tr>
                                <td class="mono">${esc(application.applicationId)}</td>
                                <td class="mono">${esc(application.customerId)}</td>
                                <td>${esc(application.productCode)}</td>
                                <td class="num mono">${money(application.principal)}</td>
                                <td>${esc(application.termMonths)} 月</td>
                                <td class="num mono">${formatRate(application.annualRate)}</td>
                                <td>${statusPill(application.status)}</td>
                                <td class="nowrap">${dateTimeText(application.createdAt)}</td>
                                <td class="actions">${applicationActions(application)}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(lendingUi.page, result.size || 10, result.total || 0)}`;

            wirePager(listBox, 'page', (step) => loadList(lendingUi.page + step));

            listBox.querySelectorAll('[data-approve]').forEach((actionButton) => {
                actionButton.addEventListener('click', async () => {
                    const applicationId = actionButton.dataset.approve;
                    setBusy(actionButton, true, '审批中…');
                    try {
                        const approval = await api(
                            `/api/v1/loan-applications/${encodeURIComponent(applicationId)}/approve`,
                            { method: 'POST' });
                        const approvedId = approval.application
                            ? approval.application.applicationId : applicationId;
                        const loan = approval.loan || null;
                        if (loan && loan.loanId) lendingUi.loanMap[approvedId] = loan.loanId;
                        toast(loan
                            ? `已通过，贷款号 ${loan.loanId}，计划 ${(approval.schedule || []).length} 期`
                            : '已通过', 'success');
                        await loadList(lendingUi.page);
                        if (loan && loan.loanId) await loadLoan(loan.loanId);
                    } catch (error) {
                        toast(`${error.code || '审批失败'}：${error.message}`, 'error');
                        setBusy(actionButton, false);
                    }
                });
            });

            listBox.querySelectorAll('[data-reject]').forEach((actionButton) => {
                actionButton.addEventListener('click', async () => {
                    const reason = requireTextInput('拒绝原因（必填，最多 200 字）', '收入证明不足');
                    if (reason === null) return;
                    setBusy(actionButton, true, '提交中…');
                    try {
                        await api(`/api/v1/loan-applications/${encodeURIComponent(actionButton.dataset.reject)}/reject`,
                            { method: 'POST', body: { reason } });
                        toast('申请已拒绝', 'success');
                        await loadList(lendingUi.page);
                    } catch (error) {
                        toast(`${error.code || '拒绝失败'}：${error.message}`, 'error');
                        setBusy(actionButton, false);
                    }
                });
            });

            listBox.querySelectorAll('[data-disburse]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => disburse(actionButton.dataset.disburse, actionButton));
            });

            listBox.querySelectorAll('[data-loan]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => openLoan(actionButton.dataset.loan));
            });
        } catch (error) {
            listBox.innerHTML = `${errBox(error)}<div class="hint">贷款服务（8095）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('lending-msg');
        msg.innerHTML = '';
        setBusy(saveButton, true, '提交中…');
        try {
            const application = await api('/api/v1/loan-applications', {
                method: 'POST',
                body: {
                    customerId: values.customerId.trim(),
                    productCode: values.productCode.trim(),
                    principal: Number(values.principal),
                    termMonths: Number(values.termMonths),
                    annualRate: Number(values.annualRate)
                }
            });
            toast(`申请已提交：${application.applicationId}`, 'success');
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('lending-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList(0);
    });

    document.getElementById('lending-loan-form').addEventListener('submit', (event) => {
        event.preventDefault();
        const loanId = document.getElementById('lending-loan-id').value.trim();
        if (!loanId) {
            toast('请填写贷款号', 'error');
            return;
        }
        loadLoan(loanId);
    });

    loadList(lendingUi.page);
    if (lendingUi.loanId) loadLoan(lendingUi.loanId);
}

/* ---------------- 页面：风险（欺诈 / AML / 争议 / 催收） ---------------- */

const riskUi = {
    tab: 'fraud',
    fraud: { status: '', page: 0 },
    aml: { status: '', page: 0 },
    dispute: { status: '', page: 0 },
    collections: { status: '', bucket: '', page: 0 }
};

const RISK_TABS = [
    { key: 'fraud', label: '欺诈' },
    { key: 'aml', label: 'AML' },
    { key: 'dispute', label: '争议' },
    { key: 'collections', label: '催收' }
];

const FRAUD_ALERT_STATUSES = ['OPEN', 'CONFIRMED', 'DISMISSED'];
const AML_ALERT_STATUSES = ['OPEN', 'CLEARED', 'ESCALATED'];
const DISPUTE_STATUSES = ['OPEN', 'UNDER_REVIEW', 'PROVISIONAL_CREDIT', 'CHARGEBACK', 'WON', 'LOST', 'CLOSED'];
const COLLECTION_STATUSES = ['OPEN', 'PTP', 'RESOLVED'];
const COLLECTION_BUCKETS = ['B1', 'B2', 'B3', 'B4'];

function pageRisk() {
    if (!requireAdmin('risk', '风险')) return;
    shell({
        active: 'risk',
        title: '风险',
        subtitle: '欺诈评分与警报、AML 名单筛查、争议案件与逾期催收（设计 14 §3–§6，管理员）',
        content: `
            <div class="card">
                <div class="section-title">
                    <h3>风险处置</h3>
                    <span class="hint">四个 Tab 各自加载，单个服务不可用不影响其它 Tab</span>
                </div>
                ${tabsBar('risk', RISK_TABS, riskUi.tab)}
            </div>
            ${tabsPanels('risk', RISK_TABS)}`
    });

    mountTabs('risk', riskUi.tab, {
        fraud: renderRiskFraud,
        aml: renderRiskAml,
        dispute: renderRiskDispute,
        collections: renderRiskCollections
    }, (key) => { riskUi.tab = key; });
}

function renderRiskFraud(panel) {
    panel.innerHTML = `
        <div class="grid cols-2">
            <div class="card">
                <h3>交易欺诈评分</h3>
                <form id="fraud-form">
                    <div class="form-grid">
                        <label class="field"><span>交易号 <span class="req">*</span></span>
                            <input name="transactionId" value="${esc(`txn-${uuid().slice(0, 8)}`)}"
                                   maxlength="36" required></label>
                        <label class="field"><span>账户号 <span class="req">*</span></span>
                            <input name="accountId" value="00000000001" maxlength="11" pattern="\\d{11}" required></label>
                        <label class="field"><span>金额 <span class="req">*</span></span>
                            <input name="amount" type="number" step="0.01" min="0.01" value="6500.00" required></label>
                        <label class="field"><span>商户名称</span>
                            <input name="merchantName" value="BLOCKED MERCHANT" maxlength="50"></label>
                        <label class="field"><span>国家</span>
                            <input name="country" value="KP" maxlength="3"></label>
                    </div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="fraud-save">评分</button>
                    </div>
                    <div class="hint mt-16">命中规则分值求和：≥80 拒绝、≥50 复核；复核/拒绝会生成 OPEN 警报。</div>
                    <div id="fraud-msg"></div>
                </form>
                <div id="fraud-result"></div>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>欺诈警报</h3>
                    <form class="inline-form" id="fraud-filter">
                        <label class="field"><span>状态</span>
                            <select id="fraud-status">
                                ${selectOptions(FRAUD_ALERT_STATUSES, riskUi.fraud.status, '全部')}
                            </select></label>
                        <button class="btn ghost" type="submit">查询</button>
                    </form>
                </div>
                <div id="fraud-alerts"></div>
            </div>
        </div>`;

    const form = document.getElementById('fraud-form');
    const saveButton = document.getElementById('fraud-save');
    const resultBox = document.getElementById('fraud-result');
    const alertsBox = document.getElementById('fraud-alerts');

    const decideAlert = async (actionButton, action) => {
        const label = action === 'confirm' ? '确认欺诈' : '排除';
        setBusy(actionButton, true, '处理中…');
        try {
            await api(`/api/v1/fraud-alerts/${encodeURIComponent(actionButton.dataset[action])}/${action}`,
                { method: 'POST' });
            toast(`警报已${label}`, 'success');
            await loadAlerts(riskUi.fraud.page);
        } catch (error) {
            toast(`${error.code || `${label}失败`}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadAlerts = async (page) => {
        riskUi.fraud.page = Math.max(0, page);
        riskUi.fraud.status = document.getElementById('fraud-status').value;
        alertsBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(riskUi.fraud.page), size: '10' });
        if (riskUi.fraud.status) params.set('status', riskUi.fraud.status);
        try {
            const result = await api(`/api/v1/fraud-alerts?${params.toString()}`);
            const items = result.items || [];
            alertsBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的警报</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>警报号</th><th>账户</th><th class="num">分值</th><th>状态</th>
                            <th>原因</th><th>创建时间</th><th>操作</th></tr></thead>
                        <tbody>${items.map((alert) => `
                            <tr>
                                <td class="mono">${esc(alert.alertId)}</td>
                                <td class="mono">${esc(alert.accountId)}</td>
                                <td class="num mono">${esc(alert.score)}</td>
                                <td>${statusPill(alert.status)}</td>
                                <td>${esc(alert.reason || '—')}</td>
                                <td class="nowrap">${dateTimeText(alert.createdAt)}</td>
                                <td class="actions">${alert.status === 'OPEN' ? `
                                    <button class="btn danger small" data-confirm="${esc(alert.alertId)}">确认</button>
                                    <button class="btn ghost small" data-dismiss="${esc(alert.alertId)}">排除</button>`
                                    : '—'}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(riskUi.fraud.page, result.size || 10, result.total || 0, 'fraudpage')}`;

            wirePager(alertsBox, 'fraudpage', (step) => loadAlerts(riskUi.fraud.page + step));
            alertsBox.querySelectorAll('[data-confirm]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => decideAlert(actionButton, 'confirm'));
            });
            alertsBox.querySelectorAll('[data-dismiss]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => decideAlert(actionButton, 'dismiss'));
            });
        } catch (error) {
            alertsBox.innerHTML = `${errBox(error)}<div class="hint">欺诈服务（8096）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('fraud-msg');
        msg.innerHTML = '';
        setBusy(saveButton, true, '评分中…');
        try {
            const evaluation = await api('/api/v1/fraud-evaluations', {
                method: 'POST',
                body: {
                    transactionId: values.transactionId.trim(),
                    accountId: values.accountId.trim(),
                    amount: Number(values.amount),
                    merchantName: values.merchantName.trim() || null,
                    country: values.country.trim() || null
                }
            });
            const matched = evaluation.matchedRules || [];
            resultBox.innerHTML = `
                <div class="alert ${evaluation.decision === 'APPROVE' ? 'success' : 'warn'} mt-16">
                    决策 <strong>${esc(evaluation.decision)}</strong> · 分值 ${esc(evaluation.score)}
                    ${evaluation.alertId ? ` · 已生成警报 <code>${esc(evaluation.alertId)}</code>` : ''}
                </div>
                <dl class="dl">
                    <dt>评分号</dt><dd class="mono">${esc(evaluation.evaluationId)}</dd>
                    <dt>交易号</dt><dd class="mono">${esc(evaluation.transactionId)}</dd>
                    <dt>账户 / 金额</dt><dd class="mono">${esc(evaluation.accountId)} / ${money(evaluation.amount)}</dd>
                    <dt>商户 / 国家</dt><dd>${esc(evaluation.merchantName || '—')} / ${esc(evaluation.country || '—')}</dd>
                    <dt>命中规则</dt><dd>${matched.length === 0 ? '—'
                        : matched.map((rule) => `<span class="badge warn">${esc(rule)}</span>`).join(' ')}</dd>
                </dl>`;
            toast(`评分完成：${evaluation.decision}（${evaluation.score}）`, 'success');
            form.querySelector('[name="transactionId"]').value = `txn-${uuid().slice(0, 8)}`;
            if (evaluation.alertId) await loadAlerts(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('fraud-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadAlerts(0);
    });

    loadAlerts(riskUi.fraud.page);
}

function renderRiskAml(panel) {
    panel.innerHTML = `
        <div class="grid cols-2">
            <div class="card">
                <h3>名单筛查</h3>
                <form id="aml-form">
                    <div class="form-grid">
                        <label class="field span-2"><span>姓名 / 实体名 <span class="req">*</span></span>
                            <input name="name" value="IVAN PETROV" maxlength="100" required></label>
                        <label class="field"><span>国家</span>
                            <input name="country" value="RU" maxlength="3"></label>
                        <label class="field"><span>实体类型</span>
                            <input name="entityType" value="INDIVIDUAL" maxlength="24"></label>
                        <label class="field"><span>客户号（可选）</span>
                            <input name="customerId" value="000000001" maxlength="9" pattern="\\d{9}"></label>
                    </div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="aml-save">筛查</button>
                    </div>
                    <div class="hint mt-16">完全匹配 BLOCKED、包含匹配 POTENTIAL_MATCH；命中生成 OPEN 警报。</div>
                    <div id="aml-msg"></div>
                </form>
                <div id="aml-result"></div>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>AML 警报</h3>
                    <form class="inline-form" id="aml-filter">
                        <label class="field"><span>状态</span>
                            <select id="aml-status">
                                ${selectOptions(AML_ALERT_STATUSES, riskUi.aml.status, '全部')}
                            </select></label>
                        <button class="btn ghost" type="submit">查询</button>
                    </form>
                </div>
                <div id="aml-alerts"></div>
            </div>
        </div>`;

    const form = document.getElementById('aml-form');
    const saveButton = document.getElementById('aml-save');
    const resultBox = document.getElementById('aml-result');
    const alertsBox = document.getElementById('aml-alerts');

    const decideAlert = async (actionButton, action) => {
        const label = action === 'clear' ? '排除' : '上报';
        setBusy(actionButton, true, '处理中…');
        try {
            await api(`/api/v1/aml-alerts/${encodeURIComponent(actionButton.dataset[action])}/${action}`,
                { method: 'POST' });
            toast(`警报已${label}`, 'success');
            await loadAlerts(riskUi.aml.page);
        } catch (error) {
            toast(`${error.code || `${label}失败`}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadAlerts = async (page) => {
        riskUi.aml.page = Math.max(0, page);
        riskUi.aml.status = document.getElementById('aml-status').value;
        alertsBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(riskUi.aml.page), size: '10' });
        if (riskUi.aml.status) params.set('status', riskUi.aml.status);
        try {
            const result = await api(`/api/v1/aml-alerts?${params.toString()}`);
            const items = result.items || [];
            alertsBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的警报</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>警报号</th><th>筛查号</th><th>客户</th><th>状态</th>
                            <th>详情</th><th>创建时间</th><th>操作</th></tr></thead>
                        <tbody>${items.map((alert) => `
                            <tr>
                                <td class="mono">${esc(alert.alertId)}</td>
                                <td class="mono">${esc(alert.screeningId)}</td>
                                <td class="mono">${esc(alert.customerId || '—')}</td>
                                <td>${statusPill(alert.status)}</td>
                                <td>${esc(alert.details || '—')}</td>
                                <td class="nowrap">${dateTimeText(alert.createdAt)}</td>
                                <td class="actions">${alert.status === 'OPEN' ? `
                                    <button class="btn ghost small" data-clear="${esc(alert.alertId)}">排除</button>
                                    <button class="btn danger small" data-escalate="${esc(alert.alertId)}">上报</button>`
                                    : '—'}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(riskUi.aml.page, result.size || 10, result.total || 0, 'amlpage')}`;

            wirePager(alertsBox, 'amlpage', (step) => loadAlerts(riskUi.aml.page + step));
            alertsBox.querySelectorAll('[data-clear]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => decideAlert(actionButton, 'clear'));
            });
            alertsBox.querySelectorAll('[data-escalate]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => decideAlert(actionButton, 'escalate'));
            });
        } catch (error) {
            alertsBox.innerHTML = `${errBox(error)}<div class="hint">AML 服务（8097）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('aml-msg');
        msg.innerHTML = '';
        setBusy(saveButton, true, '筛查中…');
        try {
            const screening = await api('/api/v1/screenings', {
                method: 'POST',
                body: {
                    name: values.name.trim(),
                    country: values.country.trim() || null,
                    entityType: values.entityType.trim() || null,
                    customerId: values.customerId.trim() || null
                }
            });
            resultBox.innerHTML = `
                <div class="alert ${screening.result === 'CLEAR' ? 'success' : 'warn'} mt-16">
                    结论 <strong>${esc(screening.result)}</strong> · 分值 ${esc(screening.score)}
                    ${screening.alertId ? ` · 已生成警报 <code>${esc(screening.alertId)}</code>` : ''}
                </div>
                <dl class="dl">
                    <dt>筛查号</dt><dd class="mono">${esc(screening.screeningId)}</dd>
                    <dt>姓名</dt><dd>${esc(screening.name)}</dd>
                    <dt>国家 / 类型</dt><dd>${esc(screening.country || '—')} / ${esc(screening.entityType || '—')}</dd>
                    <dt>命中条目</dt><dd class="mono">${esc(screening.matchedEntryId || '—')}</dd>
                    <dt>筛查时间</dt><dd>${dateTimeText(screening.createdAt)}</dd>
                </dl>`;
            toast(`筛查完成：${screening.result}`, 'success');
            if (screening.alertId) await loadAlerts(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('aml-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadAlerts(0);
    });

    loadAlerts(riskUi.aml.page);
}

function renderRiskDispute(panel) {
    panel.innerHTML = `
        <div class="card">
            <h3>开立争议案件</h3>
            <form id="dispute-form">
                <div class="form-grid">
                    <label class="field"><span>交易号 <span class="req">*</span></span>
                        <input name="transactionId" value="${esc(`txn-${uuid().slice(0, 8)}`)}" maxlength="36" required></label>
                    <label class="field"><span>账户号 <span class="req">*</span></span>
                        <input name="accountId" value="00000000001" maxlength="11" pattern="\\d{11}" required></label>
                    <label class="field"><span>客户号 <span class="req">*</span></span>
                        <input name="customerId" value="000000001" maxlength="20" required></label>
                    <label class="field"><span>原因码 <span class="req">*</span></span>
                        <input name="reasonCode" value="FRAUD" maxlength="30" required></label>
                    <label class="field"><span>争议金额 <span class="req">*</span></span>
                        <input name="amount" type="number" step="0.01" min="0.01" value="120.00" required></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="dispute-save">开案</button>
                </div>
                <div class="hint mt-16">
                    状态链路：OPEN → UNDER_REVIEW → PROVISIONAL_CREDIT → CHARGEBACK → WON/LOST → CLOSED，非法迁移返回 409。
                </div>
                <div id="dispute-msg"></div>
            </form>
        </div>
        <div class="card">
            <div class="section-title">
                <h3>争议列表</h3>
                <form class="inline-form" id="dispute-filter">
                    <label class="field"><span>状态</span>
                        <select id="dispute-status">
                            ${selectOptions(DISPUTE_STATUSES, riskUi.dispute.status, '全部')}
                        </select></label>
                    <button class="btn ghost" type="submit">查询</button>
                </form>
            </div>
            <div id="dispute-list"></div>
        </div>`;

    const form = document.getElementById('dispute-form');
    const saveButton = document.getElementById('dispute-save');
    const listBox = document.getElementById('dispute-list');

    const actOnCase = async (actionButton, path, body, successText) => {
        setBusy(actionButton, true, '处理中…');
        try {
            await api(`/api/v1/disputes/${encodeURIComponent(actionButton.dataset.caseId)}/${path}`,
                body === undefined ? { method: 'POST' } : { method: 'POST', body });
            toast(successText, 'success');
            await loadList(riskUi.dispute.page);
        } catch (error) {
            toast(`${error.code || '处理失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const caseActions = (dispute) => {
        const id = esc(dispute.caseId);
        if (dispute.status === 'OPEN') {
            return `<button class="btn primary small" data-case-id="${id}" data-act="review">受理审核</button>`;
        }
        if (dispute.status === 'UNDER_REVIEW') {
            return `<button class="btn primary small" data-case-id="${id}" data-act="provisional">临时入账</button>`;
        }
        if (dispute.status === 'PROVISIONAL_CREDIT') {
            return `<button class="btn primary small" data-case-id="${id}" data-act="chargeback">发起 chargeback</button>`;
        }
        if (dispute.status === 'CHARGEBACK') {
            return `<button class="btn primary small" data-case-id="${id}" data-act="resolve">结案</button>`;
        }
        if (dispute.status === 'WON' || dispute.status === 'LOST') {
            return `<button class="btn ghost small" data-case-id="${id}"
                            data-act="archive" data-status="${esc(dispute.status)}">归档结案</button>`;
        }
        return '—';
    };

    const loadList = async (page) => {
        riskUi.dispute.page = Math.max(0, page);
        riskUi.dispute.status = document.getElementById('dispute-status').value;
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(riskUi.dispute.page), size: '10' });
        if (riskUi.dispute.status) params.set('status', riskUi.dispute.status);
        try {
            const result = await api(`/api/v1/disputes?${params.toString()}`);
            const items = result.items || [];
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的争议案件</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>案件号</th><th>交易号</th><th>账户</th><th>原因</th>
                            <th class="num">争议金额</th><th class="num">临时入账</th><th>状态</th>
                            <th>更新时间</th><th>操作</th></tr></thead>
                        <tbody>${items.map((dispute) => `
                            <tr>
                                <td class="mono">${esc(dispute.caseId)}</td>
                                <td class="mono">${esc(dispute.transactionId)}</td>
                                <td class="mono">${esc(dispute.accountId)}</td>
                                <td>${esc(dispute.reasonCode)}</td>
                                <td class="num mono">${money(dispute.amount)}</td>
                                <td class="num mono">${money(dispute.provisionalCreditAmount)}</td>
                                <td>${statusPill(dispute.status)}</td>
                                <td class="nowrap">${dateTimeText(dispute.updatedAt)}</td>
                                <td class="actions">${caseActions(dispute)}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(riskUi.dispute.page, result.size || 10, result.total || 0, 'disputepage')}`;

            wirePager(listBox, 'disputepage', (step) => loadList(riskUi.dispute.page + step));

            listBox.querySelectorAll('[data-act]').forEach((actionButton) => {
                actionButton.addEventListener('click', async () => {
                    const act = actionButton.dataset.act;
                    if (act === 'review') {
                        await actOnCase(actionButton, 'review', undefined, '已受理审核');
                        return;
                    }
                    if (act === 'chargeback') {
                        await actOnCase(actionButton, 'chargeback', undefined, '已发起 chargeback');
                        return;
                    }
                    if (act === 'provisional') {
                        const amount = requireAmountInput('临时入账金额（不得超过争议金额）', '50.00');
                        if (amount === null) return;
                        await actOnCase(actionButton, 'provisional-credit', { amount }, '临时入账已登记');
                        return;
                    }
                    if (act === 'resolve') {
                        const outcome = window.prompt('结案结果：输入 won 判持卡人胜（WON），输入 lose 判商户胜（LOST）', 'won');
                        if (outcome === null) return;
                        const won = outcome.trim().toLowerCase() === 'won';
                        await actOnCase(actionButton, 'resolve', { won }, won ? '结案：持卡人胜（WON）' : '结案：商户胜（LOST）');
                        return;
                    }
                    if (act === 'archive') {
                        const won = actionButton.dataset.status === 'WON';
                        await actOnCase(actionButton, 'resolve', { won }, '案件已归档关闭');
                    }
                });
            });
        } catch (error) {
            listBox.innerHTML = `${errBox(error)}<div class="hint">争议服务（8098）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('dispute-msg');
        msg.innerHTML = '';
        setBusy(saveButton, true, '开案中…');
        try {
            const dispute = await api('/api/v1/disputes', {
                method: 'POST',
                body: {
                    transactionId: values.transactionId.trim(),
                    accountId: values.accountId.trim(),
                    customerId: values.customerId.trim(),
                    reasonCode: values.reasonCode.trim(),
                    amount: Number(values.amount)
                }
            });
            toast(`争议已开案：${dispute.caseId}`, 'success');
            form.querySelector('[name="transactionId"]').value = `txn-${uuid().slice(0, 8)}`;
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('dispute-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList(0);
    });

    loadList(riskUi.dispute.page);
}

function renderRiskCollections(panel) {
    panel.innerHTML = `
        <div class="card">
            <h3>建立逾期案件</h3>
            <form id="collection-form">
                <div class="form-grid">
                    <label class="field"><span>账户号 <span class="req">*</span></span>
                        <input name="accountId" value="00000000006" maxlength="11" pattern="\\d{11}" required></label>
                    <label class="field"><span>逾期天数 <span class="req">*</span></span>
                        <input name="daysPastDue" type="number" min="1" value="45" required></label>
                    <label class="field"><span>逾期金额 <span class="req">*</span></span>
                        <input name="outstandingAmount" type="number" step="0.01" min="0.01"
                               value="1234.56" required></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="collection-save">建案</button>
                </div>
                <div class="hint mt-16">分桶：1–30 B1、31–60 B2、61–90 B3、90+ B4；承诺还款日期须晚于当天。</div>
                <div id="collection-msg"></div>
            </form>
        </div>
        <div class="card">
            <div class="section-title">
                <h3>催收案件</h3>
                <form class="inline-form" id="collection-filter">
                    <label class="field"><span>状态</span>
                        <select id="collection-status">
                            ${selectOptions(COLLECTION_STATUSES, riskUi.collections.status, '全部')}
                        </select></label>
                    <label class="field"><span>分桶</span>
                        <select id="collection-bucket">
                            ${selectOptions(COLLECTION_BUCKETS, riskUi.collections.bucket, '全部')}
                        </select></label>
                    <button class="btn ghost" type="submit">查询</button>
                </form>
            </div>
            <div id="collection-list"></div>
        </div>`;

    const form = document.getElementById('collection-form');
    const saveButton = document.getElementById('collection-save');
    const listBox = document.getElementById('collection-list');

    const actOnCase = async (actionButton, path, body, successText) => {
        setBusy(actionButton, true, '处理中…');
        try {
            await api(`/api/v1/delinquency-cases/${encodeURIComponent(actionButton.dataset.caseId)}/${path}`,
                body === undefined ? { method: 'POST' } : { method: 'POST', body });
            toast(successText, 'success');
            await loadList(riskUi.collections.page);
        } catch (error) {
            toast(`${error.code || '处理失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadList = async (page) => {
        riskUi.collections.page = Math.max(0, page);
        riskUi.collections.status = document.getElementById('collection-status').value;
        riskUi.collections.bucket = document.getElementById('collection-bucket').value;
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(riskUi.collections.page), size: '10' });
        if (riskUi.collections.status) params.set('status', riskUi.collections.status);
        if (riskUi.collections.bucket) params.set('bucket', riskUi.collections.bucket);
        try {
            const result = await api(`/api/v1/delinquency-cases?${params.toString()}`);
            const items = result.items || [];
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的逾期案件</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>案件号</th><th>账户</th><th class="num">逾期天数</th><th>分桶</th>
                            <th class="num">逾期金额</th><th>状态</th><th>承诺还款日</th><th>更新时间</th>
                            <th>操作</th></tr></thead>
                        <tbody>${items.map((item) => `
                            <tr>
                                <td class="mono">${esc(item.caseId)}</td>
                                <td class="mono">${esc(item.accountId)}</td>
                                <td class="num mono">${esc(item.daysPastDue)}</td>
                                <td><span class="badge warn">${esc(item.bucket)}</span></td>
                                <td class="num mono">${money(item.outstandingAmount)}</td>
                                <td>${statusPill(item.status)}</td>
                                <td class="nowrap">${dateText(item.promiseDate)}</td>
                                <td class="nowrap">${dateTimeText(item.updatedAt)}</td>
                                <td class="actions">${item.status === 'RESOLVED' ? '—' : `
                                    <button class="btn ghost small" data-case-id="${esc(item.caseId)}"
                                            data-act="recalculate">重算</button>
                                    <button class="btn ghost small" data-case-id="${esc(item.caseId)}"
                                            data-act="promise">承诺还款</button>
                                    <button class="btn danger small" data-case-id="${esc(item.caseId)}"
                                            data-act="resolve">结案</button>`}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(riskUi.collections.page, result.size || 10, result.total || 0, 'collectionpage')}`;

            wirePager(listBox, 'collectionpage', (step) => loadList(riskUi.collections.page + step));

            listBox.querySelectorAll('[data-act]').forEach((actionButton) => {
                actionButton.addEventListener('click', async () => {
                    const act = actionButton.dataset.act;
                    if (act === 'recalculate') {
                        const raw = window.prompt('新的逾期天数（≥1）', '75');
                        if (raw === null) return;
                        const days = Number(raw);
                        if (!Number.isInteger(days) || days < 1) {
                            toast('逾期天数必须是不小于 1 的整数', 'error');
                            return;
                        }
                        await actOnCase(actionButton, 'recalculate', { daysPastDue: days }, '已重算分桶');
                        return;
                    }
                    if (act === 'promise') {
                        const date = window.prompt('承诺还款日期（YYYY-MM-DD，须晚于当天）', tomorrowDate());
                        if (date === null) return;
                        const value = date.trim();
                        if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
                            toast('日期格式应为 YYYY-MM-DD', 'error');
                            return;
                        }
                        await actOnCase(actionButton, 'promise-to-pay', { date: value }, '已登记承诺还款');
                        return;
                    }
                    if (act === 'resolve') {
                        if (!window.confirm('确认结案？结案后不可再变更。')) return;
                        await actOnCase(actionButton, 'resolve', undefined, '案件已结案');
                    }
                });
            });
        } catch (error) {
            listBox.innerHTML = `${errBox(error)}<div class="hint">催收服务（8099）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('collection-msg');
        msg.innerHTML = '';
        setBusy(saveButton, true, '建案中…');
        try {
            const created = await api('/api/v1/delinquency-cases', {
                method: 'POST',
                body: {
                    accountId: values.accountId.trim(),
                    daysPastDue: Number(values.daysPastDue),
                    outstandingAmount: Number(values.outstandingAmount)
                }
            });
            toast(`逾期案件已建立：${created.caseId}（${created.bucket}）`, 'success');
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('collection-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList(0);
    });

    loadList(riskUi.collections.page);
}

/* ---------------- 页面：运营（批处理/报表/通知/文档/工作流/审计/参考数据） ---------------- */

const opsUi = {
    tab: 'batch',
    batch: { page: 0 },
    report: { page: 0 },
    notification: { page: 0 },
    document: { page: 0, ownerType: '', ownerId: '' },
    workflow: { status: '', assignee: '', page: 0 },
    audit: { actor: '', resourceType: '', resourceId: '', page: 0 },
    reference: { domain: 'CURRENCY' }
};

const OPS_TABS = [
    { key: 'batch', label: '批处理' },
    { key: 'report', label: '报表' },
    { key: 'notification', label: '通知' },
    { key: 'document', label: '文档' },
    { key: 'workflow', label: '工作流' },
    { key: 'audit', label: '审计' },
    { key: 'reference', label: '参考数据' }
];

const WORKFLOW_STATUSES = ['PENDING', 'APPROVED', 'REJECTED'];
const DOCUMENT_TYPES = ['STATEMENT', 'CONTRACT', 'RECEIPT', 'OTHER'];
const DOCUMENT_OWNER_TYPES = ['CUSTOMER', 'ACCOUNT'];
const REFERENCE_DOMAINS = ['CURRENCY', 'COUNTRY', 'HOLIDAY', 'PRODUCT_CATEGORY'];

function pageOperations() {
    if (!requireAdmin('operations', '运营')) return;
    shell({
        active: 'operations',
        title: '运营',
        subtitle: '批处理、报表、通知、文档、工作流、审计与参考数据（设计 15，管理员）',
        content: `
            <div class="card">
                <div class="section-title">
                    <h3>运营支撑</h3>
                    <span class="hint">七个 Tab 各自加载，单个扩展服务不可用不影响其它 Tab</span>
                </div>
                ${tabsBar('operations', OPS_TABS, opsUi.tab)}
            </div>
            ${tabsPanels('operations', OPS_TABS)}`
    });

    mountTabs('operations', opsUi.tab, {
        batch: renderOpsBatch,
        report: renderOpsReport,
        notification: renderOpsNotification,
        document: renderOpsDocument,
        workflow: renderOpsWorkflow,
        audit: renderOpsAudit,
        reference: renderOpsReference
    }, (key) => { opsUi.tab = key; });
}

function renderOpsBatch(panel) {
    panel.innerHTML = `
        <div class="card">
            <h3>执行批处理作业</h3>
            <form id="job-form">
                <div class="form-grid">
                    <label class="field"><span>作业 <span class="req">*</span></span>
                        <select name="jobCode" id="job-code"><option value="">加载中…</option></select></label>
                    <label class="field"><span>业务日期 <span class="req">*</span></span>
                        <input name="businessDate" type="date" value="${esc(todayDate())}" required></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="job-run-save">执行作业</button>
                    <button class="btn ghost" type="button" id="job-refresh">刷新定义</button>
                </div>
                <div class="hint mt-16">同一 jobCode + businessDate 可重复执行，每次运行都会保留记录。</div>
                <div id="job-msg"></div>
            </form>
            <div id="job-definitions"></div>
        </div>
        <div class="card">
            <div class="section-title">
                <h3>运行历史</h3>
                <span class="hint">按开始时间倒序</span>
            </div>
            <div id="job-runs"></div>
        </div>`;

    const select = document.getElementById('job-code');
    const form = document.getElementById('job-form');
    const saveButton = document.getElementById('job-run-save');
    const runsBox = document.getElementById('job-runs');
    const definitionsBox = document.getElementById('job-definitions');

    const loadDefinitions = async () => {
        definitionsBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api('/api/v1/jobs');
            const items = result.items || [];
            select.innerHTML = items.length === 0
                ? '<option value="">（无可用作业）</option>'
                : items.map((job) => `
                    <option value="${esc(job.jobCode)}">${esc(job.jobCode)} · ${esc(job.name)}</option>`).join('');
            definitionsBox.innerHTML = items.length === 0 ? '<div class="empty">没有作业定义</div>' : `
                <div class="table-wrap mt-16"><table class="table">
                    <thead><tr><th>作业编码</th><th>名称</th><th>目标服务</th><th>状态</th><th>说明</th></tr></thead>
                    <tbody>${items.map((job) => `
                        <tr>
                            <td class="mono">${esc(job.jobCode)}</td>
                            <td>${esc(job.name)}</td>
                            <td class="mono">${esc(job.targetService || '—')}</td>
                            <td>${job.active
                                ? '<span class="badge ok">启用</span>'
                                : '<span class="badge neutral">停用</span>'}</td>
                            <td>${esc(job.description || '—')}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`;
        } catch (error) {
            select.innerHTML = '<option value="">（作业定义加载失败）</option>';
            definitionsBox.innerHTML = `${errBox(error)}<div class="hint">批处理服务（8100）可能未启动。</div>`;
        }
    };

    const loadRuns = async (page) => {
        opsUi.batch.page = Math.max(0, page);
        runsBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/job-runs?page=${opsUi.batch.page}&size=10`);
            const items = result.items || [];
            runsBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">还没有作业运行记录</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>运行号</th><th>作业</th><th>业务日期</th><th>状态</th>
                            <th>开始时间</th><th>完成时间</th><th>消息</th></tr></thead>
                        <tbody>${items.map((run) => `
                            <tr>
                                <td class="mono">${esc(run.runId)}</td>
                                <td class="mono">${esc(run.jobCode)}</td>
                                <td class="nowrap">${dateText(run.businessDate)}</td>
                                <td>${statusPill(run.status)}</td>
                                <td class="nowrap">${dateTimeText(run.startedAt)}</td>
                                <td class="nowrap">${dateTimeText(run.completedAt)}</td>
                                <td class="muted">${esc(run.message || '—')}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(opsUi.batch.page, result.size || 10, result.total || 0, 'jobpage')}`;
            wirePager(runsBox, 'jobpage', (step) => loadRuns(opsUi.batch.page + step));
        } catch (error) {
            runsBox.innerHTML = `${errBox(error)}<div class="hint">批处理服务（8100）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('job-msg');
        msg.innerHTML = '';
        if (!values.jobCode) {
            msg.innerHTML = '<div class="alert error">请选择作业</div>';
            return;
        }
        setBusy(saveButton, true, '执行中…');
        try {
            const run = await api('/api/v1/job-runs', {
                method: 'POST',
                body: { jobCode: values.jobCode, businessDate: values.businessDate }
            });
            msg.innerHTML = `<div class="alert ${run.status === 'COMPLETED' ? 'success' : 'warn'} mt-16">
                运行 <code>${esc(run.runId)}</code> ${statusPill(run.status)}
                ${run.message ? ` · ${esc(run.message)}` : ''}</div>`;
            toast(`作业已执行：${run.status}`, run.status === 'COMPLETED' ? 'success' : 'error');
            await loadRuns(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('job-refresh').addEventListener('click', loadDefinitions);

    loadDefinitions();
    loadRuns(opsUi.batch.page);
}

function renderOpsReport(panel) {
    panel.innerHTML = `
        <div class="card">
            <h3>生成报表</h3>
            <form id="report-run-form">
                <div class="form-grid">
                    <label class="field"><span>报表 <span class="req">*</span></span>
                        <select name="reportCode" id="report-code"><option value="">加载中…</option></select></label>
                    <label class="field"><span>业务日期 <span class="req">*</span></span>
                        <input name="businessDate" type="date" value="${esc(todayDate())}" required></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="report-run-save">生成</button>
                    <button class="btn ghost" type="button" id="report-refresh">刷新定义</button>
                </div>
                <div class="hint mt-16">下游交易/总账不可用时运行置 FAILED 并返回 503。</div>
                <div id="report-run-msg"></div>
            </form>
            <div id="report-definitions"></div>
        </div>
        <div class="card">
            <div class="section-title">
                <h3>运行历史</h3>
                <span class="hint">点击“查看行”读取结果明细</span>
            </div>
            <div id="report-runs"></div>
            <div id="report-rows"></div>
        </div>`;

    const select = document.getElementById('report-code');
    const form = document.getElementById('report-run-form');
    const saveButton = document.getElementById('report-run-save');
    const runsBox = document.getElementById('report-runs');
    const rowsBox = document.getElementById('report-rows');
    const definitionsBox = document.getElementById('report-definitions');

    const loadDefinitions = async () => {
        definitionsBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api('/api/v1/reports');
            const items = result.items || [];
            select.innerHTML = items.length === 0
                ? '<option value="">（无可用报表）</option>'
                : items.map((report) => `
                    <option value="${esc(report.reportCode)}">${esc(report.reportCode)} · ${esc(report.name)}</option>`).join('');
            definitionsBox.innerHTML = items.length === 0 ? '<div class="empty">没有报表定义</div>' : `
                <div class="table-wrap mt-16"><table class="table">
                    <thead><tr><th>报表编码</th><th>名称</th><th>来源服务</th><th>状态</th><th>说明</th></tr></thead>
                    <tbody>${items.map((report) => `
                        <tr>
                            <td class="mono">${esc(report.reportCode)}</td>
                            <td>${esc(report.name)}</td>
                            <td class="mono">${esc(report.sourceService || '—')}</td>
                            <td>${report.active
                                ? '<span class="badge ok">启用</span>'
                                : '<span class="badge neutral">停用</span>'}</td>
                            <td>${esc(report.description || '—')}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`;
        } catch (error) {
            select.innerHTML = '<option value="">（报表定义加载失败）</option>';
            definitionsBox.innerHTML = `${errBox(error)}<div class="hint">报表服务（8101）可能未启动。</div>`;
        }
    };

    const showRows = async (runId, actionButton) => {
        rowsBox.innerHTML = '<div class="empty">加载中…</div>';
        setBusy(actionButton, true, '读取中…');
        try {
            const run = await api(`/api/v1/report-runs/${encodeURIComponent(runId)}`);
            const rows = run.rows || [];
            rowsBox.innerHTML = `
                <div class="section-title mt-16">
                    <h3>结果行 <span class="muted">${esc(run.reportCode)} · ${dateText(run.businessDate)}
                        · ${rows.length} 行</span></h3>
                </div>
                ${rows.length === 0 ? '<div class="empty">该运行没有结果行</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>行号</th><th>维度</th><th class="num">金额</th><th>详情</th></tr></thead>
                        <tbody>${rows.map((row) => `
                            <tr>
                                <td class="mono">${esc(row.lineNo)}</td>
                                <td class="mono">${esc(row.dimension)}</td>
                                <td class="num mono">${money(row.amount)}</td>
                                <td>${esc(row.detail || '—')}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}`;
        } catch (error) {
            rowsBox.innerHTML = errBox(error);
        } finally {
            setBusy(actionButton, false);
        }
    };

    const loadRuns = async (page) => {
        opsUi.report.page = Math.max(0, page);
        runsBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/report-runs?page=${opsUi.report.page}&size=10`);
            const items = result.items || [];
            runsBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">还没有报表运行记录</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>运行号</th><th>报表</th><th>业务日期</th><th>状态</th>
                            <th class="num">行数</th><th>生成时间</th><th>消息</th><th>操作</th></tr></thead>
                        <tbody>${items.map((run) => `
                            <tr>
                                <td class="mono">${esc(run.runId)}</td>
                                <td class="mono">${esc(run.reportCode)}</td>
                                <td class="nowrap">${dateText(run.businessDate)}</td>
                                <td>${statusPill(run.status)}</td>
                                <td class="num mono">${esc(run.rowCount)}</td>
                                <td class="nowrap">${dateTimeText(run.generatedAt)}</td>
                                <td class="muted">${esc(run.message || '—')}</td>
                                <td class="actions">${run.status === 'COMPLETED'
                                    ? `<button class="btn ghost small" data-rows="${esc(run.runId)}">查看行</button>`
                                    : '—'}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(opsUi.report.page, result.size || 10, result.total || 0, 'reportpage')}`;
            wirePager(runsBox, 'reportpage', (step) => loadRuns(opsUi.report.page + step));
            runsBox.querySelectorAll('[data-rows]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => showRows(actionButton.dataset.rows, actionButton));
            });
        } catch (error) {
            runsBox.innerHTML = `${errBox(error)}<div class="hint">报表服务（8101）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('report-run-msg');
        msg.innerHTML = '';
        if (!values.reportCode) {
            msg.innerHTML = '<div class="alert error">请选择报表</div>';
            return;
        }
        setBusy(saveButton, true, '生成中…');
        try {
            const run = await api('/api/v1/report-runs', {
                method: 'POST',
                body: { reportCode: values.reportCode, businessDate: values.businessDate }
            });
            msg.innerHTML = `<div class="alert ${run.status === 'COMPLETED' ? 'success' : 'warn'} mt-16">
                运行 <code>${esc(run.runId)}</code> ${statusPill(run.status)} · ${esc(run.rowCount)} 行
                ${run.message ? ` · ${esc(run.message)}` : ''}</div>`;
            toast(`报表已生成：${run.status}`, run.status === 'COMPLETED' ? 'success' : 'error');
            await loadRuns(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('report-refresh').addEventListener('click', loadDefinitions);

    loadDefinitions();
    loadRuns(opsUi.report.page);
}

function renderOpsNotification(panel) {
    panel.innerHTML = `
        <div class="card">
            <h3>创建并发送通知</h3>
            <form id="notification-form">
                <div class="form-grid">
                    <label class="field"><span>模板 <span class="req">*</span></span>
                        <select name="templateCode" id="notification-template"><option value="">加载中…</option></select></label>
                    <label class="field"><span>接收方 <span class="req">*</span></span>
                        <input name="recipient" value="ada@example.com" maxlength="200" required></label>
                    <label class="field span-2"><span>payload（JSON 对象，模板变量 {{name}}）</span>
                        <textarea name="payload">{"name":"Ada","amount":"120.00"}</textarea></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="notification-save">创建通知</button>
                    <button class="btn ghost" type="button" id="notification-refresh">刷新模板</button>
                </div>
                <div class="hint mt-16">recipient 含 fail 时发送失败可复现（PENDING → FAILED）。</div>
                <div id="notification-msg"></div>
            </form>
            <div id="notification-templates"></div>
        </div>
        <div class="card">
            <div class="section-title">
                <h3>通知列表</h3>
                <span class="hint">PENDING 可发送</span>
            </div>
            <div id="notification-list"></div>
        </div>`;

    const select = document.getElementById('notification-template');
    const form = document.getElementById('notification-form');
    const saveButton = document.getElementById('notification-save');
    const listBox = document.getElementById('notification-list');
    const templatesBox = document.getElementById('notification-templates');

    const loadTemplates = async () => {
        templatesBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api('/api/v1/notification-templates');
            const items = result.items || [];
            select.innerHTML = items.length === 0
                ? '<option value="">（无可用模板）</option>'
                : items.map((template) => `
                    <option value="${esc(template.templateCode)}">${esc(template.templateCode)} · ${esc(template.channel)}</option>`).join('');
            templatesBox.innerHTML = items.length === 0 ? '<div class="empty">没有通知模板</div>' : `
                <div class="table-wrap mt-16"><table class="table">
                    <thead><tr><th>模板</th><th>渠道</th><th>主题</th><th>正文</th><th>状态</th></tr></thead>
                    <tbody>${items.map((template) => `
                        <tr>
                            <td class="mono">${esc(template.templateCode)}</td>
                            <td>${esc(template.channel)}</td>
                            <td>${esc(template.subject || '—')}</td>
                            <td>${esc(template.body || '—')}</td>
                            <td>${template.active
                                ? '<span class="badge ok">启用</span>'
                                : '<span class="badge neutral">停用</span>'}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`;
        } catch (error) {
            select.innerHTML = '<option value="">（模板加载失败）</option>';
            templatesBox.innerHTML = `${errBox(error)}<div class="hint">通知服务（8102）可能未启动。</div>`;
        }
    };

    const sendNotification = async (actionButton, notificationId) => {
        setBusy(actionButton, true, '发送中…');
        try {
            const sent = await api(`/api/v1/notifications/${encodeURIComponent(notificationId)}/send`,
                { method: 'POST' });
            toast(`通知${sent.status === 'SENT' ? '已发送' : `发送失败：${sent.error || '未知原因'}`}`,
                sent.status === 'SENT' ? 'success' : 'error');
            await loadList(opsUi.notification.page);
        } catch (error) {
            toast(`${error.code || '发送失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadList = async (page) => {
        opsUi.notification.page = Math.max(0, page);
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/notifications?page=${opsUi.notification.page}&size=10`);
            const items = result.items || [];
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">还没有通知记录</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>通知号</th><th>模板</th><th>接收方</th><th>渠道</th><th>状态</th>
                            <th>创建时间</th><th>发送时间</th><th>错误</th><th>操作</th></tr></thead>
                        <tbody>${items.map((item) => `
                            <tr>
                                <td class="mono">${esc(item.notificationId)}</td>
                                <td class="mono">${esc(item.templateCode)}</td>
                                <td>${esc(item.recipient)}</td>
                                <td>${esc(item.channel)}</td>
                                <td>${statusPill(item.status)}</td>
                                <td class="nowrap">${dateTimeText(item.createdAt)}</td>
                                <td class="nowrap">${dateTimeText(item.sentAt)}</td>
                                <td class="muted">${esc(item.error || '—')}</td>
                                <td class="actions">${item.status === 'PENDING'
                                    ? `<button class="btn primary small"
                                               data-send="${esc(item.notificationId)}">发送</button>`
                                    : '—'}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(opsUi.notification.page, result.size || 10, result.total || 0, 'notifpage')}`;
            wirePager(listBox, 'notifpage', (step) => loadList(opsUi.notification.page + step));
            listBox.querySelectorAll('[data-send]').forEach((actionButton) => {
                actionButton.addEventListener('click',
                    () => sendNotification(actionButton, actionButton.dataset.send));
            });
        } catch (error) {
            listBox.innerHTML = `${errBox(error)}<div class="hint">通知服务（8102）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('notification-msg');
        msg.innerHTML = '';
        if (!values.templateCode) {
            msg.innerHTML = '<div class="alert error">请选择模板</div>';
            return;
        }
        let payload = {};
        if (values.payload.trim()) {
            try {
                const parsed = JSON.parse(values.payload);
                if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
                    throw new Error('payload 必须是 JSON 对象');
                }
                payload = Object.fromEntries(Object.entries(parsed).map(([key, value]) => [key, String(value)]));
            } catch (error) {
                msg.innerHTML = `<div class="alert error">payload 解析失败：${esc(error.message)}</div>`;
                return;
            }
        }
        setBusy(saveButton, true, '创建中…');
        try {
            const created = await api('/api/v1/notifications', {
                method: 'POST',
                body: {
                    templateCode: values.templateCode,
                    recipient: values.recipient.trim(),
                    payload
                }
            });
            toast(`通知已创建：${created.notificationId}（${created.status}）`, 'success');
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('notification-refresh').addEventListener('click', loadTemplates);

    loadTemplates();
    loadList(opsUi.notification.page);
}

function renderOpsDocument(panel) {
    panel.innerHTML = `
        <div class="card">
            <h3>上传文档元数据与文本</h3>
            <form id="document-form">
                <div class="form-grid">
                    <label class="field"><span>文档类型 <span class="req">*</span></span>
                        <select name="documentType">
                            ${DOCUMENT_TYPES.map((value) => `<option value="${value}">${value}</option>`).join('')}
                        </select></label>
                    <label class="field"><span>归属类型 <span class="req">*</span></span>
                        <select name="ownerType">
                            ${DOCUMENT_OWNER_TYPES.map((value) => `<option value="${value}">${value}</option>`).join('')}
                        </select></label>
                    <label class="field"><span>归属 ID <span class="req">*</span></span>
                        <input name="ownerId" value="000000001" maxlength="36" pattern="[A-Za-z0-9-]+" required></label>
                    <label class="field"><span>文件名 <span class="req">*</span></span>
                        <input name="fileName" value="statement-2026-10.txt" maxlength="200" required></label>
                    <label class="field"><span>内容类型 <span class="req">*</span></span>
                        <input name="contentType" value="text/plain" maxlength="100" required></label>
                    <label class="field span-2"><span>文本内容 <span class="req">*</span></span>
                        <textarea name="contentText" required>CardDemo statement sample
Account: 00000000001
Balance: 1940.00</textarea></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="document-save">上传</button>
                </div>
                <div class="hint mt-16">仅保存元数据与文本内容（contentType 需为 text/*）。</div>
                <div id="document-msg"></div>
            </form>
        </div>
        <div class="card">
            <div class="section-title">
                <h3>文档列表</h3>
                <form class="inline-form" id="document-filter">
                    <label class="field"><span>归属类型</span>
                        <select id="document-owner-type">
                            ${selectOptions(DOCUMENT_OWNER_TYPES, opsUi.document.ownerType, '全部')}
                        </select></label>
                    <label class="field"><span>归属 ID</span>
                        <input id="document-owner-id" value="${esc(opsUi.document.ownerId)}" maxlength="36"
                               placeholder="可选"></label>
                    <button class="btn ghost" type="submit">查询</button>
                </form>
            </div>
            <div id="document-list"></div>
            <div id="document-content"></div>
        </div>`;

    const form = document.getElementById('document-form');
    const saveButton = document.getElementById('document-save');
    const listBox = document.getElementById('document-list');
    const contentBox = document.getElementById('document-content');

    const showContent = async (actionButton, documentId) => {
        contentBox.innerHTML = '<div class="empty">读取中…</div>';
        setBusy(actionButton, true, '读取中…');
        try {
            const text = await apiText(`/api/v1/documents/${encodeURIComponent(documentId)}/content`);
            contentBox.innerHTML = `
                <div class="section-title mt-16">
                    <h3>文档内容 <span class="muted mono">${esc(documentId)}</span></h3>
                    <button type="button" class="btn ghost small" id="document-close">关闭</button>
                </div>
                <pre class="text-block">${esc(text)}</pre>`;
            document.getElementById('document-close').addEventListener('click', () => {
                contentBox.innerHTML = '';
            });
        } catch (error) {
            contentBox.innerHTML = `${errBox(error)}<div class="hint">文档服务（8103）可能未启动。</div>`;
        } finally {
            setBusy(actionButton, false);
        }
    };

    const archiveDocument = async (actionButton, documentId) => {
        setBusy(actionButton, true, '归档中…');
        try {
            await api(`/api/v1/documents/${encodeURIComponent(documentId)}/archive`, { method: 'POST' });
            toast('文档已归档', 'success');
            await loadList(opsUi.document.page);
        } catch (error) {
            toast(`${error.code || '归档失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const deleteDocument = async (actionButton, documentId) => {
        if (!window.confirm(`确认删除文档 ${documentId}？删除后无法恢复。`)) return;
        setBusy(actionButton, true, '删除中…');
        try {
            await api(`/api/v1/documents/${encodeURIComponent(documentId)}`, { method: 'DELETE' });
            toast('文档已删除', 'success');
            await loadList(opsUi.document.page);
        } catch (error) {
            toast(`${error.code || '删除失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadList = async (page) => {
        opsUi.document.page = Math.max(0, page);
        opsUi.document.ownerType = document.getElementById('document-owner-type').value;
        opsUi.document.ownerId = document.getElementById('document-owner-id').value.trim();
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(opsUi.document.page), size: '10' });
        if (opsUi.document.ownerType) params.set('ownerType', opsUi.document.ownerType);
        if (opsUi.document.ownerId) params.set('ownerId', opsUi.document.ownerId);
        try {
            const result = await api(`/api/v1/documents?${params.toString()}`);
            const items = result.items || [];
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的文档</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>文档号</th><th>类型</th><th>归属</th><th>文件名</th><th>内容类型</th>
                            <th class="num">字节</th><th>状态</th><th>创建时间</th><th>操作</th></tr></thead>
                        <tbody>${items.map((item) => `
                            <tr>
                                <td class="mono">${esc(item.documentId)}</td>
                                <td>${esc(item.documentType)}</td>
                                <td class="mono">${esc(item.ownerType)} · ${esc(item.ownerId)}</td>
                                <td>${esc(item.fileName)}</td>
                                <td class="mono">${esc(item.contentType)}</td>
                                <td class="num mono">${esc(item.sizeBytes)}</td>
                                <td>${statusPill(item.status)}</td>
                                <td class="nowrap">${dateTimeText(item.createdAt)}</td>
                                <td class="actions">
                                    <button class="btn ghost small" data-view="${esc(item.documentId)}">查看内容</button>
                                    ${item.status === 'ACTIVE'
                                        ? `<button class="btn ghost small" data-archive="${esc(item.documentId)}">归档</button>`
                                        : ''}
                                    <button class="btn danger small" data-delete="${esc(item.documentId)}">删除</button>
                                </td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(opsUi.document.page, result.size || 10, result.total || 0, 'docpage')}`;
            wirePager(listBox, 'docpage', (step) => loadList(opsUi.document.page + step));
            listBox.querySelectorAll('[data-view]').forEach((actionButton) => {
                actionButton.addEventListener('click',
                    () => showContent(actionButton, actionButton.dataset.view));
            });
            listBox.querySelectorAll('[data-archive]').forEach((actionButton) => {
                actionButton.addEventListener('click',
                    () => archiveDocument(actionButton, actionButton.dataset.archive));
            });
            listBox.querySelectorAll('[data-delete]').forEach((actionButton) => {
                actionButton.addEventListener('click',
                    () => deleteDocument(actionButton, actionButton.dataset.delete));
            });
        } catch (error) {
            listBox.innerHTML = `${errBox(error)}<div class="hint">文档服务（8103）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('document-msg');
        msg.innerHTML = '';
        setBusy(saveButton, true, '上传中…');
        try {
            const created = await api('/api/v1/documents', {
                method: 'POST',
                body: {
                    documentType: values.documentType,
                    ownerType: values.ownerType,
                    ownerId: values.ownerId.trim(),
                    fileName: values.fileName.trim(),
                    contentType: values.contentType.trim(),
                    contentText: values.contentText
                }
            });
            toast(`文档已上传：${created.documentId}`, 'success');
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('document-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList(0);
    });

    loadList(opsUi.document.page);
}

function renderOpsWorkflow(panel) {
    panel.innerHTML = `
        <div class="card">
            <div class="section-title">
                <h3>流程定义</h3>
                <span class="hint">LIMIT_INCREASE / PAYMENT_REVIEW / DISPUTE_REVIEW</span>
            </div>
            <div id="workflow-definitions"></div>
        </div>
        <div class="card">
            <div class="section-title">
                <h3>任务</h3>
                <form class="inline-form" id="workflow-filter">
                    <label class="field"><span>状态</span>
                        <select id="workflow-status">
                            ${selectOptions(WORKFLOW_STATUSES, opsUi.workflow.status, '全部')}
                        </select></label>
                    <label class="field"><span>处理人</span>
                        <input id="workflow-assignee" value="${esc(opsUi.workflow.assignee)}" maxlength="64"
                               placeholder="可选"></label>
                    <button class="btn ghost" type="submit">查询</button>
                </form>
            </div>
            <div id="workflow-tasks"></div>
        </div>`;

    const tasksBox = document.getElementById('workflow-tasks');
    const definitionsBox = document.getElementById('workflow-definitions');

    const decideTask = async (actionButton, taskId, decision) => {
        const comment = window.prompt(`${decision === 'approve' ? '通过' : '拒绝'}意见（可选，最多 500 字）`, '');
        if (comment === null) return;
        setBusy(actionButton, true, '处理中…');
        try {
            await api(`/api/v1/workflow-tasks/${encodeURIComponent(taskId)}/${decision}`, {
                method: 'POST',
                body: { comment: comment.trim() || null }
            });
            toast(`任务已${decision === 'approve' ? '通过' : '拒绝'}`, 'success');
            await loadTasks(opsUi.workflow.page);
        } catch (error) {
            toast(`${error.code || '处理失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadDefinitions = async () => {
        definitionsBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api('/api/v1/workflow-definitions');
            const items = result.items || [];
            definitionsBox.innerHTML = items.length === 0 ? '<div class="empty">没有流程定义</div>' : `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>定义编码</th><th>名称</th><th>步骤</th><th>状态</th></tr></thead>
                    <tbody>${items.map((definition) => `
                        <tr>
                            <td class="mono">${esc(definition.definitionCode)}</td>
                            <td>${esc(definition.name)}</td>
                            <td>${(definition.steps || []).map((step) => esc(step)).join(' → ') || '—'}</td>
                            <td>${definition.active
                                ? '<span class="badge ok">启用</span>'
                                : '<span class="badge neutral">停用</span>'}</td>
                        </tr>`).join('')}
                    </tbody></table></div>`;
        } catch (error) {
            definitionsBox.innerHTML = `${errBox(error)}<div class="hint">工作流服务（8104）可能未启动。</div>`;
        }
    };

    const loadTasks = async (page) => {
        opsUi.workflow.page = Math.max(0, page);
        opsUi.workflow.status = document.getElementById('workflow-status').value;
        opsUi.workflow.assignee = document.getElementById('workflow-assignee').value.trim();
        tasksBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(opsUi.workflow.page), size: '10' });
        if (opsUi.workflow.status) params.set('status', opsUi.workflow.status);
        if (opsUi.workflow.assignee) params.set('assignee', opsUi.workflow.assignee);
        try {
            const result = await api(`/api/v1/workflow-tasks?${params.toString()}`);
            const items = result.items || [];
            tasksBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的任务</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>任务号</th><th>定义</th><th>业务引用</th><th>处理人</th><th>状态</th>
                            <th>意见</th><th>创建时间</th><th>决策时间</th><th>操作</th></tr></thead>
                        <tbody>${items.map((task) => `
                            <tr>
                                <td class="mono">${esc(task.taskId)}</td>
                                <td class="mono">${esc(task.definitionCode)}</td>
                                <td class="mono">${esc(task.businessRef)}</td>
                                <td>${esc(task.assignee)}</td>
                                <td>${statusPill(task.status)}</td>
                                <td>${esc(task.comment || '—')}</td>
                                <td class="nowrap">${dateTimeText(task.createdAt)}</td>
                                <td class="nowrap">${dateTimeText(task.decidedAt)}</td>
                                <td class="actions">${task.status === 'PENDING' ? `
                                    <button class="btn primary small" data-task-id="${esc(task.taskId)}"
                                            data-decision="approve">通过</button>
                                    <button class="btn danger small" data-task-id="${esc(task.taskId)}"
                                            data-decision="reject">拒绝</button>` : '—'}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(opsUi.workflow.page, result.size || 10, result.total || 0, 'taskpage')}`;
            wirePager(tasksBox, 'taskpage', (step) => loadTasks(opsUi.workflow.page + step));
            tasksBox.querySelectorAll('[data-decision]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => decideTask(
                    actionButton, actionButton.dataset.taskId, actionButton.dataset.decision));
            });
        } catch (error) {
            tasksBox.innerHTML = `${errBox(error)}<div class="hint">工作流服务（8104）可能未启动。</div>`;
        }
    };

    document.getElementById('workflow-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadTasks(0);
    });

    loadDefinitions();
    loadTasks(opsUi.workflow.page);
}

function renderOpsAudit(panel) {
    panel.innerHTML = `
        <div class="card">
            <h3>追加审计事件</h3>
            <form id="audit-form">
                <div class="form-grid">
                    <label class="field"><span>操作人 actor <span class="req">*</span></span>
                        <input name="actor" value="${esc(state.session && state.session.user ? state.session.user.userId : 'demo.operator')}"
                               maxlength="64" required></label>
                    <label class="field"><span>动作 action <span class="req">*</span></span>
                        <input name="action" value="CARD_STATUS_CHANGED" maxlength="80" required></label>
                    <label class="field"><span>资源类型 <span class="req">*</span></span>
                        <input name="resourceType" value="CARD" maxlength="40" required></label>
                    <label class="field"><span>资源 ID <span class="req">*</span></span>
                        <input name="resourceId" value="3344556677889900" maxlength="64" required></label>
                    <label class="field span-2"><span>payload（JSON 文本，可选）</span>
                        <input name="payload" value='{"from":"ACTIVE","to":"BLOCKED"}' maxlength="2000"></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="audit-save">追加事件</button>
                </div>
                <div class="hint mt-16">审计事件只追加，不提供修改与删除。</div>
                <div id="audit-msg"></div>
            </form>
        </div>
        <div class="card">
            <div class="section-title">
                <h3>事件检索</h3>
                <form class="inline-form" id="audit-filter">
                    <label class="field"><span>操作人</span>
                        <input id="audit-actor" value="${esc(opsUi.audit.actor)}" maxlength="64" placeholder="可选"></label>
                    <label class="field"><span>资源类型</span>
                        <input id="audit-resource-type" value="${esc(opsUi.audit.resourceType)}" maxlength="40"
                               placeholder="可选"></label>
                    <label class="field"><span>资源 ID</span>
                        <input id="audit-resource-id" value="${esc(opsUi.audit.resourceId)}" maxlength="64"
                               placeholder="可选"></label>
                    <button class="btn ghost" type="submit">查询</button>
                </form>
            </div>
            <div id="audit-list"></div>
        </div>`;

    const form = document.getElementById('audit-form');
    const saveButton = document.getElementById('audit-save');
    const listBox = document.getElementById('audit-list');

    const loadList = async (page) => {
        opsUi.audit.page = Math.max(0, page);
        opsUi.audit.actor = document.getElementById('audit-actor').value.trim();
        opsUi.audit.resourceType = document.getElementById('audit-resource-type').value.trim();
        opsUi.audit.resourceId = document.getElementById('audit-resource-id').value.trim();
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(opsUi.audit.page), size: '10' });
        if (opsUi.audit.actor) params.set('actor', opsUi.audit.actor);
        if (opsUi.audit.resourceType) params.set('resourceType', opsUi.audit.resourceType);
        if (opsUi.audit.resourceId) params.set('resourceId', opsUi.audit.resourceId);
        try {
            const result = await api(`/api/v1/audit-events?${params.toString()}`);
            const items = result.items || [];
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">没有符合条件的事件</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>事件号</th><th>操作人</th><th>动作</th><th>资源</th>
                            <th>payload</th><th>时间</th></tr></thead>
                        <tbody>${items.map((event) => `
                            <tr>
                                <td class="mono">${esc(event.eventId)}</td>
                                <td>${esc(event.actor)}</td>
                                <td class="mono">${esc(event.action)}</td>
                                <td class="mono">${esc(event.resourceType)} · ${esc(event.resourceId)}</td>
                                <td class="mono">${esc(event.payload || '—')}</td>
                                <td class="nowrap">${dateTimeText(event.createdAt)}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(opsUi.audit.page, result.size || 10, result.total || 0, 'auditpage')}`;
            wirePager(listBox, 'auditpage', (step) => loadList(opsUi.audit.page + step));
        } catch (error) {
            listBox.innerHTML = `${errBox(error)}<div class="hint">审计服务（8105）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('audit-msg');
        msg.innerHTML = '';
        setBusy(saveButton, true, '追加中…');
        try {
            const created = await api('/api/v1/audit-events', {
                method: 'POST',
                body: {
                    actor: values.actor.trim(),
                    action: values.action.trim(),
                    resourceType: values.resourceType.trim(),
                    resourceId: values.resourceId.trim(),
                    payload: values.payload.trim() || null
                }
            });
            toast(`审计事件已追加：${created.eventId}`, 'success');
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('audit-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList(0);
    });

    loadList(opsUi.audit.page);
}

function renderOpsReference(panel) {
    panel.innerHTML = `
        <div class="card">
            <div class="section-title">
                <h3>参考数据</h3>
                <form class="inline-form" id="reference-filter">
                    <label class="field"><span>域 domain</span>
                        <select id="reference-domain">
                            ${REFERENCE_DOMAINS.map((domain) => `
                                <option value="${domain}"${opsUi.reference.domain === domain ? ' selected' : ''}>${domain}</option>`).join('')}
                        </select></label>
                    <button class="btn ghost" type="submit">查询</button>
                </form>
            </div>
            <div id="reference-list"></div>
        </div>
        <div class="card">
            <h3>新增条目</h3>
            <form id="reference-form">
                <div class="form-grid">
                    <label class="field"><span>域 <span class="req">*</span></span>
                        <input name="domain" id="reference-new-domain" value="${esc(opsUi.reference.domain)}"
                               maxlength="32" required></label>
                    <label class="field"><span>编码 code <span class="req">*</span></span>
                        <input name="code" value="JPY" maxlength="32" required></label>
                    <label class="field"><span>名称 label <span class="req">*</span></span>
                        <input name="label" value="日元" maxlength="120" required></label>
                    <label class="field"><span>排序 sortOrder</span>
                        <input name="sortOrder" type="number" min="0" value="40"></label>
                </div>
                <div class="form-actions">
                    <button class="btn primary" type="submit" id="reference-save">新增</button>
                </div>
                <div class="hint mt-16">同一域内 code 唯一，重复返回 409；修改仅支持 label / sortOrder / active。</div>
                <div id="reference-msg"></div>
            </form>
        </div>`;

    const listBox = document.getElementById('reference-list');
    const form = document.getElementById('reference-form');
    const saveButton = document.getElementById('reference-save');
    const domainSelect = document.getElementById('reference-domain');

    const updateItem = async (item, actionButton) => {
        const label = window.prompt('新名称 label', item.label);
        if (label === null) return;
        const sortRaw = window.prompt('排序 sortOrder（非负整数）', String(item.sortOrder));
        if (sortRaw === null) return;
        const activeRaw = window.prompt('是否启用（true/false）', String(item.active));
        if (activeRaw === null) return;
        setBusy(actionButton, true, '保存中…');
        try {
            await api(`/api/v1/reference/${encodeURIComponent(item.itemId)}`, {
                method: 'PUT',
                body: {
                    label: label.trim(),
                    sortOrder: Number(sortRaw),
                    active: activeRaw.trim().toLowerCase() === 'true'
                }
            });
            toast('参考数据已更新', 'success');
            await loadList();
        } catch (error) {
            toast(`${error.code || '更新失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const deleteItem = async (item, actionButton) => {
        if (!window.confirm(`确认删除 ${item.domain} / ${item.code}？`)) return;
        setBusy(actionButton, true, '删除中…');
        try {
            await api(`/api/v1/reference/${encodeURIComponent(item.itemId)}`, { method: 'DELETE' });
            toast('参考数据已删除', 'success');
            await loadList();
        } catch (error) {
            toast(`${error.code || '删除失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadList = async () => {
        const domain = domainSelect.value;
        opsUi.reference.domain = domain;
        document.getElementById('reference-new-domain').value = domain;
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const result = await api(`/api/v1/reference/${encodeURIComponent(domain)}`);
            const items = result.items || [];
            listBox.innerHTML = `
                ${items.length === 0 ? '<div class="empty">该域暂无参考数据</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>条目号</th><th>域</th><th>编码</th><th>名称</th>
                            <th class="num">排序</th><th>状态</th><th>版本</th><th>操作</th></tr></thead>
                        <tbody>${items.map((item) => `
                            <tr>
                                <td class="mono">${esc(item.itemId)}</td>
                                <td class="mono">${esc(item.domain)}</td>
                                <td class="mono">${esc(item.code)}</td>
                                <td>${esc(item.label)}</td>
                                <td class="num mono">${esc(item.sortOrder)}</td>
                                <td>${item.active
                                    ? '<span class="badge ok">启用</span>'
                                    : '<span class="badge neutral">停用</span>'}</td>
                                <td class="mono">${esc(item.version)}</td>
                                <td class="actions">
                                    <button class="btn ghost small" data-edit="${esc(item.itemId)}">修改</button>
                                    <button class="btn danger small" data-remove="${esc(item.itemId)}">删除</button>
                                </td>
                            </tr>`).join('')}
                        </tbody></table></div>
                    <div class="hint mt-16">共 ${esc(result.total ?? items.length)} 条</div>`}`;

            listBox.querySelectorAll('[data-edit]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => {
                    const item = items.find((candidate) => candidate.itemId === actionButton.dataset.edit);
                    if (item) updateItem(item, actionButton);
                });
            });
            listBox.querySelectorAll('[data-remove]').forEach((actionButton) => {
                actionButton.addEventListener('click', () => {
                    const item = items.find((candidate) => candidate.itemId === actionButton.dataset.remove);
                    if (item) deleteItem(item, actionButton);
                });
            });
        } catch (error) {
            listBox.innerHTML = `${errBox(error)}<div class="hint">参考数据服务（8106）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('reference-msg');
        msg.innerHTML = '';
        setBusy(saveButton, true, '保存中…');
        try {
            const created = await api('/api/v1/reference', {
                method: 'POST',
                body: {
                    domain: values.domain.trim().toUpperCase(),
                    code: values.code.trim(),
                    label: values.label.trim(),
                    sortOrder: values.sortOrder === '' ? 0 : Number(values.sortOrder),
                    active: true
                }
            });
            toast(`参考数据已新增：${created.domain} / ${created.code}`, 'success');
            await loadList();
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    domainSelect.addEventListener('change', () => loadList());
    document.getElementById('reference-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList();
    });

    loadList();
}

/* ---------------- 页面：营销（高端卡分期返现，设计 17） ---------------- */

const CAMPAIGN_STATUSES = ['DRAFT', 'ACTIVE', 'PAUSED', 'ENDED'];
const CAMPAIGN_STATUS_TEXT = { DRAFT: '草稿', ACTIVE: '进行中', PAUSED: '已暂停', ENDED: '已结束' };
const CAMPAIGN_PRODUCT_CODES = ['PLATINUM', 'GOLD', 'CLASSIC'];
const REWARD_STATUS_TEXT = { PENDING: '待入账', GRANTED: '已入账', FAILED: '入账失败' };

const campaignStatusBadge = (status) => {
    const kind = status === 'ACTIVE' ? 'ok' : status === 'PAUSED' ? 'warn' : 'neutral';
    return `<span class="badge ${kind}">${esc(CAMPAIGN_STATUS_TEXT[status] || status || '—')}</span>`;
};

const rewardStatusBadge = (status) => {
    const kind = status === 'GRANTED' ? 'ok' : status === 'FAILED' ? 'bad' : 'warn';
    return `<span class="badge ${kind}">${esc(REWARD_STATUS_TEXT[status] || status || '—')}</span>`;
};

const campaignIdOf = (campaign) => (campaign ? campaign.campaignId || campaign.id || '' : '');

/* 资格接口可能平铺活动字段，也可能把活动包在 campaign 下 */
const campaignOf = (row) => (row && row.campaign ? { ...row.campaign, ...row } : row || {});

const pickValue = (source, keys, fallback) => {
    for (const key of keys) {
        const value = source ? source[key] : undefined;
        if (value !== undefined && value !== null) return value;
    }
    return fallback;
};

/* 优先用两个计数/金额算出百分比，缺失时回退到服务端给出的比率 */
const percentOf = (numerator, denominator, fallback) => {
    const top = Number(numerator);
    const bottom = Number(denominator);
    if (Number.isFinite(top) && Number.isFinite(bottom) && bottom > 0) {
        return `${(top / bottom * 100).toFixed(1)}%`;
    }
    return formatRate(fallback);
};

const cashbackSummary = (campaign) => {
    const description = campaign.description ? `<div class="muted">${esc(campaign.description)}</div>` : '';
    if (campaign.rewardSummary) return `<div>${esc(campaign.rewardSummary)}</div>${description}`;
    const parts = [];
    if (campaign.cashbackRate !== null && campaign.cashbackRate !== undefined) {
        parts.push(`返现 ${formatRate(campaign.cashbackRate)}`);
    }
    if (campaign.minTransactionAmount !== null && campaign.minTransactionAmount !== undefined) {
        parts.push(`满 ${money(campaign.minTransactionAmount)}`);
    }
    if (campaign.maxCashbackPerTransaction !== null && campaign.maxCashbackPerTransaction !== undefined) {
        parts.push(`单笔上限 ${money(campaign.maxCashbackPerTransaction)}`);
    }
    if (campaign.maxCashbackPerCustomer !== null && campaign.maxCashbackPerCustomer !== undefined) {
        parts.push(`累计上限 ${money(campaign.maxCashbackPerCustomer)}`);
    }
    return `<div>${esc(parts.join(' · ') || '—')}</div>${description}`;
};

/* 返现记录副标题：活动名缺失时退回活动号，避免与主标题重复 */
const rewardSubtitle = (reward) => (reward.campaignName
    ? (reward.campaignCode || '')
    : (reward.campaignId || ''));

const premiumUi = {
    accountId: '00000000006',
    cardNumber: '3344556677889900',
    customerId: '000000001',
    rewardPage: 0
};

function pagePremiumOffers() {
    shell({
        active: 'premium-offers',
        title: '高端返现',
        subtitle: '高端卡分期消费返现：查询可参与活动、报名与我的返现（设计 17 §5）',
        content: `
            <div class="card">
                <h3>客户与卡片</h3>
                <form id="premium-query">
                    <div class="form-grid">
                        <label class="field"><span>账户号 accountId <span class="req">*</span></span>
                            <input id="premium-account" name="accountId" value="${esc(premiumUi.accountId)}"
                                   maxlength="11" required></label>
                        <label class="field"><span>卡号 cardNumber <span class="req">*</span></span>
                            <input id="premium-card" name="cardNumber" value="${esc(premiumUi.cardNumber)}"
                                   maxlength="16" required></label>
                        <label class="field"><span>客户号 customerId <span class="req">*</span></span>
                            <input id="premium-customer" name="customerId" value="${esc(premiumUi.customerId)}"
                                   maxlength="9" required></label>
                    </div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="premium-query-btn">查询活动与返现</button>
                    </div>
                    <div class="hint mt-16">
                        账户 00000000006 为白金卡种子账户；资格由产品等级、活动期与报名状态共同判定。
                    </div>
                </form>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>可参与活动</h3>
                    <span class="hint">未报名且符合资格可立即报名</span>
                </div>
                <div id="premium-offers"><div class="empty">加载中…</div></div>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>我的返现</h3>
                    <span class="hint">待入账 / 入账失败可重试</span>
                </div>
                <div id="premium-rewards"><div class="empty">加载中…</div></div>
            </div>`
    });

    const offerBox = document.getElementById('premium-offers');
    const rewardBox = document.getElementById('premium-rewards');
    const queryForm = document.getElementById('premium-query');

    const currentQuery = () => {
        premiumUi.accountId = document.getElementById('premium-account').value.trim();
        premiumUi.cardNumber = document.getElementById('premium-card').value.trim();
        premiumUi.customerId = document.getElementById('premium-customer').value.trim();
        return {
            accountId: premiumUi.accountId,
            cardNumber: premiumUi.cardNumber,
            customerId: premiumUi.customerId
        };
    };

    const enroll = async (campaignId, query, actionButton) => {
        setBusy(actionButton, true, '报名中…');
        try {
            await api(`/api/v1/campaigns/${encodeURIComponent(campaignId)}/enrollments`, {
                method: 'POST',
                body: query
            });
            toast('报名成功，活动期内达标消费将自动返现', 'success');
            await loadOffers(query);
        } catch (error) {
            toast(`${error.code || '报名失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadOffers = async (query) => {
        offerBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams();
        if (query.accountId) params.set('accountId', query.accountId);
        if (query.cardNumber) params.set('cardNumber', query.cardNumber);
        if (query.customerId) params.set('customerId', query.customerId);
        try {
            const queryString = params.toString();
            const result = await api(`/api/v1/eligibilities${queryString ? `?${queryString}` : ''}`);
            const campaigns = listOf(result).map(campaignOf);
            if (campaigns.length === 0) {
                offerBox.innerHTML = '<div class="empty">当前没有可见的返现活动</div>';
                return;
            }
            offerBox.innerHTML = `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>活动名称</th><th>起止日期</th><th>期限</th><th>返现说明</th>
                        <th>资格 / 报名</th><th>操作</th></tr></thead>
                    <tbody>${campaigns.map((campaign) => {
                        const eligible = campaign.eligible
                            ? '<span class="badge ok">符合资格</span>'
                            : '<span class="badge bad">不符合资格</span>';
                        const enrolled = campaign.enrolled
                            ? '<span class="badge ok">已报名</span>'
                            : campaign.enrollmentRequired === false
                                ? '<span class="badge neutral">无需报名</span>'
                                : '<span class="badge warn">未报名</span>';
                        const reason = !campaign.eligible && (campaign.ineligibleReason || campaign.reason)
                            ? `<div class="muted">${esc(campaign.ineligibleReason || campaign.reason)}</div>`
                            : '';
                        const action = campaign.eligible && !campaign.enrolled
                            && campaign.enrollmentRequired !== false
                            ? `<button class="btn primary small" data-enroll="${esc(campaignIdOf(campaign))}">立即报名</button>`
                            : '<span class="muted">—</span>';
                        return `
                            <tr>
                                <td>
                                    <div><strong>${esc(campaign.name || campaign.campaignName)}</strong></div>
                                    <div class="mono muted">${esc(campaign.code || campaign.campaignCode)}</div>
                                </td>
                                <td class="nowrap">${dateText(campaign.startDate)} → ${dateText(campaign.endDate)}</td>
                                <td class="nowrap">${esc(campaign.durationMonths ?? '—')} 月</td>
                                <td>${cashbackSummary(campaign)}</td>
                                <td><div class="actions">${eligible}${enrolled}</div>${reason}</td>
                                <td class="actions">${action}</td>
                            </tr>`;
                    }).join('')}
                    </tbody></table></div>`;
            offerBox.querySelectorAll('[data-enroll]').forEach((button) => {
                button.addEventListener('click', () => enroll(button.dataset.enroll, query, button));
            });
        } catch (error) {
            offerBox.innerHTML = `${errBox(error)}<div class="hint">营销服务（8087）可能未启动。</div>`;
        }
    };

    const retry = async (rewardId, query, actionButton) => {
        setBusy(actionButton, true, '重试中…');
        try {
            await api(`/api/v1/rewards/${encodeURIComponent(rewardId)}/retry`, { method: 'POST' });
            toast('已重新提交入账', 'success');
            await loadRewards(query, premiumUi.rewardPage);
        } catch (error) {
            toast(`${error.code || '重试失败'}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadRewards = async (query, page) => {
        premiumUi.rewardPage = Math.max(0, page === undefined ? premiumUi.rewardPage : page);
        rewardBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(premiumUi.rewardPage), size: '10' });
        if (query.accountId) params.set('accountId', query.accountId);
        if (query.customerId) params.set('customerId', query.customerId);
        try {
            const result = await api(`/api/v1/rewards?${params.toString()}`);
            const rewards = listOf(result);
            if (rewards.length === 0) {
                rewardBox.innerHTML = '<div class="empty">暂无返现记录：活动期内达标消费入账后自动生成</div>';
                return;
            }
            rewardBox.innerHTML = `
                <div class="table-wrap"><table class="table">
                    <thead><tr><th>活动</th><th>交易</th><th class="num">消费金额</th><th class="num">返现金额</th>
                        <th>状态</th><th>时间</th><th>操作</th></tr></thead>
                    <tbody>${rewards.map((reward) => `
                        <tr>
                            <td>
                                <div>${esc(reward.campaignName || reward.campaignCode || '—')}</div>
                                <div class="mono muted">${esc(rewardSubtitle(reward))}</div>
                            </td>
                            <td class="mono">${esc(reward.transactionId)}</td>
                            <td class="num mono">${money(reward.transactionAmount)}</td>
                            <td class="num mono"><strong>${money(reward.cashbackAmount)}</strong></td>
                            <td>${rewardStatusBadge(reward.status)}
                                ${reward.failureReason ? `<div class="muted">${esc(reward.failureReason)}</div>` : ''}</td>
                            <td class="nowrap">${dateTimeText(reward.grantedAt || reward.createdAt)}</td>
                            <td class="actions">${reward.status === 'PENDING' || reward.status === 'FAILED'
                                ? `<button class="btn primary small" data-retry="${esc(reward.rewardId)}">重试入账</button>`
                                : '<span class="muted">—</span>'}</td>
                        </tr>`).join('')}
                    </tbody></table></div>
                ${pager(premiumUi.rewardPage, result.size || 10, result.total || rewards.length)}`;
            wirePager(rewardBox, 'page', (step) => loadRewards(query, premiumUi.rewardPage + step));
            rewardBox.querySelectorAll('[data-retry]').forEach((button) => {
                button.addEventListener('click', () => retry(button.dataset.retry, query, button));
            });
        } catch (error) {
            rewardBox.innerHTML = `${errBox(error)}<div class="hint">营销服务（8087）可能未启动。</div>`;
        }
    };

    queryForm.addEventListener('submit', (event) => {
        event.preventDefault();
        const query = currentQuery();
        loadOffers(query);
        loadRewards(query, 0);
    });

    const initial = currentQuery();
    loadOffers(initial);
    loadRewards(initial, 0);
}

const campaignUi = { page: 0, status: '' };

function pagePremiumCampaigns() {
    if (!requireAdmin('premium-campaigns', '返现活动')) return;
    shell({
        active: 'premium-campaigns',
        title: '返现活动',
        subtitle: '创建、发布、暂停/结束高端卡返现活动并查看效果（设计 17 §4–§5，管理员）',
        content: `
            <div class="card">
                <h3>创建活动</h3>
                <form id="campaign-form">
                    <div class="form-grid">
                        <label class="field"><span>活动编码 code <span class="req">*</span></span>
                            <input name="code" value="PLATINUM_3M_CASHBACK" maxlength="40" required></label>
                        <label class="field"><span>活动名称 name <span class="req">*</span></span>
                            <input name="name" value="白金卡 3 个月消费返现" maxlength="80" required></label>
                        <label class="field span-2"><span>活动说明 description</span>
                            <textarea name="description">活动期内消费 5% 返现，单笔最高 30，累计最高 300</textarea></label>
                        <label class="field"><span>开始日期 startDate <span class="req">*</span></span>
                            <input name="startDate" type="date" value="${todayDate()}" required></label>
                        <label class="field"><span>期限月数（1–24） <span class="req">*</span></span>
                            <input name="durationMonths" type="number" min="1" max="24" value="3" required></label>
                        <div class="field span-2"><span>适用产品等级 eligibleProductCodes <span class="req">*</span></span>
                            <div class="grid cols-3">
                                ${CAMPAIGN_PRODUCT_CODES.map((code) => `
                                    <label class="checkbox">
                                        <input type="checkbox" name="eligibleProductCodes" value="${esc(code)}"
                                               ${code === 'PLATINUM' ? 'checked' : ''}>${esc(code)}
                                    </label>`).join('')}
                            </div>
                        </div>
                        <label class="field"><span>单笔最低消费 minTransactionAmount <span class="req">*</span></span>
                            <input name="minTransactionAmount" type="number" step="0.01" min="0.01"
                                   value="50.00" required></label>
                        <label class="field"><span>返现率 cashbackRate（0–1） <span class="req">*</span></span>
                            <input name="cashbackRate" type="number" step="0.0001" min="0" max="1"
                                   value="0.05" required></label>
                        <label class="field"><span>单笔返现上限 maxCashbackPerTransaction <span class="req">*</span></span>
                            <input name="maxCashbackPerTransaction" type="number" step="0.01" min="0.01"
                                   value="30.00" required></label>
                        <label class="field"><span>客户累计上限 maxCashbackPerCustomer <span class="req">*</span></span>
                            <input name="maxCashbackPerCustomer" type="number" step="0.01" min="0.01"
                                   value="300.00" required></label>
                        <label class="field"><span>活动总预算 budget <span class="req">*</span></span>
                            <input name="budget" type="number" step="0.01" min="0.01" value="50000.00" required></label>
                        <div class="field"><span>报名要求 enrollmentRequired</span>
                            <label class="checkbox"><input type="checkbox" name="enrollmentRequired" checked>
                                客户需先报名才返现</label>
                        </div>
                    </div>
                    <div class="form-actions">
                        <button class="btn primary" type="submit" id="campaign-save">创建活动</button>
                    </div>
                    <div class="hint mt-16">
                        新建活动状态为 DRAFT，发布后才参与返现评估；结束日期由期限月数计算
                        （endDate = startDate + N 月 - 1 天）。
                    </div>
                    <div id="campaign-msg"></div>
                </form>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>活动列表</h3>
                    <form class="inline-form" id="campaign-filter">
                        <label class="field"><span>状态</span>
                            <select id="campaign-status">
                                <option value="">全部</option>
                                ${CAMPAIGN_STATUSES.map((status) => `<option value="${esc(status)}"
                                    ${status === campaignUi.status ? 'selected' : ''}>${esc(CAMPAIGN_STATUS_TEXT[status])}</option>`).join('')}
                            </select></label>
                        <button class="btn ghost" type="submit">查询</button>
                    </form>
                </div>
                <div id="campaign-list"><div class="empty">加载中…</div></div>
            </div>
            <div class="card">
                <div class="section-title">
                    <h3>活动效果</h3>
                    <span class="hint">报名数 / 奖励数 / 已发放 / 待入账 / 返现总额 / 预算使用率 / 转化率</span>
                </div>
                <div id="campaign-performance">
                    <div class="empty">在列表中点击“查看效果”查看单个活动的转化情况</div>
                </div>
            </div>`
    });

    const listBox = document.getElementById('campaign-list');
    const performanceBox = document.getElementById('campaign-performance');
    const form = document.getElementById('campaign-form');
    const saveButton = document.getElementById('campaign-save');

    const campaignActions = (campaign) => {
        const id = esc(campaignIdOf(campaign));
        const actions = [];
        if (campaign.status === 'DRAFT' || campaign.status === 'PAUSED') {
            actions.push(`<button class="btn primary small" data-activate="${id}">发布</button>`);
        }
        if (campaign.status === 'ACTIVE') {
            actions.push(`<button class="btn ghost small" data-pause="${id}">暂停</button>`);
        }
        if (campaign.status === 'ACTIVE' || campaign.status === 'PAUSED') {
            actions.push(`<button class="btn danger small" data-end="${id}">结束</button>`);
        }
        actions.push(`<button class="btn ghost small" data-performance="${id}">查看效果</button>`);
        return actions.join('');
    };

    const mutate = async (campaignId, action, label, actionButton) => {
        setBusy(actionButton, true, `${label}中…`);
        try {
            await api(`/api/v1/campaigns/${encodeURIComponent(campaignId)}/${action}`, { method: 'POST' });
            toast(`${label}成功`, 'success');
            await loadList(campaignUi.page);
        } catch (error) {
            toast(`${error.code || `${label}失败`}：${error.message}`, 'error');
            setBusy(actionButton, false);
        }
    };

    const loadPerformance = async (campaignId) => {
        performanceBox.innerHTML = '<div class="empty">加载中…</div>';
        try {
            const performance = await api(`/api/v1/campaigns/${encodeURIComponent(campaignId)}/performance`);
            const enrolled = pickValue(performance, ['enrolledCount', 'enrollmentCount'], 0);
            const rewards = pickValue(performance, ['rewardCount'], 0);
            const granted = pickValue(performance, ['grantedCount'], 0);
            const pending = pickValue(performance, ['pendingCount'], 0);
            const cashbackTotal = pickValue(performance, ['cashbackTotal'], 0);
            const budget = pickValue(performance, ['budget'], null);
            const consumed = pickValue(performance, ['consumedBudget'], null);
            const usage = percentOf(consumed, budget, pickValue(performance, ['budgetUsageRate'], null));
            const conversion = percentOf(rewards, enrolled, pickValue(performance, ['conversionRate'], null));
            performanceBox.innerHTML = `
                <h3>${esc(pickValue(performance, ['code', 'campaignCode'], campaignId))}
                    ${campaignStatusBadge(performance.status)}</h3>
                <div class="grid cols-4">
                    <div class="stat"><div class="label">报名数</div><div class="value">${esc(enrolled)}</div></div>
                    <div class="stat"><div class="label">奖励数</div><div class="value">${esc(rewards)}</div></div>
                    <div class="stat"><div class="label">已发放</div><div class="value">${esc(granted)}</div></div>
                    <div class="stat"><div class="label">待入账</div><div class="value">${esc(pending)}</div></div>
                    <div class="stat"><div class="label">返现总额</div><div class="value">${money(cashbackTotal)}</div></div>
                    <div class="stat"><div class="label">预算使用率</div>
                        <div class="value">${esc(usage)}<small>${money(consumed)} / ${money(budget)}</small></div></div>
                    <div class="stat"><div class="label">转化率</div>
                        <div class="value">${esc(conversion)}<small>奖励 / 报名</small></div></div>
                </div>
                <div class="hint mt-16">
                    活动期 ${dateText(performance.startDate)} → ${dateText(performance.endDate)} ·
                    已发放 / 待入账为奖励笔数，返现总额为已入账金额；
                    预算使用率 = 已预占预算 / 活动预算，转化率 = 奖励数 / 报名数。
                </div>`;
        } catch (error) {
            performanceBox.innerHTML = `${errBox(error)}
                <div class="hint">营销服务（8087）可能未启动，或活动不存在。</div>`;
        }
    };

    const loadList = async (page) => {
        campaignUi.page = Math.max(0, page);
        campaignUi.status = document.getElementById('campaign-status').value;
        listBox.innerHTML = '<div class="empty">加载中…</div>';
        const params = new URLSearchParams({ page: String(campaignUi.page), size: '10' });
        if (campaignUi.status) params.set('status', campaignUi.status);
        try {
            const result = await api(`/api/v1/campaigns?${params.toString()}`);
            const campaigns = listOf(result);
            listBox.innerHTML = `
                ${campaigns.length === 0 ? '<div class="empty">没有符合条件的活动，可先用上方表单创建</div>' : `
                    <div class="table-wrap"><table class="table">
                        <thead><tr><th>活动</th><th>状态</th><th>起止日期</th><th>期限</th><th class="num">返现率</th>
                            <th class="num">预算使用</th><th class="num">已消耗 / 预算</th><th>操作</th></tr></thead>
                        <tbody>${campaigns.map((campaign) => `
                            <tr>
                                <td>
                                    <div><strong>${esc(campaign.name)}</strong></div>
                                    <div class="mono muted">${esc(campaign.code)}</div>
                                </td>
                                <td>${campaignStatusBadge(campaign.status)}</td>
                                <td class="nowrap">${dateText(campaign.startDate)} → ${dateText(campaign.endDate)}</td>
                                <td class="nowrap">${esc(campaign.durationMonths ?? '—')} 月</td>
                                <td class="num mono">${formatRate(campaign.cashbackRate)}</td>
                                <td class="num mono">${percentOf(campaign.consumedBudget, campaign.budget, null)}</td>
                                <td class="num mono">${money(campaign.consumedBudget)}<br>
                                    <span class="muted">/ ${money(campaign.budget)}</span></td>
                                <td class="actions">${campaignActions(campaign)}</td>
                            </tr>`).join('')}
                        </tbody></table></div>`}
                ${pager(campaignUi.page, result.size || 10, result.total || campaigns.length)}`;
            wirePager(listBox, 'page', (step) => loadList(campaignUi.page + step));
            listBox.querySelectorAll('[data-activate]').forEach((button) => {
                button.addEventListener('click', () => mutate(button.dataset.activate, 'activate', '发布', button));
            });
            listBox.querySelectorAll('[data-pause]').forEach((button) => {
                button.addEventListener('click', () => mutate(button.dataset.pause, 'pause', '暂停', button));
            });
            listBox.querySelectorAll('[data-end]').forEach((button) => {
                button.addEventListener('click', () => {
                    if (!window.confirm('活动结束后不再参与返现评估，确认结束？')) return;
                    mutate(button.dataset.end, 'end', '结束', button);
                });
            });
            listBox.querySelectorAll('[data-performance]').forEach((button) => {
                button.addEventListener('click', () => loadPerformance(button.dataset.performance));
            });
        } catch (error) {
            listBox.innerHTML = `${errBox(error)}<div class="hint">营销服务（8087）可能未启动。</div>`;
        }
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const values = formValues(form);
        const msg = document.getElementById('campaign-msg');
        msg.innerHTML = '';
        const selected = Array.from(form.querySelectorAll('input[name="eligibleProductCodes"]:checked'))
            .map((input) => input.value);
        if (selected.length === 0) {
            toast('请至少选择一个适用产品等级', 'error');
            return;
        }
        setBusy(saveButton, true, '创建中…');
        try {
            const campaign = await api('/api/v1/campaigns', {
                method: 'POST',
                body: {
                    code: values.code.trim(),
                    name: values.name.trim(),
                    description: (values.description || '').trim(),
                    startDate: values.startDate,
                    durationMonths: Number(values.durationMonths),
                    eligibleProductCodes: selected,
                    minTransactionAmount: Number(values.minTransactionAmount),
                    cashbackRate: Number(values.cashbackRate),
                    maxCashbackPerTransaction: Number(values.maxCashbackPerTransaction),
                    maxCashbackPerCustomer: Number(values.maxCashbackPerCustomer),
                    budget: Number(values.budget),
                    enrollmentRequired: values.enrollmentRequired === 'on'
                }
            });
            toast(`活动已创建：${campaign.code || values.code}（DRAFT，发布后生效）`, 'success');
            await loadList(0);
        } catch (error) {
            msg.innerHTML = errBox(error);
        } finally {
            setBusy(saveButton, false);
        }
    });

    document.getElementById('campaign-filter').addEventListener('submit', (event) => {
        event.preventDefault();
        loadList(0);
    });

    loadList(campaignUi.page);
}

/* ---------------- 路由 ---------------- */

function pageNotFound() {
    shell({
        active: '',
        title: '页面不存在',
        subtitle: '',
        content: '<div class="card"><div class="empty">没有找到该页面。<br><a href="#/menu">返回首页</a></div></div>'
    });
}

function route() {
    loadSession();
    const hash = location.hash || '#/menu';
    const path = hash.replace(/^#/, '');
    const segments = path.split('/').filter(Boolean);

    if (!state.session) {
        if (path !== '/login') {
            location.hash = '#/login';
            return;
        }
        pageLogin();
        return;
    }
    if (path === '/login') {
        location.hash = '#/menu';
        return;
    }

    if (segments.length === 0 || segments[0] === 'menu') return pageMenu();
    if (segments[0] === 'accounts') return pageAccounts();
    if (segments[0] === 'cards') {
        return segments[1] ? pageCardDetail(segments[1]) : pageCards();
    }
    if (segments[0] === 'transactions') {
        if (segments[1] === 'new') return pageTransactionNew();
        return segments[1] ? pageTransactionDetail(segments[1]) : pageTransactions();
    }
    if (segments[0] === 'bill-pay') return pageBillPay();
    if (segments[0] === 'payments') return pagePayments();
    if (segments[0] === 'billing') return pageBilling();
    if (segments[0] === 'limits') return pageLimits();
    if (segments[0] === 'fx') return pageFx();
    if (segments[0] === 'postings') return pagePostings();
    if (segments[0] === 'reports') return pageReports();
    if (segments[0] === 'premium-offers') return pagePremiumOffers();
    if (segments[0] === 'premium-campaigns') return pagePremiumCampaigns();
    if (segments[0] === 'authorizations') return pageAuthorizations();
    if (segments[0] === 'ledger') return pageLedger();
    if (segments[0] === 'reconciliation') {
        return segments[1] ? pageReconciliationRun(segments[1]) : pageReconciliation();
    }
    if (segments[0] === 'portal') return pagePortal();
    if (segments[0] === 'lending') return pageLending();
    if (segments[0] === 'risk') return pageRisk();
    if (segments[0] === 'operations') return pageOperations();
    if (segments[0] === 'users') {
        if (segments[1] === 'new') return pageUserNew();
        return segments[1] ? pageUserEdit(segments[1]) : pageUsers();
    }
    return pageNotFound();
}

window.addEventListener('hashchange', route);
window.addEventListener('DOMContentLoaded', route);
