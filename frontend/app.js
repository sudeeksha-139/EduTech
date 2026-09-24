const API_ROOT =
  window.EDUSUPPORT_API_ROOT ||
  (location.hostname === "localhost" && location.port === "4173"
    ? "http://localhost:8080/api"
    : "/api");
const state = {
  session: null,
  chartInstances: [],
  dashboard: null,
  tickets: [],
  adminFilters: {},
};

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

function saveSession(session) {
  state.session = session;
}
function clearSession() {
  state.session = null;
}
function rolePath() {
  return state.session?.role === "ADMIN"
    ? "admin"
    : state.session?.role === "STAFF"
      ? "staff"
      : "student";
}
function roleLabel(role = state.session?.role) {
  return (
    { STUDENT: "Student", STAFF: "Support staff", ADMIN: "Administrator" }[
      role
    ] || "Support user"
  );
}
function initials(name = "User") {
  return name
    .split(/\s+/)
    .map((part) => part[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}
function escapeHtml(value = "") {
  return String(value).replace(
    /[&<>'"]/g,
    (char) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[
        char
      ],
  );
}
function formatDate(value) {
  if (!value) return "Not available";
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(value));
}
function formatDateTime(value) {
  if (!value) return "Not available";
  return new Intl.DateTimeFormat(undefined, {
    month: "short",
    day: "numeric",
    hour: "numeric",
    minute: "2-digit",
  }).format(new Date(value));
}
function formatDuration(seconds) {
  if (seconds == null) return "Overdue";
  const total = Math.max(0, Math.floor(seconds));
  const days = Math.floor(total / 86400);
  const hours = Math.floor((total % 86400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  if (days) return `${days}d ${hours}h`;
  if (hours) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}
function formatAge(seconds) {
  if (seconds == null) return "Not available";
  return formatDuration(seconds);
}
function statusClass(status = "") {
  return `badge-status-${status.toLowerCase().replaceAll("_", "-")}`;
}
function priorityClass(priority = "") {
  return `badge-priority-${priority.toLowerCase()}`;
}
function statusLabel(status = "") {
  return status.replaceAll("_", " ");
}
function slaClass(sla) {
  return sla.overdue ? "danger" : sla.approachingSla ? "warning" : "";
}
function showToast(message, type = "success") {
  const toast = document.createElement("div");
  toast.className = `app-toast ${type}`;
  toast.innerHTML = `<i class="bi ${type === "error" ? "bi-exclamation-circle" : "bi-check-circle"}"></i><span>${escapeHtml(message)}</span><button aria-label="Dismiss"><i class="bi bi-x"></i></button>`;
  $("#toastRegion").appendChild(toast);
  toast.querySelector("button").onclick = () => toast.remove();
  setTimeout(() => toast.remove(), 4800);
}
function showError(message, retry) {
  return `<div class="error-state"><i class="bi bi-cloud-slash"></i><h3>Something went wrong</h3><p>${escapeHtml(message || "We couldn't load this view. Please try again.")}</p><button class="btn btn-brand btn-sm" data-retry="${retry}"><i class="bi bi-arrow-clockwise me-1"></i>Retry</button></div>`;
}
function emptyState(icon, title, copy, action = "") {
  return `<div class="empty-state"><div class="empty-icon"><i class="bi ${icon}"></i></div><h3>${title}</h3><p>${copy}</p>${action}</div>`;
}
function skeletons(count = 4) {
  return `<div class="kpi-grid">${Array.from({ length: count }, () => `<div class="skeleton skeleton-kpi"></div>`).join("")}</div><div class="panel p-3">${Array.from({ length: 5 }, () => `<div class="skeleton skeleton-row"></div>`).join("")}</div>`;
}

async function api(path, options = {}) {
  const headers = {
    ...(options.body ? { "Content-Type": "application/json" } : {}),
    ...(options.headers || {}),
  };
  if (state.session?.token)
    headers.Authorization = `Bearer ${state.session.token}`;
  const response = await fetch(`${API_ROOT}${path}`, { ...options, headers });
  if (response.status === 401) {
    clearSession();
    renderLogin();
    throw new Error("Your session has expired. Please sign in again.");
  }
  if (!response.ok) {
    let message = `Request failed (${response.status})`;
    try {
      const body = await response.json();
      message = body.message || message;
    } catch {}
    throw new Error(message);
  }
  return response.status === 204 ? null : response.json();
}

function renderLogin() {
  $("#loginView").classList.remove("d-none");
  $("#appView").classList.add("d-none");
}
function renderShell() {
  $("#loginView").classList.add("d-none");
  $("#appView").classList.remove("d-none");
  $("#userAvatar").textContent = initials(state.session.name);
  $("#userName").textContent = state.session.name;
  $("#userRole").textContent = roleLabel();
  const role = state.session.role;
  $$(".admin-only").forEach((el) =>
    el.classList.toggle("d-none", role !== "ADMIN"),
  );
  $$(".student-staff-only").forEach((el) =>
    el.classList.toggle("d-none", role === "ADMIN"),
  );
  $("#ticketsNavLabel").textContent =
    role === "STAFF"
      ? "Assigned tickets"
      : role === "ADMIN"
        ? "All tickets"
        : "My tickets";
  route();
}
function route() {
  if (!state.session) return renderLogin();
  const routeValue = location.hash.replace(/^#/, "") || "dashboard";
  const [page, id] = routeValue.split("/");
  $$(".sidebar-link").forEach((link) =>
    link.classList.toggle(
      "active",
      link.dataset.route === page ||
        (page === "ticket" && link.dataset.route === "tickets"),
    ),
  );
  const titles = {
    dashboard: "Dashboard",
    tickets: "Tickets",
    pending: "Pending actions",
    create: "Create ticket",
    reports: "Management reports",
    profile: "Profile",
    ticket: "Ticket details",
  };
  $("#pageTitle").textContent = titles[page] || "Dashboard";
  $("#topbarKicker").textContent = roleLabel();
  if (page === "ticket" && id) return renderTicketDetail(id);
  if (page === "tickets") return renderTicketsPage();
  if (page === "create") return renderCreatePage();
  if (page === "pending") return renderPendingPage();
  if (page === "reports") return renderDashboard(true);
  if (page === "profile") return renderProfile();
  return renderDashboard(false);
}

function dashboardEndpoint() {
  const query =
    state.session.role === "ADMIN"
      ? new URLSearchParams(
          Object.entries(state.adminFilters).filter(([, value]) => value),
        ).toString()
      : "";
  return `/dashboard/${rolePath()}${query ? `?${query}` : ""}`;
}
async function renderDashboard(reportsMode = false) {
  const content = $("#pageContent");
  content.innerHTML = skeletons();
  try {
    const dashboard = await api(dashboardEndpoint());
    state.dashboard = dashboard;
    updateNavCounts(dashboard);
    content.innerHTML = dashboardMarkup(dashboard, reportsMode);
    if (state.session.role === "ADMIN") {
      content.insertAdjacentHTML("afterbegin", adminFilterMarkup(dashboard));
    }
    if (reportsMode || state.session.role === "ADMIN") drawCharts(dashboard);
    bindDashboardActions();
  } catch (error) {
    content.innerHTML = showError(
      error.message,
      reportsMode ? "reports" : "dashboard",
    );
    bindRetry();
  }
}
function updateNavCounts(dashboard) {
  const pending = dashboard.pendingActionCount || 0;
  const el = $("#pendingNavCount");
  el.textContent = pending;
  el.classList.toggle("d-none", pending === 0);
  const count = $("#ticketsNavCount");
  count.textContent = dashboard.totalTickets || 0;
  count.classList.toggle("d-none", !dashboard.totalTickets);
}
function adminFilterMarkup(dashboard) {
  const selected = (key) => escapeHtml(state.adminFilters[key] || "");
  const options = (values, key) =>
    values
      .map(
        (value) =>
          `<option value="${escapeHtml(value)}" ${selected(key) === value ? "selected" : ""}>${statusLabel(value)}</option>`,
      )
      .join("");
  const categories = Object.keys(dashboard.ticketsByCategory || {}).filter(
    (value) => value !== "UNCATEGORIZED",
  );
  const staff = dashboard.staffWorkload || [];
  return `<div id="adminFilterBar" class="filter-bar"><div><span class="eyebrow mb-1">Management filters</span><strong class="d-block">Focus the live queue</strong></div><select class="filter-control" data-admin-filter="status"><option value="">All statuses</option>${options(["NEW", "ASSIGNED", "IN_PROGRESS", "PENDING_STUDENT", "RESOLVED", "CLOSED", "REOPENED"], "status")}</select><select class="filter-control" data-admin-filter="priority"><option value="">All priorities</option>${options(["CRITICAL", "HIGH", "MEDIUM", "LOW"], "priority")}</select><select class="filter-control" data-admin-filter="category"><option value="">All categories</option>${options(categories, "category")}</select><select class="filter-control" data-admin-filter="assignedStaffId"><option value="">All staff</option>${staff.map((item) => `<option value="${item.staffId}" ${selected("assignedStaffId") === String(item.staffId) ? "selected" : ""}>${escapeHtml(item.staffName || "Staff member")}</option>`).join("")}</select><select class="filter-control" data-admin-filter="overdue"><option value="">All SLA states</option><option value="true" ${selected("overdue") === "true" ? "selected" : ""}>Overdue</option><option value="false" ${selected("overdue") === "false" ? "selected" : ""}>Within SLA</option></select><select class="filter-control" data-admin-filter="escalated"><option value="">Escalation</option><option value="true" ${selected("escalated") === "true" ? "selected" : ""}>Escalated</option><option value="false" ${selected("escalated") === "false" ? "selected" : ""}>Not escalated</option></select><button id="clearAdminFilters" class="btn btn-light btn-sm" type="button"><i class="bi bi-x-circle me-1"></i>Clear</button></div>`;
}
function kpi(label, value, icon, support, tone = "") {
  return `<article class="kpi-card ${tone}"><div class="kpi-top"><span>${label}</span><span class="kpi-icon"><i class="bi ${icon}"></i></span></div><strong class="kpi-number">${value ?? 0}</strong><p class="kpi-support">${support}</p></article>`;
}
function dashboardMarkup(d, reportsMode) {
  const isAdmin = state.session.role === "ADMIN";
  const action = d.pendingActionCount
    ? `<a href="#pending" class="btn btn-brand btn-sm">Review pending actions <i class="bi bi-arrow-up-right ms-1"></i></a>`
    : "";
  const title = reportsMode
    ? "Management reports"
    : `Good ${new Date().getHours() < 12 ? "morning" : "afternoon"}, ${escapeHtml(state.session.name.split(" ")[0])}`;
  const subtitle = reportsMode
    ? "A clear view of institutional support performance."
    : isAdmin
      ? "Here is what needs your team’s attention today."
      : state.session.role === "STAFF"
        ? "Your assigned work, SLA risk, and next actions in one view."
        : "Track every request and know exactly what happens next.";
  const kpis = isAdmin
    ? [
        kpi(
          "Total tickets",
          d.totalTickets,
          "bi-inbox",
          "Across the institution",
        ),
        kpi(
          "Active work",
          d.activeTickets,
          "bi-activity",
          "Currently being handled",
          "kpi-blue",
        ),
        kpi(
          "SLA at risk",
          d.approachingSlaTickets,
          "bi-hourglass-split",
          "Approaching the deadline",
          "kpi-warning",
        ),
        kpi(
          "Overdue",
          d.overdueTickets,
          "bi-exclamation-octagon",
          "Needs management attention",
          "kpi-danger",
        ),
        kpi(
          "Escalated",
          d.escalatedTickets,
          "bi-arrow-up-right-circle",
          "Raised for review",
          "kpi-purple",
        ),
        kpi(
          "Pending action",
          d.pendingActionCount,
          "bi-person-raised-hand",
          "Waiting on students",
          "kpi-warning",
        ),
      ]
    : state.session.role === "STAFF"
      ? [
          kpi(
            "Assigned tickets",
            d.assignedCount,
            "bi-inbox",
            "Your owned workload",
          ),
          kpi(
            "In progress",
            d.inProgressTickets,
            "bi-play-circle",
            "Currently moving",
            "kpi-blue",
          ),
          kpi(
            "SLA at risk",
            d.approachingSlaTickets,
            "bi-hourglass-split",
            "Approaching the deadline",
            "kpi-warning",
          ),
          kpi(
            "Overdue",
            d.overdueTickets,
            "bi-exclamation-octagon",
            "Needs action",
            "kpi-danger",
          ),
          kpi(
            "Pending student",
            d.pendingStudentTickets,
            "bi-person-raised-hand",
            "Waiting for information",
            "kpi-warning",
          ),
        ]
      : [
          kpi(
            "Total tickets",
            d.totalTickets,
            "bi-inbox",
            "All requests submitted",
          ),
          kpi(
            "Open tickets",
            d.activeTickets,
            "bi-activity",
            "Currently being handled",
            "kpi-blue",
          ),
          kpi(
            "Pending action",
            d.pendingActionCount,
            "bi-person-raised-hand",
            "Need your response",
            "kpi-warning",
          ),
          kpi(
            "Resolved",
            d.resolvedTickets,
            "bi-check2-circle",
            "Successfully completed",
            "kpi-purple",
          ),
          kpi(
            "Overdue",
            d.overdueTickets,
            "bi-exclamation-octagon",
            "Needs attention",
            "kpi-danger",
          ),
        ];
  return `<div class="welcome-strip"><div><span class="eyebrow">${isAdmin ? "Institutional pulse" : "Your support space"}</span><h2>${title}</h2><p>${subtitle}</p></div>${action}</div><div class="kpi-grid">${kpis.join("")}</div><div class="content-grid"><section class="panel"><div class="panel-header"><div><h3>${isAdmin ? "Recent institution activity" : "Recent tickets"}</h3><p>Live data from the support queue</p></div><a href="#tickets" class="panel-link">View all <i class="bi bi-arrow-right"></i></a></div>${ticketTable(d.recentTickets)}</section><section class="panel"><div class="panel-header"><div><h3>${isAdmin ? "Staff workload" : "SLA pulse"}</h3><p>${isAdmin ? "Assigned ticket distribution" : "Your current service health"}</p></div></div>${isAdmin ? workloadMarkup(d.staffWorkload) : slaPulseMarkup(d)}</section></div><div class="content-grid"><section class="panel"><div class="panel-header"><div><h3>${d.pendingActionCount ? "Requires your attention" : "Activity status"}</h3><p>${d.pendingActionCount ? "Tickets waiting for the next action" : "No pending action is waiting"}</p></div></div>${ticketTable(d.ticketsRequiringStudentAction, true)}</section>${isAdmin ? `<section class="panel"><div class="panel-header"><div><h3>Ticket mix</h3><p>Status and priority at a glance</p></div></div><div class="chart-grid"><div class="chart-wrap"><canvas id="statusChart" aria-label="Tickets by status"></canvas></div><div class="chart-wrap"><canvas id="priorityChart" aria-label="Tickets by priority"></canvas></div></div></section>` : `<section class="panel"><div class="panel-header"><div><h3>Keep moving</h3><p>Useful next steps</p></div></div><div class="quick-actions"><a href="#tickets" class="quick-action"><i class="bi bi-search"></i><span>Browse your tickets<small>Review updates and SLA</small></span><i class="bi bi-arrow-up-right"></i></a>${state.session.role === "STUDENT" ? `<a href="#create" class="quick-action"><i class="bi bi-plus-circle"></i><span>Start a new request<small>Tell us what you need</small></span><i class="bi bi-arrow-up-right"></i></a>` : ""}</div></section>`}</div>`;
}
function ticketTable(tickets = [], compact = false) {
  if (!tickets.length)
    return emptyState(
      compact ? "bi-check2-circle" : "bi-inbox",
      compact ? "Nothing waiting here" : "No tickets yet",
      compact
        ? "You’re all caught up."
        : "Your support queue will appear here when requests are created.",
      state.session.role === "STUDENT" && !compact
        ? `<a href="#create" class="btn btn-brand btn-sm mt-3">Create your first ticket</a>`
        : "",
    );
  return `<div class="ticket-table-wrap"><table class="ticket-table"><thead><tr><th>Ticket</th><th>Status</th><th>Priority</th><th>Owner</th><th>SLA</th></tr></thead><tbody>${tickets.map(ticketRow).join("")}</tbody></table></div>`;
}
function ticketRow(ticket) {
  const sla = ticket.sla || ticket;
  const remaining = sla.remainingSeconds;
  const label = sla.overdue ? "Overdue" : `${formatDuration(remaining)} left`;
  const cls = slaClass(sla);
  return `<tr data-ticket-id="${ticket.id}"><td><span class="ticket-number">${escapeHtml(ticket.ticketNumber || `#${ticket.id}`)}</span><span class="ticket-subject">${escapeHtml(ticket.subject || "Untitled request")}</span><span class="ticket-date">${formatDate(ticket.createdAt)}</span></td><td><span class="badge-soft ${statusClass(ticket.status)}">${statusLabel(ticket.status)}</span></td><td><span class="badge-soft ${priorityClass(ticket.priority)}">${ticket.priority}</span></td><td>${escapeHtml(ticket.assignedStaffName || "Unassigned")}</td><td><span class="sla-chip ${cls}"><i class="bi ${sla.overdue ? "bi-exclamation-circle" : "bi-clock"}"></i>${label}</span></td></tr>`;
}
function workloadMarkup(workload = []) {
  if (!workload.length)
    return emptyState(
      "bi-people",
      "No assigned workload",
      "Assigned staff capacity will appear here.",
    );
  const max = Math.max(...workload.map((item) => item.assignedTickets), 1);
  return `<div class="workload-list">${workload.map((item) => `<div class="workload-row"><div class="workload-person"><div class="avatar avatar-blue">${initials(item.staffName || "Staff")}</div><span>${escapeHtml(item.staffName || "Staff member")}<small>${item.assignedTickets} assigned ticket${item.assignedTickets === 1 ? "" : "s"}</small></span></div><strong>${item.assignedTickets}</strong><div class="load-bar"><span style="width:${Math.max(8, (item.assignedTickets / max) * 100)}%"></span></div></div>`).join("")}</div>`;
}
function slaPulseMarkup(d) {
  const total = Math.max(1, d.activeTickets || 1);
  const safe = Math.max(0, total - d.overdueTickets - d.approachingSlaTickets);
  return `<div class="sla-card"><div class="sla-card-head"><h4>Service health</h4><span class="badge-soft ${d.overdueTickets ? "badge-priority-critical" : "badge-status-resolved"}">${d.overdueTickets ? "Needs attention" : "On track"}</span></div><span class="sla-big">${safe} of ${total} <small class="text-muted fs-6">on track</small></span><div class="sla-progress ${d.overdueTickets ? "danger" : d.approachingSlaTickets ? "warning" : ""}"><span style="width:${(safe / total) * 100}%"></span></div><div class="sla-meta"><span>${d.overdueTickets} overdue</span><span>${d.approachingSlaTickets} approaching</span></div></div>`;
}

async function renderTicketsPage() {
  const content = $("#pageContent");
  content.innerHTML = skeletons(3);
  try {
    const endpoint =
      state.session.role === "ADMIN"
        ? "/tickets"
        : state.session.role === "STAFF"
          ? "/tickets/assigned-to-me"
          : "/tickets/mine";
    state.tickets = await api(endpoint);
    content.innerHTML = `<div class="page-heading"><div><span class="eyebrow">${roleLabel()} workspace</span><h2>${state.session.role === "ADMIN" ? "All tickets" : state.session.role === "STAFF" ? "Assigned tickets" : "My tickets"}</h2><p>Every request, owner, and SLA signal in one place.</p></div>${state.session.role !== "ADMIN" ? `<a class="btn btn-brand" href="#create"><i class="bi bi-plus-lg me-2"></i>New ticket</a>` : ""}</div><div class="filter-bar"><div class="input-shell"><i class="bi bi-search"></i><input id="ticketSearch" class="form-control" placeholder="Search by subject or ticket number"></div><select id="statusFilter" class="filter-control"><option value="">All statuses</option>${["NEW", "ASSIGNED", "IN_PROGRESS", "PENDING_STUDENT", "RESOLVED", "CLOSED", "REOPENED"].map((value) => `<option value="${value}">${statusLabel(value)}</option>`).join("")}</select><select id="priorityFilter" class="filter-control"><option value="">All priorities</option>${["CRITICAL", "HIGH", "MEDIUM", "LOW"].map((value) => `<option>${value}</option>`).join("")}</select></div><section id="ticketsPanel" class="panel">${ticketTable(state.tickets)}</section>`;
    bindTicketFilters();
  } catch (error) {
    content.innerHTML = showError(error.message, "tickets");
    bindRetry();
  }
}
function bindTicketFilters() {
  const render = () => {
    const term = ($("#ticketSearch").value || "").toLowerCase();
    const status = $("#statusFilter").value;
    const priority = $("#priorityFilter").value;
    const tickets = state.tickets.filter(
      (ticket) =>
        (!term ||
          `${ticket.ticketNumber} ${ticket.subject}`
            .toLowerCase()
            .includes(term)) &&
        (!status || ticket.status === status) &&
        (!priority || ticket.priority === priority),
    );
    $("#ticketsPanel").innerHTML = ticketTable(tickets);
    bindTicketRows($("#ticketsPanel"));
  };
  $("#ticketSearch").addEventListener("input", render);
  $("#statusFilter").addEventListener("change", render);
  $("#priorityFilter").addEventListener("change", render);
  bindTicketRows($("#ticketsPanel"));
}
function bindTicketRows(root = document) {
  $$("[data-ticket-id]", root).forEach((row) =>
    row.addEventListener("click", () => {
      location.hash = `#ticket/${row.dataset.ticketId}`;
    }),
  );
}

async function renderPendingPage() {
  const content = $("#pageContent");
  content.innerHTML = skeletons(2);
  try {
    const d = await api(
      state.session.role === "ADMIN"
        ? "/dashboard/admin?pendingStudent=true"
        : dashboardEndpoint(),
    );
    content.innerHTML = `<div class="page-heading"><div><span class="eyebrow">Next action</span><h2>Pending actions</h2><p>Tickets that are waiting for information or action from students.</p></div></div><section class="panel">${ticketTable(d.ticketsRequiringStudentAction, true)}</section>`;
    bindTicketRows(content);
  } catch (error) {
    content.innerHTML = showError(error.message, "pending");
    bindRetry();
  }
}

async function renderTicketDetail(id) {
  const content = $("#pageContent");
  content.innerHTML = skeletons(2);
  try {
    const [ticket, comments, history] = await Promise.all([
      api(`/tickets/${id}`),
      api(`/tickets/${id}/comments`),
      api(`/tickets/${id}/history`),
    ]);
    content.innerHTML = detailMarkup(ticket, comments, history);
    bindDetailActions(ticket);
  } catch (error) {
    content.innerHTML = showError(error.message, `ticket/${id}`);
    bindRetry();
  }
}
function detailMarkup(ticket, comments, history) {
  const sla = ticket.sla || {};
  const pending = ticket.pendingStudentAction;
  const canComment =
    state.session.role === "ADMIN" ||
    state.session.role === "STUDENT" ||
    (state.session.role === "STAFF" &&
      ticket.assignedStaffId === state.session.userId);
  return `<div class="detail-header"><a href="#tickets" class="back-link"><i class="bi bi-arrow-left"></i>Back to tickets</a><div class="d-flex flex-wrap align-items-center gap-2 mb-2"><span class="eyebrow mb-0">${escapeHtml(ticket.ticketNumber || `Ticket #${ticket.id}`)}</span><span class="badge-soft ${statusClass(ticket.status)}">${statusLabel(ticket.status)}</span><span class="badge-soft ${priorityClass(ticket.priority)}">${ticket.priority}</span></div><h2>${escapeHtml(ticket.subject)}</h2><div class="detail-subline"><span><i class="bi bi-calendar3 me-1"></i>Created ${formatDate(ticket.createdAt)}</span><span>·</span><span>${escapeHtml(ticket.category?.name || ticket.category || "General support")}</span></div></div><div class="detail-layout"><div class="detail-main"><section class="detail-card"><h3>Request details</h3><p class="description-text">${escapeHtml(ticket.description || "No description provided.")}</p></section>${pending ? `<div class="pending-callout"><i class="bi bi-person-raised-hand"></i><span><strong>Student action required.</strong><br>${escapeHtml(ticket.pendingActionDescription || "Please provide the information requested by support.")}</span></div>` : ""}<section class="detail-card"><div class="d-flex justify-content-between align-items-center mb-3"><h3 class="mb-0">Conversation</h3><span class="text-muted small">${comments.length} message${comments.length === 1 ? "" : "s"}</span></div><div class="comment-list">${comments.length ? comments.map(commentMarkup).join("") : emptyState("bi-chat-left-text", "No conversation yet", "Responses and updates will appear here.")}</div>${canComment ? `<form id="commentForm" class="comment-form mt-4"><label class="form-label" for="commentContent">${pending && state.session.role === "STUDENT" ? "Your response" : "Add a comment"}</label><textarea id="commentContent" class="form-control" maxlength="2000" required placeholder="Write a clear update..."></textarea><div class="d-flex justify-content-between align-items-center mt-2"><span id="commentCount" class="character-count">0 / 2000</span><button class="btn btn-brand btn-sm" type="submit"><i class="bi bi-send me-1"></i>${pending && state.session.role === "STUDENT" ? "Send response" : "Add comment"}</button></div></form>` : ""}</section><section class="detail-card"><h3>Activity timeline</h3>${history.length ? `<div class="timeline">${history.map(historyMarkup).join("")}</div>` : emptyState("bi-clock-history", "No activity yet", "Ticket activity will appear here.")}</section></div><aside class="detail-side"><section class="detail-card"><div class="d-flex justify-content-between align-items-start"><h3>SLA health</h3><span class="sla-chip ${slaClass(sla)}">${sla.overdue ? "Overdue" : sla.approachingSla ? "Approaching" : "On track"}</span></div><span class="sla-big">${sla.overdue ? "Deadline passed" : `${formatDuration(sla.remainingSeconds)} remaining`}</span><div class="sla-progress ${slaClass(sla)}"><span style="width:${Math.min(100, Math.max(4, (sla.ageSeconds / Math.max(1, sla.ageSeconds + (sla.remainingSeconds || 0))) * 100))}%"></span></div><div class="sla-meta"><span>Age ${formatAge(sla.ageSeconds)}</span><span>Due ${formatDateTime(sla.dueAt)}</span></div></section><section class="detail-card"><h3>Ticket information</h3><div class="detail-meta-list"><div class="detail-meta-row"><span>Priority</span><strong><span class="badge-soft ${priorityClass(ticket.priority)}">${ticket.priority}</span></strong></div><div class="detail-meta-row"><span>Status</span><strong>${statusLabel(ticket.status)}</strong></div><div class="detail-meta-row"><span>Assigned to</span><strong>${escapeHtml(ticket.assignedStaffName || "Unassigned")}</strong></div><div class="detail-meta-row"><span>Created</span><strong>${formatDate(ticket.createdAt)}</strong></div><div class="detail-meta-row"><span>Escalation</span><strong>${ticket.escalated ? "Escalated" : "Not escalated"}</strong></div></div></section>${actionControls(ticket)}</aside></div>`;
}
function commentMarkup(comment) {
  return `<div class="comment-item"><div class="avatar avatar-brand">${initials(comment.authorName)}</div><div class="comment-body"><div class="comment-meta"><strong>${escapeHtml(comment.authorName || "Support user")}</strong><span>${formatDateTime(comment.createdAt)}</span></div><p>${escapeHtml(comment.content)}</p></div></div>`;
}
function historyMarkup(item) {
  const labels = {
    CREATED: "Ticket created",
    ASSIGNED: "Ticket assigned",
    REASSIGNED: "Ticket reassigned",
    STATUS_CHANGED: "Status changed",
    COMMENTED: "Comment added",
    ESCALATED: "Ticket escalated",
    DE_ESCALATED: "Ticket de-escalated",
  };
  return `<div class="timeline-item"><span class="timeline-dot"></span><div class="timeline-copy"><strong>${labels[item.action] || statusLabel(item.action)}</strong><small>${formatDateTime(item.createdAt)}</small>${item.beforeValue || item.afterValue ? `<p>${escapeHtml(item.beforeValue || "")} ${item.afterValue ? `→ ${escapeHtml(item.afterValue)}` : ""}</p>` : ""}</div></div>`;
}
function actionControls(ticket) {
  if (state.session.role === "STUDENT" && ticket.pendingStudentAction)
    return `<section class="detail-card"><h3>Next action</h3><p class="text-muted small">Your response will move this ticket back into active processing.</p></section>`;
  if (state.session.role === "STAFF" && ticket.status === "IN_PROGRESS")
    return `<section class="detail-card"><h3>Support action</h3><form id="pendingForm"><label class="form-label" for="pendingReason">Request student information</label><textarea id="pendingReason" class="form-control" maxlength="2000" required placeholder="Explain what the student needs to provide..."></textarea><button class="btn btn-outline-warning btn-sm w-100 mt-3" type="submit"><i class="bi bi-person-raised-hand me-1"></i>Move to pending</button></form></section>`;
  if (state.session.role === "ADMIN")
    return `<section class="detail-card"><h3>Management action</h3>${ticket.escalated ? `<button class="btn btn-outline-secondary btn-sm w-100" id="deEscalateButton"><i class="bi bi-arrow-down-right me-1"></i>De-escalate ticket</button>` : `<button class="btn btn-outline-danger btn-sm w-100" id="escalateButton"><i class="bi bi-arrow-up-right me-1"></i>Escalate ticket</button>`}</section>`;
  return "";
}

async function renderCreatePage() {
  $("#pageContent").innerHTML =
    `<div class="page-heading"><div><span class="eyebrow">Start a request</span><h2>Create a support ticket</h2><p>Give the support team enough context to take the right next step.</p></div></div><div class="create-layout"><section class="detail-card"><form id="createTicketForm"><div class="mb-4"><label class="form-label" for="ticketSubject">Subject</label><input id="ticketSubject" class="form-control" required maxlength="160" placeholder="What do you need help with?"></div><div class="mb-4"><label class="form-label" for="ticketDescription">Description</label><textarea id="ticketDescription" class="form-control" required maxlength="4000" rows="7" placeholder="Share dates, reference numbers, and any relevant context..."></textarea><div id="descriptionCount" class="character-count mt-1">0 / 4000</div></div><div class="mb-4"><label class="form-label">How urgent is this request?</label><div class="priority-grid">${["LOW", "MEDIUM", "HIGH", "CRITICAL"].map((value, index) => `<div class="priority-option"><input id="priority-${value}" type="radio" name="priority" value="${value}" ${index === 1 ? "checked" : ""}><label for="priority-${value}"><span class="d-flex align-items-center gap-2"><span class="priority-dot ${value.toLowerCase()}"></span><strong>${value}</strong></span><small>${{ LOW: "General request", MEDIUM: "Normal impact", HIGH: "Time-sensitive", CRITICAL: "Severe impact" }[value]}</small></label></div>`).join("")}</div></div><div class="create-note"><i class="bi bi-info-circle"></i><span>Category and ownership are confirmed during triage so your request reaches the right support team.</span></div><div class="d-flex justify-content-end gap-2 mt-4"><a href="#dashboard" class="btn btn-light">Cancel</a><button class="btn btn-brand" type="submit"><i class="bi bi-send me-2"></i>Submit ticket</button></div></form></section></div>`;
  bindCreateForm();
}
function bindCreateForm() {
  const description = $("#ticketDescription");
  description.addEventListener("input", () => {
    $("#descriptionCount").textContent = `${description.value.length} / 4000`;
  });
  $("#createTicketForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = event.target.querySelector("button[type=submit]");
    button.disabled = true;
    try {
      const created = await api("/tickets", {
        method: "POST",
        body: JSON.stringify({
          subject: $("#ticketSubject").value.trim(),
          description: description.value.trim(),
          priority: $("input[name=priority]:checked").value,
          studentId: state.session.userId,
        }),
      });
      showToast("Ticket created successfully");
      location.hash = `#ticket/${created.id}`;
    } catch (error) {
      showToast(error.message, "error");
      button.disabled = false;
    }
  });
}
function renderProfile() {
  $("#pageContent").innerHTML =
    `<div class="page-heading"><div><span class="eyebrow">Your account</span><h2>Profile</h2><p>Your EduSupport access and support identity.</p></div></div><section class="detail-card profile-card"><div class="profile-hero"><div class="avatar avatar-brand profile-avatar">${initials(state.session.name)}</div><div><h3>${escapeHtml(state.session.name)}</h3><p>${escapeHtml(state.session.email || "")}</p></div></div><div class="detail-meta-list mt-4"><div class="detail-meta-row"><span>Role</span><strong>${roleLabel()}</strong></div><div class="detail-meta-row"><span>Access level</span><strong>${state.session.role === "ADMIN" ? "Institution-wide visibility" : state.session.role === "STAFF" ? "Assigned ticket ownership" : "Personal ticket support"}</strong></div></div></section>`;
}

async function loadRouteFromRetry(routeValue) {
  location.hash = `#${routeValue}`;
  route();
}
function bindRetry() {
  $("[data-retry]")?.addEventListener("click", (event) =>
    loadRouteFromRetry(event.currentTarget.dataset.retry),
  );
}
function bindDashboardActions() {
  bindTicketRows();

  $$("[data-admin-filter]").forEach((control) => {
    control.addEventListener("change", () => {
      state.adminFilters[control.dataset.adminFilter] = control.value;
      renderDashboard(true);
    });
  });

  $("#clearAdminFilters")?.addEventListener("click", () => {
    state.adminFilters = {};
    renderDashboard(true);
  });
}

function bindDetailActions(ticket) {
  const comment = $("#commentContent");
  if (comment) {
    comment.addEventListener("input", () => {
      $("#commentCount").textContent = `${comment.value.length} / 2000`;
    });
    $("#commentForm").addEventListener("submit", async (event) => {
      event.preventDefault();
      const pendingResponse =
        ticket.pendingStudentAction && state.session.role === "STUDENT";
      try {
        await api(
          pendingResponse
            ? `/tickets/${ticket.id}/student-response`
            : `/tickets/${ticket.id}/comments`,
          { method: "POST", body: JSON.stringify({ content: comment.value }) },
        );
        showToast(
          pendingResponse ? "Student response submitted" : "Comment added",
        );
        renderTicketDetail(ticket.id);
      } catch (error) {
        showToast(error.message, "error");
      }
    });
  }
  const pendingForm = $("#pendingForm");
  if (pendingForm)
    pendingForm.addEventListener("submit", async (event) => {
      event.preventDefault();
      try {
        await api(`/tickets/${ticket.id}/status`, {
          method: "PUT",
          body: JSON.stringify({
            status: "PENDING_STUDENT",
            reason: $("#pendingReason").value,
          }),
        });
        showToast("Ticket is now waiting for student action");
        renderTicketDetail(ticket.id);
      } catch (error) {
        showToast(error.message, "error");
      }
    });
  const escalate = $("#escalateButton");
  if (escalate)
    escalate.onclick = () =>
      requestConfirmation(
        "Escalate this ticket?",
        "This will flag it for management attention.",
        async () => {
          try {
            await api(`/tickets/${ticket.id}/escalation`, {
              method: "POST",
              body: JSON.stringify({
                reason: "Management review requested from ticket detail",
              }),
            });
            showToast("Ticket escalated");
            renderTicketDetail(ticket.id);
          } catch (error) {
            showToast(error.message, "error");
          }
        },
      );
  const deEscalate = $("#deEscalateButton");
  if (deEscalate)
    deEscalate.onclick = () =>
      requestConfirmation(
        "De-escalate this ticket?",
        "The escalation flag will be removed from the active queue.",
        async () => {
          try {
            await api(`/tickets/${ticket.id}/escalation`, {
              method: "DELETE",
              body: JSON.stringify({ reason: "Management review complete" }),
            });
            showToast("Ticket de-escalated");
            renderTicketDetail(ticket.id);
          } catch (error) {
            showToast(error.message, "error");
          }
        },
      );
}
function requestConfirmation(title, message, action) {
  $("#confirmTitle").textContent = title;
  $("#confirmMessage").textContent = message;
  const modal = bootstrap.Modal.getOrCreateInstance($("#confirmModal"));
  const button = $("#confirmAction");
  button.onclick = async () => {
    button.disabled = true;
    modal.hide();
    await action();
    button.disabled = false;
  };
  modal.show();
}
function drawCharts(dashboard) {
  state.chartInstances.forEach((chart) => chart.destroy());
  state.chartInstances = [];
  if (!window.Chart) return;
  const palette = [
    "#1f7a78",
    "#417ab2",
    "#e7a33d",
    "#7864b4",
    "#c85861",
    "#85a6ad",
  ];
  const makeChart = (id, values, labels, type) => {
    const canvas = document.getElementById(id);
    if (!canvas) return;
    state.chartInstances.push(
      new Chart(canvas, {
        type,
        data: {
          labels,
          datasets: [
            {
              data: labels.map((label) => values[label] || 0),
              backgroundColor: palette,
              borderColor: type === "line" ? "#1f7a78" : "#fff",
              borderWidth: type === "doughnut" ? 3 : 2,
              borderRadius: type === "bar" ? 6 : 0,
              tension: 0.35,
            },
          ],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: {
              display: type === "doughnut",
              position: "bottom",
              labels: {
                boxWidth: 9,
                usePointStyle: true,
                font: { family: "DM Sans", size: 10 },
              },
            },
            tooltip: {
              padding: 10,
              backgroundColor: "#102a43",
              displayColors: false,
            },
          },
          scales:
            type === "bar"
              ? {
                  y: {
                    beginAtZero: true,
                    ticks: {
                      precision: 0,
                      font: { family: "DM Sans", size: 10 },
                    },
                    grid: { color: "#edf2f5" },
                  },
                  x: {
                    ticks: { font: { family: "DM Sans", size: 9 } },
                    grid: { display: false },
                  },
                }
              : {},
        },
      }),
    );
  };
  makeChart(
    "statusChart",
    dashboard.ticketsByStatus || {},
    Object.keys(dashboard.ticketsByStatus || {}),
    "doughnut",
  );
  makeChart(
    "priorityChart",
    dashboard.ticketsByPriority || {},
    Object.keys(dashboard.ticketsByPriority || {}),
    "bar",
  );
}

$("#loginForm").addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = $("#loginEmail").value.trim();
  const password = $("#loginPassword").value;
  const error = $("#loginError");
  const submit = $("#loginSubmit");
  error.classList.add("d-none");
  if (!email || !password) {
    error.textContent = "Enter your email and password to continue.";
    error.classList.remove("d-none");
    return;
  }
  submit.disabled = true;
  $(".button-label", submit).textContent = "Signing in...";
  $(".spinner-border", submit).classList.remove("d-none");
  try {
    const result = await api("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    saveSession(result);
    showToast("Login successful");
    renderShell();
  } catch (loginError) {
    error.textContent =
      "We couldn't sign you in. Check your details and try again.";
    error.classList.remove("d-none");
  } finally {
    submit.disabled = false;
    $(".button-label", submit).textContent = "Sign in";
    $(".spinner-border", submit).classList.add("d-none");
  }
});
$("#passwordToggle").addEventListener("click", () => {
  const input = $("#loginPassword");
  const visible = input.type === "text";
  input.type = visible ? "password" : "text";
  $("#passwordToggle i").className =
    `bi ${visible ? "bi-eye" : "bi-eye-slash"}`;
  $("#passwordToggle").setAttribute(
    "aria-label",
    visible ? "Show password" : "Hide password",
  );
});
$("#logoutButton").addEventListener("click", () =>
  requestConfirmation(
    "Sign out of EduSupport?",
    "Your session will be closed on this device.",
    () => {
      clearSession();
      location.hash = "";
      renderLogin();
      showToast("Signed out");
    },
  ),
);
$("#refreshButton").addEventListener("click", () => route());
$("#sidebarToggle").addEventListener("click", () => {
  $("#sidebar").classList.add("open");
  $("#sidebarBackdrop").classList.remove("d-none");
});
$("#sidebarClose").addEventListener("click", closeSidebar);
$("#sidebarBackdrop").addEventListener("click", closeSidebar);
$$(".sidebar-link").forEach((link) =>
  link.addEventListener("click", closeSidebar),
);
function closeSidebar() {
  $("#sidebar").classList.remove("open");
  $("#sidebarBackdrop").classList.add("d-none");
}
window.addEventListener("hashchange", route);
window.addEventListener("load", () =>
  state.session ? renderShell() : renderLogin(),
);
