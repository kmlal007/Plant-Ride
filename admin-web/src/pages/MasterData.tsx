import { Building2, CarFront, FolderKanban, Handshake, MapPin, Tags, Users, Wallet } from 'lucide-react';
import { CrudPage, Field } from '../components/CrudPage';
import { VehicleStatusBadge } from '../components/StatusBadge';

type Row = Record<string, unknown> & { id: number };
const byCodeName = (r: Row) => `${r.code} — ${r.name}`;
const byName = (r: Row) => String(r.name);

const departmentLookup = { path: '/api/admin/departments', label: byCodeName };
const costCenterLookup = { path: '/api/admin/cost-centers', label: byCodeName };
const vendorLookup = { path: '/api/admin/vendors', label: byName };

const VEHICLE_TYPES = ['BUS', 'SHUTTLE', 'CAR', 'SUV', 'TWO_WHEELER'];

export function DepartmentsPage() {
  const fields: Field[] = [
    { key: 'code', label: 'Code', required: true },
    { key: 'name', label: 'Name', required: true },
    { key: 'lat', label: 'Entrance latitude', type: 'number', help: 'Used as a ride destination in the user app' },
    { key: 'lng', label: 'Entrance longitude', type: 'number' },
    { key: 'active', label: 'Active', type: 'checkbox' },
  ];
  return <CrudPage icon={Building2} title="Departments" description="Departments of the plant. The entrance location is offered as a ride destination." path="/api/admin/departments" fields={fields} />;
}

export function CostCentersPage() {
  const fields: Field[] = [
    { key: 'code', label: 'Code', required: true, help: 'Same code as in SAP' },
    { key: 'name', label: 'Name', required: true },
    { key: 'departmentId', label: 'Department', lookup: departmentLookup },
    { key: 'monthlyBudget', label: 'Monthly budget (₹)', type: 'number' },
    { key: 'active', label: 'Active', type: 'checkbox' },
  ];
  return (
    <CrudPage
      icon={Wallet}
      title="Cost Centers"
      description="Every ride is charged to a cost center or a project."
      path="/api/admin/cost-centers"
      fields={fields}
    />
  );
}

export function ProjectsPage() {
  const fields: Field[] = [
    { key: 'code', label: 'WBS / project code', required: true },
    { key: 'name', label: 'Name', required: true },
    { key: 'costCenterId', label: 'Cost center', lookup: costCenterLookup, required: true },
    { key: 'active', label: 'Active', type: 'checkbox' },
  ];
  return <CrudPage icon={FolderKanban} title="Projects" description="Projects (SAP WBS elements) that rides can be charged to instead of a cost center." path="/api/admin/projects" fields={fields} />;
}

export function UsersPage() {
  const fields: Field[] = [
    { key: 'loginId', label: 'Login (employee code / phone)', required: true },
    { key: 'name', label: 'Name', required: true },
    {
      key: 'role',
      label: 'Role',
      options: ['EMPLOYEE', 'DRIVER', 'DISPATCHER', 'ADMIN'],
      required: true,
      render: (r) => {
        const tone = { ADMIN: 'accent', DISPATCHER: 'violet', DRIVER: 'info', EMPLOYEE: '' }[String(r.role)] ?? '';
        return <span className={`badge ${tone}`}>{String(r.role).toLowerCase()}</span>;
      },
    },
    { key: 'phone', label: 'Phone' },
    { key: 'email', label: 'Email', formOnly: true },
    { key: 'grade', label: 'Grade', formOnly: true },
    { key: 'departmentId', label: 'Department', lookup: departmentLookup },
    { key: 'defaultCostCenterId', label: 'Default cost center', lookup: costCenterLookup, formOnly: true },
    {
      key: 'managerId',
      label: 'Approver (manager)',
      lookup: { path: '/api/admin/users', label: (r) => `${r.name} (${r.loginId})` },
      formOnly: true,
      help: 'Approves this user’s exclusive rides',
    },
    { key: 'vendorId', label: 'Vendor (drivers)', lookup: vendorLookup, formOnly: true },
    { key: 'password', label: 'Password', type: 'password', formOnly: true, required: true, help: 'Leave empty to keep' },
    { key: 'active', label: 'Active', type: 'checkbox' },
  ];
  return <CrudPage icon={Users} title="People" description="Employees, approvers, drivers and control-room staff." path="/api/admin/users" fields={fields} />;
}

