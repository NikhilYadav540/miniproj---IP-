// ===== Library frontend logic: talks to the Java backend using fetch() =====
let user = JSON.parse(localStorage.getItem("user") || "null");   // null = signed out (survives refresh)
let currentBooks = [];   // last loaded catalog, so Edit can reuse it without refetching
let activeCat = "all";   // selected category pill ("all" = no filter)

// Short names for the pop-ups declared in index.html
const bookDialog = document.getElementById("bookDialog");
const registerDialog = document.getElementById("registerDialog");
const $ = (id) => document.getElementById(id);

const PAGE_TITLES = {
  dashboard: "Dashboard", books: "Books", mybooks: "My Books",
  members: "Members", issue: "Borrowed Books", settings: "Settings",
};

/** Calls the backend. With `data` it sends a POST form, otherwise a GET.
 *  All backend routes live under /api (see Main.java), so the prefix is added here. */
async function call(path, data) {
  const options = data ? { method: "POST", body: new URLSearchParams(data) } : {};
  if (user && user.token) options.headers = { "X-Auth-Token": user.token };
  const res = await fetch("/api" + path, options);
  const body = await res.json();
  if (res.status === 401) {   // session gone (server restarted, or not signed in) -> login screen
    user = null;
    localStorage.removeItem("user");
    $("loginView").hidden = false;
    $("appView").hidden = true;
  }
  if (!res.ok) throw new Error(body.error || "Something went wrong");
  return body;
}

/** Stops text from the database being treated as HTML (security). */
const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

// ---------- Small helpers ----------
const todayLocal = () => new Date().toLocaleDateString("en-CA");   // local yyyy-mm-dd
const AVATAR_COLORS = ["#f59e0b", "#10b981", "#6366f1", "#ec4899", "#14b8a6"];
const initials = (name) => String(name).split(/\s+/).map((w) => w[0]).filter(Boolean).slice(0, 2).join("").toUpperCase();
function avatarColor(name) {
  let h = 0;
  for (const c of String(name)) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return AVATAR_COLORS[h % AVATAR_COLORS.length];
}
function setAvatar(el, name) { el.textContent = initials(name); el.style.background = avatarColor(name); }
/** Badge for a loan row: returned (+fine), overdue, or issued. */
function loanBadge(t) {
  if (t.return_date) return `<span class="badge gray">Returned${t.fine ? " · fine ₹" + t.fine : ""}</span>`;
  if (t.due_date < todayLocal()) return `<span class="badge red">Overdue</span>`;
  return `<span class="badge orange">Issued</span>`;
}

// ---------- Login / logout / register ----------
function enterApp() {
  $("loginView").hidden = true;
  $("appView").hidden = false;
  document.body.classList.toggle("member", user.role !== "admin");   // hide admin controls for members
  setAvatar($("sideAvatar"), user.name);
  setAvatar($("topAvatar"), user.name);
  $("sideName").textContent = user.name;
  $("sideRole").textContent = user.role === "admin" ? "Administrator" : "Member";
  showPage("dashboard");
}
async function login() {
  $("loginError").textContent = "";
  try {
    user = await call("/login", { email: $("email").value, password: $("password").value });
    localStorage.setItem("user", JSON.stringify(user));
    enterApp();
  } catch (e) { $("loginError").textContent = e.message; }
}
async function logout() {
  try { await call("/logout", {}); } catch (e) { /* already signed out is fine */ }
  localStorage.removeItem("user");
  location.reload();
}
function openRegister() {
  $("registerTitle").textContent = user && user.role === "admin" ? "Add Member" : "Create Account";
  $("registerError").textContent = "";
  $("rName").value = $("rEmail").value = $("rPassword").value = "";
  registerDialog.showModal();
}
async function register() {
  $("registerError").textContent = "";
  if (!$("rName").value.trim() || !$("rEmail").value.trim() || !$("rPassword").value)
    return $("registerError").textContent = "Please fill in your name, email and password.";
  if ($("rPassword").value.length < 4)
    return $("registerError").textContent = "Password must be at least 4 characters.";
  try {
    await call("/members/add", { name: $("rName").value, email: $("rEmail").value, password: $("rPassword").value });
    registerDialog.close();
    alert("Account created. You can sign in now.");
    if (user && user.role === "admin") loadMembers();
  } catch (e) { $("registerError").textContent = e.message; }
}
function openOverdue() {
  showPage(user && user.role === "admin" ? "issue" : "mybooks");
}
function setBell(count) {
  const n = Number(count) || 0;
  $("bellDot").hidden = n <= 0;
  $("bell").title = n ? n + " overdue book" + (n === 1 ? "" : "s") : "No overdue books";
}

