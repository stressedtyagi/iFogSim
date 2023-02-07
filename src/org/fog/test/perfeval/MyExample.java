package org.fog.test.perfeval;

import org.cloudbus.cloudsim.Host;
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
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Tuple;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;

import java.util.*;


public class MyExample {
    static int numOfFogDevices = 10;
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static Map<String, Integer> getIdByName = new HashMap<String, Integer>();
    private static FogDevice createAFogDevice(String nodeName, long mips,int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower)
    {
        List<Pe> peList = new ArrayList<Pe>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000;
        int bw = 10000;
        PowerHost host = new PowerHost(hostId, new RamProvisionerSimple(ram), new BwProvisionerOverbooking(bw), storage, peList, new StreamOperatorScheduler(peList), new FogLinearPowerModel(busyPower, idlePower));
        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<Storage>();
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(arch, os, vmm, host, time_zone,cost, costPerMem, costPerStorage, costPerBw);
        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics, new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
            fogdevice.setLevel(level);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return fogdevice;
    }

    private static void createFogDevices() {
        FogDevice cloud = createAFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        getIdByName.put(cloud.getName(), cloud.getId());
        for(int i=0;i<numOfFogDevices;i++){
            FogDevice device = createAFogDevice("FogDevice-"+i, getValue(12000, 15000), getValue(4000, 8000), getValue(200, 300), getValue(500, 1000), 1, 0.01, getValue(100,120), getValue(70, 75));
            device.setParentId(cloud.getId());
            device.setUplinkLatency(10);
            fogDevices.add(device);
            getIdByName.put(device.getName(), device.getId());
        }
    }

    private static Application createApplication(String appId, int brokerId){
        Application application = Application.createApplication(appId, brokerId);
        application.addAppModule("MasterModule", 10);
        application.addAppModule("WorkerModule-1", 10);
        application.addAppModule("WorkerModule-2", 10);
        application.addAppModule("WorkerModule-3", 10);
        application.addAppEdge("Sensor", "MasterModule", 3000, 500, "Sensor", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("MasterModule", "WorkerModule-1", 100, 1000, "Task-1", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("MasterModule", "WorkerModule-2", 100, 1000, "Task-2", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("MasterModule", "WorkerModule-3", 100, 1000, "Task-3", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("WorkerModule-1", "MasterModule",20, 50, "Response-1", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("WorkerModule-2", "MasterModule",20, 50, "Response-2", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("WorkerModule-3", "MasterModule",20, 50, "Response-3", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("MasterModule", "Actuators", 100, 50, "OutputData", Tuple.DOWN, AppEdge.ACTUATOR);
        application.addTupleMapping("MasterModule", " Sensor ", "Task-1", new FractionalSelectivity(0.3));
        application.addTupleMapping("MasterModule", " Sensor ", "Task-2", new FractionalSelectivity(0.3));
        application.addTupleMapping("MasterModule", " Sensor ", "Task-3", new FractionalSelectivity(0.3));
        application.addTupleMapping("WorkerModule-1", "Task-1", "Response-1", new FractionalSelectivity(1.0));
        application.addTupleMapping("WorkerModule-2", "Task-2", "Response-2", new FractionalSelectivity(1.0));
        application.addTupleMapping("WorkerModule-3", "Task-3", "Response-3", new FractionalSelectivity(1.0));
        application.addTupleMapping("MasterModule", "Response-1", "OutputData", new FractionalSelectivity(0.3));
        application.addTupleMapping("MasterModule", "Response-2", "OutputData", new FractionalSelectivity(0.3));
        application.addTupleMapping("MasterModule", "Response-3", "OutputData", new FractionalSelectivity(0.3));
        final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{
            add("Sensor");
            add("MasterModule");
            add("WorkerModule-1");
            add("MasterModule");
            add("Actuator");
        }});
        final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{
            add("Sensor");
            add("MasterModule");
            add("WorkerModule-2");
            add("MasterModule");
            add("Actuator");
        }});
        final AppLoop loop3 = new AppLoop(new ArrayList<String>(){{
            add("Sensor");
            add("MasterModule");
            add("WorkerModule-3");
            add("MasterModule");
            add("Actuator");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>(){{
            add(loop1);
            add(loop2);
            add(loop3);
        }};
        application.setLoops(loops);
        return application;
    }

    private static int getValue(int lower, int upper) {
        Random rand = new Random();
        return rand.nextInt(upper- lower) + lower;
    }

    public static void main(String[] args) {
        CloudSim.init(10, Calendar.getInstance(), false);
        createFogDevices();
        System.out.println(getIdByName);
    }

}

