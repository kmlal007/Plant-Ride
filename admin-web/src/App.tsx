import {
  BadgeIndianRupee,
  Building2,
  Bus,
  CarFront,
  FolderKanban,
  Handshake,
  LayoutDashboard,
  ListChecks,
  LogOut,
  LucideIcon,
  MapPin,
  Monitor,
  Moon,
  Route as RouteIcon,
  Settings,
  Sun,
  Tags,
  Users,
  Wallet,
} from 'lucide-react';
import { NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
import { PartnerCredit } from './components/PartnerCredit';
import { useApi } from './hooks';
import { DashboardPage } from './pages/DashboardPage';
import { LoginPage } from './pages/LoginPage';
import {
  CostCentersPage,
  DepartmentsPage,
  ProjectsPage,
  RateCardsPage,
  StopsPage,
  UsersPage,
  VehiclesPage,
  VendorsPage,
} from './pages/MasterData';
import { PlantsPage } from './pages/PlantsPage';
import { ReportsPage } from './pages/ReportsPage';
import { RidesPage } from './pages/RidesPage';
import { RoutesPage } from './pages/RoutesPage';
import { useTheme } from './theme';

interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  adminOnly?: boolean;
}

const NAV: { title: string; items: NavItem[] }[] = [
  {
    title: 'Operations',
    items: [
      { to: '/', label: 'Control room', icon: LayoutDashboard },
      { to: '/rides', label: 'Rides', icon: ListChecks },
      { to: '/reports', label: 'Cost reports', icon: BadgeIndianRupee, adminOnly: true },
    ],
  },
  {
    title: 'Fleet',
    items: [
      { to: '/vehicles', label: 'Vehicles', icon: CarFront, adminOnly: true },
      { to: '/vendors', label: 'Vendors', icon: Handshake, adminOnly: true },
      { to: '/rate-cards', label: 'Rate cards', icon: Tags, adminOnly: true },
    ],
  },
  {
    title: 'Network',
    items: [
      { to: '/routes', label: 'Routes & timetables', icon: RouteIcon, adminOnly: true },
      { to: '/stops', label: 'Stops', icon: MapPin, adminOnly: true },
    ],
  },
  {
    title: 'Organisation',
    items: [
      { to: '/users', label: 'People', icon: Users, adminOnly: true },
      { to: '/departments', label: 'Departments', icon: Building2, adminOnly: true },
      { to: '/cost-centers', label: 'Cost centers', icon: Wallet, adminOnly: true },
      { to: '/projects', label: 'Projects', icon: FolderKanban, adminOnly: true },
    ],
  },
  {
    title: 'Administration',
    items: [{ to: '/plants', label: 'Plants & settings', icon: Settings, adminOnly: true }],
  },
];

function initials(name: string) {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]!.toUpperCase())
    .join('');
}

function Layout() {
  const { session, logout } = useAuth();
  const theme = useTheme();
  const plants = useApi<{ id: number; name: string }[]>(session?.role === 'ADMIN' ? '/api/admin/plants' : null);
  if (!session) return <Navigate to="/login" replace />;
  const isAdmin = session.role === 'ADMIN';
  const plantName = plants.data?.find((p) => p.id === session.plantId)?.name ?? 'Plant';
  const ThemeIcon = theme.choice === 'dark' ? Moon : theme.choice === 'light' ? Sun : Monitor;

  return (
    <div className="layout">
      <nav className="sidebar" aria-label="Main">
        <div className="brand">
          <img src="/favicon.svg" alt="" />
          <div>
            <div className="brand-name">
              Plant<span>Ride</span>
            </div>
            <div className="brand-sub">Mobility platform</div>
          </div>
        </div>
        {NAV.map((group) => {
          const items = group.items.filter((n) => isAdmin || !n.adminOnly);
          if (!items.length) return null;
          return (
            <div className="nav-group" key={group.title}>
              <div className="nav-group-title">{group.title}</div>
              {items.map(({ to, label, icon: Icon }) => (
                <NavLink key={to} to={to} end={to === '/'}>
                  <Icon aria-hidden />
                  {label}
                </NavLink>
              ))}
            </div>
          );
        })}
        <div className="sidebar-footer">
          <PartnerCredit size="sm" />
          <div style={{ marginTop: 6 }}>Plant-Ride · v0.1 pilot</div>
        </div>
      </nav>
      <div className="main">
        <header className="topbar">
          <div className="plant-chip">
            <Bus aria-hidden size={18} />
            {plantName}
            <span className="live-dot" title="Live" />
          </div>
          <div className="topbar-right">
            <button
              className="icon"
              onClick={theme.next}
              title={`Theme: ${theme.choice} (click to change)`}
              aria-label={`Theme: ${theme.choice}`}
            >
              <ThemeIcon />
            </button>
            <span className="avatar" aria-hidden>
              {initials(session.name)}
            </span>
            <div className="user-meta">
              <strong>{session.name}</strong>
              <small>{session.role === 'ADMIN' ? 'Administrator' : 'Control room'}</small>
            </div>
            <button className="icon" onClick={logout} title="Sign out" aria-label="Sign out">
              <LogOut />
            </button>
          </div>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<Layout />}>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/rides" element={<RidesPage />} />
        <Route path="/reports" element={<ReportsPage />} />
        <Route path="/vehicles" element={<VehiclesPage />} />
        <Route path="/vendors" element={<VendorsPage />} />
        <Route path="/rate-cards" element={<RateCardsPage />} />
        <Route path="/stops" element={<StopsPage />} />
        <Route path="/routes" element={<RoutesPage />} />
        <Route path="/users" element={<UsersPage />} />
        <Route path="/departments" element={<DepartmentsPage />} />
        <Route path="/cost-centers" element={<CostCentersPage />} />
        <Route path="/projects" element={<ProjectsPage />} />
        <Route path="/plants" element={<PlantsPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
