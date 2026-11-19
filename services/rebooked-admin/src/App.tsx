import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Badge, Button, Card, CardHeader,
  Input, Select, Spinner, Tab, TabList, Textarea, Toast, Toaster, ToastTitle, Tooltip,
  useId, useToastController,
} from "@fluentui/react-components";
import {
  Add20Regular, ArrowLeft20Regular, ArrowSync20Regular, BookOpen24Regular, Checkmark20Regular,
  ChevronRight20Regular, Clock20Regular, Delete20Regular, Dismiss20Regular, DocumentBulletList24Regular,
  Edit20Regular, Home24Regular, People24Regular, Person24Regular, Search20Regular, ShieldCheckmark24Regular,
  SignOut20Regular, Sparkle24Regular, Tag24Regular, WeatherMoon20Regular, WeatherSunny20Regular,
} from "@fluentui/react-icons";
import { Link, Navigate, Outlet, Route, Routes, useLocation, useNavigate, useParams } from "react-router-dom";
import { adminApi, isMockMode, mockAccount } from "./api";
import type { Category, FeaturedSet, ReviewItem } from "./api";

type LoaderState<T> = { data: T | null; loading: boolean; error: string | null };
function useResource<T>(load: () => Promise<T>, dependencies: unknown[] = []) {
  const [state, setState] = useState<LoaderState<T>>({ data: null, loading: true, error: null });
  const reload = useCallback(async () => {
    setState((current) => ({ ...current, loading: true, error: null }));
    try {
      const data = await load();
      setState({ data, loading: false, error: null });
    } catch (error) {
      setState((current) => ({ ...current, loading: false, error: error instanceof Error ? error.message : "Something went wrong." }));
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencies);
  useEffect(() => { void reload(); }, [reload]);
  return { ...state, reload, setData: (data: T) => setState({ data, loading: false, error: null }) };
}

function useNotice() {
  const toasterId = useId("admin-toaster");
  const { dispatchToast } = useToastController(toasterId);
  const notice = (message: string, intent: "success" | "error" = "success") => {
    dispatchToast(<Toast><ToastTitle>{message}</ToastTitle></Toast>, { intent, timeout: 3500 });
  };
  return { toasterId, notice };
}

function RequireAuth() {
  return localStorage.getItem("rebooked-admin-session") ? <Outlet /> : <Navigate to="/login" replace />;
}

const navigation = [
  { to: "/authors", label: "Authors", icon: <People24Regular />, group: "Catalogue" },
  { to: "/reviews", label: "Review queue", icon: <DocumentBulletList24Regular />, group: "Catalogue" },
  { to: "/imports", label: "Imports", icon: <ArrowSync20Regular />, group: "Operations" },
  { to: "/homepage", label: "Homepage", icon: <Home24Regular />, group: "Operations" },
  { to: "/categories", label: "Categories", icon: <Tag24Regular />, group: "Operations" },
];

function AppShell() {
  const location = useLocation();
  const navigate = useNavigate();
  const [dark, setDark] = useState(localStorage.getItem("rebooked-admin-theme") === "dark");
  const [menuOpen, setMenuOpen] = useState(false);
  const userEmail = localStorage.getItem("rebooked-admin-email") ?? mockAccount.email;
  const active = location.pathname.startsWith("/authors/") ? "/authors" : location.pathname;
  const toggleTheme = () => {
    const next = !dark;
    setDark(next);
    localStorage.setItem("rebooked-admin-theme", next ? "dark" : "light");
    document.documentElement.dataset.theme = next ? "dark" : "light";
  };
  const signOut = () => {
    localStorage.removeItem("rebooked-admin-session");
    localStorage.removeItem("rebooked-admin-token");
    navigate("/login");
  };
  return (
    <div className={`app-shell${dark ? " dark" : ""}`}>
      <header className="topbar">
        <Button className="mobile-menu" appearance="subtle" aria-label="Toggle navigation" onClick={() => setMenuOpen(!menuOpen)}>☰</Button>
        <Link to="/authors" className="brand"><span className="brand-mark">R</span><span>rebooked<span className="brand-admin"> / admin</span></span></Link>
        <div className="topbar-right">
          <span className="signed-in"><span className="online-dot" /> Super Admin <span className="topbar-divider">·</span> Signed in</span>
          <Tooltip content={dark ? "Switch to light mode" : "Switch to dark mode"} relationship="label">
            <Button appearance="subtle" icon={dark ? <WeatherSunny20Regular /> : <WeatherMoon20Regular />} aria-label="Toggle dark mode" onClick={toggleTheme} />
          </Tooltip>
          <Button appearance="subtle" icon={<SignOut20Regular />} onClick={signOut}>Sign out</Button>
          <div className="profile-chip"><span className="profile-avatar">SA</span><span>{userEmail}</span></div>
        </div>
      </header>
      <div className="shell-body">
        <aside className={`sidebar${menuOpen ? " sidebar-open" : ""}`}>
          <div className="sidebar-heading">WORKSPACE</div>
          {["Catalogue", "Operations"].map((group) => (
            <div key={group}>
              <div className="sidebar-group">{group}</div>
              {navigation.filter((item) => item.group === group).map((item) => (
                <Link key={item.to} to={item.to} onClick={() => setMenuOpen(false)} className={`nav-item${active === item.to ? " active" : ""}`}>
                  <span className="nav-icon">{item.icon}</span><span>{item.label}</span>
                  {active === item.to && <span className="nav-active-mark" />}
                </Link>
              ))}
            </div>
          ))}
          <div className="sidebar-footer"><ShieldCheckmark24Regular /><span>Protected admin area</span></div>
        </aside>
        {menuOpen && <button className="sidebar-scrim" aria-label="Close navigation" onClick={() => setMenuOpen(false)} />}
        <main className="main-content"><Outlet /></main>
      </div>
    </div>
  );
}

function PageHeader({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: React.ReactNode }) {
  return <div className="page-header"><div>{eyebrow && <div className="eyebrow">{eyebrow}</div>}<h1>{title}</h1>{description && <p>{description}</p>}</div>{action && <div className="page-header-action">{action}</div>}</div>;
}

function Panel({ title, subtitle, action, children, className = "" }: { title?: string; subtitle?: string; action?: React.ReactElement; children: React.ReactNode; className?: string }) {
  return <Card className={`panel ${className}`}><>{(title || action) && <CardHeader header={title && <div><h2 className="panel-title">{title}</h2>{subtitle && <div className="panel-subtitle">{subtitle}</div>}</div>} action={action} />}{children}</></Card>;
}

function LoadingPanel({ rows = 4 }: { rows?: number }) {
  return <div className="loading-panel" role="status" aria-label="Loading content"><Spinner size="small" label="Loading data…" />{Array.from({ length: rows }, (_, index) => <div key={index} className="skeleton-line" />)}</div>;
}
function ErrorPanel({ message, retry }: { message: string; retry: () => void }) {
  return <div className="state-card error-state"><div className="state-icon danger"><Dismiss20Regular /></div><h3>We couldn’t load this view</h3><p>{message}</p><Button appearance="primary" icon={<ArrowSync20Regular />} onClick={retry}>Try again</Button></div>;
}
function EmptyState({ title, detail }: { title: string; detail: string }) {
  return <div className="state-card empty-state"><div className="state-icon"><BookOpen24Regular /></div><h3>{title}</h3><p>{detail}</p></div>;
}
function StatusBadge({ value }: { value: string }) {
  const normalized = value.toUpperCase();
  const color: "success" | "warning" | "danger" | "informative" | "subtle" =
    ["VERIFIED", "COMPLETED", "RESOLVED", "ACTIVE", "PUBLISHED"].includes(normalized) ? "success" :
      ["PENDING", "PENDING_REVIEW", "RUNNING", "OPEN"].includes(normalized) ? "warning" :
        ["FAILED", "REJECTED"].includes(normalized) ? "danger" : "subtle";
  return <Badge appearance="tint" color={color}>{value.replaceAll("_", " ").toLowerCase().replace(/(^|\s)\S/g, (letter) => letter.toUpperCase())}</Badge>;
}
function initials(name: string) {
  return name.split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase();
}

function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState(mockAccount.email);
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError("");
    try {
      const result = await adminApi.login(email, password);
      localStorage.setItem("rebooked-admin-session", "true");
      localStorage.setItem("rebooked-admin-token", result.token);
      localStorage.setItem("rebooked-admin-email", result.user.email);
      navigate("/authors");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Unable to sign in.");
    } finally {
      setBusy(false);
    }
  };
  return (
    <div className="login-page">
      <header className="login-topbar"><Link to="/login" className="brand"><span className="brand-mark">R</span><span>rebooked<span className="brand-admin"> / admin</span></span></Link><span className="secure-label"><ShieldCheckmark24Regular /> Secure workspace</span></header>
      <main className="login-main">
        <div className="login-intro"><div className="login-emblem"><BookOpen24Regular /></div><div className="eyebrow">ADMINISTRATION · REBOOKED</div><h1>Welcome back.</h1><p>Sign in to keep the Rebooked catalogue thoughtful, accurate, and ready to discover.</p><div className="login-note"><span className="online-dot" /> Your workspace is protected with admin access.</div></div>
        <Card className="login-card">
          <div className="login-card-head"><div className="eyebrow">SUPER-ADMIN ACCESS</div><h2>Sign in to your account</h2><p>Use your administrator credentials to continue.</p></div>
          <form onSubmit={submit} className="form-stack">
            <label className="field-label" htmlFor="admin-email">Email address</label>
            <Input id="admin-email" type="email" autoComplete="username" value={email} onChange={(_, data) => setEmail(data.value)} required contentBefore={<Person24Regular />} />
            <label className="field-label" htmlFor="admin-password">Password</label>
            <Input id="admin-password" type="password" autoComplete="current-password" value={password} onChange={(_, data) => setPassword(data.value)} required contentBefore={<ShieldCheckmark24Regular />} />
            {error && <div className="inline-error" role="alert">{error}</div>}
            <Button appearance="primary" type="submit" className="login-submit" disabled={busy}>{busy ? <Spinner size="tiny" /> : <>Sign in <ChevronRight20Regular /></>}</Button>
          </form>
          {isMockMode && <div className="preview-credentials"><div><span className="credentials-dot" /> PREVIEW ACCOUNT</div><p><strong>{mockAccount.email}</strong><br />Password: <strong>{mockAccount.password}</strong></p><span>Local mock mode · data resets when the app reloads</span></div>}
          <div className="login-card-foot"><span>Need access?</span><span>Contact your workspace owner</span></div>
        </Card>
      </main>
      <footer className="login-footer"><span>© 2026 Rebooked</span><span>Built for the love of books <span className="heart">♥</span></span><span>Admin portal · v1.0</span></footer>
    </div>
  );
}

