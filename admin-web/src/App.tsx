import { NavLink, Navigate, Outlet, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth';
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

const NAV: { to: string; label: string; adminOnly?: boolean }[] = [
  { to: '/', label: 'Control room' },
  { to: '/rides', label: 'Rides' },
  { to: '/reports', label: 'Cost reports', adminOnly: true },
  { to: '/vehicles', label: 'Vehicles', adminOnly: true },
  { to: '/vendors', label: 'Vendors', adminOnly: true },
  { to: '/rate-cards', label: 'Rate cards', adminOnly: true },
  { to: '/stops', label: 'Stops', adminOnly: true },
  { to: '/routes', label: 'Routes', adminOnly: true },
  { to: '/users', label: 'Users', adminOnly: true },
  { to: '/departments', label: 'Departments', adminOnly: true },
  { to: '/cost-centers', label: 'Cost centers', adminOnly: true },
  { to: '/projects', label: 'Projects', adminOnly: true },
  { to: '/plants', label: 'Plants & settings', adminOnly: true },
];

function Layout() {
  const { session, logout } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  const isAdmin = session.role === 'ADMIN';
  return (
    <div className="layout">
      <nav className="sidebar">
        <div className="brand">Plant-Ride</div>
        {NAV.filter((n) => isAdmin || !n.adminOnly).map((n) => (
          <NavLink key={n.to} to={n.to} end={n.to === '/'}>
            {n.label}
          </NavLink>
        ))}
        <div className="sidebar-footer">
          <div>{session.name}</div>
          <div className="muted">{session.role}</div>
          <button className="link" onClick={logout}>
            Sign out
          </button>
        </div>
      </nav>
      <main>
        <Outlet />
      </main>
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
