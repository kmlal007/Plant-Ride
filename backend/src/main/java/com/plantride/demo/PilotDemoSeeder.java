package com.plantride.demo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.billing.CostPolicies.DepartmentVehicleSharing;
import com.plantride.billing.CostPolicies.LentVehicleChargeMode;
import com.plantride.billing.CostPolicies.OwnVehicleChargeMode;
import com.plantride.billing.CostPolicies.ShuttleCostAllocation;
import com.plantride.billing.RideCostingService;
import com.plantride.billing.ShuttleCostService;
import com.plantride.common.GeoUtils;
import com.plantride.fleet.OwnerType;
import com.plantride.fleet.RateCard;
import com.plantride.fleet.RateCardRepository;
import com.plantride.fleet.ServiceMode;
import com.plantride.fleet.Vehicle;
import com.plantride.fleet.VehicleRepository;
import com.plantride.fleet.VehicleStatus;
import com.plantride.fleet.VehicleType;
import com.plantride.fleet.Vendor;
import com.plantride.fleet.VendorRepository;
import com.plantride.network.Route;
import com.plantride.network.RouteKind;
import com.plantride.network.RouteRepository;
import com.plantride.network.RouteStop;
import com.plantride.network.RouteStopRepository;
import com.plantride.network.Stop;
import com.plantride.network.StopRepository;
import com.plantride.org.CostCenter;
import com.plantride.org.CostCenterRepository;
import com.plantride.org.Department;
import com.plantride.org.DepartmentRepository;
import com.plantride.org.Project;
import com.plantride.org.ProjectRepository;
import com.plantride.plant.Plant;
import com.plantride.plant.PlantRepository;
import com.plantride.plant.PlantService;
import com.plantride.ride.RideRequest;
import com.plantride.ride.RideRequestRepository;
import com.plantride.ride.RideStatus;
import com.plantride.ride.RideType;
import com.plantride.security.Role;
import com.plantride.tracking.SafetyEvent;
import com.plantride.tracking.SafetyEventRepository;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

/**
 * Creates a fictional pilot plant, "Demo Steel Works", for customer showcases: departments, cost
 * centers, projects, people, a mixed fleet, shuttle and commute routes, 30 days of ride history and
 * a few live items (an approval waiting, a scheduled visitor ride). All passwords are "password".
 * Deterministic (fixed random seed) so every demo looks the same.
 */