function AuthorsPage() {
  const navigate = useNavigate();
  const [search, setSearch] = useState("");
  const resource = useResource(() => adminApi.listAuthors(search), [search]);
  const authors = resource.data?.content ?? [];
  return <div className="page-stack">
    <PageHeader eyebrow="CATALOGUE" title="Author catalogue" description="Explore, verify, and maintain the people behind every story." action={<Button appearance="primary" icon={<Add20Regular />} onClick={() => navigate("/authors/chimamanda-adichie")}>Add identifier</Button>} />
    <div className="summary-strip"><span><strong>{resource.data?.totalElements ?? 4_892}</strong> authors in catalogue</span><span className="summary-separator" /><span><strong>12</strong> open review items</span><span className="summary-right">Last catalogue sync · 18 Nov 2026, 03:00</span></div>
    <Panel className="filter-panel"><div className="filters"><Input className="search-field" contentBefore={<Search20Regular />} placeholder="Search name or identifier…" value={search} onChange={(_, data) => setSearch(data.value)} /><Select aria-label="Filter by status" defaultValue="all"><option value="all">All statuses</option><option value="verified">Verified</option><option value="pending">Pending review</option><option value="merged">Merged</option></Select><Select aria-label="Filter by origin" defaultValue="all"><option value="all">All origins</option><option value="OPEN_LIBRARY">Open Library</option><option value="WIKIDATA">Wikidata</option><option value="PROPOSAL">Proposal</option><option value="MANUAL">Manual</option></Select><Button appearance="subtle" onClick={() => void resource.reload()}>Refresh</Button></div></Panel>
    {resource.loading ? <LoadingPanel /> : resource.error ? <ErrorPanel message={resource.error} retry={() => void resource.reload()} /> : authors.length === 0 ? <EmptyState title="No authors found" detail="Try changing your search or filters to find catalogue entries." /> : (
      <Panel title="Authors" subtitle={`${resource.data?.totalElements.toLocaleString()} records`}>
        <div className="table-scroll"><table className="data-table"><thead><tr><th>AUTHOR</th><th>STATUS</th><th>ORIGIN</th><th>OPEN REVIEWS</th><th>IDENTIFIERS</th><th><span className="sr-only">Open author</span></th></tr></thead><tbody>
          {authors.map((author) => <tr key={author.id}><td><Link to={`/authors/${author.id}`} className="person-cell"><span className="avatar">{initials(author.name)}</span><span><strong>{author.name}</strong><small>{author.birthYear ?? "—"} · {author.workCount ?? 0} linked works</small></span></Link></td><td><StatusBadge value={author.status} /></td><td><span className="origin-label">{author.origin.replaceAll("_", " ")}</span></td><td>{author.openReviewItems ? <span className="review-count">{author.openReviewItems}</span> : <span className="muted">—</span>}</td><td><div className="identifier-list">{author.sources.map((id) => <span className="id-chip" key={id}>{id}</span>)}</div></td><td><Link className="row-link" to={`/authors/${author.id}`} aria-label={`Open ${author.name}`}><ChevronRight20Regular /></Link></td></tr>)}
        </tbody></table></div>
        <div className="table-footer"><span>Showing {authors.length} of {resource.data?.totalElements.toLocaleString()} authors</span><div><Button size="small" disabled>Previous</Button><span className="page-counter">Page 1 of {resource.data?.totalPages || 1}</span><Button size="small" disabled>Next</Button></div></div>
      </Panel>
    )}
  </div>;
}