// ---------- Page switching ----------
function showPage(name) {
  document.querySelectorAll(".page").forEach((p) => (p.hidden = p.id !== name));
  document.querySelectorAll("#sideNav button").forEach((b) => b.classList.toggle("active", b.dataset.page === name));
  $("pageTitle").textContent = PAGE_TITLES[name] || "Library";
  if (name === "dashboard") loadDashboard();
  if (name === "books") loadBooksPage();
  if (name === "mybooks") loadMyBooks();
  if (name === "issue") loadIssuePage();
  if (name === "members") loadMembers();
  if (name === "settings") loadSettings();
}
document.querySelectorAll("#sideNav button").forEach((b) => (b.onclick = () => showPage(b.dataset.page)));
// The top search bar jumps to the catalog and filters it
$("globalSearch").addEventListener("input", () => {
  if ($("appView").hidden) return;
  $("search").value = $("globalSearch").value;
  showPage("books");
});

// ---------- Dashboard ----------
const statCard = (icon, value, label, tone) =>
  `<div class="card stat"><div class="stat-icon ${tone}">${icon}</div><div><b>${value}</b><span>${label}</span></div></div>`;
async function loadDashboard() {
  try {
  const isAdmin = user.role === "admin";
  const [s, books, txns] = await Promise.all([
    call("/stats"), call("/books?q="), isAdmin ? call("/transactions") : call("/mybooks"),
  ]);
  setBell(s.overdue);
  $("stats").innerHTML =
    statCard("📚", s.books, "Total Books", "orange") +
    statCard("📕", s.issued, isAdmin ? "Borrowed" : "My Borrowed", "blue") +
    (isAdmin ? statCard("👥", s.members, "Members", "purple") : "") +
    statCard("✅", s.available, "Available", "green") +
    statCard("⏰", s.overdue, "Overdue", "red");

  const recent = [...books].sort((a, b) => b.book_id - a.book_id).slice(0, 5);
  $("recentTable").innerHTML =
    "<tr><th>Title</th><th>Author</th><th>Status</th></tr>" +
    (recent.length ? recent.map((b) => `
      <tr><td class="cell-main">${esc(b.title)}</td><td>${esc(b.author)}</td>
      <td>${b.available_copies > 0 ? '<span class="badge green">Available</span>' : '<span class="badge red">Borrowed</span>'}</td></tr>`).join("")
      : `<tr><td colspan="3" class="empty">No books yet.</td></tr>`);

  const open = txns.filter((t) => !t.return_date).slice(0, 5);
  $("dashTxnTable").innerHTML =
    "<tr><th>Book</th><th>Member</th><th>Due</th></tr>" +
    (open.length ? open.map((t) => `
      <tr><td class="cell-main">${esc(t.title)}</td><td>${esc(t.member || user.name)}</td>
      <td>${t.due_date} ${!t.return_date && t.due_date < todayLocal() ? '<span class="badge red">Overdue</span>' : ""}</td></tr>`).join("")
      : `<tr><td colspan="3" class="empty">Nothing borrowed right now.</td></tr>`);
  } catch (e) { if (user) console.warn(e); }
}

// ---------- Books ----------
async function loadBooksPage() {
  loadPills();
  loadBooks();
}
async function loadPills() {
  let cats = [];
  try { cats = await call("/categories"); } catch (e) { /* ignore, pills just stay empty */ }
  $("catPills").innerHTML =
    `<button data-cat="all" onclick="setCat('all')">All</button>` +
    cats.map((c) => `<button data-cat="${c.category_id}" onclick="setCat(${c.category_id})">${esc(c.name)}</button>`).join("");
  markPill();
}
function markPill() {
  document.querySelectorAll("#catPills button").forEach((b) => b.classList.toggle("on", b.dataset.cat === String(activeCat)));
}
function setCat(id) { activeCat = String(id); markPill(); loadBooks(); }

let searchTimer = null;
$("search").addEventListener("input", () => {           // small debounce so typing feels smooth
  clearTimeout(searchTimer);
  searchTimer = setTimeout(loadBooks, 150);
});

async function loadBooks() {
  const books = await call("/books?q=" + encodeURIComponent($("search").value.trim()));
  currentBooks = books;
  const shown = books.filter((b) => activeCat === "all" || String(b.category_id) === activeCat);
  const isAdmin = user.role === "admin";
  $("bookTable").innerHTML =
    "<tr><th>Title</th><th>Author</th><th>Category</th><th>Copies</th><th>Status</th><th></th></tr>" +
    (shown.length ? shown.map((b) => `
      <tr>
        <td class="cell-main">${esc(b.title)}</td><td>${esc(b.author)}</td><td>${esc(b.category)}</td>
        <td>${b.available_copies} of ${b.total_copies}</td>
        <td>${b.available_copies > 0 ? '<span class="badge green">Available</span>' : '<span class="badge red">Borrowed</span>'}</td>
        <td>${isAdmin ? `<button onclick="editBook(${b.book_id})">Edit</button>
                        <button class="danger" onclick="deleteBook(${b.book_id})">Delete</button>` : ""}</td>
      </tr>`).join("") : `<tr><td colspan="6" class="empty">No books match your search.</td></tr>`);
}

