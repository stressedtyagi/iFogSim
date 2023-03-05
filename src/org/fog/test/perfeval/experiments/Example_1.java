package org.fog.test.perfeval.experiments;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public class Example_1 {
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();
    static int numOfDepts = 1;
    static double GPS_TRANSMISSION_TIME = 5;
    static int numOfMobilesPerDept = 4;

    public static void main(String[] args) {
        try {
            Log.disable();
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events

            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "Translation_Service"; // identifier of the application

            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());

            createFogDevices(broker.getId(), appId);

            for (FogDevice fogdevice : fogDevices) {
                System.out.println(fogdevice.getId() + " : " + fogdevice.getName() + " --> " + fogdevice.getParentId() + " :: " + fogdevice.getChildrenIds());
            }
            MyFogDevice.initializeNeighbours((MyFogDevice) fogDevices.get(0));
            MyFogDevice.showNetwork((MyFogDevice) fogDevices.get(0));

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            moduleMapping.addModuleToDevice("storage", "cloud");

            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("m")) {
                    moduleMapping.addModuleToDevice("client", device.getName());
                }
            }

            //Running simulation

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

            controller.submitApplication(application, 0, new MyModulePlacementOld(fogDevices, sensors, actuators, application, moduleMapping));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            Log.printLine("Simulation finished!");


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void createFogDevices(int userId, String appId) {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25); // creates the fog device Cloud at the apex of the hierarchy with level=0
        cloud.setParentId(-1);

        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333); // creates the fog device Proxy Server (level=1)
        proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
        proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms

        cloud.setChildrenIds(new ArrayList<Integer>() {{
            add(proxy.getId());
        }});

        fogDevices.add(cloud);
        fogDevices.add(proxy);

        List<Integer> gateways = new ArrayList<>();

        for (int i = 0; i < numOfDepts; i++) {
            FogDevice fogDevice = addGw(i + "", proxy.getId(), userId, appId);
            gateways.add(fogDevice.getId()); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
        }

        proxy.setChildrenIds(gateways);

    }

    private static FogDevice addGw(String id, int parentId, int userId, String appId) {
        FogDevice dept = createFogDevice("d-" + id, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        fogDevices.add(dept);
        dept.setParentId(parentId);
        dept.setUplinkLatency(4); // latency of connection between gateways and proxy server is 4 ms

        List<Integer> mobiles = new ArrayList<>();

        for (int i = 0; i < numOfMobilesPerDept; i++) {
            String mobileId = id + "-" + i;

            // adding mobiles to the physical topology. Smartphones have been modeled as fog devices as well.
            FogDevice mobile = createFogDevice("m-" + i, 1000, 1000, 10000, 270, 3, 0, 87.53, 82.44);

            Sensor gpsSensor = new Sensor("s-" + i, "GPS", userId, appId, new DeterministicDistribution(GPS_TRANSMISSION_TIME));
            Actuator display = new Actuator("a-" + i, userId, appId, "DISPLAY");
            sensors.add(gpsSensor);
            actuators.add(display);
            gpsSensor.setGatewayDeviceId(mobile.getId());
            display.setGatewayDeviceId(mobile.getId());

            // adding parent id for each fogDevice as the gateway ID
            mobile.setParentId(dept.getId());

            mobile.setUplinkLatency(2); // latency of connection between the smartphone and proxy server is 2 ms
            fogDevices.add(mobile);

            mobiles.add(mobile.getId());
        }

        dept.setChildrenIds(mobiles);

        return dept;
    }

    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // host storage
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        try {
            fogdevice = new MyFogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        return fogdevice;
    }

    /**
     * Function to create the application.
     *
     * @param appId  unique identifier of the application
     * @param userId identifier of the user of the application
     * @return
     */
    @SuppressWarnings({"serial"})
    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);

        application.addAppModule("client", 10);
        application.addAppModule("track-module", 10);
        application.addAppModule("storage", 10);

        application.addAppEdge("GPS", "client", 3000, 500, "GPS", Tuple.UP, AppEdge.SENSOR);

        application.addAppEdge("client", "track-module", 3500, 500, "GEO-DATA", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("track-module", "storage", 100, 1000, 1000, "USER-LOCATION", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("storage", "track-module", 100, 1500, 2000, "SATELLITE-DATA", Tuple.DOWN, AppEdge.MODULE);

        application.addAppEdge("track-module", "client", 14, 500, "MAP-DATA", Tuple.DOWN, AppEdge.MODULE);

        application.addAppEdge("client", "DISPLAY", 1000, 500, "MAP-GUI", Tuple.DOWN, AppEdge.ACTUATOR);

        application.addAppEdge("client", "DISPLAY", 1000, 500, "CURRENT-TRAFFIC", Tuple.DOWN, AppEdge.ACTUATOR);


        application.addTupleMapping("client", "GPS", "GEO-DATA", new FractionalSelectivity(1));

        application.addTupleMapping("client", "MAP-DATA", "MAP-GUI", new FractionalSelectivity(0.8));

        application.addTupleMapping("client", "MAP-DATA", "CURRENT-TRAFFIC", new FractionalSelectivity(0.2));

        application.addTupleMapping("track-module", "GEO-DATA", "USER-LOCATION", new FractionalSelectivity(1));

        application.addTupleMapping("track-module", "GEO-DATA", "USER-LOCATION", new FractionalSelectivity(0.5));

        application.addTupleMapping("track-module", "SATELLITE-DATA", "MAP-DATA", new FractionalSelectivity(0.5));

        application.addTupleMapping("storage", "USER-LOCATION", "SATELLITE-DATA", new FractionalSelectivity(0.5));

        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("GPS");
            add("client");
            add("track-module");
            add("client");
            add("DISPLAY");
        }});

        final AppLoop loop2 = new AppLoop(new ArrayList<String>() {{
            add("GPS");
            add("client");
            add("track-module");
            add("storage");
            add("track-module");
            add("client");
            add("DISPLAY");
        }});
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
            add(loop2);
        }};
        application.setLoops(loops);

        return application;
    }
}