@Component
@Order(2)
public class PilotDemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PilotDemoSeeder.class);

    public static final String PLANT_CODE = "DSW";
    static final double LAT = 22.7900;
    static final double LNG = 86.1900;
    private static final String PASSWORD = "password";

    private final DemoProperties props;
    private final PlantRepository plants;
    private final PlantService plantService;
    private final DepartmentRepository departments;
    private final CostCenterRepository costCenters;
    private final ProjectRepository projects;
    private final AppUserRepository users;
    private final VendorRepository vendors;
    private final VehicleRepository vehicles;
    private final RateCardRepository rateCards;
    private final StopRepository stops;
    private final RouteRepository routes;
    private final RouteStopRepository routeStops;
    private final RideRequestRepository rides;
    private final SafetyEventRepository safetyEvents;
    private final RideCostingService costing;
    private final ShuttleCostService shuttleCosts;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private String passwordHash;
    private final Random random = new Random(20261005L);

    public PilotDemoSeeder(DemoProperties props, PlantRepository plants, PlantService plantService,
                           DepartmentRepository departments, CostCenterRepository costCenters,
                           ProjectRepository projects, AppUserRepository users, VendorRepository vendors,
                           VehicleRepository vehicles, RateCardRepository rateCards, StopRepository stops,
                           RouteRepository routes, RouteStopRepository routeStops, RideRequestRepository rides,
                           SafetyEventRepository safetyEvents, RideCostingService costing,
                           ShuttleCostService shuttleCosts, PasswordEncoder passwordEncoder, Clock clock) {
        this.props = props;
        this.plants = plants;
        this.plantService = plantService;
        this.departments = departments;
        this.costCenters = costCenters;
        this.projects = projects;
        this.users = users;
        this.vendors = vendors;
        this.vehicles = vehicles;
        this.rateCards = rateCards;
        this.stops = stops;
        this.routes = routes;
        this.routeStops = routeStops;
        this.rides = rides;
        this.safetyEvents = safetyEvents;
        this.costing = costing;
        this.shuttleCosts = shuttleCosts;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed() || plants.findByCode(PLANT_CODE).isPresent()) {
            return;
        }
        passwordHash = passwordEncoder.encode(PASSWORD);
        plantService.setMultiPlantEnabled(true);

        Plant plant = new Plant();
        plant.setCode(PLANT_CODE);
        plant.setName("Demo Steel Works (Pilot)");
        plant.setCenterLat(LAT + 0.002);
        plant.setCenterLng(LNG + 0.006);
        plant.setDispatchRadiusKm(15);
        plant.setSpeedLimitKmh(40);
        plant.setVisitorModuleEnabled(true);
        plant.setExclusiveRideRequiresApproval(true);
        plant.setDepartmentVehicleSharing(DepartmentVehicleSharing.LEND_WHEN_IDLE);
        plant.setOwnVehicleChargeMode(OwnVehicleChargeMode.CHARGE);
        plant.setLentVehicleChargeMode(LentVehicleChargeMode.CHARGE_BOOKER_CREDIT_OWNER);
        plant.setShuttleCostAllocation(ShuttleCostAllocation.HEADCOUNT);
        plants.save(plant);
        Long p = plant.getId();

        // ---- Departments (entrance coordinates double as ride destinations) and cost centers
        Map<String, Department> dept = new LinkedHashMap<>();
        Map<String, CostCenter> cc = new HashMap<>();
        Object[][] depts = {
                {"GO", "General Office", 0.004, -0.006, "CC1000", 400000},
                {"MED", "Medical Centre", 0.002, -0.010, "CC1100", 150000},
                {"TRG", "Training Centre", -0.003, -0.008, "CC1200", 100000},
                {"STO", "Central Stores", -0.008, 0.000, "CC1300", 120000},
                {"PWR", "Power House", 0.016, 0.010, "CC2000", 150000},
                {"COKE", "Coke Plant", 0.012, 0.004, "CC3000", 200000},
                {"SIN", "Sinter Plant", 0.010, 0.012, "CC3100", 180000},
                {"BF", "Blast Furnace", 0.006, 0.016, "CC4100", 300000},
                {"SMS", "LD Steel Melt Shop", -0.002, 0.020, "CC4200", 300000},
                {"HSM", "Hot Strip Mill", -0.009, 0.016, "CC5100", 250000},
                {"CRM", "Cold Rolling Mill", -0.012, 0.008, "CC5200", 200000},
        };
        for (Object[] d : depts) {
            Department dep = new Department();
            dep.setPlantId(p);
            dep.setCode((String) d[0]);
            dep.setName((String) d[1]);
            dep.setLat(LAT + (double) d[2]);
            dep.setLng(LNG + (double) d[3]);
            departments.save(dep);
            dept.put(dep.getCode(), dep);
            CostCenter c = new CostCenter();
            c.setPlantId(p);
            c.setCode((String) d[4]);
            c.setName(dep.getName());
            c.setDepartmentId(dep.getId());
            c.setMonthlyBudget(BigDecimal.valueOf((int) d[5]));
            costCenters.save(c);
            cc.put(dep.getCode(), c);
        }
        project(p, "WBS-BF3-RELINE", "Blast Furnace 3 Relining", cc.get("BF"));
        project(p, "WBS-DIGITAL", "Plant Digitalisation Programme", cc.get("GO"));
        project(p, "WBS-SAFETY-AUDIT", "Annual Safety Audit 2026", cc.get("GO"));

        // ---- People
        user(p, "demo.admin", "Transport Admin", Role.ADMIN, "GO", cc, dept, null);
        user(p, "CR01", "Control Room Desk", Role.DISPATCHER, "GO", cc, dept, null);
        AppUser rajesh = user(p, "M100", "Rajesh Sinha (Head, Blast Furnace)", Role.EMPLOYEE, "BF", cc, dept, null);
        AppUser meera = user(p, "M200", "Meera Iyer (Head, Steel Melt Shop)", Role.EMPLOYEE, "SMS", cc, dept, null);
        AppUser arjun = user(p, "M300", "Arjun Mehta (Head, Administration)", Role.EMPLOYEE, "GO", cc, dept, null);
        List<AppUser> employees = new ArrayList<>(List.of(
                user(p, "E101", "Ravi Kumar", Role.EMPLOYEE, "BF", cc, dept, rajesh),
                user(p, "E102", "Sunita Das", Role.EMPLOYEE, "BF", cc, dept, rajesh),
                user(p, "E201", "Priya Singh", Role.EMPLOYEE, "SMS", cc, dept, meera),
                user(p, "E202", "Amit Verma", Role.EMPLOYEE, "SMS", cc, dept, meera),
                user(p, "E301", "Neha Gupta", Role.EMPLOYEE, "GO", cc, dept, arjun),
                user(p, "E302", "Rohit Bansal", Role.EMPLOYEE, "GO", cc, dept, arjun),
                user(p, "E401", "Vikram Patel", Role.EMPLOYEE, "HSM", cc, dept, null),
                user(p, "E501", "Kiran Rao", Role.EMPLOYEE, "COKE", cc, dept, null),
                user(p, "E601", "Farhan Ali", Role.EMPLOYEE, "CRM", cc, dept, null),
                user(p, "E701", "Dr. Deepa Nair", Role.EMPLOYEE, "MED", cc, dept, null),
                user(p, "E801", "Sanjay Mishra", Role.EMPLOYEE, "PWR", cc, dept, null),
                user(p, "E901", "Pooja Sharma", Role.EMPLOYEE, "SIN", cc, dept, null)));
        employees.addAll(List.of(rajesh, meera, arjun));
        // Headcount for shuttle cost allocation: the wider workforce, as records that cannot log in.
        int[] extraHeads = {8, 3, 2, 5, 6, 12, 9, 24, 22, 18, 14};
        int i = 0;
        for (String code : dept.keySet()) {
            for (int n = 0; n < extraHeads[i]; n++) {
                AppUser u = user(p, "W-" + code + "-" + (n + 1), "Workforce " + code + " " + (n + 1),
                        Role.EMPLOYEE, code, cc, dept, null);
                u.setPasswordHash("!disabled");
            }
            i++;
        }

        Vendor travels = vendor(p, "Steel City Travels Pvt Ltd", "Manoj Tiwari", "9431000001");
        Vendor movers = vendor(p, "Jharkhand Fleet Movers", "S. K. Roy", "9431000002");

        List<AppUser> drivers = new ArrayList<>();
        String[][] driverData = {
                {"9000000001", "Suresh Yadav"}, {"9000000002", "Ramesh Mahato"}, {"9000000003", "Dinesh Oraon"},
                {"9000000004", "Mukesh Singh"}, {"9000000005", "Prakash Munda"}, {"9000000006", "Anil Hembrom"},
                {"9000000007", "Bablu Prasad"}};
        for (String[] d : driverData) {
            AppUser driver = user(p, d[0], d[1], Role.DRIVER, null, cc, dept, null);
            driver.setPhone(d[0]);
            drivers.add(driver);
        }
        drivers.get(0).setVendorId(travels.getId());
        drivers.get(1).setVendorId(travels.getId());
        drivers.get(2).setVendorId(travels.getId());
        drivers.get(3).setVendorId(movers.getId());

        // ---- Rate cards (internal charge rates)
        rate(p, VehicleType.CAR, RideType.EXCLUSIVE, "60", "15", "1.5", "80");
        rate(p, VehicleType.CAR, RideType.SHARED, "25", "7", "0.5", "30");
        rate(p, VehicleType.SUV, RideType.EXCLUSIVE, "90", "20", "2", "120");
        rate(p, VehicleType.SUV, RideType.SHARED, "35", "9", "0.7", "40");

        // ---- Stops and routes
        Map<String, Stop> stop = new HashMap<>();
        for (Department d : dept.values()) {
            stop.put(d.getCode(), stop(p, d.getCode(), d.getName(), d.getLat(), d.getLng()));
        }
        stop.put("MG", stop(p, "MG", "Main Gate", LAT, LNG));
        stop.put("TWN1", stop(p, "TWN1", "Township – Circuit House", LAT + 0.030, LNG - 0.034));
        stop.put("TWN2", stop(p, "TWN2", "Township – Market Square", LAT + 0.018, LNG - 0.022));

        Route s1 = route(p, "S1", "Iron Zone Loop", RouteKind.SHUTTLE, "06:00", "22:00", 15, "1,2,3,4,5,6,7", 180000,
                stop, new Object[][] {{"MG", 0}, {"GO", 4}, {"PWR", 10}, {"COKE", 14}, {"SIN", 18}, {"BF", 22}, {"MG", 32}});
        Route s2 = route(p, "S2", "Steel Zone Loop", RouteKind.SHUTTLE, "06:10", "21:50", 20, "1,2,3,4,5,6", 150000,
                stop, new Object[][] {{"MG", 0}, {"STO", 5}, {"CRM", 10}, {"HSM", 15}, {"SMS", 20}, {"BF", 25}, {"GO", 34}});
        Route c1 = route(p, "C1", "Township Shift Bus", RouteKind.COMMUTE, "05:15", "21:15", 480, "1,2,3,4,5,6,7", 240000,
                stop, new Object[][] {{"TWN1", 0}, {"TWN2", 15}, {"MG", 35}});

        // ---- Fleet. SIM- devices are driven by the demo simulator.
        fixedRoute(p, "JH05DS1001", VehicleType.SHUTTLE, 22, s1, "SIM-SH-1");
        fixedRoute(p, "JH05DS1002", VehicleType.SHUTTLE, 22, s1, "SIM-SH-2");
        fixedRoute(p, "JH05DS1003", VehicleType.SHUTTLE, 22, s2, "SIM-SH-3");
        fixedRoute(p, "JH05DS2001", VehicleType.BUS, 45, c1, "SIM-BUS-1");
        List<Vehicle> onDemand = new ArrayList<>();
        onDemand.add(onDemand(p, "JH05TC3001", VehicleType.CAR, 4, OwnerType.VENDOR, travels.getId(), null, "SIM-CAR-1",
                drivers.get(0), stop.get("GO")));
        onDemand.add(onDemand(p, "JH05TC3002", VehicleType.CAR, 4, OwnerType.VENDOR, travels.getId(), null, "SIM-CAR-2",
                drivers.get(1), stop.get("COKE")));
        onDemand.add(onDemand(p, "JH05TC3003", VehicleType.SUV, 6, OwnerType.VENDOR, travels.getId(), null, "SIM-SUV-1",
                drivers.get(2), stop.get("HSM")));
        onDemand.add(onDemand(p, "JH05FM4001", VehicleType.CAR, 4, OwnerType.VENDOR, movers.getId(), null, "SIM-CAR-3",
                null, stop.get("MG")));
        onDemand.add(onDemand(p, "JH05PL5001", VehicleType.SUV, 6, OwnerType.POOL, null, null, "SIM-SUV-2",
                drivers.get(4), stop.get("STO")));
        onDemand.add(onDemand(p, "JH05BF6001", VehicleType.CAR, 4, OwnerType.DEPARTMENT, null, dept.get("BF").getId(),
                "SIM-BF-1", drivers.get(5), stop.get("BF")));
        onDemand.add(onDemand(p, "JH05SM7001", VehicleType.SUV, 6, OwnerType.DEPARTMENT, null, dept.get("SMS").getId(),
                "SIM-SMS-1", null, stop.get("SMS")));
        vehicles.flush();

        seedHistory(p, employees, onDemand, new ArrayList<>(dept.values()), drivers);
        seedLiveItems(p, rajesh, employees, dept, stop);
        seedSafetyEvents(p, onDemand);
        shuttleCosts.post(p, YearMonth.now(clock).minusMonths(1));

        log.warn("Pilot demo plant '{}' seeded. Admin login demo.admin / {}. See docs/DEMO_WALKTHROUGH.md",
                PLANT_CODE, PASSWORD);
    }

    /** 30 days of mostly completed rides so dashboards and cost reports have substance. */
    private void seedHistory(Long p, List<AppUser> employees, List<Vehicle> fleet, List<Department> depts,
                             List<AppUser> drivers) {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        Instant now = clock.instant();
        for (int day = 30; day >= 1; day--) {
            LocalDate date = today.minusDays(day);
            int count = date.getDayOfWeek() == DayOfWeek.SUNDAY ? 3 : 8 + random.nextInt(8);
            for (int n = 0; n < count; n++) {
                Instant created = date.atTime(LocalTime.of(8, 0).plusMinutes(random.nextInt(11 * 60)))
                        .atZone(zone).toInstant();
                if (created.isAfter(now)) {
                    continue;
                }
                AppUser rider = employees.get(random.nextInt(employees.size()));
                Department from = depts.stream().filter(d -> d.getId().equals(rider.getDepartmentId())).findFirst()
                        .orElse(depts.get(0));
                Department to = depts.get(random.nextInt(depts.size()));
                if (to.getId().equals(from.getId())) {
                    continue;
                }
                historicRide(p, rider, from, to, created, fleet, drivers);
            }
        }
    }

    private void historicRide(Long p, AppUser rider, Department from, Department to, Instant created,
                              List<Vehicle> fleet, List<AppUser> drivers) {
        RideRequest r = new RideRequest();
        r.setPlantId(p);
        r.setRequesterId(rider.getId());
        r.setRideType(random.nextInt(10) < 7 ? RideType.SHARED : RideType.EXCLUSIVE);
        r.setPickupLabel(from.getName());
        r.setPickupLat(from.getLat());
        r.setPickupLng(from.getLng());
        r.setDropLabel(to.getName());
        r.setDropLat(to.getLat());
        r.setDropLng(to.getLng());
        r.setPassengerCount(1 + (random.nextInt(10) < 8 ? 0 : random.nextInt(3)));
        r.setPurpose(PURPOSES[random.nextInt(PURPOSES.length)]);
        r.setCostCenterId(rider.getDefaultCostCenterId());
        r.setCreatedAt(created);
        r.setOtp(String.format("%04d", random.nextInt(10_000)));
        if (r.getRideType() == RideType.EXCLUSIVE && rider.getManagerId() != null) {
            r.setApproverId(rider.getManagerId());
        }
        r.setApprovedAt(created.plusSeconds(r.getApproverId() == null ? 0 : 300 + random.nextInt(900)));
        if (random.nextInt(100) < 8) {
            r.setVisitorName(VISITORS[random.nextInt(VISITORS.length)]);
            r.setVisitorPhone("98" + (10000000 + random.nextInt(89999999)));
            r.setGatePassRef("GP-2026-" + (1000 + random.nextInt(9000)));
        }

        int roll = random.nextInt(100);
        Vehicle v = pickVehicle(fleet, rider);
        Long driverId = v.getCurrentDriverId() != null ? v.getCurrentDriverId()
                : drivers.get(random.nextInt(drivers.size())).getId();
        Instant searchStart = r.getApprovedAt();
        r.setSearchStartedAt(searchStart);
        if (roll < 3) {
            r.setStatus(RideStatus.UNFULFILLED);
            rides.save(r);
            return;
        }
        if (roll < 9) {
            r.setStatus(RideStatus.CANCELLED);
            r.setCancelledAt(searchStart.plusSeconds(120 + random.nextInt(400)));
            r.setCancelReason(random.nextBoolean() ? "Meeting rescheduled" : "Found shuttle instead");
            rides.save(r);
            return;
        }
        r.setVehicleId(v.getId());
        r.setDriverId(driverId);
        Instant accepted = searchStart.plusSeconds(20 + random.nextInt(60));
        r.setAcceptedAt(accepted);
        Instant arrived = accepted.plusSeconds(180 + random.nextInt(420));
        r.setArrivedAt(arrived);
        if (roll < 12) {
            r.setStatus(RideStatus.NO_SHOW);
            rides.save(r);
            return;
        }
        Instant started = arrived.plusSeconds(30 + random.nextInt(240));
        double km = GeoUtils.haversineKm(r.getPickupLat(), r.getPickupLng(), r.getDropLat(), r.getDropLng())
                * GeoUtils.ROAD_DETOUR_FACTOR;
        int minutes = (int) Math.max(3, Math.round(km / 22 * 60) + random.nextInt(4));
        r.setStartedAt(started);
        r.setCompletedAt(started.plus(Duration.ofMinutes(minutes)));
        r.setDistanceKm(BigDecimal.valueOf(km).setScale(2, java.math.RoundingMode.HALF_UP));
        r.setDurationMinutes(minutes);
        r.setDistanceSource("GPS");
        r.setStatus(RideStatus.COMPLETED);
        rides.save(r);
        costing.cost(r, v, r.getCompletedAt());
    }

    private Vehicle pickVehicle(List<Vehicle> fleet, AppUser rider) {
        List<Vehicle> own = fleet.stream()
                .filter(v -> v.getOwnerType() == OwnerType.DEPARTMENT && v.getOwnerDepartmentId().equals(rider.getDepartmentId()))
                .toList();
        if (!own.isEmpty() && random.nextInt(100) < 60) {
            return own.get(0);
        }
        List<Vehicle> shared = fleet.stream().filter(v -> v.getOwnerType() != OwnerType.DEPARTMENT).toList();
        if (random.nextInt(100) < 8) {
            List<Vehicle> lent = fleet.stream().filter(v -> v.getOwnerType() == OwnerType.DEPARTMENT
                    && !v.getOwnerDepartmentId().equals(rider.getDepartmentId())).toList();
            return lent.get(random.nextInt(lent.size()));
        }
        return shared.get(random.nextInt(shared.size()));
    }

    /** Things the presenter can act on immediately. */
    private void seedLiveItems(Long p, AppUser approver, List<AppUser> employees, Map<String, Department> dept,
                               Map<String, Stop> stop) {
        Instant now = clock.instant();
        AppUser ravi = employees.get(0);
        RideRequest pending = new RideRequest();
        pending.setPlantId(p);
        pending.setRequesterId(ravi.getId());
        pending.setRideType(RideType.EXCLUSIVE);
        pending.setVehicleType(VehicleType.SUV);
        place(pending, dept.get("BF"), dept.get("SMS"));
        pending.setPassengerCount(4);
        pending.setPurpose("Hot metal logistics review with SMS");
        pending.setCostCenterId(ravi.getDefaultCostCenterId());
        pending.setStatus(RideStatus.PENDING_APPROVAL);
        pending.setApproverId(approver.getId());
        pending.setCreatedAt(now.minus(Duration.ofMinutes(12)));
        pending.setOtp("4821");
        rides.save(pending);

        AppUser neha = employees.get(4);
        RideRequest visitor = new RideRequest();
        visitor.setPlantId(p);
        visitor.setRequesterId(neha.getId());
        visitor.setRideType(RideType.EXCLUSIVE);
        visitor.setVehicleType(VehicleType.SUV);
        visitor.setPickupLabel("Main Gate");
        visitor.setPickupLat(stop.get("MG").getLat());
        visitor.setPickupLng(stop.get("MG").getLng());
        visitor.setDropLabel(dept.get("GO").getName());
        visitor.setDropLat(dept.get("GO").getLat());
        visitor.setDropLng(dept.get("GO").getLng());
        visitor.setScheduledAt(now.plus(Duration.ofHours(3)));
        visitor.setPassengerCount(2);
        visitor.setPurpose("Customer delegation – plant visit");
        visitor.setVisitorName("Mr. K. Tanaka (Delegate)");
        visitor.setVisitorPhone("9876500001");
        visitor.setGatePassRef("GP-2026-0412");
        projects.findByPlantIdOrderByIdAsc(p).stream().filter(pr -> pr.getCode().equals("WBS-DIGITAL")).findFirst()
                .ifPresent(pr -> {
                    visitor.setProjectId(pr.getId());
                    visitor.setCostCenterId(pr.getCostCenterId());
                });
        visitor.setStatus(RideStatus.SCHEDULED);
        visitor.setApproverId(neha.getManagerId());
        visitor.setCreatedAt(now.minus(Duration.ofHours(1)));
        visitor.setApprovedAt(now.minus(Duration.ofMinutes(40)));
        visitor.setOtp("7310");
        rides.save(visitor);
    }

    private void seedSafetyEvents(Long p, List<Vehicle> fleet) {
        Instant now = clock.instant();
        int[] hoursAgo = {2, 5, 26, 50, 75, 120};
        for (int k = 0; k < hoursAgo.length; k++) {
            Vehicle v = fleet.get(k % fleet.size());
            SafetyEvent e = new SafetyEvent();
            e.setPlantId(p);
            e.setVehicleId(v.getId());
            e.setEventType(SafetyEvent.OVER_SPEED);
            e.setLat(v.getLastLat());
            e.setLng(v.getLastLng());
            e.setSpeedKmh(38.0 + k * 3);
            e.setOccurredAt(now.minus(Duration.ofHours(hoursAgo[k])));
            e.setAcknowledged(k >= 2);
            safetyEvents.save(e);
        }
    }

    // ------------------------------------------------------------------ builders

    private static void place(RideRequest r, Department from, Department to) {
        r.setPickupLabel(from.getName());
        r.setPickupLat(from.getLat());
        r.setPickupLng(from.getLng());
        r.setDropLabel(to.getName());
        r.setDropLat(to.getLat());
        r.setDropLng(to.getLng());
    }

    private AppUser user(Long p, String login, String name, Role role, String deptCode, Map<String, CostCenter> cc,
                         Map<String, Department> dept, AppUser manager) {
        AppUser u = new AppUser();
        u.setPlantId(p);
        u.setLoginId(login);
        u.setName(name);
        u.setPhone(login.matches("[EM]\\d+") ? "98765" + login.substring(1).replaceAll("\\D", "") + "00" : null);
        u.setPasswordHash(passwordHash);
        u.setRole(role);
        if (deptCode != null) {
            u.setDepartmentId(dept.get(deptCode).getId());
            u.setDefaultCostCenterId(cc.get(deptCode).getId());
        }
        u.setManagerId(manager == null ? null : manager.getId());
        return users.save(u);
    }

    private void project(Long p, String code, String name, CostCenter cc) {
        Project pr = new Project();
        pr.setPlantId(p);
        pr.setCode(code);
        pr.setName(name);
        pr.setCostCenterId(cc.getId());
        projects.save(pr);
    }

    private Vendor vendor(Long p, String name, String contact, String phone) {
        Vendor v = new Vendor();
        v.setPlantId(p);
        v.setName(name);
        v.setContactName(contact);
        v.setPhone(phone);
        return vendors.save(v);
    }

    private void rate(Long p, VehicleType type, RideType rideType, String base, String perKm, String perMin,
                      String min) {
        RateCard r = new RateCard();
        r.setPlantId(p);
        r.setVehicleType(type);
        r.setRideType(rideType);
        r.setBaseFare(new BigDecimal(base));
        r.setPerKm(new BigDecimal(perKm));
        r.setPerMinute(new BigDecimal(perMin));
        r.setMinimumFare(new BigDecimal(min));
        rateCards.save(r);
    }

    private Stop stop(Long p, String code, String name, double lat, double lng) {
        Stop s = new Stop();
        s.setPlantId(p);
        s.setCode(code);
        s.setName(name);
        s.setLat(lat);
        s.setLng(lng);
        return stops.save(s);
    }

    private Route route(Long p, String code, String name, RouteKind kind, String first, String last, int headway,
                        String days, int monthlyCost, Map<String, Stop> stop, Object[][] pattern) {
        Route r = new Route();
        r.setPlantId(p);
        r.setCode(code);
        r.setName(name);
        r.setRouteKind(kind);
        r.setFirstDeparture(LocalTime.parse(first));
        r.setLastDeparture(LocalTime.parse(last));
        r.setHeadwayMinutes(headway);
        r.setDaysOfWeek(days);
        r.setMonthlyCost(BigDecimal.valueOf(monthlyCost));
        routes.save(r);
        int seq = 1;
        for (Object[] s : pattern) {
            RouteStop rs = new RouteStop();
            rs.setRouteId(r.getId());
            rs.setSeq(seq++);
            rs.setStopId(stop.get((String) s[0]).getId());
            rs.setOffsetMinutes((int) s[1]);
            routeStops.save(rs);
        }
        return r;
    }

    private void fixedRoute(Long p, String reg, VehicleType type, int capacity, Route route, String device) {
        Vehicle v = new Vehicle();
        v.setPlantId(p);
        v.setRegistrationNo(reg);
        v.setVehicleType(type);
        v.setCapacity(capacity);
        v.setOwnerType(OwnerType.POOL);
        v.setServiceMode(ServiceMode.FIXED_ROUTE);
        v.setRouteId(route.getId());
        v.setGpsDeviceId(device);
        vehicles.save(v);
    }

    private Vehicle onDemand(Long p, String reg, VehicleType type, int capacity, OwnerType owner, Long vendorId,
                             Long deptId, String device, AppUser driverOnDuty, Stop parking) {
        Vehicle v = new Vehicle();
        v.setPlantId(p);
        v.setRegistrationNo(reg);
        v.setVehicleType(type);
        v.setCapacity(capacity);
        v.setOwnerType(owner);
        v.setVendorId(vendorId);
        v.setOwnerDepartmentId(deptId);
        v.setServiceMode(ServiceMode.ON_DEMAND);
        v.setGpsDeviceId(device);
        v.setLastLat(parking.getLat());
        v.setLastLng(parking.getLng());
        v.setLastSpeedKmh(0.0);
        v.setLastFixAt(clock.instant());
        if (driverOnDuty != null) {
            v.setCurrentDriverId(driverOnDuty.getId());
            v.setStatus(VehicleStatus.AVAILABLE);
        }
        return vehicles.save(v);
    }

    private static final String[] PURPOSES = {
            "Production review meeting", "Shift handover", "Safety walk-through", "Maintenance planning",
            "Quality audit", "Vendor inspection", "Training session", "Medical check-up", "Stores material pickup",
            "Energy review", "Management review", "Project site visit"};

    private static final String[] VISITORS = {
            "Ms. A. Fernandes (Auditor)", "Mr. L. Weber (OEM Engineer)", "Mr. R. Khanna (Customer)",
            "Dr. S. Bose (Consultant)", "Ms. Y. Kim (Delegate)"};
}