function AuthorDetailPage() {
  const { id = "" } = useParams();
  const { toasterId, notice } = useNotice();
  const resource = useResource(() => adminApi.getAuthor(id), [id]);
  const [tab, setTab] = useState("Overview");
  const [source, setSource] = useState("OPEN_LIBRARY");
  const [sourceId, setSourceId] = useState("");
  const author = resource.data;
  const verify = async () => {
    try { resource.setData(await adminApi.verifyAuthor(id)); notice("Author verified."); }
    catch (error) { notice(error instanceof Error ? error.message : "Could not verify author.", "error"); }
  };
  const addIdentifier = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!sourceId.trim() || !author) return;
    const updated = { ...author, externalIds: [...(author.externalIds ?? []), { source, sourceId }], sources: [...author.sources, sourceId] };
    resource.setData(updated);
    setSourceId("");
    notice("Identifier added in preview mode.");
  };
  return <div className="page-stack">
    <div className="back-link"><Link to="/authors"><ArrowLeft20Regular /> Back to authors</Link><span> / Author profile</span></div>
    {resource.loading ? <LoadingPanel /> : resource.error || !author ? <ErrorPanel message={resource.error ?? "Author not found."} retry={() => void resource.reload()} /> : <>
      <PageHeader eyebrow="AUTHOR PROFILE" title={author.name} description={`${author.status.replaceAll("_", " ")} · ${author.origin.replaceAll("_", " ")}`} action={<div className="inline-actions"><Button appearance="secondary" icon={<Edit20Regular />} onClick={() => notice("Profile editing will be available when live API mode is connected.")}>Edit profile</Button>{!author.verified && <Button appearance="primary" icon={<Checkmark20Regular />} onClick={() => void verify()}>Verify author</Button>}</div>} />
      <TabList selectedValue={tab} onTabSelect={(_, data) => setTab(String(data.value))} className="detail-tabs">
        {["Overview", "Books", "Identifiers", "Audit"].map((item) => <Tab key={item} value={item}>{item}</Tab>)}
      </TabList>
      {tab === "Overview" && <div className="detail-grid">
        <Panel title="Author profile" subtitle="Core identity and catalogue status">
          <div className="profile-cover"><div className="large-avatar">{initials(author.name)}</div><div><h3>{author.name}</h3><span className="muted">{author.normalizedName ?? author.name.toLowerCase()}</span></div><StatusBadge value={author.status} /></div>
          <dl className="detail-facts"><div><dt>Birth year</dt><dd>{author.birthYear ?? "Not recorded"}</dd></div><div><dt>Catalogue origin</dt><dd>{author.origin.replaceAll("_", " ")}</dd></div><div><dt>Linked books</dt><dd>{author.workCount ?? author.books?.length ?? 0} works</dd></div><div><dt>Available copies</dt><dd>—</dd></div><div><dt>About</dt><dd>{author.biography ?? "No biography has been added."}</dd></div></dl>
        </Panel>
        <Panel title="Linked books" subtitle="Published works attributed to this author" action={<Link className="text-link" to={`/authors/${id}`} onClick={() => setTab("Books")}>View all <ChevronRight20Regular /></Link>}>
          <div className="book-list">{(author.books ?? []).map((book, index) => <div className="book-row" key={book.id}><div className={`book-cover cover-${index + 1}`}><BookOpen24Regular /></div><div className="book-info"><strong>{book.title}</strong><small>Literary fiction · Rebooked collection</small></div><StatusBadge value={book.status} /></div>)}</div>
        </Panel>
        <Panel title="Review history" subtitle="Current review activity"><div className="activity-row"><span className="timeline-dot" /><div><strong>{author.verified ? "Author profile verified" : "Awaiting verification"}</strong><small>{author.verified ? "Identity confirmed by an administrator" : `${author.openReviewItems} open review items`}</small></div><span className="muted">Today</span></div><div className="panel-bottom-link"><Link to="/reviews">Go to review queue <ChevronRight20Regular /></Link></div></Panel>
      </div>}
      {tab === "Books" && <Panel title="Linked books" subtitle={`${author.books?.length ?? 0} records currently linked`}><div className="book-list">{(author.books ?? []).length ? author.books?.map((book) => <div className="book-row" key={book.id}><div className="book-cover"><BookOpen24Regular /></div><div className="book-info"><strong>{book.title}</strong><small>Book record · {book.id}</small></div><StatusBadge value={book.status} /></div>) : <EmptyState title="No linked books yet" detail="Books associated with this author will appear here." />}</div></Panel>}
      {tab === "Identifiers" && <div className="detail-grid">
        <Panel title="External identifiers" subtitle="Trusted catalogue references">
          {(author.externalIds ?? []).map((externalId) => <div className="identifier-row" key={`${externalId.source}-${externalId.sourceId}`}><span className="source-logo">{externalId.source === "WIKIDATA" ? "W" : "OL"}</span><div><strong>{externalId.source.replaceAll("_", " ")}</strong><small>{externalId.sourceId}</small></div><Badge appearance="tint" color="success">Linked</Badge></div>)}
          <form className="add-identifier-form" onSubmit={(event) => void addIdentifier(event)}><div className="form-label-row"><h3>Add identifier</h3><span>ADMIN ACTION</span></div><Select value={source} onChange={(_, data) => setSource(data.value)} aria-label="Identifier source"><option value="OPEN_LIBRARY">Open Library</option><option value="WIKIDATA">Wikidata</option><option value="MANUAL">Manual</option></Select><Input value={sourceId} onChange={(_, data) => setSourceId(data.value)} placeholder="e.g. OL26514A or Q230352" required /><Button type="submit" appearance="primary" icon={<Add20Regular />}>Add identifier</Button></form>
        </Panel><Panel title="Identity details" subtitle="Names and sources">{(author.aliases ?? []).map((alias) => <div className="identifier-row" key={alias.name}><span className="alias-mark">A</span><div><strong>{alias.name}</strong><small>{alias.source.replaceAll("_", " ")}</small></div></div>)}{!author.aliases?.length && <EmptyState title="No aliases recorded" detail="Alternative names will show here when available." />}</Panel>
      </div>}
      {tab === "Audit" && <Panel title="Audit history" subtitle="Administrative activity for this record"><div className="activity-row"><span className="timeline-dot" /><div><strong>{author.verified ? "Author verified" : "Author record created"}</strong><small>Catalogue record · {author.origin.replaceAll("_", " ")}</small></div><span className="muted">Recent</span></div><div className="activity-row"><span className="timeline-dot neutral" /><div><strong>External identifier linked</strong><small>{author.sources[0] ?? "No source identifier"}</small></div><span className="muted">Earlier</span></div></Panel>}
    </>}
    <Toaster toasterId={toasterId} />
  </div>;
}

