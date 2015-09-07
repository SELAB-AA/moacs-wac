/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author aashraf
 */
public class MOACSWACController implements ExperimentParameters {
    AntControllerVR antControllerVR;
    AntControllerNM antControllerNM;
    ExperimentalSetupAndReporting sim;    
    List<MigrationTuple> migrationPlanGlobalBest;
    Ant antGlobalBest;
    static long millisAtStart=0;
    static long millisAtEnd=0;    
    static int noImprovementCounter=0;
    final static int maxIterationsForNoImprovement=3;
    
    public MOACSWACController(ExperimentalSetupAndReporting sim){
        this.antControllerVR=new AntControllerVR(sim);
        this.antControllerNM=new AntControllerNM(sim);   
        this.sim=sim;        
        this.migrationPlanGlobalBest=null;        
        this.antGlobalBest=null;
    }
        
    //@Override
    public void run(){        
        int totalNumberOfServersInThePreviousIteration=this.sim.appServers.size();
        this.antControllerVR.updateGlobalCPUAndMEMAveragesOfCloseToCompletionServers();        
        millisAtStart = System.currentTimeMillis();        
        while(this.antControllerVR.globalCPULoadAvgOfCloseToCompletionServers<DESIRED_CPU_UTILIZATION_THRESHOLD && 
                this.antControllerVR.globalMEMAvgOfCloseToCompletionServers<DESIRED_MEM_UTILIZATION_THRESHOLD){
            this.antControllerVR.updateUnderutilizedServers();                        
            this.antControllerVR.updateCloseToCompletionServers();            
            this.antControllerVR.updateCloseToCompletionAndUnderutilizedServers();            
            
            Thread antControllerVRThread=new Thread(this.antControllerVR);
            Main.threads.add(antControllerVRThread);        
            antControllerVRThread.start();
            
            Thread antControllerNMThread=new Thread(this.antControllerNM);
            Main.threads.add(antControllerNMThread);        
            antControllerNMThread.start();
            
            try{
                antControllerVRThread.join(); 
            }
            catch(InterruptedException e){
                System.out.println(e);
                e.printStackTrace();                
            }
            
            try{
                antControllerNMThread.join(); 
            }
            catch(InterruptedException e){
                System.out.println(e);
                e.printStackTrace();                
            }
            
            boolean acsVRInAction=false;            
            if(this.migrationPlanGlobalBest==null || this.antControllerVR.calculateScore(this.antControllerVR.migrationPlanGlobalBestVR, this.antControllerVR.antGlobalBestVR)> 
                    this.antControllerVR.calculateScore(this.migrationPlanGlobalBest, this.antGlobalBest)){
                if(this.migrationPlanGlobalBest!=null){
                    this.migrationPlanGlobalBest.clear();
                }
                this.migrationPlanGlobalBest=new ArrayList(this.antControllerVR.migrationPlanGlobalBestVR); 
                this.antGlobalBest=this.antControllerVR.antGlobalBestVR;
                acsVRInAction=true;                                
            }
            
            boolean acsNMInAction=false;
            if(this.antControllerVR.calculateScore(this.antControllerNM.migrationPlanGlobalBestNM, this.antControllerNM.antGlobalBestNM)>= 
                    this.antControllerVR.calculateScore(this.migrationPlanGlobalBest, this.antGlobalBest)){
                if(this.antControllerNM.calculateScore(this.antControllerNM.migrationPlanGlobalBestNM, this.antControllerNM.antGlobalBestNM)>
                        this.antControllerNM.calculateScore(this.migrationPlanGlobalBest, this.antGlobalBest)){
                    this.migrationPlanGlobalBest.clear();
                    this.migrationPlanGlobalBest=new ArrayList(this.antControllerNM.migrationPlanGlobalBestNM);
                    this.antGlobalBest=this.antControllerNM.antGlobalBestNM;
                    acsNMInAction=true;
                    acsVRInAction=false;
                }
            }

            List<AppServer> setOfServersToBeReleased=this.antControllerVR.getServersToBeReleased(this.migrationPlanGlobalBest);
            if(setOfServersToBeReleased.size()>0){                                
                List<MigrationTuple> migratedApps=new ArrayList();
                for(MigrationTuple mt:this.migrationPlanGlobalBest){ 
                    if(setOfServersToBeReleased.contains(mt.source)){
                        if(setOfServersToBeReleased.contains(mt.destination)){ 
                            boolean migrationToADyingServerIsFixed=false;
                            for(MigrationTuple mtOther:this.migrationPlanGlobalBest){ 
                                if(mtOther.source.equals(mt.source) && mtOther.app.equals(mt.app) && !mtOther.destination.equals(mt.destination) 
                                        && !setOfServersToBeReleased.contains(mtOther.destination)){
                                    mt=mtOther;
                                    migrationToADyingServerIsFixed=true;
                                    break;
                                }
                            }                            
                        }
                        boolean alreadyMigrated=false;
                        for(MigrationTuple alreadyMigratedMT:migratedApps){
                            if(alreadyMigratedMT.source.equals(mt.source) && alreadyMigratedMT.app.equals(mt.app)){
                                alreadyMigrated=true;
                                break;
                            }
                        }
                        if(!alreadyMigrated){
                            if(mt.destination.appInstances.size()>0){ 
                                if(mt.destination.cpu_util+mt.app.cpu_util<TOTAL_CAPACITY && mt.destination.mem_util+mt.app.mem_util<TOTAL_CAPACITY){
                                    migratedApps.add(mt);                                    
                                    this.sim.migrateApp(mt.source, mt.app, mt.destination);
                                }
                                else{
                                    boolean migrationResultingInOverlaodingIsFixed=false;
                                    for(MigrationTuple mtOther:this.migrationPlanGlobalBest){
                                        if(mtOther.source.equals(mt.source) && mtOther.app.equals(mt.app) && !mtOther.destination.equals(mt.destination)
                                                && !setOfServersToBeReleased.contains(mtOther.destination)){
                                            if(mtOther.destination.appInstances.size()>0 && !migratedApps.contains(mtOther) && 
                                                    mtOther.destination.cpu_util+mtOther.app.cpu_util<TOTAL_CAPACITY && mtOther.destination.mem_util+mtOther.app.mem_util<TOTAL_CAPACITY){                                                
                                                mt=mtOther;                                                        
                                                migratedApps.add(mt);                                                
                                                this.sim.migrateApp(mt.source, mt.app, mt.destination);                                                 
                                                migrationResultingInOverlaodingIsFixed=true;
                                                break;
                                            }
                                        }
                                    }                                    
                                }
                            }
                            else{                                 
                                boolean migrationToAnEmptyServerIsFixed=false;
                                for(MigrationTuple mtOther:this.migrationPlanGlobalBest){
                                    if(mtOther.source.equals(mt.source) && mtOther.app.equals(mt.app) && !mtOther.destination.equals(mt.destination) 
                                            && !setOfServersToBeReleased.contains(mtOther.destination) && mtOther.destination.appInstances.size()>0 &&
                                            !migratedApps.contains(mtOther) && mtOther.destination.cpu_util+mtOther.app.cpu_util<TOTAL_CAPACITY && 
                                            mtOther.destination.mem_util+mtOther.app.mem_util<TOTAL_CAPACITY){                                        
                                        mt=mtOther;
                                        migratedApps.add(mt);                                        
                                        this.sim.migrateApp(mt.source, mt.app, mt.destination);                                         
                                        migrationToAnEmptyServerIsFixed=true;
                                        break;
                                    }
                                }                                
                            }
                        }                        
                    }
                }
                
                for(AppServer server:setOfServersToBeReleased){
                    if(server.appInstances.size()==0){ 
                        this.sim.terminateServer(server);                        
                    }
                }
            }
            
            this.sim.report(); 
            this.antControllerVR.updateGlobalCPUAndMEMAveragesOfCloseToCompletionServers();            
            if(totalNumberOfServersInThePreviousIteration>this.sim.appServers.size()){
                totalNumberOfServersInThePreviousIteration=this.sim.appServers.size();
                noImprovementCounter=0;
            }
            else{
                if(noImprovementCounter<maxIterationsForNoImprovement){
                    noImprovementCounter++;
                }
                else{
                    break; 
                }
            }        
        this.migrationPlanGlobalBest.clear();         
        this.migrationPlanGlobalBest=null;
        this.antGlobalBest=null;                
        }         
        millisAtEnd = System.currentTimeMillis();        
        this.sim.printServersAndAppInstances();
        this.sim.closeFile();
        this.sim.closeDetailedFile();
    }

}