export function VendorsPage() {
  const fields: Field[] = [
    { key: 'name', label: 'Name', required: true },
    { key: 'contactName', label: 'Contact person' },
    { key: 'phone', label: 'Phone' },
    { key: 'active', label: 'Active', type: 'checkbox' },
  ];
  return <CrudPage icon={Handshake} title="Vendors" description="Contract transport providers and their contacts." path="/api/admin/vendors" fields={fields} />;
}

export function VehiclesPage() {
  const fields: Field[] = [
    { key: 'registrationNo', label: 'Registration no.', required: true },
    { key: 'vehicleType', label: 'Type', options: VEHICLE_TYPES, required: true },
    { key: 'capacity', label: 'Seats', type: 'number', required: true },
    { key: 'ownerType', label: 'Owner', options: ['POOL', 'VENDOR', 'DEPARTMENT'], required: true },
    { key: 'ownerDepartmentId', label: 'Owner department', lookup: departmentLookup, formOnly: true },
    { key: 'vendorId', label: 'Vendor', lookup: vendorLookup, formOnly: true },
    { key: 'serviceMode', label: 'Service', options: ['ON_DEMAND', 'FIXED_ROUTE'], required: true },
    {
      key: 'routeId',
      label: 'Route (fixed-route only)',
      lookup: {
        path: '/api/admin/routes',
        label: (r) => {
          const route = r.route as Row;
          return `${route.code} — ${route.name}`;
        },
        id: (r) => (r.route as Row).id,
      },
      formOnly: true,
    },
    { key: 'gpsDeviceId', label: 'GPS device id', help: 'IMEI / Traccar unique id' },
    {
      key: 'status',
      label: 'Status',
      tableOnly: true,
      render: (r) =>
        r.serviceMode === 'FIXED_ROUTE' ? (
          <span className="badge info">Tracked</span>
        ) : (
          <VehicleStatusBadge status={String(r.status)} />
        ),
    },
    { key: 'active', label: 'Active', type: 'checkbox' },
  ];
  return <CrudPage icon={CarFront} title="Vehicles" description="Department, vendor and pool vehicles; on-demand cabs and fixed-route shuttles." path="/api/admin/vehicles" fields={fields} />;
}

export function RateCardsPage() {
  const fields: Field[] = [
    { key: 'vehicleType', label: 'Vehicle type', options: VEHICLE_TYPES, required: true },
    { key: 'rideType', label: 'Ride type', options: ['EXCLUSIVE', 'SHARED'], required: true },
    { key: 'baseFare', label: 'Base fare (₹)', type: 'number', required: true },
    { key: 'perKm', label: 'Per km (₹)', type: 'number', required: true },
    { key: 'perMinute', label: 'Per minute (₹)', type: 'number', required: true },
    { key: 'minimumFare', label: 'Minimum fare (₹)', type: 'number', required: true },
  ];
  return (
    <CrudPage
      icon={Tags}
      title="Rate Cards"
      description="Internal charge rates used to cost rides to cost centers: max(minimum, base + km × per km + minutes × per minute)."
      path="/api/admin/rate-cards"
      fields={fields}
    />
  );
}

export function StopsPage() {
  const fields: Field[] = [
    { key: 'code', label: 'Code', required: true },
    { key: 'name', label: 'Name', required: true },
    { key: 'lat', label: 'Latitude', type: 'number', required: true },
    { key: 'lng', label: 'Longitude', type: 'number', required: true },
    { key: 'active', label: 'Active', type: 'checkbox' },
  ];
  return <CrudPage icon={MapPin} title="Stops" description="Shuttle and bus stops used by routes and the employee app." path="/api/admin/stops" fields={fields} />;
}