function ReviewsPage() {
  const [showResolved, setShowResolved] = useState(false);
  const resource = useResource(() => adminApi.listReviews(), []);
  const [resolved, setResolved] = useState<ReviewItem[]>([]);
  const entries = showResolved ? resolved : resource.data?.content ?? [];
  const keepSeparate = async (item: ReviewItem) => {
    try {
      await adminApi.keepSeparate(item.id);
      setResolved((current) => [...current, { ...item, status: "RESOLVED" }]);
      resource.setData({ ...resource.data!, content: resource.data!.content.filter((row) => row.id !== item.id), totalElements: Math.max(0, resource.data!.totalElements - 1) });
    } catch (error) {
      window.alert(error instanceof Error ? error.message : "Unable to resolve review.");
    }
  };
  return <div className="page-stack">
    <PageHeader eyebrow="QUALITY & TRUST" title="Review queue" description="Resolve identity conflicts and keep the author catalogue reliable." action={<div className="queue-summary"><span className="queue-number">{resource.data?.content.length ?? 3}</span><span>items need<br />your attention</span></div>} />
    <div className="queue-banner"><div className="banner-icon"><Clock20Regular /></div><div><strong>Oldest items first</strong><span>Reviews are ordered by the date they were submitted.</span></div><div className="banner-stat"><strong>{resource.data?.content.length ?? 3}</strong><span>open reviews</span></div></div>
    <div className="toolbar-tabs"><button className={!showResolved ? "selected" : ""} onClick={() => setShowResolved(false)}>Open <span>{resource.data?.content.length ?? 3}</span></button><button className={showResolved ? "selected" : ""} onClick={() => setShowResolved(true)}>Resolved <span>{resolved.length}</span></button></div>
    {resource.loading && !resource.data ? <LoadingPanel /> : resource.error ? <ErrorPanel message={resource.error} retry={() => void resource.reload()} /> : entries.length === 0 ? <EmptyState title={showResolved ? "No resolved reviews yet" : "Queue is clear"} detail={showResolved ? "Completed decisions will appear here." : "There are no open author reviews right now."} /> : <Panel title={showResolved ? "Resolved reviews" : "Open reviews"} subtitle="Author identity decisions">
      <div className="table-scroll"><table className="data-table"><thead><tr><th>CANDIDATE</th><th>REVIEW TYPE</th><th>STATUS</th><th>SUBMITTED</th><th>ACTION</th></tr></thead><tbody>
        {entries.map((item) => <tr key={item.id}><td><div className="candidate-cell"><span className="avatar compact">{initials(item.author?.name ?? "?")}</span><div><strong>{item.author?.name ?? "Unknown author"}</strong><small>{item.reason ?? "Author identity review"}</small></div></div></td><td><span className="review-type">{item.type.replaceAll("_", " ").toLowerCase()}</span></td><td><StatusBadge value={item.status} /></td><td>{new Date(item.createdAt).toLocaleDateString("en-GB", { day: "2-digit", month: "short", year: "numeric", timeZone: "UTC" })}</td><td>{!showResolved && <Button size="small" appearance="secondary" icon={<Checkmark20Regular />} onClick={() => void keepSeparate(item)}>Keep separate</Button>}</td></tr>)}
      </tbody></table></div>
    </Panel>}
  </div>;
}

