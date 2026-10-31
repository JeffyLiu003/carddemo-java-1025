/*
 * CardDemo Web UI 中英文共享层。
 *
 * 不修改 app.js 也能切换界面语言：本文件在 app.js 之前加载，用运行时翻译层
 * 把 DOM 里的中文静态文案替换成英文（WeakMap 记录原文），并用 MutationObserver
 * 监听后续渲染出来的节点。重新切回中文时按记录恢复原文。
 *
 * 约定：
 * - 只翻译「整段文本」；动态业务数据（客户名、商户名、卡号、地址、金额、日期）
 *   保持原样，不做子串替换，避免破坏数据。
 * - 带 data-i18n-skip 的元素（语言切换按钮本身）整棵子树不翻译。
 * - fetch 包装：/api/ 请求自动补 Accept-Language 头（不覆盖调用方已有头）。
 */
(function () {
    'use strict';

    const STORAGE_KEY = 'carddemo.locale';
    const ZH = 'zh-CN';
    const EN = 'en-US';
    const DEFAULT_LOCALE = ZH;
    const SKIP_ATTR = 'data-i18n-skip';
    const TEXT_ATTRS = ['placeholder', 'title', 'aria-label'];
    const SKIP_TAGS = { SCRIPT: 1, STYLE: 1, NOSCRIPT: 1 };
    const HAN = /[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]/;

    /* ------------------------------------------------------------------ *
     * 词典：中文 → 英文（精确匹配整段文本，优先级最高）
     * 末尾一节是旧营销分支（demo-b / demo）的「活动中心 / 我的奖励 / 活动管理」
     * 文案，便于把本文件同步到那些分支。
     * ------------------------------------------------------------------ */
    const DICT = {
        /* ---- 外壳 / 导航 ---- */
        '信用卡核心系统': 'Credit card core system',
        'CardDemo 信用卡核心系统': 'CardDemo · Credit card core system',
        '演示环境 · 数据为种子样例': 'Demo environment · seeded sample data',
        'CardDemo Java 微服务版': 'CardDemo Java microservices edition',
        '退出': 'Sign out',
        '已退出登录': 'Signed out',
        '首页': 'Home',
        '账户与卡片': 'Accounts & cards',
        '客户 360': 'Customer 360',
        '账户视图': 'Account overview',
        '信用卡': 'Credit cards',
        '交易与支付': 'Transactions & payments',
        '交易查询': 'Transaction search',
        '交易录入': 'New transaction',
        '账单支付': 'Bill payment',
        '支付': 'Payments',
        '账单': 'Statements',
        '汇率': 'FX rates',
        '过账': 'Postings',
        '交易报表': 'Transaction reports',
        '营销': 'Marketing',
        '高端返现': 'Premium cashback',
        '返现活动': 'Cashback campaigns',
        '信贷与风险': 'Credit & risk',
        '贷款': 'Loans',
        '授权管理': 'Authorizations',
        '风险': 'Risk',
        '运营管理': 'Operations',
        '总账': 'General ledger',
        '对账与 EOD': 'Reconciliation & EOD',
        '限额': 'Limits',
        '运营': 'Operations',
        '用户管理': 'User management',

        /* ---- 登录 ---- */
        '登录': 'Sign in',
        '登录中…': 'Signing in…',
        '登录后进入账户、卡片、交易与授权管理': 'Manage accounts, cards, transactions and authorizations after signing in',
        '用户号': 'User ID',
        '密码': 'Password',
        '管理员 ADMIN001 / admin123': 'Administrator ADMIN001 / admin123',
        '用户 USER0001 / user1234': 'User USER0001 / user1234',
        '演示账号已预填，可直接登录。管理员可额外访问“用户管理”。':
            'Demo accounts are pre-filled so you can sign in directly. Administrators also get User management.',
        '本环境为 POC，接口尚未强制校验 token，请勿暴露到公网。':
            'This is a POC environment: APIs do not enforce tokens yet, so do not expose it to the internet.',

        /* ---- 通用 ---- */
        '正常': 'Active',
        '停用': 'Inactive',
        '管理员': 'Administrator',
        '普通用户': 'Standard user',
        '批准': 'Approved',
        '拒绝': 'Declined',
        '请求失败': 'Request failed',
        '处理中…': 'Processing…',
        '登录已失效，请重新登录': 'Session expired, please sign in again',
        '加载中…': 'Loading…',
        '读取中…': 'Loading…',
        '统计中…': 'Calculating…',
        '保存中…': 'Saving…',
        '创建中…': 'Creating…',
        '提交中…': 'Submitting…',
        '查询': 'Search',
        '查询中…': 'Searching…',
        '搜索': 'Search',
        '搜索中…': 'Searching…',
        '全部': 'All',
        '是': 'Yes',
        '否': 'No',
        '否': 'No',
        '操作': 'Actions',
        '详情': 'Details',
        '查看': 'View',
        '修改': 'Edit',
        '删除': 'Delete',
        '新增': 'Add',
        '归档': 'Archive',
        '关闭': 'Close',
        '返回': 'Back',
        '返回列表': 'Back to list',
        '上一步': 'Previous',
        '上一页': 'Previous',
        '下一页': 'Next',
        '刷新定义': 'Reload definitions',
        '刷新模板': 'Reload templates',
        '释放': 'Release',
        '冲正': 'Reverse',
        '重算': 'Recalculate',
        '承诺还款': 'Promise to pay',
        '结案': 'Close case',
        '归档结案': 'Archive and close',
        '通过': 'Approve',
        '排除': 'Dismiss',
        '确认': 'Confirm',
        '上报': 'Escalate',
        '发送': 'Send',
        '上传': 'Upload',
        '执行': 'Run',
        '提交': 'Submit',
        '完成': 'Done',
        '客户': 'Customer',
        '账户': 'Account',
        '卡号': 'Card number',
        '交易号': 'Transaction ID',
        '姓名': 'Name',
        '名称': 'Name',
        '描述': 'Description',
        '类型': 'Type',
        '类别': 'Category',
        '状态': 'Status',
        '期间': 'Period',
        '币种': 'Currency',
        '来源': 'Source',
        '金额': 'Amount',
        '时间': 'Time',
        '操作人': 'Actor',
        '资源 ID': 'Resource ID',
        '资源类型': 'Resource type',
        '消息': 'Message',
        '说明': 'Notes',
        '备注': 'Notes',
        '原因': 'Reason',
        '原因码': 'Reason code',
        '结果': 'Result',
        '方向': 'Direction',
        '科目': 'Account',
        '版本号': 'Version',
        '创建时间': 'Created',
        '更新时间': 'Updated',
        '完成时间': 'Completed',
        '开始时间': 'Started',
        '到期日': 'Expiry date',
        '到期时间': 'Expires',
        '记账时间': 'Posted at',
        '处理时间': 'Processed',
        '授权时间': 'Authorized',
        '业务日期': 'Business date',
        '提示': 'Notice',
        '合计': 'Total',
        '小计': 'Subtotal',
        '净额': 'Net',
        '费用': 'Fees',
        '利息': 'Interest',
        '本金': 'Principal',
        '商户': 'Merchant',
        '商户号': 'Merchant ID',
        '商户名称': 'Merchant name',
        '商户城市': 'Merchant city',
        '商户邮编': 'Merchant ZIP',
        '地址': 'Address',
        '州 / 邮编': 'State / ZIP',
        '电话': 'Phone',
        '出生日期': 'Date of birth',
        '客户号': 'Customer ID',
        '账户号': 'Account number',
        '账户号（11 位）': 'Account number (11 digits)',
        '账户号（可选，11 位）': 'Account number (optional, 11 digits)',
        '客户号（可选，9 位）': 'Customer ID (optional, 9 digits)',
        '账户号 accountId': 'Account number accountId',
        '卡号 cardNumber': 'Card number cardNumber',
        '客户号 customerId': 'Customer ID customerId',
        '关键词 q': 'Keyword q',
        '当前身份：': 'Signed in as:',
        '· 额度': '· limit',
        '周': 'Week',
        '月': 'Month',
        '个科目': 'accounts',
        '笔': 'entries',
        '条': 'records',
        '张': 'cards',
        '行': 'rows',
        '项': 'items',
        '期': 'instalments',
        '页': '',

        /* ---- 首页 ---- */
        '今天的演示可以做什么': 'What you can demo today',
        '余额、额度、账期与客户资料一屏查看': 'Balances, limits, billing cycles and customer data on one screen',
        '信用卡管理': 'Card management',
        '按账户查询卡片、查看详情与维护卡状态': 'Find cards by account, view details and maintain card status',
        '按交易号/卡号/账户检索交易明细': 'Search transactions by transaction ID, card number or account',
        '人工补录交易，支持类型与类别选择': 'Enter transactions manually with type and category selection',
        '全额还款，实时更新账户余额': 'Pay the balance in full and update the account in real time',
        '受理支付指令、自动筛查，接受、清算与退汇':
            'Accept payment orders with automatic screening, then accept, settle or return them',
        '生成信用卡账单，查看利息与最低还款并全额支付':
            'Generate statements, review interest and minimum payment, then pay in full',
        '维护汇率、获取 5 分钟有效报价并执行换汇':
            'Maintain FX rates, get a 5-minute quote and execute conversions',
        '按过账类型生成借贷分录，支持冲正与幂等重试':
            'Generate debit/credit entries by posting type with reversal and idempotent retries',
        '按日期区间生成交易明细与合计': 'Produce transaction details and totals for a date range',
        '高端卡分期消费返现：查看可参与活动、报名并跟踪返现入账':
            'Premium card instalment cashback: view eligible campaigns, enroll and track cashback postings',
        '创建活动、发布/暂停/结束并查看报名与返现转化效果':
            'Create campaigns, publish/pause/end them and review enrollment and cashback conversion',
        '待授权汇总、明细与欺诈标记': 'Authorization summary, details and fraud flagging',
        'BFF 聚合额度位置、卡片、客户、最近交易与权益，并统一搜索':
            'BFF aggregates credit position, cards, customer, recent transactions and benefits, plus unified search',
        '贷款申请、审批、放款、还款计划与还款':
            'Loan applications, approval, disbursement, repayment schedule and repayments',
        '欺诈评分与警报、AML 筛查、争议案件与逾期催收':
            'Fraud scoring and alerts, AML screening, disputes and delinquency collections',
        '批处理、报表、通知、文档、工作流、审计与参考数据':
            'Batch jobs, reports, notifications, documents, workflows, audit and reference data',
        '客户/账户/渠道限额的创建、占用、释放与可用性检查':
            'Create, hold, release and check customer/account/channel limits',
        '科目表、日记账、试算平衡与期间关账':
            'Chart of accounts, journals, trial balance and period close',
        '批次与总账对账、差异处理、EOD 作业':
            'Batch-to-ledger reconciliation, break handling and EOD jobs',
        '维护登录用户与管理员账号': 'Maintain sign-in users and administrator accounts',
        '本界面连接 IAM、客户、卡片、账户、交易、授权、总账、对账、支付、账单、限额与汇率等微服务，':
            'This UI talks to the IAM, customer, card, account, transaction, authorization, ledger, reconciliation, payment, statement, limit and FX microservices,',
        '全部数据来自遗留 COBOL 系统的归一化种子。':
            'All data comes from seeds normalized from the legacy COBOL system.',
        '可以先看账户 00000000001（额度 2020、欠款 1940、可用 80），再试一笔账单支付、支付指令或换汇报价。':
            'Start with account 00000000001 (limit 2020, balance 1940, available 80), then try a bill payment, a payment order or an FX quote.',

        /* ---- 账户视图 ---- */
        '账户 + 额度位置 + 冻结 + 客户 + 卡片组合视图（对应遗留 COACTVWC）':
            'Combined account + credit position + holds + customer + cards view (legacy COACTVWC)',
        '产品编码': 'Product code',
        '当前欠款余额': 'Current balance',
        '信用额度': 'Credit limit',
        '可用额度': 'Available credit',
        '现金额度': 'Cash credit limit',
        '本期贷 / 借': 'Cycle credit / debit',
        '开户 / 到期': 'Opened / expires',
        '重发日': 'Reissue date',
        '账户组': 'Account group',
        '可用额度 = 信用额度 − 当前欠款 − 生效冻结合计（授权占用另计）。':
            'Available credit = credit limit − current balance − active holds (authorization holds excluded).',
        '额度位置': 'Credit position',
        '当前欠款': 'Current balance',
        '冻结合计': 'Active holds',
        '产品': 'Product',
        '类别 / 币种': 'Category / currency',
        '年利率 APR': 'Annual rate (APR)',
        '月费': 'Monthly fee',
        '账单周期日': 'Statement cycle day',
        '主卡持有人': 'Primary cardholder',
        '该账户暂无关联客户（未找到卡片映射）':
            'No customer is linked to this account (no card mapping found)',
        '生效中冻结': 'Active holds',
        '冻结占用可用额度，释放后立即归还':
            'Holds reserve available credit and return it immediately when released',
        '来源类型': 'Source type',
        '来源 ID': 'Source ID',
        '来源 ID（自动生成）': 'Source ID (generated automatically)',
        '到期时间（可选）': 'Expiry time (optional)',
        '创建冻结': 'Create hold',
        '该账户当前没有生效中的冻结': 'This account has no active holds',
        '冻结号': 'Hold ID',
        '关联卡片': 'Linked cards',
        '前往卡片管理': 'Go to card management',
        '暂无卡片': 'No cards',
        '冻结已释放': 'Hold released',
        '释放中…': 'Releasing…',
        '释放失败': 'Release failed',
        '创建冻结失败': 'Creating the hold failed',

        /* ---- 卡片 ---- */
        '按账户或客户查询卡片，维护卡面姓名与状态（对应遗留 COCRDLIC/UPC）':
            'Look up cards by account or customer and maintain embossed name and status (legacy COCRDLIC/UPC)',
        '没有符合条件的卡片': 'No cards match the criteria',
        '查看 / 维护': 'View / maintain',
        '卡片详情': 'Card details',
        '卡片信息': 'Card information',
        '所属账户': 'Account',
        '所属客户': 'Customer',
        '卡面姓名': 'Embossed name',
        '维护卡片': 'Maintain card',
        '卡状态': 'Card status',
        'Y · 正常': 'Y · Active',
        'N · 停用': 'N · Inactive',
        '到期月': 'Expiry month',
        '到期年': 'Expiry year',
        '保存修改': 'Save changes',
        '卡片已更新': 'Card updated',
        '不可修改）': 'cannot be changed)',
        'CVV 仅存储于服务端，不对外返回，也不能通过本页面修改。':
            'The CVV is stored server-side only: it is never returned and cannot be changed here.',
        '详情 / 维护': 'Details / maintain',

        /* ---- 交易 ---- */
        '按交易号前缀、卡号或账户查询（对应遗留 COTRN00C/01C）':
            'Search by transaction ID prefix, card number or account (legacy COTRN00C/01C)',
        '交易号前缀': 'Transaction ID prefix',
        '如 00000000000000': 'e.g. 00000000000000',
        '新增交易': 'New transaction',
        '没有符合条件的交易': 'No transactions match the criteria',
        '类型 / 类别': 'Type / category',
        '交易详情': 'Transaction details',
        '账户 / 卡号': 'Account / card',
        '原始时间': 'Original time',
        '去账单支付': 'Go to bill payment',
        '人工补录一笔交易（对应遗留 COTRN02C）': 'Enter a transaction manually (legacy COTRN02C)',
        '正在加载参照数据…': 'Loading reference data…',
        '交易类型': 'Transaction type',
        '交易类别': 'Transaction category',
        '卡号（与账户一致）': 'Card number (same as account)',
        '金额（-99999999.99）': 'Amount (-99999999.99)',
        '原始日期': 'Original date',
        '处理日期': 'Processed date',
        '提交交易': 'Submit transaction',
        '（该类型无类别）': '(no category for this type)',
        '（无可用过账类型）': '(no posting type available)',
        '（类型加载失败）': '(failed to load types)',
        '（无科目）': '(no account)',
        '（科目加载失败）': '(failed to load accounts)',
        '（无可用作业）': '(no job available)',
        '（作业定义加载失败）': '(failed to load job definitions)',
        '（无可用报表）': '(no report available)',
        '（报表定义加载失败）': '(failed to load report definitions)',
        '（无可用模板）': '(no template available)',
        '（模板加载失败）': '(failed to load templates)',

        /* ---- 账单支付 ---- */
        '全额偿还账户当前欠款（对应遗留 COBIL00C）':
            "Pay the account's outstanding balance in full (legacy COBIL00C)",
        '查询账户': 'Account lookup',
        '查询余额': 'Check balance',
        '确认支付': 'Confirm payment',
        '支付金额恒等于账户当前欠款余额，支付后余额归零。余额 ≤ 0 时不可支付。':
            "The payment always equals the account's current balance and clears it to zero. It is disabled when the balance is ≤ 0.",
        '我确认执行全额还款': 'I confirm the full repayment',
        '执行支付': 'Pay',
        '当前欠款余额': 'Current balance',
        '请先查询账户余额': 'Look up the account balance first',
        '请勾选确认后再提交': 'Tick the confirmation box before submitting',
        '支付中…': 'Paying…',
        '账单支付成功': 'Bill payment completed',
        '支付成功：交易号': 'Payment completed: transaction',
        '，账户新余额': ', new account balance',
        '查看交易': 'View transaction',

        /* ---- 交易报表 ---- */
        '按处理日期闭区间统计交易明细与合计（对应遗留 CORPT00C / CBTRN03C）':
            'Totals transaction details over an inclusive processing-date range (legacy CORPT00C / CBTRN03C)',
        '开始日期': 'Start date',
        '结束日期': 'End date',
        '卡号（可选）': 'Card number (optional)',
        '生成报表': 'Generate report',
        '请选择开始与结束日期': 'Select a start and end date',
        '交易笔数': 'Transactions',
        '区间合计': 'Range total',
        '本页合计': 'Page total',
        '该区间没有交易': 'No transactions in this range',
        '按卡号合计': 'Total by card',

        /* ---- 授权 ---- */
        '待授权汇总、授权明细、欺诈标记与过期清理（对应遗留 COPAUS0C/1C/2C、CBPAUP0C）':
            'Authorization summary, details, fraud flagging and expiry purge (legacy COPAUS0C/1C/2C, CBPAUP0C)',
        '待授权占用': 'Authorized holds',
        '批准笔数': 'Approved',
        '拒绝笔数': 'Declined',
        '该账户暂无待授权汇总；新授权批准后会自动创建。':
            'This account has no authorization summary yet; one is created as soon as an authorization is approved.',
        '暂无授权记录': 'No authorizations',
        '已标记': 'Flagged',
        '标记欺诈': 'Flag fraud',
        '过期授权清理': 'Purge expired authorizations',
        '删除早于“今天 − 过期天数”的授权明细，并回退其占用的信用额度。':
            'Delete authorization details older than “today − expiry days” and release the credit they hold.',
        '过期天数': 'Expiry days',
        '执行清理': 'Purge',
        '明细删除': 'Authorization details deleted:',
        '条，汇总删除': 'records; summaries deleted:',
        '个， 回退占用额度': 'summaries; credit released:',
        '回退占用额度': 'credit released',

        /* ---- 过账 ---- */
        '按过账类型生成复式分录并驱动账户余额，幂等批次可冲正':
            'Generate double-entry postings by posting type, drive account balances and reverse idempotent batches',
        '已过账': 'Posted',
        '已冲正': 'Reversed',
        '借 DR': 'DR',
        '贷 CR': 'CR',
        '无分录行': 'No entry lines',
        '行号': 'Line',
        '账户角色': 'Account role',
        '总账科目': 'GL account',
        '提交结果': 'Submission result',
        '批次号': 'Batch ID',
        '幂等键': 'Idempotency key',
        '过账时间': 'Posted at',
        '冲正时间': 'Reversed at',
        '冲正原批次': 'Reverses batch',
        '总账分录': 'GL entry',
        '分录行': 'Entry lines',
        '新建过账批次': 'New posting batch',
        '过账类型': 'Posting type',
        '提交过账': 'Submit posting',
        '同一幂等键重复提交只产生一个批次；类型决定借贷方向与总账科目。':
            'Re-submitting the same idempotency key yields a single batch; the type determines debit/credit direction and the GL account.',
        '提交后会显示批次信息与借贷分录行':
            'Batch information and debit/credit entry lines appear after submission',
        '最近批次': 'Recent batches',
        '按创建时间倒序，已过账批次可冲正':
            'Newest first; posted batches can be reversed',
        '过账中…': 'Posting…',
        '冲正中…': 'Reversing…',
        '冲正失败': 'Reversal failed',
        '过账失败': 'Posting failed',
        '还没有过账批次': 'No posting batches yet',

        /* ---- 总账 ---- */
        '科目表、日记账、试算平衡与期间关账（管理员）':
            'Chart of accounts, journals, trial balance and period close (admin)',
        '资产': 'Asset',
        '负债': 'Liability',
        '权益': 'Equity',
        '收入': 'Revenue',
        '费用': 'Expense',
        '已记账': 'Posted',
        '开放': 'Open',
        '已关账': 'Closed',
        '科目表': 'Chart of accounts',
        '会计期间': 'Accounting periods',
        '关账后该期间不可再记账': 'A closed period no longer accepts postings',
        '试算平衡': 'Trial balance',
        '期间（YYYY-MM）': 'Period (YYYY-MM)',
        '计算': 'Calculate',
        '选择期间后点击“计算”': 'Pick a period and press “Calculate”',
        '日记账': 'Journal',
        '按记账时间倒序，含过账批次同步的分录':
            'Newest posting first, including entries synchronized from posting batches',
        '手工记账': 'Manual journal entry',
        '记账时间 postedAt': 'Posted at postedAt',
        '提交分录': 'Submit entry',
        '两行借贷金额必须相等，否则服务端返回 UNBALANCED_JOURNAL。':
            'Both lines must have equal amounts, otherwise the server returns UNBALANCED_JOURNAL.',
        '暂无科目': 'No accounts',
        '可用': 'Available',
        '暂无会计期间': 'No accounting periods',
        '关账时间': 'Closed at',
        '关账': 'Close period',
        '关账中…': 'Closing period…',
        '请填写期间（YYYY-MM）': 'Enter a period (YYYY-MM)',
        '计算中…': 'Calculating…',
        '借方合计': 'Total debit',
        '贷方合计': 'Total credit',
        '是否平衡': 'Balanced',
        '平衡': 'Balanced',
        '不平衡': 'Unbalanced',
        '该期间没有日记账分录': 'No journal entries in this period',
        '暂无日记账': 'No journal entries',
        '分录号': 'Entry ID',
        '借方与贷方科目不能相同': 'Debit and credit accounts must differ',
        '两行金额必须相等，否则借贷不平衡': 'Both line amounts must be equal, otherwise the entry is unbalanced',
        '关账失败': 'Closing the period failed',
        '记账失败': 'Posting the entry failed',

        /* ---- 对账 / EOD ---- */
        '按业务日期对账过账批次与总账日记账，登记差异处理并查看 EOD 作业（管理员）':
            'Reconcile posting batches against GL journals by business date, record break handling and review EOD runs (admin)',
        '已完成': 'Completed',
        '执行中': 'Running',
        '失败': 'Failed',
        '批次 vs 日记账': 'Batch vs journal',
        '匹配': 'Matched',
        '不匹配': 'Mismatched',
        '缺日记账': 'Missing journal',
        '缺批次': 'Missing batch',
        '金额不一致': 'Amount mismatch',
        '试算不平衡': 'Trial balance unbalanced',
        '已处理': 'Handled',
        '待处理': 'Pending',
        '执行对账 / EOD': 'Run reconciliation / EOD',
        '对账只读业务与总账，不修改任何账务数据':
            'Reconciliation only reads posting and ledger data; it never changes accounting data',
        '执行对账': 'Run reconciliation',
        '执行 EOD': 'Run EOD',
        'EOD 当前步骤为对账：按同一业务日期触发对账，并把 EOD 运行与对账运行关联起来。':
            'EOD currently consists of reconciliation: it runs reconciliation for the same business date and links the EOD run to it.',
        '对账历史': 'Reconciliation history',
        '点击运行号查看摘要与差异明细': 'Select a run ID to see its summary and break details',
        'EOD 历史': 'EOD history',
        '最近 5 次 EOD 作业，含关联对账运行号':
            'The last five EOD jobs, including the linked reconciliation run ID',
        '还没有对账运行': 'No reconciliation runs yet',
        '运行号': 'Run ID',
        '批次 / 日记账': 'Batches / journals',
        '差异数': 'Breaks',
        '还没有 EOD 运行': 'No EOD runs yet',
        '对账运行号': 'Reconciliation run ID',
        '请选择业务日期': 'Select a business date',
        '对账中…': 'Reconciling…',
        '执行中…': 'Running…',
        '对账运行详情': 'Reconciliation run details',
        '请输入差异处理说明（记录在对账明细上，例如：已确认总账同步延迟，手工补录）':
            'Enter break handling notes (stored on the reconciliation item, e.g. confirmed ledger sync lag, manually posted)',
        '处理说明不能为空': 'Handling notes cannot be empty',
        '登记中…': 'Saving…',
        '差异处理说明已登记': 'Break handling notes saved',
        '登记失败': 'Saving the notes failed',
        '错误信息': 'Error message',
        '该运行没有对账明细': 'This run has no reconciliation items',
        '登记处理': 'Record handling',
        '批次数量': 'Batch count',
        '批次金额': 'Batch amount',
        '日记账数量': 'Journal count',
        '业务日期 / 期间': 'Business date / period',
        '日记账借方合计': 'Journal total debit',
        '日记账贷方合计': 'Journal total credit',
        '匹配项 / 未匹配项': 'Matched / unmatched',
        '差异明细': 'Break details',
        '未匹配项可登记处理说明，刷新后保留':
            'Unmatched items accept handling notes, which persist across refreshes',
        '检查类型': 'Check type',
        '引用': 'Reference',
        '应记金额': 'Expected',
        '实际金额': 'Actual',
        '差异': 'Difference',
        '处理状态': 'Handling status',
        '对账运行': 'Reconciliation run',
        'EOD 运行': 'EOD run',

        /* ---- 用户管理 ---- */
        '维护登录用户与管理员（对应遗留 COUSR00C–03C）':
            'Maintain sign-in users and administrators (legacy COUSR00C–03C)',
        '用户列表': 'Users',
        '新增用户': 'New user',
        '用户号 8 位字母数字，密码 8–64 位，类型 A/U':
            'User ID: 8 alphanumeric characters; password: 8–64 characters; type: A/U',
        '编辑': 'Edit',
        '编辑用户': 'Edit user',
        '至少 8 位': 'At least 8 characters',
        '用户已删除': 'User deleted',
        '用户已创建': 'User created',
        '用户已更新': 'User updated',
        '删除中…': 'Deleting…',
        '删除失败': 'Delete failed',
        '用户类型': 'User type',
        'U · 普通用户': 'U · Standard user',
        'A · 管理员': 'A · Administrator',
        '名': 'First name',
        '姓': 'Last name',
        '新密码（留空则不修改）': 'New password (leave blank to keep it)',
        '创建用户': 'Create user',
        '仅管理员可访问': 'Administrators only',
        '当前账号不是管理员，无法访问该页面。请使用 ADMIN001 登录。':
            'This account is not an administrator and cannot open this page. Sign in as ADMIN001.',

        /* ---- 支付 ---- */
        '受理支付指令、自动筛查、接受、清算与退汇（设计 13 §2）':
            'Accept payment orders with automatic screening, then accept, settle or return them (design 13 §2)',
        '已受理': 'Accepted',
        '已筛查': 'Screened',
        '已接受': 'Taken',
        '已清算': 'Settled',
        '已退汇': 'Returned',
        '已拒绝': 'Rejected',
        'ACH · 美国自动清算': 'ACH · US automated clearing',
        'SEPA · 欧洲': 'SEPA · Europe',
        'WIRE · 电汇': 'WIRE · Wire transfer',
        'INSTANT · 即时': 'INSTANT · Instant',
        '支付详情': 'Payment details',
        '点击列表中的“详情”查看单据': 'Select “Details” in the list to open an order',
        '支付号': 'Payment ID',
        '付款账户': 'Debit account',
        '收款人': 'Beneficiary',
        '收款账号': 'Beneficiary account',
        '收款银行': 'Beneficiary bank',
        '支付类型': 'Payment type',
        '参考号': 'Reference',
        '过账批次': 'Posting batch',
        '清算时间': 'Settled at',
        '退汇时间': 'Returned at',
        '拒绝原因': 'Rejection reason',
        '创建支付指令': 'Create payment order',
        '付款账户（11 位）': 'Debit account (11 digits)',
        '收款银行代码': 'Beneficiary bank code',
        '提交支付': 'Submit payment',
        '提交后自动筛查：收款人非空且不在拒绝名单 → 已筛查（可接受）；否则直接拒绝。':
            'Orders are screened automatically: a non-empty beneficiary that is not on the deny list becomes Screened (acceptable); otherwise the order is rejected immediately.',
        '同一幂等键重复提交返回原单据。':
            'Re-submitting the same idempotency key returns the original order.',
        '支付指令': 'Payment orders',
        '可选': 'Optional',
        '暂无支付指令': 'No payment orders',
        '操作失败': 'Operation failed',
        '退汇': 'Return',

        /* ---- 账单 ---- */
        '生成账单、查看利息与最低还款、到期日与全额支付（设计 13 §3）':
            'Generate statements, review interest, minimum payment and due date, and pay in full (design 13 §3)',
        '已生成': 'Generated',
        '已支付': 'Paid',
        '已逾期': 'Overdue',
        '账单详情': 'Statement details',
        '生成或查看账单后显示明细': 'Details appear after generating or opening a statement',
        '账单号': 'Statement ID',
        '账期日': 'Cycle date',
        '期初 / 期末': 'Opening / closing',
        '消费 / 还款': 'Purchases / payments',
        '费用 / 利息': 'Fees / interest',
        '最低还款': 'Minimum payment',
        '生成时间': 'Generated at',
        '支付时间': 'Paid at',
        '生成账单': 'Generate statement',
        '账单期间': 'Statement period',
        '同账户 + 同期间只生成一张账单，重复生成返回已有账单。':
            'One statement per account and period; repeating the request returns the existing statement.',
        '账单查询': 'Statement search',
        '暂无账单': 'No statements',
        '全额支付': 'Pay in full',
        '生成中…': 'Generating…',
        '加载中…': 'Loading…',
        '支付失败': 'Payment failed',
        '消费': 'Purchases',
        '还款': 'Payments',

        /* ---- 限额 ---- */
        '客户/账户/渠道限额的创建、占用、释放、调整与可用性检查（设计 13 §4）':
            'Create, hold, release, adjust and check customer/account/channel limits (design 13 §4)',
        '客户': 'Customer',
        '渠道': 'Channel',
        '取现': 'Cash withdrawal',
        '日累计': 'Daily total',
        '转账': 'Transfer',
        '启用': 'Enabled',
        '允许': 'Allowed',
        '不允许': 'Not allowed',
        '金额必须大于零': 'The amount must be greater than zero',
        '创建限额': 'Create limit',
        '范围类型': 'Scope type',
        '范围 ID': 'Scope ID',
        '限额类型': 'Limit type',
        '额度': 'Limit',
        '同一范围 + 类型 + 币种只允许一条限额；调整额度不得低于已用金额。':
            'Only one limit per scope, type and currency; the limit cannot be lowered below the used amount.',
        '可用性检查': 'Availability check',
        '交易金额': 'Transaction amount',
        '检查限额': 'Check limit',
        '限额列表': 'Limits',
        '范围': 'Scope',
        '已用': 'Used',
        '剩余': 'Remaining',
        '占用': 'Hold',
        '调整': 'Adjust',
        '占用金额': 'Hold amount',
        '释放金额': 'Release amount',
        '新的额度': 'New limit',
        '检查中…': 'Checking…',
        '额度充足：剩余': 'Limit available: remaining',
        '额度不足或没有匹配的限额定义，本笔交易将被拒绝。':
            'Either the limit is insufficient or no limit definition matches, so this transaction would be declined.',
        '暂无限额定义': 'No limit definitions',
        '占用失败': 'Holding funds failed',
        '调整失败': 'Adjusting the limit failed',
        '占用成功': 'Hold placed',
        '创建限额失败': 'Creating the limit failed',

        /* ---- 汇率 ---- */
        '汇率维护、5 分钟有效报价、单次消费的换汇执行与换汇记录（设计 13 §5）':
            'Maintain FX rates, issue 5-minute quotes, execute single-conversion trades and list conversions (design 13 §5)',
        '维护汇率': 'Maintain FX rate',
        '基础币种': 'Base currency',
        '报价币种': 'Quote currency',
        '汇率（6 位小数）': 'Rate (6 decimals)',
        '保存汇率': 'Save rate',
        '按币种对 upsert：同一基础/报价币种只保留一条汇率。':
            'Upserted by currency pair: one rate per base/quote pair.',
        '报价与换汇': 'Quote and convert',
        '卖出币种': 'Sell currency',
        '买入币种': 'Buy currency',
        '获取报价': 'Get quote',
        '报价 5 分钟内有效；执行换汇后同一报价不可重复消费。':
            'Quotes are valid for five minutes; a quote cannot be consumed twice.',
        '汇率表': 'FX rates',
        '按基础币种 / 报价币种排序': 'Sorted by base / quote currency',
        '换汇记录': 'Conversions',
        '按创建时间倒序': 'Newest first',
        '暂无汇率，请先维护': 'No FX rates yet — maintain them first',
        '暂无换汇记录': 'No conversions yet',
        '币种对': 'Currency pair',
        '生效时间': 'Effective from',
        '记录号': 'Record ID',
        '报价号': 'Quote ID',
        '卖出金额': 'Sell amount',
        '买入金额': 'Buy amount',
        '换得金额': 'Buy amount',
        '报价号': 'Quote ID',
        '失效时间': 'Expires at',
        '已消费': 'Consumed',
        '可执行': 'Executable',
        '执行换汇': 'Convert',
        '换汇中…': 'Converting…',
        '报价中…': 'Requesting quote…',
        '换汇失败': 'Conversion failed',
        '汇率已保存': 'FX rate saved',
        '换汇完成': 'Conversion completed',
        '换汇成功：记录': 'Conversion completed: record',
        '汇率': 'Rate',

        /* ---- 客户 360 ---- */
        'BFF 聚合额度位置、卡片、客户、最近交易与权益，并提供客户/交易统一搜索（设计 16 §2–§3）':
            'BFF aggregates credit position, cards, customer, recent transactions and benefits, plus unified customer/transaction search (design 16 §2–§3)',
        '输入账户号后点击查询': 'Enter an account number and search',
        '统一搜索': 'Unified search',
        '客户按客户号/姓名匹配；交易支持交易号、11 位账户号、16 位卡号':
            'Customers match by customer ID or name; transactions by transaction ID, 11-digit account or 16-digit card number',
        '如 Immanuel 或 000000001': 'e.g. Immanuel or 000000001',
        '下游降级': 'Upstream degraded',
        '暂不可用，相关区块可能为空，其余数据仍然可用。':
            'is temporarily unavailable; those sections may be empty while the rest still works.',
        '搜索降级': 'Search degraded',
        '暂不可用。': 'is temporarily unavailable.',
        '额度位置': 'Credit position',
        '最近交易': 'Recent transactions',
        '未返回额度位置（账户可能不存在）': 'No credit position returned (the account may not exist)',
        '未返回客户资料': 'No customer profile returned',
        '暂无最近交易': 'No recent transactions',
        '没有匹配结果': 'No matches',
        '请输入搜索关键词': 'Enter a search keyword',
        '类型': 'Type',
        'ID': 'ID',
        '标题': 'Title',
        '引用': 'Reference',
        '搜索结果': 'Search results',
        'BFF 服务（8107）或其下游未启动时无法聚合；账户不存在返回 404。':
            'Nothing can be aggregated while the BFF service (8107) or its upstreams are down; a missing account returns 404.',

        /* ---- 贷款 ---- */
        '贷款申请、审批、放款、还款计划与还款（设计 14 §2）':
            'Loan applications, approval, disbursement, repayment schedule and repayments (design 14 §2)',
        '提交贷款申请': 'Submit loan application',
        '产品编码': 'Product code',
        '期限（月）': 'Term (months)',
        '年利率（0–1）': 'Annual rate (0–1)',
        '提交申请': 'Submit application',
        '审批通过后按等额本息生成还款计划；放款需要申请对应的 loanId。':
            'Approval generates an equal-instalment schedule; disbursement needs the application’s loanId.',
        '申请列表': 'Applications',
        '贷款详情与还款': 'Loan details and repayment',
        '贷款号 loanId': 'Loan ID loanId',
        '查询贷款': 'Look up loan',
        '审批或放款后可在此查看贷款与还款计划':
            'After approval or disbursement the loan and its schedule appear here',
        '审批/放款响应中的 loan.loanId': 'loan.loanId from the approval/disbursement response',
        '贷款号': 'Loan ID',
        '申请号': 'Application ID',
        '剩余本金': 'Outstanding principal',
        '期限 / 年利率': 'Term / annual rate',
        '放款时间': 'Disbursed at',
        '还款': 'Repayment',
        '还款金额': 'Repayment amount',
        '提交还款': 'Submit repayment',
        '金额须等于下一 PENDING 期次合计': 'The amount must equal the next PENDING instalment total',
        '或剩余全部期次总额（提前结清），否则服务端返回 409。':
            'or the total of all remaining instalments (early settlement); otherwise the server returns 409.',
        '还款计划': 'Repayment schedule',
        '等额本息，按到期日排序': 'Equal instalments, ordered by due date',
        '期次': 'Instalment',
        '应还合计': 'Total due',
        '还款时间': 'Repaid at',
        '还款中…': 'Repaying…',
        '还款成功：利息与本金的期次已冲抵':
            'Repayment completed: instalment interest and principal settled',
        '已还': 'Repaid',
        '冲抵期次': 'settling instalments',
        '贷款状态': 'Loan status',
        '该申请未记录 loanId，请填写审批响应中的 loan.loanId':
            'This application has no loanId on record; enter loan.loanId from the approval response',
        '放款中…': 'Disbursing…',
        '放款': 'Disburse',
        '贷款详情': 'Loan details',
        '没有符合条件的贷款申请': 'No loan applications match',
        '审批中…': 'Approving…',
        '已通过': 'Approved',
        '拒绝原因（必填，最多 200 字）': 'Rejection reason (required, max 200 characters)',
        '收入证明不足': 'Insufficient proof of income',
        '申请已拒绝': 'Application rejected',
        '拒绝失败': 'Rejecting the application failed',
        '审批失败': 'Approval failed',
        '放款失败': 'Disbursement failed',
        '请填写贷款号': 'Enter a loan ID',
        '申请已提交': 'Application submitted',
        '全部': 'All',

        /* ---- 风险 ---- */
        '欺诈评分与警报、AML 名单筛查、争议案件与逾期催收（设计 14 §3–§6，管理员）':
            'Fraud scoring and alerts, AML list screening, disputes and delinquency collections (design 14 §3–§6, admin)',
        '欺诈': 'Fraud',
        '争议': 'Disputes',
        '催收': 'Collections',
        '风险处置': 'Risk handling',
        '四个 Tab 各自加载，单个服务不可用不影响其它 Tab':
            'Each tab loads independently; one unavailable service does not affect the others',
        '交易欺诈评分': 'Transaction fraud scoring',
        '国家': 'Country',
        '评分': 'Score',
        '命中规则分值求和：≥80 拒绝、≥50 复核；复核/拒绝会生成 OPEN 警报。':
            'Rule scores are summed: ≥80 declines, ≥50 refers for review; review/decline creates an OPEN alert.',
        '欺诈警报': 'Fraud alerts',
        '警报号': 'Alert ID',
        '分值': 'Score',
        '已生成警报': 'alert created',
        '确认欺诈': 'Confirm fraud',
        '警报已确认': 'Alert confirmed',
        '警报已排除': 'Alert dismissed',
        '没有符合条件的警报': 'No alerts match',
        '评分中…': 'Scoring…',
        '评分完成': 'Scoring completed',
        '决策': 'Decision',
        '评分号': 'Evaluation ID',
        '账户 / 金额': 'Account / amount',
        '商户 / 国家': 'Merchant / country',
        '命中规则': 'Matched rules',
        '名单筛查': 'List screening',
        '姓名 / 实体名': 'Name / entity name',
        '实体类型': 'Entity type',
        '筛查': 'Screen',
        '完全匹配 BLOCKED、包含匹配 POTENTIAL_MATCH；命中生成 OPEN 警报。':
            'Exact matches become BLOCKED and partial matches POTENTIAL_MATCH; hits create OPEN alerts.',
        'AML 警报': 'AML alerts',
        '筛查号': 'Screening ID',
        '命中条目': 'Matched entry',
        '筛查时间': 'Screened at',
        '结论': 'Result',
        '筛查中…': 'Screening…',
        '筛查完成': 'Screening completed',
        '开立争议案件': 'Open dispute',
        '争议金额': 'Dispute amount',
        '开案': 'Open case',
        '开案中…': 'Opening case…',
        '状态链路：OPEN → UNDER_REVIEW → PROVISIONAL_CREDIT → CHARGEBACK → WON/LOST → CLOSED，非法迁移返回 409。':
            'State path: OPEN → UNDER_REVIEW → PROVISIONAL_CREDIT → CHARGEBACK → WON/LOST → CLOSED; invalid transitions return 409.',
        '争议列表': 'Disputes',
        '案件号': 'Case ID',
        '临时入账': 'Provisional credit',
        '受理审核': 'Accept for review',
        '临时入账金额（不得超过争议金额）': 'Provisional credit amount (cannot exceed the dispute amount)',
        '发起 chargeback': 'Raise chargeback',
        '结案': 'Close case',
        '已受理审核': 'Accepted for review',
        '已发起 chargeback': 'Chargeback raised',
        '临时入账已登记': 'Provisional credit recorded',
        '结案结果：输入 won 判持卡人胜（WON），输入 lose 判商户胜（LOST）':
            'Outcome: type won for the cardholder (WON) or lose for the merchant (LOST)',
        '结案：持卡人胜（WON）': 'Outcome: cardholder won (WON)',
        '结案：商户胜（LOST）': 'Outcome: merchant won (LOST)',
        '案件已归档关闭': 'Case archived and closed',
        '没有符合条件的争议案件': 'No disputes match',
        '争议已开案': 'Dispute opened',
        '建立逾期案件': 'Create delinquency case',
        '逾期天数': 'Days delinquent',
        '逾期金额': 'Delinquent amount',
        '建案': 'Create case',
        '分桶：1–30 B1、31–60 B2、61–90 B3、90+ B4；承诺还款日期须晚于当天。':
            'Buckets: 1–30 B1, 31–60 B2, 61–90 B3, 90+ B4; a promise-to-pay date must be later than today.',
        '催收案件': 'Collection cases',
        '分桶': 'Bucket',
        '逾期天数': 'Days delinquent',
        '承诺还款日': 'Promise-to-pay date',
        '新的逾期天数（≥1）': 'New days delinquent (≥1)',
        '逾期天数必须是不小于 1 的整数': 'Days delinquent must be an integer ≥ 1',
        '已重算分桶': 'Bucket recalculated',
        '承诺还款日期（YYYY-MM-DD，须晚于当天）':
            'Promise-to-pay date (YYYY-MM-DD, must be later than today)',
        '日期格式应为 YYYY-MM-DD': 'The date must use YYYY-MM-DD',
        '已登记承诺还款': 'Promise to pay recorded',
        '确认结案？结案后不可再变更。': 'Close the case? It cannot be changed afterwards.',
        '案件已结案': 'Case closed',
        '没有符合条件的逾期案件': 'No delinquency cases match',
        '建案中…': 'Creating case…',
        '处理失败': 'Handling failed',

        /* ---- 运营 ---- */
        '批处理、报表、通知、文档、工作流、审计与参考数据（设计 15，管理员）':
            'Batch jobs, reports, notifications, documents, workflows, audit and reference data (design 15, admin)',
        '批处理': 'Batch',
        '报表': 'Reports',
        '通知': 'Notifications',
        '文档': 'Documents',
        '工作流': 'Workflows',
        '审计': 'Audit',
        '参考数据': 'Reference data',
        '运营支撑': 'Operations support',
        '七个 Tab 各自加载，单个扩展服务不可用不影响其它 Tab':
            'Each of the seven tabs loads independently; one unavailable service does not affect the others',
        '执行批处理作业': 'Run batch job',
        '作业': 'Job',
        '执行业务日期': 'Business date',
        '执行业务日期': 'Business date',
        '执行作业': 'Run job',
        '同一 jobCode + businessDate 可重复执行，每次运行都会保留记录。':
            'The same jobCode + businessDate may run repeatedly; every run keeps its own record.',
        '运行历史': 'Run history',
        '按开始时间倒序': 'Newest start time first',
        '作业编码': 'Job code',
        '目标服务': 'Target service',
        '没有作业定义': 'No job definitions',
        '请选择作业': 'Select a job',
        '作业已执行': 'Job executed',
        '还没有作业运行记录': 'No job runs yet',
        '生成报表': 'Generate report',
        '报表编码': 'Report code',
        '来源服务': 'Source service',
        '没有报表定义': 'No report definitions',
        '请选择报表': 'Select a report',
        '报表已生成': 'Report generated',
        '该运行没有结果行': 'This run has no result rows',
        '还没有报表运行记录': 'No report runs yet',
        '查看行': 'View rows',
        '结果行': 'Result rows',
        '维度': 'Dimension',
        '行数': 'Rows',
        '下游交易/总账不可用时运行置 FAILED 并返回 503。':
            'If the transaction/ledger services are unavailable the run is marked FAILED and 503 is returned.',
        '点击“查看行”读取结果明细': 'Select “View rows” to read the result details',
        '创建并发送通知': 'Create and send notification',
        '模板': 'Template',
        '接收方': 'Recipient',
        'payload（JSON 对象，模板变量 {{name}}）': 'payload (JSON object, template variable {{name}})',
        '创建通知': 'Create notification',
        'recipient 含 fail 时发送失败可复现（PENDING → FAILED）。':
            'A recipient containing fail reproduces a failed send (PENDING → FAILED).',
        '通知列表': 'Notifications',
        'PENDING 可发送': 'PENDING notifications can be sent',
        '渠道': 'Channel',
        '主题': 'Subject',
        '正文': 'Body',
        '没有通知模板': 'No notification templates',
        '发送中…': 'Sending…',
        '发送失败': 'Sending failed',
        '通知已发送': 'Notification sent',
        '通知发送失败': 'Notification failed to send',
        '暂无通知记录': 'No notifications yet',
        '请选择模板': 'Select a template',
        'payload 必须是 JSON 对象': 'payload must be a JSON object',
        'payload 解析失败': 'payload could not be parsed',
        '通知已创建': 'Notification created',
        '上传文档元数据与文本': 'Upload document metadata and text',
        '文档类型': 'Document type',
        '归属类型': 'Owner type',
        '归属 ID': 'Owner ID',
        '文件名': 'File name',
        '内容类型': 'Content type',
        '文本内容': 'Text content',
        '仅保存元数据与文本内容（contentType 需为 text/*）。':
            'Only metadata and text content are stored (contentType must be text/*).',
        '文档列表': 'Documents',
        '归属': 'Owner',
        '字节': 'Bytes',
        '文档内容': 'Document content',
        '查看内容': 'View content',
        '没有符合条件的文档': 'No documents match',
        '归档中…': 'Archiving…',
        '文档已归档': 'Document archived',
        '归档失败': 'Archiving failed',
        '文档已删除': 'Document deleted',
        '文档已上传': 'Document uploaded',
        '上传中…': 'Uploading…',
        '流程定义': 'Workflow definitions',
        '任务': 'Tasks',
        '处理人': 'Assignee',
        '定义编码': 'Definition code',
        '步骤': 'Step',
        '任务号': 'Task ID',
        '定义': 'Definition',
        '业务引用': 'Business reference',
        '意见': 'Comment',
        '决策时间': 'Decided at',
        '没有流程定义': 'No workflow definitions',
        '没有符合条件的任务': 'No tasks match',
        '任务已通过': 'Task approved',
        '任务已拒绝': 'Task rejected',
        '意见（可选，最多 500 字）': 'Comment (optional, max 500 characters)',
        '追加审计事件': 'Append audit event',
        '操作人 actor': 'Actor actor',
        '动作 action': 'Action action',
        'payload（JSON 文本，可选）': 'payload (JSON text, optional)',
        '追加事件': 'Append event',
        '事件检索': 'Event search',
        '事件号': 'Event ID',
        '动作': 'Action',
        '资源': 'Resource',
        '没有符合条件的事件': 'No events match',
        '审计事件只追加，不提供修改与删除。':
            'Audit events are append-only; they cannot be edited or deleted.',
        '追加中…': 'Appending…',
        '审计事件已追加': 'Audit event appended',
        '域 domain': 'Domain domain',
        '域': 'Domain',
        '编码': 'Code',
        '编码 code': 'Code code',
        '名称 label': 'Label label',
        '排序 sortOrder': 'Sort order sortOrder',
        '排序': 'Sort order',
        '新增条目': 'New entry',
        '同一域内 code 唯一，重复返回 409；修改仅支持 label / sortOrder / active。':
            'code is unique per domain and duplicates return 409; only label / sortOrder / active can be updated.',
        '该域暂无参考数据': 'No reference data in this domain',
        '参考数据已更新': 'Reference data updated',
        '参考数据已删除': 'Reference data deleted',
        '参考数据已新增': 'Reference data added',
        '更新失败': 'Update failed',
        '新名称 label': 'New label label',
        '排序 sortOrder（非负整数）': 'Sort order sortOrder (non-negative integer)',
        '是否启用（true/false）': 'Enabled (true/false)',
        '日元': 'Japanese yen',

        /* ---- 营销：main 高端返现 / 返现活动 ---- */
        '草稿': 'Draft',
        '进行中': 'Active',
        '已暂停': 'Paused',
        '已结束': 'Ended',
        '已入账': 'Posted',
        '入账失败': 'Posting failed',
        '高端卡分期消费返现：查询可参与活动、报名与我的返现（设计 17 §5）':
            'Premium card instalment cashback: view eligible campaigns, enroll and track my cashback (design 17 §5)',
        '客户与卡片': 'Customer & cards',
        '查询活动与返现': 'Search campaigns and cashback',
        '账户 00000000006 为白金卡种子账户；资格由产品等级、活动期与报名状态共同判定。':
            'Account 00000000006 is the platinum seed account; eligibility depends on product tier, campaign window and enrollment status.',
        '可参与活动': 'Available campaigns',
        '未报名且符合资格可立即报名': 'Eligible campaigns you have not joined can be enrolled right away',
        '我的返现': 'My cashback',
        '待入账 / 入账失败可重试': 'Pending or failed postings can be retried',
        '活动名称': 'Campaign name',
        '起止日期': 'Window',
        '期限': 'Term',
        '返现说明': 'Cashback terms',
        '资格 / 报名': 'Eligibility / enrollment',
        '符合资格': 'Eligible',
        '不符合资格': 'Not eligible',
        '已报名': 'Enrolled',
        '无需报名': 'No enrollment required',
        '未报名': 'Not enrolled',
        '立即报名': 'Enroll now',
        '报名中…': 'Enrolling…',
        '报名成功，活动期内达标消费将自动返现':
            'Enrolled — qualifying spend during the campaign earns cashback automatically',
        '报名失败': 'Enrollment failed',
        '当前没有可见的返现活动': 'No cashback campaigns are available right now',
        '暂无返现记录：活动期内达标消费入账后自动生成':
            'No cashback yet — records appear after qualifying spend posts during the campaign',
        '重试入账': 'Retry posting',
        '重试中…': 'Retrying…',
        '重试失败': 'Retry failed',
        '已重新提交入账': 'Posting resubmitted',
        '返现': 'Cashback',
        '满': 'Min spend',
        '单笔上限': 'Per-transaction cap',
        '累计上限': 'Customer cap',
        '活动': 'Campaign',
        '交易': 'Transaction',
        '消费金额': 'Spend',
        '返现金额': 'Cashback',
        '创建活动': 'Create campaign',
        '活动编码 code': 'Campaign code code',
        '活动名称 name': 'Campaign name name',
        '活动说明 description': 'Campaign description description',
        '开始日期 startDate': 'Start date startDate',
        '期限月数（1–24）': 'Term in months (1–24)',
        '适用产品等级 eligibleProductCodes': 'Eligible product tiers eligibleProductCodes',
        '单笔最低消费 minTransactionAmount': 'Minimum spend per transaction minTransactionAmount',
        '返现率 cashbackRate（0–1）': 'Cashback rate cashbackRate (0–1)',
        '单笔返现上限 maxCashbackPerTransaction':
            'Cashback cap per transaction maxCashbackPerTransaction',
        '客户累计上限 maxCashbackPerCustomer': 'Customer cumulative cap maxCashbackPerCustomer',
        '活动总预算 budget': 'Total budget budget',
        '报名要求 enrollmentRequired': 'Enrollment required enrollmentRequired',
        '客户需先报名才返现': 'Customers must enroll before earning cashback',
        '活动期内消费 5% 返现，单笔最高 30，累计最高 300':
            'Earn 5% cashback on spend during the campaign, up to 30 per transaction and 300 in total',
        '新建活动状态为 DRAFT，发布后才参与返现评估；结束日期由期限月数计算':
            'New campaigns start as DRAFT and only join cashback evaluation once published; the end date derives from the term',
        '（endDate = startDate + N 月 - 1 天）。': '(endDate = startDate + N months − 1 day).',
        '活动列表': 'Campaigns',
        '活动效果': 'Campaign performance',
        '报名数 / 奖励数 / 已发放 / 待入账 / 返现总额 / 预算使用率 / 转化率':
            'enrollments / rewards / granted / pending / cashback total / budget utilisation / conversion rate',
        '在列表中点击“查看效果”查看单个活动的转化情况':
            'Select “View performance” in the list to see one campaign’s conversion',
        '报名数': 'Enrollments',
        '奖励数': 'Rewards',
        '已发放': 'Granted',
        '待入账': 'Pending',
        '返现总额': 'Cashback total',
        '奖励 / 报名': 'rewards / enrollments',
        '活动期': 'Campaign window',
        '已发放 / 待入账为奖励笔数，返现总额为已入账金额；':
            'granted / pending count rewards while the cashback total is the posted amount;',
        '预算使用率 = 已预占预算 / 活动预算，转化率 = 奖励数 / 报名数。':
            'budget utilisation = reserved budget / campaign budget; conversion rate = rewards / enrollments.',
        '预算使用率': 'Budget utilisation',
        '预算使用': 'Budget used',
        '已消耗 / 预算': 'Consumed / budget',
        '返现率': 'Cashback rate',
        '发布': 'Publish',
        '暂停': 'Pause',
        '结束': 'End',
        '查看效果': 'View performance',
        '没有符合条件的活动，可先用上方表单创建':
            'No campaigns match — create one with the form above',
        '活动结束后不再参与返现评估，确认结束？':
            'An ended campaign stops participating in cashback evaluation. End it now?',
        '请至少选择一个适用产品等级': 'Select at least one eligible product tier',
        '白金卡 3 个月消费返现': 'Platinum card — 3 months of spend cashback',
        '创建、发布、暂停/结束高端卡返现活动并查看效果（设计 17 §4–§5，管理员）':
            'Create, publish, pause/end premium card cashback campaigns and review performance (design 17 §4–§5, admin)',
        /* ---- 表头/标签补充 ---- */
        '是否匹配': 'Matched',
        '运行': 'Run',
        '卡片': 'Cards',
        '接受': 'Accept',
        '清算': 'Settle',
        '限额号': 'Limit ID',
        '报价': 'Quote',
        '年利率': 'Annual rate',
        '提交时间': 'Submitted',
        '生成': 'Generate',
        '通知号': 'Notification ID',
        '发送时间': 'Sent at',
        '错误': 'Error',
        '文档号': 'Document ID',
        '条目号': 'Item ID',
        '版本': 'Version',
        '实体名': 'Entity name',
        '客户号（可选）': 'Customer ID (optional)',
        '暂无用户': 'No users',
        '暂无还款计划': 'No repayment schedule',
        '还没有通知记录': 'No notifications yet',

        /* ---- 服务未启动提示 ---- */
        '搜索服务（8108）可能未启动。': 'The search service (8108) may not be running.',
        '贷款服务（8095）可能未启动。': 'The loan service (8095) may not be running.',
        '贷款服务（8095）可能未启动，或贷款号不存在。':
            'The loan service (8095) may not be running, or the loan does not exist.',
        '欺诈服务（8096）可能未启动。': 'The fraud service (8096) may not be running.',
        'AML 服务（8097）可能未启动。': 'The AML service (8097) may not be running.',
        '争议服务（8098）可能未启动。': 'The dispute service (8098) may not be running.',
        '催收服务（8099）可能未启动。': 'The collections service (8099) may not be running.',
        '批处理服务（8100）可能未启动。': 'The batch service (8100) may not be running.',
        '报表服务（8101）可能未启动。': 'The reporting service (8101) may not be running.',
        '通知服务（8102）可能未启动。': 'The notification service (8102) may not be running.',
        '文档服务（8103）可能未启动。': 'The document service (8103) may not be running.',
        '工作流服务（8104）可能未启动。': 'The workflow service (8104) may not be running.',
        '审计服务（8105）可能未启动。': 'The audit service (8105) may not be running.',
        '参考数据服务（8106）可能未启动。': 'The reference-data service (8106) may not be running.',
        '营销服务（8087）可能未启动。': 'The marketing service (8087) may not be running.',
        '营销服务（8087）可能未启动，或活动不存在。':
            'The marketing service (8087) may not be running, or the campaign does not exist.',

        /* ---- 跨行段落（DOM 里是一个带换行的文本节点） ---- */
        '演示账号已预填，可直接登录。管理员可额外访问“用户管理”。 本环境为 POC，接口尚未强制校验 token，请勿暴露到公网。':
            'Demo accounts are pre-filled so you can sign in directly. Administrators also get User management. This is a POC environment: APIs do not enforce tokens yet, so do not expose it to the internet.',
        '本界面连接 IAM、客户、卡片、账户、交易、授权、总账、对账、支付、账单、限额与汇率等微服务， 全部数据来自遗留 COBOL 系统的归一化种子。':
            'This UI talks to the IAM, customer, card, account, transaction, authorization, ledger, reconciliation, payment, statement, limit and FX microservices, and all data comes from seeds normalized from the legacy COBOL system.',
        '本界面连接 IAM、客户、卡片、账户、交易、授权、总账、对账、支付、账单、限额与汇率等微服务， 全部数据来自遗留 COBOL 系统的归一化种子。 可以先看账户 00000000001（额度 2020、欠款 1940、可用 80），再试一笔账单支付、支付指令或换汇报价。':
            'This UI talks to the IAM, customer, card, account, transaction, authorization, ledger, reconciliation, payment, statement, limit and FX microservices, and all data comes from seeds normalized from the legacy COBOL system. Start with account 00000000001 (limit 2020, balance 1940, available 80), then try a bill payment, a payment order or an FX quote.',
        '提交后自动筛查：收款人非空且不在拒绝名单 → 已筛查（可接受）；否则直接拒绝。 同一幂等键重复提交返回原单据。':
            'Orders are screened automatically: a non-empty beneficiary that is not on the deny list becomes Screened (acceptable); otherwise the order is rejected immediately. Re-submitting the same idempotency key returns the original order.',
        '新建活动状态为 DRAFT，发布后才参与返现评估；结束日期由期限月数计算 （endDate = startDate + N 月 - 1 天）。':
            'New campaigns start as DRAFT and only join cashback evaluation once published; the end date derives from the term (endDate = startDate + N months − 1 day).',

        '页面不存在': 'Page not found',
        '没有找到该页面。': 'This page could not be found.',
        '返回首页': 'Back to home',

        /* ---- 旧营销分支（demo-b / demo）：活动中心 / 我的奖励 / 活动管理 ---- */
        '我的奖励': 'My rewards',
        '活动中心': 'Campaign center',
        '活动管理': 'Campaign management',
        '卡号（16 位）': 'Card number (16 digits)',
        '次数限制': 'Usage limit',
        '报名要求': 'Enrollment requirement',
        '结束时间': 'End time',
        '目标账户组': 'Target account group',
        '暂无权益推荐': 'No benefit recommendations',
        '可用额度 = 信用额度 − 当前欠款（授权时还会扣除待授权占用）。':
            'Available credit = credit limit − current balance (authorization holds are deducted as well).',
        '类型规则：首刷礼需固定返现额；类别返现需返现率或固定额，且预算 &gt; 0；积分倍数活动需倍数。 只有草稿状态可以编辑规则。':
            'Type rules: first-purchase offers need a fixed cashback amount; category cashback needs a rate or fixed amount with budget > 0; points-multiplier campaigns need a multiplier. Rules can only be edited while the campaign is a draft.',
        '本界面连接 IAM、客户、卡片、账户、交易、授权、营销、总账、对账、支付、账单、限额与汇率等微服务， 全部数据来自遗留 COBOL 系统的归一化种子。 可以先看账户 00000000001（额度 2020、欠款 1940、可用 80），再试一笔账单支付、支付指令或换汇报价。':
            'This UI talks to the IAM, customer, card, account, transaction, authorization, marketing, ledger, reconciliation, payment, statement, limit and FX microservices, and all data comes from seeds normalized from the legacy COBOL system. Start with account 00000000001 (limit 2020, balance 1940, available 80), then try a bill payment, a payment order or an FX quote.',
        '本界面连接 6 个微服务（IAM、客户、卡片、账户、交易、授权），全部数据来自遗留 COBOL 系统的归一化种子。 可以先看账户 00000000001（额度 2020、欠款 1940、可用 80），再试一笔账单支付或授权。':
            'This UI talks to six microservices (IAM, customer, card, account, transaction, authorization), and all data comes from seeds normalized from the legacy COBOL system. Start with account 00000000001 (limit 2020, balance 1940, available 80), then try a bill payment or an authorization.',
        '查看可参与的营销活动并报名': 'Browse campaigns you can join and enroll',
        '返现与积分记录，失败可重试': 'Cashback and points records; failed postings can be retried',
        '新建活动、发布、暂停与效果分析': 'Create campaigns, publish, pause and analyse performance',
        '首刷返现': 'First-purchase cashback',
        '类别返现': 'Category cashback',
        '积分倍数': 'Points multiplier',
        '活动未生效': 'Campaign not active yet',
        '账户不存在': 'Account not found',
        '账户已停用': 'Account inactive',
        '不在活动目标客群': 'Not in the campaign target segment',
        '交易类别不符': 'Transaction category does not match',
        '未达最低消费金额': 'Below the minimum spend',
        '需要先报名': 'Enrollment required first',
        '已达活动参与次数上限': 'Campaign participation limit reached',
        '已享受过首刷礼': 'First-purchase offer already used',
        '活动预算已用完': 'Campaign budget exhausted',
        '奖励金额为 0': 'Reward amount is zero',
        '该交易已评估过': 'This transaction has already been evaluated',
        '倍积分': '× points',
        '查看当前可参与的营销活动，报名后消费达标自动返现/积分':
            'Browse campaigns you can join; qualifying spend earns cashback/points automatically after enrolling',
        '当前没有可参与的活动': 'No campaigns available right now',
        '报名成功，消费达标后自动返现':
            'Enrolled — qualifying spend earns cashback automatically',
        '需报名': 'Enrollment required',
        '可参与': 'Available',
        '暂不可参与': 'Not available yet',
        '已满足参与条件，达标消费会自动发放奖励。':
            'You meet the participation criteria; qualifying spend earns rewards automatically.',
        '不限': 'Unlimited',
        '需要报名': 'Enrollment required',
        '无需报名': 'No enrollment required',
        '剩余预算': 'Remaining budget',
        '返现与积分发放记录；待入账的返现可重试':
            'Cashback and points postings; pending cashback can be retried',
        '暂无奖励记录': 'No reward records',
        '积分': 'Points',
        '返现已入账': 'Cashback posted',
        '创建、发布、暂停活动并查看效果（管理员）':
            'Create campaigns, publish, pause and review performance (admin)',
        '暂无活动': 'No campaigns',
        '新建活动': 'New campaign',
        '创建后为草稿状态，发布后才对用户可见':
            'Campaigns start as drafts and become visible only after publishing',
        '活动已创建（草稿）': 'Campaign created (draft)',
        '活动详情': 'Campaign details',
        '发布活动': 'Publish campaign',
        '恢复活动': 'Resume campaign',
        '结束活动': 'End campaign',
        '活动已发布，规则不可再编辑；如需调整请新建活动。':
            'The campaign is published and its rules can no longer be edited; create a new campaign to adjust them.',
        '活动已更新': 'Campaign updated',
        '全部账户': 'All accounts',
        '查询活动': 'Search campaigns',
        '查询奖励': 'Search rewards',
        '该活动需要报名，报名后消费达标即可获得奖励。':
            'This campaign requires enrollment; qualifying spend earns rewards once you enroll.',
        '奖励': 'Reward',
        '有效期': 'Valid period',
        '参与门槛': 'Eligibility threshold',
        '活动类型': 'Campaign type',
        '目标账户组（可选）': 'Target account group (optional)',
        '交易类别（可选）': 'Transaction category (optional)',
        '最低交易金额': 'Minimum transaction amount',
        '返现率（0.05 = 5%）': 'Cashback rate (0.05 = 5%)',
        '固定返现额': 'Fixed cashback amount',
        '单笔返现上限': 'Cashback cap per transaction',
        '每卡最多奖励次数': 'Max rewards per card',
        '预算（返现活动必填）': 'Budget (required for cashback campaigns)',
        '需要用户报名后参与': 'Customers must enroll to participate',
        '类型规则：首刷礼需固定返现额；类别返现需返现率或固定额，且预算 > 0；积分倍数活动需倍数。':
            'Type rules: first-purchase offers need a fixed cashback amount; category cashback needs a rate or fixed amount with budget > 0; points-multiplier campaigns need a multiplier.',
        '只有草稿状态可以编辑规则。': 'Rules can only be edited while the campaign is a draft.',
        '创建草稿': 'Create draft',
        '报名人数': 'Enrollments',
        '已发奖励': 'Rewards granted',
        '返现成本': 'Cashback cost',
        '奖励规则': 'Reward rules',
        '最低金额 / 每卡次数': 'Minimum amount / max per card',
        '预算 / 已消耗': 'Budget / consumed',
        '转化率': 'Conversion rate',
        '已发奖励 / 报名人数': 'rewards granted / enrollments',
        '仅草稿可编辑': 'Draft only',
        '编辑规则': 'Edit rules',
        '筛选': 'Filter',
        '操作成功': 'Operation succeeded',
        '活动已创建（草稿）': 'Campaign created (draft)',
        '如 A000000000': 'e.g. A000000000',
        '如 0001': 'e.g. 0001',
        '类别': 'Category'
    };

    /* 动词表：用于「发布中…」「发布失败」「警报已确认」这类动态句式 */
    const VERBS = {
        '发布': { ing: 'Publishing', done: 'Published', failed: 'Publishing failed' },
        '暂停': { ing: 'Pausing', done: 'Paused', failed: 'Pausing failed' },
        '结束': { ing: 'Ending', done: 'Ended', failed: 'Ending failed' },
        '恢复': { ing: 'Resuming', done: 'Resumed', failed: 'Resuming failed' },
        '冲正': { ing: 'Reversing', done: 'Reversed', failed: 'Reversal failed' },
        '释放': { ing: 'Releasing', done: 'Released', failed: 'Release failed' },
        '占用': { ing: 'Holding', done: 'Held', failed: 'Holding funds failed' },
        '调整': { ing: 'Adjusting', done: 'Adjusted', failed: 'Adjustment failed' },
        '审批': { ing: 'Approving', done: 'Approved', failed: 'Approval failed' },
        '放款': { ing: 'Disbursing', done: 'Disbursed', failed: 'Disbursement failed' },
        '还款': { ing: 'Repaying', done: 'Repaid', failed: 'Repayment failed' },
        '报名': { ing: 'Enrolling', done: 'Enrolled', failed: 'Enrollment failed' },
        '评分': { ing: 'Scoring', done: 'Scored', failed: 'Scoring failed' },
        '筛查': { ing: 'Screening', done: 'Screened', failed: 'Screening failed' },
        '登记': { ing: 'Saving', done: 'Saved', failed: 'Saving failed' },
        '发送': { ing: 'Sending', done: 'Sent', failed: 'Sending failed' },
        '归档': { ing: 'Archiving', done: 'Archived', failed: 'Archiving failed' },
        '上传': { ing: 'Uploading', done: 'Uploaded', failed: 'Uploading failed' },
        '创建': { ing: 'Creating', done: 'Created', failed: 'Creation failed' },
        '更新': { ing: 'Updating', done: 'Updated', failed: 'Update failed' },
        '删除': { ing: 'Deleting', done: 'Deleted', failed: 'Delete failed' },
        '加载': { ing: 'Loading', done: 'Loaded', failed: 'Loading failed' },
        '重试': { ing: 'Retrying', done: 'Retried', failed: 'Retry failed' },
        '处理': { ing: 'Processing', done: 'Processed', failed: 'Processing failed' },
        '换汇': { ing: 'Converting', done: 'Converted', failed: 'Conversion failed' },
        '关账': { ing: 'Closing the period', done: 'Period closed', failed: 'Closing the period failed' },
        '过账': { ing: 'Posting', done: 'Posted', failed: 'Posting failed' },
        '查询': { ing: 'Searching', done: 'Found', failed: 'Search failed' },
        '计算': { ing: 'Calculating', done: 'Calculated', failed: 'Calculation failed' }
    };

    const VERB_KEYS = Object.keys(VERBS).join('|');

    /* 复合句式规则（整段匹配，捕获组内的动态数据原样保留） */
    const RULES = [
        /* 分页 / 计数 */
        [/^共\s*([\d,]+)\s*条\s*·\s*第\s*([\d,]+)\s*\/\s*([\d,]+)\s*页$/,
            (m) => `${m[1]} records · page ${m[2]}/${m[3]}`],
        [/^共\s*([\d,]+)\s*项\s*·\s*第\s*([\d,]+)\s*\/\s*([\d,]+)\s*页$/,
            (m) => `${m[1]} items · page ${m[2]}/${m[3]}`],
        [/^第\s*([\d,]+)\s*\/\s*([\d,]+)\s*页$/, (m) => `Page ${m[1]}/${m[2]}`],
        [/^共\s*([\d,]+)\s*条\s*·\s*第\s*(.+)$/, (m) => `${m[1]} records · ${m[2]}`],
        [/^共\s*([\d,]+)\s*项$/, (m) => `${m[1]} items`],
        [/^共\s*([\d,]+)\s*条$/, (m) => `${m[1]} records`],
        [/^共\s*([\d,]+)\s*条$/, (m) => `${m[1]} records`],
        [/^共\s*([\d,]+)\s*条结果（最多返回\s*(\d+)\s*条）。$/,
            (m) => `${m[1]} results (at most ${m[2]} returned).`],
        [/^共\s*([\d,]+)\s*条结果$/, (m) => `${m[1]} results`],
        [/^已选择\s*([\d,]+)\s*项$/, (m) => `${m[1]} selected`],
        [/^([\d,]+)\s*张$/, (m) => `${m[1]} cards`],
        [/^([\d,]+)\s*条$/, (m) => `${m[1]} records`],
        [/^([\d,]+)\s*笔$/, (m) => `${m[1]} entries`],
        [/^([\d,]+)\s*期$/, (m) => `${m[1]} instalments`],
        [/^([\d,]+)\s*行$/, (m) => `${m[1]} rows`],
        [/^([\d,]+)\s*项$/, (m) => `${m[1]} items`],
        [/^([\d,]+)\s*次$/, (m) => `${m[1]} times`],
        [/^([\d,]+)\s*月$/, (m) => `${m[1]} months`],
        [/^([\d,]+)\s*天$/, (m) => `${m[1]} days`],
        [/^([\d,]+)\s*个$/, (m) => `${m[1]} items`],
        [/^([\d,]+)\s*个科目$/, (m) => `${m[1]} accounts`],
        [/^([\d,]+)\s*笔结果$/, (m) => `${m[1]} results`],
        [/^每卡\s*([\d,]+)\s*次$/, (m) => `Max ${m[1]} per card`],
        [/^单笔\s*≥\s*(.+)$/, (m) => `Per transaction ≥ ${m[1]}`],
        [/^额度\s*(?:[—－-]\s*(.*))?$/, (m) => (m[1] ? `Limit ${m[1]}` : 'Limit')],

        /* 欢迎语 / 身份 */
        [/^欢迎，(.+)（(管理员|普通用户)）$/, (m) => `Welcome, ${m[1]} (${DICT[m[2]]})`],
        [/^欢迎，(.+)$/, (m) => `Welcome, ${m[1]}`],
        [/^当前身份：(.+)$/, (m) => `Signed in as: ${translate(m[1])}`],

        /* 卡片 / 交易等带业务号的副标题 */
        [/^卡号\s+(\S+)$/, (m) => `Card ${m[1]}`],
        [/^交易号\s+(\S+)$/, (m) => `Transaction ${m[1]}`],
        [/^用户号\s+(\S+)$/, (m) => `User ${m[1]}`],
        [/^运行号\s+(\S+)$/, (m) => `Run ${m[1]}`],
        [/^期间\s+(\S+)\s*已关账$/, (m) => `Period ${m[1]} closed`],
        [/^（日\s*(\S+)\s*不可修改）$/, (m) => `(day ${m[1]} cannot be changed)`],
        [/^（第\s*(\d+)\s*期\s*(.*)）$/, (m) => `(instalment ${m[1]}${m[2] ? ', ' + m[2] : ''})`],
        [/^第\s*(\d+)\s*期$/, (m) => `Instalment ${m[1]}`],

        /* 动作 + 状态 */
        [new RegExp(`^(${VERB_KEYS})中…$`), (m) => `${VERBS[m[1]].ing}…`],
        [new RegExp(`^(${VERB_KEYS})成功$`), (m) => `${VERBS[m[1]].done} successfully`],
        [new RegExp(`^(${VERB_KEYS})失败$`), (m) => VERBS[m[1]].failed],
        [new RegExp(`^警报已(${VERB_KEYS}|确认|排除)$`), (m) => {
            if (m[1] === '确认') return 'Alert confirmed';
            if (m[1] === '排除') return 'Alert dismissed';
            return `Alert ${VERBS[m[1]].done.toLowerCase()}`;
        }],
        [new RegExp(`^任务已(${VERB_KEYS})$`), (m) => `Task ${VERBS[m[1]].done.toLowerCase()}`],
        [/^活动已创建：(.+)（DRAFT，发布后生效）$/, (m) => `Campaign created: ${m[1]} (DRAFT, effective after publishing)`],
        [/^活动期\s+(.+)\s*→\s*(.+)$/, (m) => `Campaign window ${m[1]} → ${m[2]}`],

        /* 提示 / toast（前缀 + 动态值） */
        [/^冻结已创建：(.+)$/, (m) => `Hold created: ${m[1]}`],
        [/^交易已创建：(.+)$/, (m) => `Transaction created: ${m[1]}`],
        [/^批次已过账：(.+)$/, (m) => `Batch posted: ${m[1]}`],
        [/^已冲正，冲正批次\s*(.+)$/, (m) => `Reversed, reversal batch ${m[1]}`],
        [/^分录已记账：(.+)$/, (m) => `Entry posted: ${m[1]}`],
        [/^对账已执行：(.+)$/, (m) => `Reconciliation executed: ${m[1]}`],
        [/^EOD 已执行：(.+)$/, (m) => `EOD executed: ${m[1]}`],
        [/^账单已支付：(.+)$/, (m) => `Statement paid: ${m[1]}`],
        [/^账单已生成：(.+)$/, (m) => `Statement generated: ${m[1]}`],
        [/^限额已创建：(.+)$/, (m) => `Limit created: ${m[1]}`],
        [/^已占用，剩余\s*(.+)$/, (m) => `Held, remaining ${m[1]}`],
        [/^已释放，剩余\s*(.+)$/, (m) => `Released, remaining ${m[1]}`],
        [/^额度已调整为\s*(.+)$/, (m) => `Limit adjusted to ${m[1]}`],
        [/^额度充足：剩余\s*(.+)$/, (m) => `Limit available: remaining ${m[1]}`],
        [/^汇率已保存：(.+)$/, (m) => `FX rate saved: ${m[1]}`],
        [/^换汇完成：(.+)$/, (m) => `Conversion completed: ${m[1]}`],
        [/^已放款：贷款号\s*(.+)$/, (m) => `Disbursed: loan ${m[1]}`],
        [/^已通过，贷款号\s*(.+)$/, (m) => `Approved, loan ${m[1]}`],
        [/^申请已提交：(.+)$/, (m) => `Application submitted: ${m[1]}`],
        [/^评分完成：(.+)$/, (m) => `Scoring completed: ${m[1]}`],
        [/^筛查完成：(.+)$/, (m) => `Screening completed: ${m[1]}`],
        [/^争议已开案：(.+)$/, (m) => `Dispute opened: ${m[1]}`],
        [/^逾期案件已建立：(.+)$/, (m) => `Delinquency case created: ${m[1]}`],
        [/^作业已执行：(.+)$/, (m) => `Job executed: ${m[1]}`],
        [/^报表已生成：(.+)$/, (m) => `Report generated: ${m[1]}`],
        [/^通知已创建：(.+)$/, (m) => `Notification created: ${m[1]}`],
        [/^文档已上传：(.+)$/, (m) => `Document uploaded: ${m[1]}`],
        [/^审计事件已追加：(.+)$/, (m) => `Audit event appended: ${m[1]}`],
        [/^参考数据已新增：(.+)$/, (m) => `Reference data added: ${m[1]}`],
        [/^已标记欺诈，卡号\s*(.+)$/, (m) => `Marked as fraud, card ${m[1]}`],
        [/^明细删除\s*(.+)$/, (m) => `Authorization details deleted: ${m[1]}`],
        [/^回退占用额度\s*(.+)$/, (m) => `credit released: ${m[1]}`],

        /* 确认框 / 输入框（动态编号原样保留） */
        [/^确认释放冻结\s*(.+?)？释放后可用额度立即归还。$/,
            (m) => `Release hold ${m[1]}? Available credit returns immediately.`],
        [/^确认将授权\s*(.+?)\s*标记为欺诈？该卡后续授权将被拒绝（5100）。$/,
            (m) => `Flag authorization ${m[1]} as fraud? Later authorizations on this card will be declined (5100).`],
        [/^确认清理\s*(.+?)\s*天前的授权明细？$/,
            (m) => `Purge authorization details older than ${m[1]} days?`],
        [/^确认冲正批次\s*(.+?)？将生成方向相反的分录并回退账户余额。$/,
            (m) => `Reverse batch ${m[1]}? Opposite entries will be created and the account balance rolled back.`],
        [/^确认关闭会计期间\s*(.+?)？关账后该期间不可再记账。$/,
            (m) => `Close accounting period ${m[1]}? No further postings will be accepted in it.`],
        [/^确认删除用户\s*(.+?)？$/, (m) => `Delete user ${m[1]}?`],
        [/^确认对支付\s*(.+?)\s*退汇？已清算的支付会先冲正过账。$/,
            (m) => `Return payment ${m[1]}? Settled payments are reversed first.`],
        [/^确认全额支付账单\s*(.+?)？将调用账户支付扣款。$/,
            (m) => `Pay statement ${m[1]} in full? The account payment will be debited.`],
        [/^确认删除文档\s*(.+?)？删除后无法恢复。$/,
            (m) => `Delete document ${m[1]}? This cannot be undone.`],
        [/^确认删除\s*(.+?)？$/, (m) => `Delete ${m[1]}?`],
        [/^确认(.+?)？$/, (m) => `${m[1]}?`],

        /* 搜索 / 列表尾部提示 */
        [/^(\S+)\s*暂不可用，相关区块可能为空，其余数据仍然可用。$/,
            (m) => `${m[1]} is temporarily unavailable; those sections may be empty while the rest still works.`],
        [/^(\S+)\s*暂不可用。$/, (m) => `${m[1]} is temporarily unavailable.`],

        /* 其它常见句式 */
        [/^当前没有可参与的活动$/, () => 'No campaigns available right now']
    ];

    /* 句读符号：用于被内联标签拆开的句子片段 */
    const PUNCT_LEAD = { '，': ', ', '。': '. ', '：': ': ', '、': ', ', '！': '! ', '？': '? ', '（': ' (', '）': ') ', '；': '; ', '“': ' "', '”': '" ' };
    const PUNCT_TRAIL = { '，': ',', '。': '.', '：': ':', '、': ',', '！': '!', '？': '?', '）': ')', '；': ';', '“': '"', '”': '"', '（': ' (' };
    const PUNCT_ONLY = /^[，。：、！？（）；“”\s]+$/;

    /* ------------------------------------------------------------------ *
     * 翻译内核
     * ------------------------------------------------------------------ */
    const hasHan = (text) => HAN.test(text);

    function lookup(text) {
        return Object.prototype.hasOwnProperty.call(DICT, text) ? DICT[text] : undefined;
    }

    function matchRule(text) {
        for (let i = 0; i < RULES.length; i += 1) {
            const rule = RULES[i];
            const m = rule[0].exec(text);
            if (m) {
                const out = rule[1](m);
                if (out !== null && out !== undefined) return out;
            }
        }
        return null;
    }

    /* "ADMIN001 · 管理员" / "类型 / 类别"：逐段翻译，动态数据段原样保留 */
    function translateComposite(text) {
        if (!/[/·]/.test(text)) return null;
        const parts = text.split(/(\s*[/·]\s*)/);
        let changed = false;
        const mapped = parts.map((part, index) => {
            if (index % 2 === 1) return part;
            const hit = lookup(part) || matchRule(part);
            if (hit !== undefined && hit !== null) {
                changed = true;
                return hit;
            }
            return part;
        });
        return changed ? mapped.join('').replace(/\s{2,}/g, ' ').trim() : null;
    }

    /* "， 金额" / "（日" 这类被标签拆开的句子片段 */
    function translatePunctuated(text, depth) {
        if (PUNCT_ONLY.test(text)) {
            return text.replace(/[，。：、！？（）；“”]/g, (ch) => PUNCT_LEAD[ch] || '').trim();
        }
        const lead = /^([，。：、！？（）；“”])\s*/.exec(text);
        const trail = /([，。：、！？（）；“”])\s*$/.exec(text);
        let start = 0;
        let end = text.length;
        let prefix = '';
        let suffix = '';
        if (lead) {
            prefix = PUNCT_LEAD[lead[1]] || '';
            start = lead[0].length;
        }
        if (trail && trail.index >= start) {
            suffix = PUNCT_TRAIL[trail[1]] || '';
            end = trail.index;
        }
        const middle = text.slice(start, end).trim();
        if (!middle || !hasHan(middle)) return null;
        const inner = depth > 0 ? translateCore(middle) : lookup(middle);
        if (inner === null || inner === undefined || inner === middle) return null;
        return `${prefix}${inner}${suffix}`.trim();
    }

    function translateCore(text) {
        const exact = lookup(text);
        if (exact !== undefined) return exact;
        const rule = matchRule(text);
        if (rule !== null) return rule;
        const composite = translateComposite(text);
        if (composite !== null) return composite;
        const punctuated = translatePunctuated(text, 0);
        if (punctuated !== null) return punctuated;
        /* 模板里跨行的段落：折成单空格后再试一次 */
        const flattened = text.replace(/\s+/g, ' ').trim();
        if (flattened !== text) {
            const byFlat = lookup(flattened);
            if (byFlat !== undefined) return byFlat;
        }
        return null;
    }

    /* 对外：中文 → 英文；无映射时原样返回（保留首尾空白） */
    function translate(input) {
        if (typeof input !== 'string' || input === '' || !hasHan(input)) return input;
        const m = /^(\s*)([\s\S]*?)(\s*)$/.exec(input);
        const core = m[2];
        if (!core) return input;
        const out = translateCore(core);
        if (out === null || out === undefined) return input;
        return m[1] + out + m[3];
    }

    /* ------------------------------------------------------------------ *
     * 语言状态
     * ------------------------------------------------------------------ */
    let locale = readLocale();

    function readLocale() {
        try {
            const stored = window.localStorage.getItem(STORAGE_KEY);
            return stored === EN || stored === ZH ? stored : DEFAULT_LOCALE;
        } catch (ignored) {
            return DEFAULT_LOCALE;
        }
    }

    function persist(next) {
        try {
            window.localStorage.setItem(STORAGE_KEY, next);
        } catch (ignored) {
            /* 隐私模式下 localStorage 不可用时忽略 */
        }
    }

    const isEnglish = () => locale === EN;

    /* ------------------------------------------------------------------ *
     * DOM 翻译（WeakMap 记录原文，切回中文时按记录恢复）
     * ------------------------------------------------------------------ */
    const textOriginals = new WeakMap();  // Text -> { zh, en }
    const attrOriginals = new WeakMap();  // Element -> { attr: { zh, en } }
    let titleOriginal = null;

    const roots = () => [document.getElementById('app'), document.getElementById('toasts')]
        .filter((el) => el !== null);

    function walk(root, visitText, visitElement) {
        const callbacks = {
            acceptNode(node) {
                if (node.nodeType === 1) {
                    if (node.hasAttribute(SKIP_ATTR)) return NodeFilter.FILTER_REJECT;
                    if (SKIP_TAGS[node.tagName]) return NodeFilter.FILTER_REJECT;
                    return NodeFilter.FILTER_ACCEPT;
                }
                return node.nodeType === 3 ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
            }
        };
        if (root.nodeType === 1 && !root.hasAttribute(SKIP_ATTR) && !SKIP_TAGS[root.tagName]) {
            visitElement(root);
        }
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT, callbacks);
        let node = walker.nextNode();
        while (node) {
            if (node.nodeType === 3) visitText(node);
            else visitElement(node);
            node = walker.nextNode();
        }
    }

    function translateTextNode(node) {
        const record = textOriginals.get(node);
        let zh = node.data;
        if (record && node.data === record.en) {
            zh = record.zh;
        }
        const en = translate(zh);
        if (en === zh) {
            if (record) textOriginals.delete(node);
            return;
        }
        textOriginals.set(node, { zh, en });
        if (node.data !== en) node.data = en;
    }

    function restoreTextNode(node) {
        const record = textOriginals.get(node);
        if (!record) return;
        if (node.data === record.en) node.data = record.zh;
        textOriginals.delete(node);
    }

    function translateElementAttributes(el) {
        for (const attr of TEXT_ATTRS) {
            if (!el.hasAttribute(attr)) continue;
            let store = attrOriginals.get(el);
            if (!store) {
                store = {};
                attrOriginals.set(el, store);
            }
            const record = store[attr];
            const current = el.getAttribute(attr);
            const zh = record && current === record.en ? record.zh : current;
            const en = translate(zh);
            if (en === zh) {
                if (record) delete store[attr];
                continue;
            }
            store[attr] = { zh, en };
            if (current !== en) el.setAttribute(attr, en);
        }
    }

    function restoreElementAttributes(el) {
        const store = attrOriginals.get(el);
        if (!store) return;
        Object.keys(store).forEach((attr) => {
            if (el.getAttribute(attr) === store[attr].en) el.setAttribute(attr, store[attr].zh);
        });
        attrOriginals.delete(el);
    }

    function translatePage() {
        roots().forEach((root) => walk(root, translateTextNode, translateElementAttributes));
        if (document.title && hasHan(document.title)) {
            if (titleOriginal === null) titleOriginal = document.title;
            document.title = translate(titleOriginal);
        }
    }

    function restorePage() {
        roots().forEach((root) => walk(root, restoreTextNode, restoreElementAttributes));
        if (titleOriginal !== null) {
            document.title = titleOriginal;
            titleOriginal = null;
        }
    }

    /* ------------------------------------------------------------------ *
     * 语言切换按钮（注入到 .user-chip 内、退出按钮之前）
     * ------------------------------------------------------------------ */
    const TOGGLE_ATTR = 'data-i18n-toggle';

    function ensureToggle() {
        const chip = document.querySelector('.user-chip');
        if (!chip) return;
        let button = chip.querySelector(`[${TOGGLE_ATTR}]`);
        if (!button) {
            button = document.createElement('button');
            button.type = 'button';
            button.className = 'btn ghost small i18n-toggle';
            button.setAttribute(TOGGLE_ATTR, '');
            button.setAttribute(SKIP_ATTR, '');
            const logout = chip.querySelector('[data-action="logout"]');
            if (logout) chip.insertBefore(button, logout);
            else chip.appendChild(button);
            button.addEventListener('click', (event) => {
                event.preventDefault();
                toggle();
            });
        }
        const label = isEnglish() ? '中文' : 'EN';
        if (button.textContent !== label) button.textContent = label;
        setAttributeIfChanged(button, 'title', isEnglish() ? '切换到中文' : 'Switch to English');
        setAttributeIfChanged(button, 'aria-label', isEnglish() ? '切换到中文' : 'Switch to English');
        setAttributeIfChanged(button, 'aria-pressed', isEnglish() ? 'true' : 'false');
    }

    /* 观察器监听属性变化，重复写入同一值会造成回调自激，所以先比较 */
    function setAttributeIfChanged(el, name, value) {
        if (el.getAttribute(name) !== value) el.setAttribute(name, value);
    }

    /* ------------------------------------------------------------------ *
     * 切换 / 应用语言
     * ------------------------------------------------------------------ */
    function applyLocale(next, notify) {
        locale = next;
        persist(next);
        document.documentElement.lang = next;
        if (isEnglish()) translatePage();
        else restorePage();
        ensureToggle();
        if (notify) {
            document.dispatchEvent(new CustomEvent('carddemo:localechange', { detail: { locale: next } }));
        }
    }

    function setLocale(next) {
        const target = next === EN ? EN : ZH;
        if (target === locale) {
            ensureToggle();
            return;
        }
        applyLocale(target, true);
    }

    function toggle() {
        setLocale(isEnglish() ? ZH : EN);
    }

    /* ------------------------------------------------------------------ *
     * 监听运行期渲染出来的节点
     * ------------------------------------------------------------------ */
    function handleMutations(mutations) {
        ensureToggle();
        if (!isEnglish()) return;
        for (const mutation of mutations) {
            if (mutation.type === 'characterData') {
                translateTextNode(mutation.target);
                continue;
            }
            if (mutation.type === 'attributes') {
                if (mutation.target.nodeType === 1) translateElementAttributes(mutation.target);
                continue;
            }
            for (const node of mutation.addedNodes) {
                if (node.nodeType === 3) translateTextNode(node);
                else if (node.nodeType === 1) walk(node, translateTextNode, translateElementAttributes);
            }
        }
    }

    function startObserver() {
        const observer = new MutationObserver(handleMutations);
        const targets = roots();
        const watched = targets.length ? targets : [document.body];
        watched.forEach((target) => observer.observe(target, {
            childList: true,
            subtree: true,
            characterData: true,
            attributes: true,
            attributeFilter: TEXT_ATTRS
        }));
        return observer;
    }

    /* ------------------------------------------------------------------ *
     * 后台接口：/api/ 请求自动带 Accept-Language
     * ------------------------------------------------------------------ */
    function acceptLanguage() {
        return isEnglish() ? 'en-US,en;q=0.9' : 'zh-CN,zh;q=0.9';
    }

    const nativeFetch = typeof window.fetch === 'function' ? window.fetch.bind(window) : null;

    function isApiRequest(url) {
        if (!url) return false;
        return url.indexOf('/api/') === 0 || /^[a-z][a-z0-9+.-]*:\/\/[^/]+\/api\//i.test(url);
    }

    function withLanguageHeader(input, init) {
        const target = typeof input === 'string' ? input : (input && input.url) || '';
        if (!isApiRequest(target)) return null;
        const options = Object.assign({}, init);
        let headers;
        try {
            headers = new window.Headers(options.headers || (input && input.headers) || undefined);
        } catch (ignored) {
            headers = null;
        }
        if (!headers) return null;
        if (!headers.has('Accept-Language')) headers.set('Accept-Language', acceptLanguage());
        options.headers = headers;
        return options;
    }

    if (nativeFetch) {
        window.fetch = function (input, init) {
            const patched = withLanguageHeader(input, init);
            return nativeFetch(input, patched || init);
        };
    }

    /* 原生确认框 / 输入框里也是中文文案，展示前先翻译 */
    const nativeConfirm = typeof window.confirm === 'function' ? window.confirm.bind(window) : null;
    if (nativeConfirm) {
        window.confirm = (message) => nativeConfirm(translate(message));
    }
    const nativePrompt = typeof window.prompt === 'function' ? window.prompt.bind(window) : null;
    if (nativePrompt) {
        window.prompt = (message, defaultValue) => nativePrompt(translate(message), defaultValue);
    }

    /* ------------------------------------------------------------------ *
     * 启动
     * ------------------------------------------------------------------ */
    function boot() {
        document.documentElement.lang = locale;
        if (isEnglish()) translatePage();
        ensureToggle();
        startObserver();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot, { once: true });
    } else {
        boot();
    }

    /* 供测试与调试使用 */
    window.CardDemoI18n = {
        getLocale: () => locale,
        setLocale,
        toggle,
        translate: (text) => translate(text),
        dictionarySize: () => Object.keys(DICT).length + RULES.length
    };
})();
