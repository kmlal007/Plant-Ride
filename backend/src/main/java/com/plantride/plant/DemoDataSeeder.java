package com.plantride.plant;

import java.math.BigDecimal;
import java.time.LocalTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.plantride.fleet.OwnerType;
import com.plantride.fleet.RateCard;
import com.plantride.fleet.RateCardRepository;
import com.plantride.fleet.ServiceMode;
import com.plantride.fleet.Vehicle;
import com.plantride.fleet.VehicleRepository;
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
import com.plantride.ride.RideType;
import com.plantride.security.Role;
import com.plantride.user.AppUser;
import com.plantride.user.AppUserRepository;

/**
 * Demo data for local development (plantride.seed-demo=true). Fictional plant around a
 * reference point; all passwords are "password". Never enable in production.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "plantride.seed-demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final double LAT = 22.7900;
    private static final double LNG = 86.1900;

    private final PlantRepository plants;
    private final DepartmentRepository departments;
    private final CostCenterRepository costCenters;
    private final AppUserRepository users;
    private final VendorRepository vendors;
    private final VehicleRepository vehicles;
    private final RateCardRepository rateCards;
    private final StopRepository stops;
    private final RouteRepository routes;
    private final RouteStopRepository routeStops;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(PlantRepository plants, DepartmentRepository departments, CostCenterRepository costCenters,
                          AppUserRepository users, VendorRepository vendors, VehicleRepository vehicles,
                          RateCardRepository rateCards, StopRepository stops, RouteRepository routes,
                          RouteStopRepository routeStops, PasswordEncoder passwordEncoder) {
        this.plants = plants;
        this.departments = departments;
        this.costCenters = costCenters;
        this.users = users;
        this.vendors = vendors;
        this.vehicles = vehicles;
        this.rateCards = rateCards;
        this.stops = stops;
        this.routes = routes;
        this.routeStops = routeStops;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.findByLoginId("E1001").isPresent()) {
            return;
        }
        Plant plant = plants.findAll().get(0);
        plant.setCenterLat(LAT);
        plant.setCenterLng(LNG);
        plant.setVisitorModuleEnabled(true);
        Long p = plant.getId();

        Department bf = dept(p, "BF", "Blast Furnace", 0.006, 0.004);
        Department sms = dept(p, "SMS", "Steel Melt Shop", -0.004, 0.008);
        Department adm = dept(p, "ADM", "General Office", 0.000, -0.006);
        CostCenter ccBf = cc(p, "CC-BF", "Blast Furnace Ops", bf.getId());
        CostCenter ccSms = cc(p, "CC-SMS", "Steel Melt Shop Ops", sms.getId());
        cc(p, "CC-ADM", "Administration", adm.getId());

        AppUser manager = user(p, "M2001", "Asha Manager", Role.EMPLOYEE, bf.getId(), ccBf.getId(), null);
        user(p, "E1001", "Ravi Kumar", Role.EMPLOYEE, bf.getId(), ccBf.getId(), manager.getId());
        user(p, "E1002", "Priya Singh", Role.EMPLOYEE, sms.getId(), ccSms.getId(), null);
        user(p, "DSP01", "Control Room", Role.DISPATCHER, null, null, null);

        Vendor vendor = new Vendor();
        vendor.setPlantId(p);
        vendor.setName("City Cabs Pvt Ltd");
        vendors.save(vendor);
        AppUser driver1 = user(p, "9000000001", "Suresh Driver", Role.DRIVER, null, null, null);
        driver1.setVendorId(vendor.getId());
        user(p, "9000000002", "Mahesh Driver", Role.DRIVER, null, null, null);

        vehicle(p, "JH05AB1001", VehicleType.CAR, 4, OwnerType.VENDOR, null, vendor.getId(), ServiceMode.ON_DEMAND, "DEV-CAR-1");
        vehicle(p, "JH05AB1002", VehicleType.SUV, 6, OwnerType.POOL, null, null, ServiceMode.ON_DEMAND, "DEV-SUV-1");
        vehicle(p, "JH05AB2001", VehicleType.CAR, 4, OwnerType.DEPARTMENT, bf.getId(), null, ServiceMode.ON_DEMAND, "DEV-CAR-2");

        rate(p, VehicleType.CAR, RideType.EXCLUSIVE, "50", "14", "1");
        rate(p, VehicleType.CAR, RideType.SHARED, "20", "7", "0.5");
        rate(p, VehicleType.SUV, RideType.EXCLUSIVE, "80", "18", "1.5");
        rate(p, VehicleType.SUV, RideType.SHARED, "30", "9", "0.5");

        Stop s1 = stop(p, "MG", "Main Gate", 0.000, 0.000);
        Stop s2 = stop(p, "GO", "General Office", 0.001, -0.006);
        Stop s3 = stop(p, "BF", "Blast Furnace", 0.006, 0.004);
        Stop s4 = stop(p, "SMS", "Steel Melt Shop", -0.004, 0.008);
        Route route = new Route();
        route.setPlantId(p);
        route.setCode("S1");
        route.setName("Shuttle Loop 1");
        route.setRouteKind(RouteKind.SHUTTLE);
        route.setFirstDeparture(LocalTime.of(6, 0));
        route.setLastDeparture(LocalTime.of(22, 0));
        route.setHeadwayMinutes(15);
        routes.save(route);
        int seq = 1;
        int[] offsets = {0, 4, 9, 14};
        Stop[] order = {s1, s2, s3, s4};
        for (int i = 0; i < order.length; i++) {
            RouteStop rs = new RouteStop();
            rs.setRouteId(route.getId());
            rs.setSeq(seq++);
            rs.setStopId(order[i].getId());
            rs.setOffsetMinutes(offsets[i]);
            routeStops.save(rs);
        }
        Vehicle shuttle = vehicle(p, "JH05SH0001", VehicleType.SHUTTLE, 20, OwnerType.POOL, null, null,
                ServiceMode.FIXED_ROUTE, "DEV-SHUTTLE-1");
        shuttle.setRouteId(route.getId());
        log.warn("Demo data seeded (logins E1001, M2001, E1002, DSP01, 9000000001, 9000000002; password 'password')");
    }

    private Department dept(Long p, String code, String name, double dLat, double dLng) {
        Department d = new Department();
        d.setPlantId(p);
        d.setCode(code);
        d.setName(name);
        d.setLat(LAT + dLat);
        d.setLng(LNG + dLng);
        return departments.save(d);
    }

    private CostCenter cc(Long p, String code, String name, Long deptId) {
        CostCenter c = new CostCenter();
        c.setPlantId(p);
        c.setCode(code);
        c.setName(name);
        c.setDepartmentId(deptId);
        c.setMonthlyBudget(new BigDecimal("50000"));
        return costCenters.save(c);
    }

    private AppUser user(Long p, String login, String name, Role role, Long deptId, Long ccId, Long managerId) {
        AppUser u = new AppUser();
        u.setPlantId(p);
        u.setLoginId(login);
        u.setName(name);
        u.setPhone(login.length() == 10 ? login : "98" + login.replaceAll("\\D", "") + "0000".substring(0, 4));
        u.setRole(role);
        u.setDepartmentId(deptId);
        u.setDefaultCostCenterId(ccId);
        u.setManagerId(managerId);
        u.setPasswordHash(passwordEncoder.encode("password"));
        return users.save(u);
    }

    private Vehicle vehicle(Long p, String reg, VehicleType type, int capacity, OwnerType owner, Long deptId,
                            Long vendorId, ServiceMode mode, String device) {
        Vehicle v = new Vehicle();
        v.setPlantId(p);
        v.setRegistrationNo(reg);
        v.setVehicleType(type);
        v.setCapacity(capacity);
        v.setOwnerType(owner);
        v.setOwnerDepartmentId(deptId);
        v.setVendorId(vendorId);
        v.setServiceMode(mode);
        v.setGpsDeviceId(device);
        return vehicles.save(v);
    }

    private void rate(Long p, VehicleType type, RideType rideType, String base, String perKm, String perMin) {
        RateCard r = new RateCard();
        r.setPlantId(p);
        r.setVehicleType(type);
        r.setRideType(rideType);
        r.setBaseFare(new BigDecimal(base));
        r.setPerKm(new BigDecimal(perKm));
        r.setPerMinute(new BigDecimal(perMin));
        r.setMinimumFare(new BigDecimal(base));
        rateCards.save(r);
    }

    private Stop stop(Long p, String code, String name, double dLat, double dLng) {
        Stop s = new Stop();
        s.setPlantId(p);
        s.setCode(code);
        s.setName(name);
        s.setLat(LAT + dLat);
        s.setLng(LNG + dLng);
        return stops.save(s);
    }
}