function ImportsPage() {
  const resource = useResource(() => adminApi.listImports(), []);
  const [busy, setBusy] = useState("");
  const [localError, setLocalError] = useState("");
  const entries = resource.data?.content ?? [];
  const runImport = async (source: string) => {
    setBusy(source);
    setLocalError("");
    try { await adminApi.startImport(source); await resource.reload(); }
    catch (error) { setLocalError(error instanceof Error ? error.message : "Import could not be started."); }
    finally { setBusy(""); }
  };
  const totals = entries.reduce((acc, job) => ({ records: acc.records + job.recordsCreated + job.recordsUpdated, failed: acc.failed + job.recordsFailed }), { records: 0, failed: 0 });
  return <div className="page-stack">
    <PageHeader eyebrow="CATALOGUE OPERATIONS" title="Author imports" description="Bring author identities into Rebooked from trusted open data sources." action={<div className="inline-actions"><Button appearance="secondary" icon={busy === "OPEN_LIBRARY" ? <Spinner size="tiny" /> : <Add20Regular />} disabled={!!busy} onClick={() => void runImport("OPEN_LIBRARY")}>Open Library</Button><Button appearance="primary" icon={busy === "WIKIDATA" ? <Spinner size="tiny" /> : <Add20Regular />} disabled={!!busy} onClick={() => void runImport("WIKIDATA")}>Start Wikidata</Button></div>} />
    {localError && <div className="inline-error" role="alert">{localError}</div>}
    <div className="metric-grid"><Metric label="Authors imported" value={resource.loading ? "—" : totals.records.toLocaleString()} note="Across recent jobs" icon={<People24Regular />} /><Metric label="Active jobs" value={entries.filter((job) => ["RUNNING", "PENDING"].includes(job.status)).length} note="Currently processing" icon={<ArrowSync20Regular />} /><Metric label="Errors · last 7 days" value={resource.loading ? "—" : totals.failed.toLocaleString()} note="Records needing attention" icon={<Dismiss20Regular />} accent /></div>
    {resource.loading ? <LoadingPanel /> : resource.error ? <ErrorPanel message={resource.error} retry={() => void resource.reload()} /> : entries.length === 0 ? <EmptyState title="No import history yet" detail="Start an Open Library or Wikidata import to populate the author catalogue." /> : <Panel title="Recent import jobs" subtitle="Import activity and processing results" action={<Button appearance="subtle" icon={<ArrowSync20Regular />} onClick={() => void resource.reload()}>Refresh</Button>}>
      <div className="table-scroll"><table className="data-table"><thead><tr><th>SOURCE</th><th>STATUS</th><th>RECORDS PROCESSED</th><th>STARTED</th><th>RESULT</th></tr></thead><tbody>
        {entries.map((job) => <tr key={job.id}><td><div className="source-cell"><span className={`source-logo ${job.source === "WIKIDATA" ? "wikidata" : ""}`}>{job.source === "WIKIDATA" ? "W" : "OL"}</span><strong>{job.source.replaceAll("_", " ")}</strong></div></td><td><StatusBadge value={job.status} /></td><td><div className="progress-cell"><strong>{job.recordsRead.toLocaleString()}</strong><div className="progress-track"><span style={{ width: job.status === "RUNNING" ? "62%" : job.status === "COMPLETED" ? "100%" : "36%" }} /></div><small>{job.status === "RUNNING" ? "62% complete" : `${job.recordsCreated + job.recordsUpdated} authors created or updated`}</small></div></td><td>{new Date(job.createdAt).toLocaleString("en-GB", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit", timeZone: "UTC" })}</td><td>{job.errorSummary ? <span className="danger-text">{job.recordsFailed} errors</span> : job.status === "COMPLETED" ? <span className="success-text">Completed successfully</span> : <span className="muted">—</span>}</td></tr>)}
      </tbody></table></div>
    </Panel>}
    <div className="note-card"><Sparkle24Regular /><div><strong>Responsible data use</strong><span>Imports add public catalogue facts. External biographies and photos remain hidden until reviewed for reuse terms.</span></div></div>
  </div>;
}
function Metric({ label, value, note, icon, accent = false }: { label: string; value: React.ReactNode; note: string; icon: React.ReactNode; accent?: boolean }) {
  return <Card className={`metric-card${accent ? " metric-accent" : ""}`}><div className="metric-top"><span>{label}</span><span className="metric-icon">{icon}</span></div><strong className="metric-value">{value}</strong><span className="metric-note">{note}</span></Card>;
}

function HomepagePage() {
  const todayWeek = "2026-11-16";
  const [week, setWeek] = useState(todayWeek);
  const resource = useResource(() => adminApi.getFeatured(week), [week]);
  const [draft, setDraft] = useState<FeaturedSet | null>(null);
  const [notice, setNotice] = useState("");
  useEffect(() => { if (resource.data) setDraft(resource.data); }, [resource.data]);
  const update = (key: keyof FeaturedSet, value: string) => setDraft((current) => current ? { ...current, [key]: value } : current);
  const save = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!draft) return;
    try { setDraft(await adminApi.saveFeatured(draft)); setNotice("Featured homepage set saved."); }
    catch (error) { setNotice(error instanceof Error ? error.message : "Unable to save featured set."); }
  };
  const remove = async () => {
    try { await adminApi.removeFeatured(week); setDraft((current) => current ? { ...current, overrideActive: false } : current); setNotice("Featured override removed."); }
    catch (error) { setNotice(error instanceof Error ? error.message : "Unable to remove override."); }
  };
  const refresh = async (section: string) => {
    try { await adminApi.refreshSection(section); setNotice(`${section.replaceAll("_", " ")} refresh queued.`); }
    catch (error) { setNotice(error instanceof Error ? error.message : "Unable to refresh section."); }
  };
  const sections = [{ key: "ai-picks", name: "AI Picks", detail: "Personalised book recommendations", icon: <Sparkle24Regular /> }, { key: "for-you", name: "For You", detail: "Reader-specific selections", icon: <Person24Regular /> }, { key: "new-arrivals", name: "New Arrivals", detail: "Recently listed books", icon: <BookOpen24Regular /> }, { key: "trending-authors", name: "Trending Authors", detail: "Authors readers love right now", icon: <People24Regular /> }];
  return <div className="page-stack">
    <PageHeader eyebrow="DISCOVERY & CURATION" title="Featured homepage" description="Curate the weekly spotlight and keep the reader experience fresh." action={<div className="week-pill"><Clock20Regular /><span>WEEK OF</span><strong>16 NOV 2026</strong></div>} />
    {notice && <div className="inline-success" role="status"><Checkmark20Regular />{notice}<button onClick={() => setNotice("")} aria-label="Dismiss"><Dismiss20Regular /></button></div>}
    {resource.loading ? <LoadingPanel rows={3} /> : resource.error || !draft ? <ErrorPanel message={resource.error ?? "Featured set unavailable."} retry={() => void resource.reload()} /> : <div className="homepage-grid">
      <Card className="panel featured-panel"><CardHeader header={<div><h2 className="panel-title">This week’s featured set</h2><div className="panel-subtitle">A spotlight selected for the Rebooked community.</div></div>} action={<StatusBadge value={draft.overrideActive ? "Override active" : "Automatic"} />} />
        <form className="featured-form" onSubmit={(event) => void save(event)}>
          <div className="form-row"><div><label className="field-label" htmlFor="week-date">Featured week</label><span className="field-hint">The Monday this weekly collection begins.</span></div><Input id="week-date" type="date" value={week} onChange={(_, data) => setWeek(data.value)} /></div>
          <div className="feature-hero"><div className="hero-art"><div className="hero-art-book"><span>CHIMAMANDA<br />NGOZI ADICHIE</span><strong>PURPLE<br />HIBISCUS</strong><i>FARRAR, STRAUS AND GIROUX</i></div></div><div className="hero-copy"><Badge appearance="tint" color="informative">WEEKLY SPOTLIGHT</Badge><h3>Purple Hibiscus</h3><p>Chimamanda Ngozi Adichie</p><span>Hero book selection</span></div></div>
          <div className="form-row"><div><label className="field-label" htmlFor="hero-book">Hero book</label><span className="field-hint">Search a title or paste its book ID.</span></div><Input id="hero-book" value={draft.hero} onChange={(_, data) => update("hero", data.value)} /></div>
          <div className="form-row author-form-row"><div><label className="field-label" htmlFor="featured-authors">Featured authors</label><span className="field-hint">One author name per line.</span></div><Textarea id="featured-authors" rows={4} value={draft.featuredAuthors.join("\n")} onChange={(_, data) => setDraft({ ...draft, featuredAuthors: data.value.split("\n") })} /></div>
          <div className="form-actions"><Button appearance="subtle" onClick={() => void remove()}>Remove override</Button><Button appearance="primary" type="submit">Save featured set <Checkmark20Regular /></Button></div>
        </form>
      </Card>
      <div className="section-refresh-column"><Panel title="Homepage sections" subtitle="Refresh a section to rebuild its content."><div className="refresh-list">{sections.map((section, index) => <div className="refresh-row" key={section.key}><span className={`section-icon section-${index}`}>{section.icon}</span><div className="refresh-info"><strong>{section.name}</strong><small>{section.detail}</small></div><Button appearance="subtle" icon={<ArrowSync20Regular />} aria-label={`Refresh ${section.name}`} onClick={() => void refresh(section.key)}>Refresh</Button></div>)}</div></Panel><div className="timezone-note"><Clock20Regular /><span>Weekly schedule follows <strong>Africa/Lagos</strong> time.</span></div></div>
    </div>}
  </div>;
}

function CategoriesPage() {
  const resource = useResource(() => adminApi.listCategories(), []);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [editing, setEditing] = useState<string | null>(null);
  const [editName, setEditName] = useState("");
  const [error, setError] = useState("");
  const create = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!name.trim()) return;
    setError("");
    try { await adminApi.createCategory(name.trim(), description.trim()); setName(""); setDescription(""); await resource.reload(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to create category."); }
  };
  const rename = async (category: Category) => {
    if (!editName.trim()) return;
    await adminApi.renameCategory(category.id, editName.trim());
    setEditing(null);
    await resource.reload();
  };
  const remove = async (category: Category) => {
    if (!window.confirm(`Delete “${category.name}”? This action cannot be undone.`)) return;
    try { await adminApi.deleteCategory(category.id); await resource.reload(); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to delete category."); }
  };
  const categories = resource.data ?? [];
  return <div className="page-stack">
    <PageHeader eyebrow="MARKETPLACE TAXONOMY" title="Category management" description="Organise the marketplace so readers can find their next favourite book." action={<a href="#create-category" className="button-link"><Add20Regular /> New category</a>} />
    <div className="category-summary"><div className="category-summary-icon"><Tag24Regular /></div><div><strong>{resource.loading ? "—" : categories.length} categories</strong><span>Available to sellers and readers across the marketplace</span></div><div className="summary-books"><strong>{categories.reduce((total, category) => total + (category.booksCount ?? 0), 0).toLocaleString()}</strong><span>books categorised</span></div></div>
    {error && <div className="inline-error" role="alert">{error}</div>}
    <div className="categories-layout">
      {resource.loading ? <LoadingPanel /> : resource.error ? <ErrorPanel message={resource.error} retry={() => void resource.reload()} /> : categories.length === 0 ? <EmptyState title="No categories yet" detail="Create a category to help readers discover books." /> : <Panel title="Marketplace categories" subtitle={`${categories.length} active categories`} className="category-table-panel">
        <div className="table-scroll"><table className="data-table"><thead><tr><th>CATEGORY</th><th>BOOKS</th><th>STATUS</th><th>ACTIONS</th></tr></thead><tbody>{categories.map((category, index) => <tr key={category.id}><td>{editing === category.id ? <div className="edit-category"><Input autoFocus value={editName} onChange={(_, data) => setEditName(data.value)} onKeyDown={(event) => { if (event.key === "Enter") void rename(category); if (event.key === "Escape") setEditing(null); }} /><Button size="small" appearance="primary" onClick={() => void rename(category)}>Save</Button><Button size="small" appearance="subtle" onClick={() => setEditing(null)}>Cancel</Button></div> : <div className="category-name-cell"><span className={`category-swatch swatch-${index % 4}`}><Tag24Regular /></span><strong>{category.name}</strong></div>}</td><td><strong>{(category.booksCount ?? 0).toLocaleString()}</strong></td><td><Badge appearance="tint" color="success"><span className="active-dot" /> Active</Badge></td><td><div className="row-actions"><Button size="small" appearance="subtle" icon={<Edit20Regular />} aria-label={`Edit ${category.name}`} onClick={() => { setEditing(category.id); setEditName(category.name); }}>Edit</Button><Button size="small" appearance="subtle" icon={<Delete20Regular />} aria-label={`Delete ${category.name}`} onClick={() => void remove(category)}>Delete</Button></div></td></tr>)}</tbody></table></div>
      </Panel>}
      <Panel title="Create category" subtitle="Add a new way to explore" className="create-category-panel">
        <form id="create-category" className="category-form" onSubmit={(event) => void create(event)}>
          <label className="field-label" htmlFor="category-name">Category name <span className="required">*</span></label>
          <Input id="category-name" placeholder="e.g. Contemporary fiction" value={name} onChange={(_, data) => setName(data.value)} required />
          <label className="field-label" htmlFor="category-description">Description <span className="optional">Optional</span></label>
          <Textarea id="category-description" placeholder="Help readers understand what belongs in this category…" rows={4} value={description} onChange={(_, data) => setDescription(data.value)} />
          <div className="category-tip"><Sparkle24Regular /><span>Choose a clear name that helps readers browse naturally.</span></div>
          <div className="form-actions"><Button appearance="subtle" onClick={() => { setName(""); setDescription(""); }}>Clear</Button><Button appearance="primary" type="submit" icon={<Add20Regular />}>Create category</Button></div>
        </form>
      </Panel>
    </div>
  </div>;
}

function NotFound() {
  return <div className="state-card"><div className="state-icon"><BookOpen24Regular /></div><h3>Page not found</h3><p>The page you’re looking for isn’t part of this workspace.</p><Link to="/authors">Return to the catalogue</Link></div>;
}

export default function App() {
  const location = useLocation();
  const isLoggedIn = useMemo(() => Boolean(localStorage.getItem("rebooked-admin-session")), [location.pathname]);
  useEffect(() => { document.documentElement.dataset.theme = localStorage.getItem("rebooked-admin-theme") ?? "light"; }, []);
  return <Routes>
    <Route path="/login" element={isLoggedIn ? <Navigate to="/authors" replace /> : <LoginPage />} />
    <Route element={<RequireAuth />}>
      <Route element={<AppShell />}>
        <Route path="/" element={<Navigate to="/authors" replace />} />
        <Route path="/authors" element={<AuthorsPage />} />
        <Route path="/authors/:id" element={<AuthorDetailPage />} />
        <Route path="/reviews" element={<ReviewsPage />} />
        <Route path="/imports" element={<ImportsPage />} />
        <Route path="/homepage" element={<HomepagePage />} />
        <Route path="/categories" element={<CategoriesPage />} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Route>
  </Routes>;
}
