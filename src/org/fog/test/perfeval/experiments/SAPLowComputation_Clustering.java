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
public class SAPLowComputation_Clustering {
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();

    static double clusterLatency = 15;
    static double SENSOR_TRANSMISSION_TIME = 10;
    static int numOfAreas = 2;
    static int numOfEdgeServersPerArea = 8;

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
                    moduleMapping.addModuleToDevice("waste-info-module", device.getName());  // fixing all instances of the Client module(waste-info-module) to the Terminal devices
                }
            }


            Controller controller = new Controller("master-controller", fogDevices, sensors,
                    actuators);

            controller.submitApplication(app, 0, new MyModulePlacement(fogDevices, sensors, actuators, app, moduleMapping));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();

            CloudSim.stopSimulation();


//            List<Integer> clusterLevelIdentifier = new ArrayList<>();
//            clusterLevelIdentifier.add(2);
//
//            Map<Integer, List<FogDevice>> monitored = new HashMap<>();
//            for (FogDevice f : fogDevices) {
//                MicroserviceFogDevice msf = (MicroserviceFogDevice) f;
//                if (msf.getDeviceType() == MicroserviceFogDevice.FON || msf.getDeviceType() == MicroserviceFogDevice.CLOUD) {
//                    List<FogDevice> fogDevices = new ArrayList<>();
//                    fogDevices.add(f);
//                    monitored.put(f.getId(), fogDevices);
//                    msf.setFonID(f.getId());
//                } else if (msf.getDeviceType() == MicroserviceFogDevice.CLIENT) {
//                    msf.setFonID(f.getParentId());
//                }
//            }
//
//            /**
//             * Central controller for performing preprocessing functions
//             */
//            int placementAlgo = PlacementLogicFactory.DISTRIBUTED_MICROSERVICES_PLACEMENT;
//            MicroservicesController microservicesController = new MicroservicesController("controller", fogDevices, sensors, applications, clusterLevelIdentifier, clusterLatency, placementAlgo, monitored);
//
//
//            // generate placement requests
//            List<PlacementRequest> placementRequests = new ArrayList<>();
//            for (Sensor s : sensors) {
//                Map<String, Integer> placedMicroservicesMap = new HashMap<>();
//                placedMicroservicesMap.put("client", s.getGatewayDeviceId());
//                PlacementRequest p = new PlacementRequest(s.getAppId(), s.getId(), s.getGatewayDeviceId(), placedMicroservicesMap);
//                placementRequests.add(p);
//            }
//
//            microservicesController.submitPlacementRequests(placementRequests, 0);
//
//            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
//
//            CloudSim.startSimulation();
//
//            CloudSim.stopSimulation();

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
        FogDevice cloud = createFogDevice("cloud", 44000, 40000, 10000, 10000, 0, 0.01, 1332, 1648, MicroserviceFogDevice.CLOUD); // creates the fog device Cloud at the apex of the hierarchy with level=0
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        for (int i = 0; i < numOfAreas; i++) {
            addGw(i + "", userId, appId, cloud.getId()); // adding a fog device for every Area's proxy in physical topology. The parent of every proxy is cloud
        }

    }

    private static FogDevice addGw(String id, int userId, String appId, int parentId) {
        FogDevice area = createFogDevice("d-" + id, 2500, 2000, 1000, 10000, 1, 0.0, 107.339, 83.4333, MicroserviceFogDevice.FON);
        fogDevices.add(area);
        area.setParentId(parentId);
        area.setUplinkLatency(40); // latency of connection between gateways and cloud
        for (int i = 0; i < numOfEdgeServersPerArea; i++) {
            String edgeServerId = id + "-" + i;
            MicroserviceFogDevice edgeServer = addEdgeServer(edgeServerId, userId, appId, area.getId());
            edgeServer.setUplinkLatency(60); // latency of connection between the edge server and proxy server
            fogDevices.add(edgeServer);

            FogDevice terminal = addTerminal("t-" + id, userId, edgeServer.getId());
            terminal.setUplinkLatency(10);
        }
        return area;
    }

    private static MicroserviceFogDevice addEdgeServer(String id, int userId, String appId, int parentId) {
        Random random = new Random();
        int CPU_MIN = 2800;
        int CPU_MAX = 6000;
        int RAM_MIN = 3000;
        int RAM_MAX = 8000;
        double IDLE_POWER_MIN = 112.34;
        double IDLE_POWER_MAX = 143.34;

        int mips = random.nextInt(CPU_MAX - CPU_MIN + 1) + CPU_MIN;
        int ram = random.nextInt(RAM_MAX - RAM_MIN + 1) + RAM_MIN;
        double randomDouble = new Random().nextDouble();
        double idlePower = IDLE_POWER_MIN + ((IDLE_POWER_MAX - IDLE_POWER_MIN) * randomDouble);

        MicroserviceFogDevice edgeServer = createFogDevice("fog-" + id, mips, ram, 5000, 10000, 3, 0, 83.43, idlePower, MicroserviceFogDevice.FCN);
        edgeServer.setParentId(parentId);
//        Sensor eegSensor = new Sensor("s-" + id, "EEG", userId, appId, new DeterministicDistribution(EEG_TRANSMISSION_TIME)); // inter-transmission time of EEG sensor follows a deterministic distribution
//        sensors.add(eegSensor);
//        Actuator display = new Actuator("a-" + id, userId, appId, "DISPLAY");
//        actuators.add(display);
//        eegSensor.setGatewayDeviceId(edgeServer.getId());
//        eegSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
//        display.setGatewayDeviceId(edgeServer.getId());
//        display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
        return edgeServer;
    }

    private static FogDevice addTerminal(String name, int userId, int parentId) {
        FogDevice mobile = createFogDevice(name, 1000, 1500, 3000, 3000, 4, 0.0, 60.43, 87.43, MicroserviceFogDevice.CLIENT);
        mobile.setParentId(parentId);

        Application application = applications.get(0);
        Sensor mobileSensor = new Sensor("s-" + name, "BIN", userId, application.getAppId(), new DeterministicDistribution(SENSOR_TRANSMISSION_TIME));
        mobileSensor.setApp(application);
        sensors.add(mobileSensor);
        Actuator mobileDisplay = new Actuator("a-" + name, userId, application.getAppId(), "ACT_CONTROL");
        actuators.add(mobileDisplay);

        mobileSensor.setGatewayDeviceId(mobile.getId());
        mobileSensor.setLatency(6.0);

        mobileDisplay.setGatewayDeviceId(mobile.getId());
        mobileDisplay.setLatency(1.0);
        mobileDisplay.setApp(application);

        fogDevices.add(mobile);
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
    private static MicroserviceFogDevice createFogDevice(String nodeName, long mips,
                                                         int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower, String deviceType) {

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

        MicroserviceFogDevice fogdevice = null;
        try {
            fogdevice = new MicroserviceFogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 10000, 0, ratePerMips, deviceType);
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

//        Application application = Application.createApplication(appId, userId); // creates an empty application model (empty directed graph)
//
//        /*
//         * Adding modules (vertices) to the application model (directed graph)
//         */
//        application.addAppModule("client", 10);
//        application.addAppModule("module_1", 10);
//        application.addAppModule("module_2", 10);
//        application.addAppModule("module_3", 10);
//
////        /*
////         * Connecting the application modules (vertices) in the application model (directed graph) with edges
////         */
////        if (EEG_TRANSMISSION_TIME == 10)
////            application.addAppEdge("EEG", "client", 2000, 500, "EEG", Tuple.UP, AppEdge.SENSOR); // adding edge from EEG (sensor) to Client module carrying tuples of type EEG
////        else
////            application.addAppEdge("EEG", "client", 3000, 500, "EEG", Tuple.UP, AppEdge.SENSOR);
////        application.addAppEdge("client", "concentration_calculator", 3500, 500, "_SENSOR", Tuple.UP, AppEdge.MODULE); // adding edge from Client to Concentration Calculator module carrying tuples of type _SENSOR
////        application.addAppEdge("concentration_calculator", "connector", 100, 1000, 1000, "PLAYER_GAME_STATE", Tuple.UP, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Concentration Calculator to Connector module carrying tuples of type PLAYER_GAME_STATE
////        application.addAppEdge("concentration_calculator", "client", 14, 500, "CONCENTRATION", Tuple.DOWN, AppEdge.MODULE);  // adding edge from Concentration Calculator to Client module carrying tuples of type CONCENTRATION
////        application.addAppEdge("connector", "client", 100, 28, 1000, "GLOBAL_GAME_STATE", Tuple.DOWN, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Connector to Client module carrying tuples of type GLOBAL_GAME_STATE
////        application.addAppEdge("client", "DISPLAY", 1000, 500, "SELF_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type SELF_STATE_UPDATE
////        application.addAppEdge("client", "DISPLAY", 1000, 500, "GLOBAL_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type GLOBAL_STATE_UPDATE
////
////        /*
////         * Defining the input-output relationships (represented by selectivity) of the application modules.
////         */
////        application.addTupleMapping("client", "EEG", "_SENSOR", new FractionalSelectivity(0.9)); // 0.9 tuples of type _SENSOR are emitted by Client module per incoming tuple of type EEG
////        application.addTupleMapping("client", "CONCENTRATION", "SELF_STATE_UPDATE", new FractionalSelectivity(1.0)); // 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per incoming tuple of type CONCENTRATION
////        application.addTupleMapping("concentration_calculator", "_SENSOR", "CONCENTRATION", new FractionalSelectivity(1.0)); // 1.0 tuples of type CONCENTRATION are emitted by Concentration Calculator module per incoming tuple of type _SENSOR
////        application.addTupleMapping("client", "GLOBAL_GAME_STATE", "GLOBAL_STATE_UPDATE", new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE
//
////        application.addAppEdge("Sensor", "client", );
//
//        application.addAppEdge("SENSOR", "client", 1000, 5000, "SENSOR_TUPLE", Tuple.UP, AppEdge.SENSOR);
//        application.addAppEdge("client", "module_1", 3500, 4500, "CLIENT_MOD1_TUPLE", Tuple.UP, AppEdge.MODULE);
//        application.addAppEdge("module_1", "module_2", 3000, 3000, "MOD1_MOD2_TUPLE", Tuple.UP, AppEdge.MODULE);
//        application.addAppEdge("module_2", "module_3", 2000, 1500, "MOD2_MOD3_TUPLE", Tuple.UP, AppEdge.MODULE);
//        application.addAppEdge("module_3", "client", 1000, 1000, "MOD3_CLIENT_TUPLE", Tuple.DOWN, AppEdge.MODULE);
//        /*
//         * Defining application loops to monitor the latency of.
//         * Here, we add only one loop for monitoring : EEG(sensor) -> Client -> Concentration Calculator -> Client -> DISPLAY (actuator)
//         */
//        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
//            add("EEG");
//            add("client");
//            add("concentration_calculator");
//            add("client");
//            add("DISPLAY");
//        }});
//        List<AppLoop> loops = new ArrayList<AppLoop>() {{
//            add(loop1);
//        }};
//        application.setLoops(loops);
//
//        return application;

        Application application = Application.createApplication(appId, userId);
        application.addAppModule("waste-info-module", 10);
        application.addAppModule("master-module", 10);
        application.addAppModule("recycle-module", 10);
        application.addAppModule("health-module", 10);
        application.addAppModule("municipal-module", 10);
        application.addAppEdge("BIN", "waste-info-module", 1000, 2000,
                "BIN", Tuple.UP,
                AppEdge.SENSOR);
        application.addAppEdge("waste-info-module", "master-module",
                1000, 2000, "Task1",
                Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("master-module", "municipal-module",
                1000, 2000, "Task2",
                Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("master-module", "recycle-module",
                1000, 2000, "Task3",
                Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("master-module", "health-module",
                1000, 2000, "Task4",
                Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("master-module", "ACT_CONTROL",
                100, 28, 100, "ACT_PARAMS",
                Tuple.UP, AppEdge.ACTUATOR);
        application.addTupleMapping("waste-info-module",
                "BIN", "Task1",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("master-module", "BIN", "Task2",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("master-module", "BIN", "Task3",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("master-module", "BIN", "Task4",
                new FractionalSelectivity(1.0));
        application.addTupleMapping("master-module", "BIN", "ACT_CONTROL",
                new FractionalSelectivity(1.0));
        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("BIN");
            add("waste-info-module");
            add("master-module");
            add("municipal-module");
            add("recycle-module");
            add("health-module");
            add("ACT_CONTROL");
        }});
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
        }};
        application.setLoops(loops);
        return application;
    }
}