let editingId = null;   // null = adding a new book, number = editing that book
async function openBook(book) {
  const cats = await call("/categories");
  $("bCategory").innerHTML = cats.map((c) => `<option value="${c.category_id}">${esc(c.name)}</option>`).join("");
  editingId = book ? book.book_id : null;
  $("bookDialogTitle").textContent = book ? "Edit Book" : "Add Book";
  $("bTitle").value = book ? book.title : "";
  $("bAuthor").value = book ? book.author : "";
  $("bIsbn").value = book ? book.isbn || "" : "";
  $("bCategory").value = book ? book.category_id : cats[0].category_id;
  $("bCopies").value = book ? book.total_copies : 1;
  bookDialog.showModal();
}
/** Opens the pop-up filled with the book's details (reused from the last catalog load). */
function editBook(id) {
  openBook(currentBooks.find((b) => b.book_id === id));
}
async function saveBook() {
  const data = { title: $("bTitle").value, author: $("bAuthor").value, isbn: $("bIsbn").value,
                 category_id: $("bCategory").value, copies: $("bCopies").value };
  if (!data.title.trim() || !data.author.trim()) return alert("Title and author are required.");
  if (!Number(data.copies) || Number(data.copies) < 1) return alert("Copies must be a number of 1 or more.");
  try {
    if (editingId) data.book_id = editingId;
    await call(editingId ? "/books/update" : "/books/add", data);
    bookDialog.close();
    loadBooks();
  } catch (e) { alert(e.message); }
}
async function deleteBook(id) {
  if (!confirm("Delete this book?")) return;
  try { await call("/books/delete", { book_id: id }); loadBooks(); }
  catch (e) { alert(e.message); }
}

// ---------- My Books (the signed-in member's own loans) ----------
async function loadMyBooks() {
  const rows = await call("/mybooks");
  $("myBooksTable").innerHTML =
    "<tr><th>Title</th><th>Author</th><th>Issued</th><th>Due</th><th>Status</th></tr>" +
    (rows.length ? rows.map((t) => `
      <tr><td class="cell-main">${esc(t.title)}</td><td>${esc(t.author)}</td>
      <td>${t.issue_date}</td><td>${t.due_date}</td><td>${loanBadge(t)}</td></tr>`).join("")
      : `<tr><td colspan="5" class="empty">You haven't borrowed any books yet.</td></tr>`);
}

// ---------- Issue & return (admin) ----------
async function loadIssuePage() {
  const [books, members] = await Promise.all([call("/books?q="), call("/members")]);
  const avail = books.filter((b) => b.available_copies > 0);
  const mems = members.filter((m) => m.role === "member");
  $("issueBook").innerHTML = avail.length
    ? avail.map((b) => `<option value="${b.book_id}">${esc(b.title)}</option>`).join("")
    : `<option value="">No copies available</option>`;
  $("issueMember").innerHTML = mems.length
    ? mems.map((m) => `<option value="${m.user_id}">${esc(m.name)}</option>`).join("")
    : `<option value="">No members yet</option>`;
  loadTransactions();
}
async function issueBook() {
  if (!$("issueBook").value || !$("issueMember").value)
    return alert("Pick a book and a member.");
  try { await call("/issue", { book_id: $("issueBook").value, user_id: $("issueMember").value }); loadIssuePage(); }
  catch (e) { alert(e.message); }
}
async function loadTransactions() {
  const rows = await call("/transactions");
  $("txnTable").innerHTML =
    "<tr><th>Book</th><th>Member</th><th>Issued</th><th>Due</th><th>Status</th><th></th></tr>" +
    (rows.length ? rows.map((t) => `
      <tr><td class="cell-main">${esc(t.title)}</td><td>${esc(t.member)}</td>
      <td>${t.issue_date}</td><td>${t.due_date}</td><td>${loanBadge(t)}</td>
      <td>${t.return_date ? "" : `<button onclick="returnBook(${t.txn_id})">Return</button>`}</td></tr>`).join("")
      : `<tr><td colspan="6" class="empty">No loans yet.</td></tr>`);
}
async function returnBook(id) {
  if (!confirm("Mark this book as returned?")) return;
  try { await call("/return", { txn_id: id }); loadIssuePage(); } catch (e) { alert(e.message); }
}

// ---------- Members (admin) ----------
async function loadMembers() {
  const rows = await call("/members");
  $("memberTable").innerHTML =
    "<tr><th></th><th>Name</th><th>Email</th><th>Role</th><th>Joined</th></tr>" +
    rows.map((m) => `
      <tr><td><span class="avatar sm" style="background:${avatarColor(m.name)}">${initials(m.name)}</span></td>
      <td class="cell-main">${esc(m.name)}</td><td>${esc(m.email)}</td>
      <td><span class="badge ${m.role === "admin" ? "orange" : "gray"}">${esc(m.role)}</span></td>
      <td>${m.joined_on}</td></tr>`).join("");
}

// ---------- Settings ----------
function loadSettings() {
  setAvatar($("setAvatar"), user.name);
  $("setName").textContent = user.name;
  $("setEmail").textContent = user.email;
  $("setRole").textContent = user.role === "admin" ? "Administrator" : "Member";
  $("setRole").className = "badge " + (user.role === "admin" ? "orange" : "gray");
}

// ---------- Still signed in from before? (e.g. after a page refresh) ----------
if (user) enterApp();
