package org.fog.test.perfeval;

import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;

import java.util.List;

public class MyFogDevice extends FogDevice {
    private int mips;

    public MyFogDevice(String name, FogDeviceCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval, double uplinkBandwidth, double downlinkBandwidth, double uplinkLatency, double ratePerMips) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval, uplinkBandwidth, downlinkBandwidth, uplinkLatency, ratePerMips);
    }

    public int getMips() {
        return mips;
    }

    public void setMips(int mips) {
        this.mips = mips;
    }
}