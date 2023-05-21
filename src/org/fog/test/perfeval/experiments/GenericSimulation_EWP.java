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
import org.fog.placement.*;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import java.util.*;

/**
 * Simulation setup for case study 1 - EEG Beam Tractor Game
 *
 * @author Harshit Gupta
 */
public class GenericSimulation_EWP {
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();

    static double SENSOR_TRANSMISSION_TIME = 10;
    static int numOfAreas = 5;
    static int numOfEdgeServersPerArea = 4;

    static int numOfEndDevicesPerFog = 2;

    static List<Application> applications = new ArrayList<>();

    public static void main(String[] args) {

        Log.printLine("Starting Low Computation Application...");

        try {
            Log.disable();
            int num_user = 1; // number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // mean trace events

            CloudSim.init(num_user, calendar, trace_flag);

            String appId = "sap_low_compute"; // identifier of the application

            FogBroker broker = new FogBroker("broker");

            Application app = createApplication(appId, broker.getId());
            app.setUserId(broker.getId());

            applications.add(app);

            createFogDevices(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping


            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("t")) {
                    moduleMapping.addModuleToDevice("module_1", device.getName());
                }
            }


            Controller controller = new Controller("master-controller", fogDevices, sensors,
                    actuators);

            controller.submitApplication(app, 0, new ModulePlacementEdgewards(fogDevices, sensors, actuators, app, moduleMapping));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();

            CloudSim.stopSimulation();

            Log.printLine("Low Computation Application finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }

    /**
     * Creates the fog devices in the physical topology of the simulation.
     *
     * @param userId
     * @param appId
     */
    private static void createFogDevices(int userId, String appId) {
        FogDevice cloud = createFogDevice("cloud", 40000, 40960, 1000, 10000, 0, 0.01, 1332, 1648); // creates the fog device Cloud at the apex of the hierarchy with level=0
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        for (int i = 0; i < numOfAreas; i++) {
            addGw(i + "", userId, appId, cloud.getId()); // adding a fog device for every Area's proxy in physical topology. The parent of every proxy is cloud
        }

    }

    private static FogDevice addGw(String id, int userId, String appId, int parentId) {
        FogDevice area = createFogDevice("d-" + id, 5000, 8192, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        fogDevices.add(area);
        area.setParentId(parentId);
        area.setUplinkLatency(200); // latency of connection between gateways and cloud
        for (int i = 0; i < numOfEdgeServersPerArea; i++) {
            String edgeServerId = id + "-" + i;
            FogDevice edgeServer = addEdgeServer(edgeServerId, userId, appId, area.getId());
            edgeServer.setUplinkLatency(25); // latency of connection between the edge server and proxy server
            fogDevices.add(edgeServer);
        }
        return area;
    }

    private static FogDevice addEdgeServer(String id, int userId, String appId, int parentId) {
        Random random = new Random();
        int CPU_MIN = 4000;
        int CPU_MAX = 6000;
        int RAM_MIN = 3000;
        int RAM_MAX = 8000;
        double IDLE_POWER_MIN = 112.34;
        double IDLE_POWER_MAX = 143.34;

        int mips = random.nextInt(CPU_MAX - CPU_MIN + 1) + CPU_MIN;
        int ram = random.nextInt(RAM_MAX - RAM_MIN + 1) + RAM_MIN;
        double randomDouble = new Random().nextDouble();
        double idlePower = IDLE_POWER_MIN + ((IDLE_POWER_MAX - IDLE_POWER_MIN) * randomDouble);

        FogDevice edgeServer = createFogDevice("f-" + id, mips, ram, 10000, 10000, 1, 0, 83.43, idlePower);
        edgeServer.setParentId(parentId);

        for (int i = 0; i < numOfEndDevicesPerFog; i++) {
            FogDevice terminal = addTerminal("t-" + id + "-" + i, userId, edgeServer.getId());
            terminal.setUplinkLatency(5);
            fogDevices.add(terminal);
        }

        return edgeServer;
    }

    private static FogDevice addTerminal(String name, int userId, int parentId) {
        FogDevice mobile = createFogDevice(name, 800, 2048, 100, 250, 2, 0.0, 60.43, 87.43);
        mobile.setParentId(parentId);

        Application application = applications.get(0);
        Sensor mobileSensor = new Sensor("sensor-" + name, "IoT_Sensor", userId, application.getAppId(), new DeterministicDistribution(SENSOR_TRANSMISSION_TIME));
        mobileSensor.setApp(application);
        sensors.add(mobileSensor);
        Actuator mobileDisplay = new Actuator("actuator-" + name, userId, application.getAppId(), "DISPLAY");
        actuators.add(mobileDisplay);

        mobileSensor.setGatewayDeviceId(mobile.getId());
        mobileSensor.setLatency(2.0);

        mobileDisplay.setGatewayDeviceId(mobile.getId());
        mobileDisplay.setLatency(3.0);
        mobileDisplay.setApp(application);
        return mobile;
    }

    /**
     * Creates a vanilla fog device
     *
     * @param nodeName    name of the device to be used in simulation
     * @param mips        MIPS
     * @param ram         RAM
     * @param upBw        uplink bandwidth
     * @param downBw      downlink bandwidth
     * @param level       hierarchy level of the device
     * @param ratePerMips cost rate per MIPS used
     * @param busyPower
     * @param idlePower
     * @return
     */
    private static FogDevice createFogDevice(String nodeName, long mips,
                                             int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

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
            fogdevice = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        return fogdevice;
    }

    /**
     * Function to create the EEG Tractor Beam game application in the DDF model.
     *
     * @param appId  unique identifier of the application
     * @param userId identifier of the user of the application
     * @return
     */
    @SuppressWarnings({"serial"})
    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);
//        application.addAppModule("module_1", 1024, 500, 25);
//        application.addAppModule("module_2", 4096, 1000, 50);
//        application.addAppModule("module_3", 2048, 2000, 100);
//        application.addAppModule("module_4", 1024, 1500, 30);
//        application.addAppModule("module_5", 6144, 3000, 200);
//        application.addAppModule("module_6", 8192, 1500, 500);

        application.addAppModule("module_1", 10);
        application.addAppModule("module_2", 10);
        application.addAppModule("module_3", 10);
        application.addAppModule("module_4", 10);
        application.addAppModule("module_5", 10);
        application.addAppModule("module_6", 10);

        application.addAppEdge("IoT_Sensor", "module_1", 3000, 500,
                "IoT_Sensor", Tuple.UP, AppEdge.SENSOR);

        application.addAppEdge("module_1", "DISPLAY", 2000, 500,
                "ACTUATOR_A", Tuple.DOWN, AppEdge.ACTUATOR);

        application.addAppEdge("module_1", "DISPLAY", 2000, 500,
                "ACTUATOR_B", Tuple.DOWN, AppEdge.ACTUATOR);

        application.addAppEdge("module_1", "module_2", 6000, 500,
                "TT_2", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("module_2", "module_3", 6000, 500,
                "TT_3", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("module_3", "module_4", 6000, 500,
                "TT_4", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("module_4", "module_5", 6000, 500,
                "TT_5", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("module_5", "module_4", 1000, 500,
                "TT_6", Tuple.DOWN, AppEdge.MODULE);

        application.addAppEdge("module_4", "module_3", 1000, 500,
                "TT_7", Tuple.DOWN, AppEdge.MODULE);

        application.addAppEdge("module_3", "module_2", 1000, 500,
                "TT_8", Tuple.DOWN, AppEdge.MODULE);

        application.addAppEdge("module_2", "module_1", 1000, 500,
                "TT_9", Tuple.DOWN, AppEdge.MODULE);

        application.addAppEdge("module_5", "module_6", 100, 1500, 1000,
                "TT_10", Tuple.DOWN, AppEdge.MODULE);

        application.addAppEdge("module_6", "module_1", 100, 1500, 1000,
                "TT_11", Tuple.DOWN, AppEdge.MODULE);

        application.addTupleMapping("module_1", "IoT_Sensor", "TT_2",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("module_1", "TT_11", "ACTUATOR_A",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("module_1", "TT_9", "ACTUATOR_B",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("module_2", "TT_2", "TT_3",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("module_2", "TT_8", "TT_9",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("module_3", "TT_3", "TT_4",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("module_3", "TT_7", "TT_8",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("module_4", "TT_4", "TT_5",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("module_4", "TT_6", "TT_7",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("module_5", "TT_5", "TT_10",
                new FractionalSelectivity(0.1));
        application.addTupleMapping("module_5", "TT_5", "TT_6",
                new FractionalSelectivity(0.9));
        application.addTupleMapping("module_6", "TT_10", "TT_11",
                new FractionalSelectivity(1.0));

        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("IoT_Sensor");
            add("module_1");
            add("module_2");
            add("module_3");
            add("module_4");
            add("module_5");
            add("module_4");
            add("module_3");
            add("module_2");
            add("module_1");
            add("DISPLAY");
        }});

        final AppLoop loop2 = new AppLoop(new ArrayList<String>() {{
            add("module_5");
            add("module_6");
            add("module_1");
            add("DISPLAY");
        }});
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
//            add(loop2);
        }};
        application.setLoops(loops);
        return application;
    }
}