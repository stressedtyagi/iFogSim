package org.fog.test.perfeval.experiments;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MyFogDevice extends FogDevice {
    protected FogDevice next = null;
    protected FogDevice prev = null;

    public MyFogDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth, double uplinkLatency, double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
    }

    public MyFogDevice(String name, long mips, int ram, double uplinkBandwidth, double downlinkBandwidth, double ratePerMips, PowerModel powerModel) throws Exception {
        super(name, mips, ram, uplinkBandwidth, downlinkBandwidth, ratePerMips, powerModel);
    }

    public static FogDevice initializeNeighbours(MyFogDevice root) {
        Queue<MyFogDevice> queue = new LinkedList<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Integer parents = queue.size();
            while (parents != 0) {
                MyFogDevice currentDevice = queue.poll();
                MyFogDevice prevChild = null;
                for (Integer childrenId : currentDevice.getChildrenIds()) {
                    MyFogDevice child = (MyFogDevice) CloudSim.getEntity(childrenId);
                    if (prevChild != null) {
                        prevChild.setNext(child);
                        child.setPrev(prevChild);
                    }
                    prevChild = child;
                    queue.add(child);
                }
                parents--;
            }
        }
        return root;
    }

    public static void showNetwork(MyFogDevice root) {

        System.out.println(root.getName());
        String prefix = "--";
        if(root.getChildrenIds().isEmpty()) {
            return;
        }
        Integer childId = root.getChildrenIds().get(0);
        MyFogDevice child = (MyFogDevice) CloudSim.getEntity(childId);
        while (child != null) {
            System.out.println(prefix + "|");
            showNetwork(child, prefix + "  ");
            child = (MyFogDevice) child.getNext();
        }
    }

    private static void showNetwork(MyFogDevice node, String prefix) {
        System.out.println(prefix + node.getName());
        prefix = prefix + "--";
        if(node.getChildrenIds().isEmpty()) {
            return;
        }
        Integer childId = node.getChildrenIds().get(0);
        MyFogDevice child = (MyFogDevice) CloudSim.getEntity(childId);
        while (child != null) {
            System.out.println(prefix + "|");
            showNetwork(child, prefix + "  ");
            child = (MyFogDevice) child.getNext();
        }
    }

    public FogDevice getNext() {
        return next;
    }

    public void setNext(FogDevice next) {
        this.next = next;
    }

    public FogDevice getPrev() {
        return prev;
    }

    public void setPrev(FogDevice prev) {
        this.prev = prev;
    }

}