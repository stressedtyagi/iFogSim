package org.fog.test.perfeval;

import org.apache.commons.math3.util.Pair;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.SelectivityModel;
import org.fog.scheduler.TupleScheduler;
import org.fog.utils.FogUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyApplication {
    private Map<Integer, Map<String, Double>> deadlineInfo;
    private Map<Integer, Map<String, Integer>> additionalMipsInfo;

    public Map<Integer, Map<String, Integer>> getAdditionalMipsInfo() {
        return additionalMipsInfo;
    }

    public List<AppModule> getModules() {
        return modules;
    }

    public void setModules(List<AppModule> modules) {
        this.modules = modules;
    }

    /**
     * List of application modules in the application
     */
    private List<AppModule> modules;
    private String appId;
    private int userId;

    public void setAdditionalMipsInfo(Map<Integer, Map<String, Integer>> additionalMipsInfo) {
        this.additionalMipsInfo = additionalMipsInfo;
    }

    public static MyApplication createApplication(String appId, int userId){
        return new Application(appId, userId);
    }

    public void setDeadlineInfo(Map<Integer, Map<String, Double>> deadlineInfo) {
        this.deadlineInfo = deadlineInfo;
    }

    public Map<Integer, Map<String, Double>> getDeadlineInfo() {
        return deadlineInfo;
    }

    public void addAppModule(String moduleName, int ram, int mips, long size, long bw) {
        String vmm = "Xen";
        AppModule module = new AppModule(FogUtils.generateEntityId(), moduleName, appId, userId, mips, ram, bw, size, vmm, new TupleScheduler(mips, 1), new HashMap<Pair<String, String>, SelectivityModel>());
        getModules().add(module);
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}