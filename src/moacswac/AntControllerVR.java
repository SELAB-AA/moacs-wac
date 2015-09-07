/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author SELAB
 */
public class AntControllerVR implements Runnable, ExperimentParameters{ 
// 1st ACS colony to maximize the no. of released VMs |VR|
    ExperimentalSetupAndReporting sim;
    
    static double PHEROMONE_0=0.0; 
    
    final static double BETA=2.0; 
    final static double PETA=0.1; 
    final static int N_CYCLES=2; 
    final static int N_ANTS=10; 
    final static double E=0.02; 
    
    static int nTotalAntThreadsVR=0; 
        
    List<MigrationTuple> setOfAllTuples; 
    
    HashMap<MigrationTuple, Double> tuples2pheromone; 
    List<MigrationTuple> migrationPlanGlobalBestVR;
    AntVR antGlobalBestVR;
    
    List<AppServer> underutilizedServers; 
    
    List<AppServer> closeToCompletionServers; 
    List<AppServer> closeToCompletionAndUnderutilizedServers;
    
    final int NEIGHBORHOOD_SIZE=5;
    List<List<AppServer>> allNeighborhoods;
    
    double globalCPULoadAvgOfCloseToCompletionServers=0.0;
    double globalMEMAvgOfCloseToCompletionServers=0.0;
        
    public AntControllerVR(ExperimentalSetupAndReporting sim){
        this.sim=sim;
        
        this.setOfAllTuples=new ArrayList();        
        
        this.tuples2pheromone=new HashMap();
        
        this.migrationPlanGlobalBestVR=null;
        this.antGlobalBestVR=null;
        
        this.underutilizedServers=new ArrayList();
        
        this.closeToCompletionServers=new ArrayList();
        this.closeToCompletionAndUnderutilizedServers=new ArrayList();
                
        this.allNeighborhoods=new ArrayList();
    }
    
    @Override
    public void run(){
        if(this.closeToCompletionAndUnderutilizedServers.size()>=1 ){
            this.makeGlobalBestMigrationPlanVR();
        }
    }
    
    public void makeGlobalBestMigrationPlanVR(){         
        this.initializeAllTuples();  
        this.migrationPlanGlobalBestVR=null;        
        for(int i=0; i<N_CYCLES;i++){
            List<AntVR> ants=new ArrayList();
            List<Thread> antThreads=new ArrayList();        
            for(int j=0; j<N_ANTS;j++){
                AntVR ant=new AntVR("Ant"+(j+1),this.sim);                
                ants.add(ant);
                Thread antThread=new Thread(ant);
                antThreads.add(antThread);
                antThread.start();
                nTotalAntThreadsVR+=1;
            }
            
            for(Thread t:antThreads){
                try{
                    t.join(); 
                }
                catch(InterruptedException e){
                    System.out.println(e);
                    e.printStackTrace();                
                }
            }
            
            int randomIndex=this.sim.random.nextInt(ants.size());            
            List<MigrationTuple> migrationPlanCycleBest=new ArrayList(ants.get(randomIndex).migrationPlan);
            AntVR antCycleBest=ants.get(randomIndex); 
            
            for(AntVR ant:ants){
                if(this.calculateScore(ant.migrationPlan, ant) > this.calculateScore(migrationPlanCycleBest, antCycleBest)){
                    migrationPlanCycleBest.clear(); 
                    migrationPlanCycleBest=new ArrayList(ant.migrationPlan);
                    antCycleBest=ant;
                }
            }
            if(this.migrationPlanGlobalBestVR==null || this.calculateScore(migrationPlanCycleBest, antCycleBest)>this.calculateScore(this.migrationPlanGlobalBestVR, this.antGlobalBestVR)){
                if(this.migrationPlanGlobalBestVR!=null){
                    this.migrationPlanGlobalBestVR.clear();
                }
                this.migrationPlanGlobalBestVR=new ArrayList(migrationPlanCycleBest); 
                this.antGlobalBestVR=antCycleBest;                
            }
            
            this.applyGlobalPheromoneTrailEvaporationRule();
            ants.clear();
            antThreads.clear();
        }
    }
    
    public void initializeAllTuples(){                    
        this.tuples2pheromone.clear();        
        this.setOfAllTuples.clear();
        this.allNeighborhoods.clear();                        
        if(this.underutilizedServers.size() > NEIGHBORHOOD_SIZE){             
            allNeighborhoods.add(new ArrayList<AppServer>()); 
            for(AppServer server: this.underutilizedServers){
                if(allNeighborhoods.get(allNeighborhoods.size()-1).size()==NEIGHBORHOOD_SIZE){                    
                    allNeighborhoods.add(new ArrayList<AppServer>());
                }
                allNeighborhoods.get(allNeighborhoods.size()-1).add(server); 
            }            
            for(List<AppServer> neighborhood:this.allNeighborhoods){
                if(neighborhood.size()>=2){
                    for(AppServer sourceServer:neighborhood){                    
                        for(WebAppInstance app:sourceServer.appInstances){
                            for(AppServer destinationServer:neighborhood){
                                if(destinationServer!=sourceServer && this.closeToCompletionAndUnderutilizedServers.contains(sourceServer) && this.underutilizedServers.contains(destinationServer)
                                        && !this.closeToCompletionAndUnderutilizedServers.contains(destinationServer)){
                                    MigrationTuple aNewTuple = new MigrationTuple(sourceServer, destinationServer, app);
                                    this.setOfAllTuples.add(aNewTuple);
                                }
                            }
                        }
                    }
                }
                else if(neighborhood.size()==1){
                    for(AppServer sourceServer:neighborhood){                    
                        for(WebAppInstance app:sourceServer.appInstances){
                            for(AppServer destinationServer:this.allNeighborhoods.get(0)){ 
                                if(destinationServer!=sourceServer && this.closeToCompletionAndUnderutilizedServers.contains(sourceServer) && this.underutilizedServers.contains(destinationServer)
                                        && !this.closeToCompletionAndUnderutilizedServers.contains(destinationServer)){
                                    MigrationTuple aNewTuple = new MigrationTuple(sourceServer, destinationServer, app);
                                    this.setOfAllTuples.add(aNewTuple);
                                }
                            }
                        }
                    }                 
                }
            }
            this.computePHEROMONE_0();
            for(MigrationTuple tuple:this.setOfAllTuples){
                this.tuples2pheromone.put(tuple, PHEROMONE_0); 
            }
        }
        else{ 
            for(AppServer sourceServer:this.underutilizedServers){
                for(WebAppInstance app:sourceServer.appInstances){
                    for(AppServer destinationServer:this.underutilizedServers){ 
                        if(destinationServer!=sourceServer && this.closeToCompletionAndUnderutilizedServers.contains(sourceServer) && this.underutilizedServers.contains(destinationServer)
                                && !this.closeToCompletionAndUnderutilizedServers.contains(destinationServer)){ 
                            MigrationTuple aNewTuple = new MigrationTuple(sourceServer, destinationServer, app);
                            this.setOfAllTuples.add(aNewTuple);
                        }                    
                    }
                }
            }
            this.computePHEROMONE_0();
            for(MigrationTuple tuple:this.setOfAllTuples){
                this.tuples2pheromone.put(tuple, PHEROMONE_0); 
            }
        }
        
        System.out.println("nTuples="+this.setOfAllTuples.size());
    }
    
    public void computePHEROMONE_0(){
        int nServers=this.sim.appServers.size(); 
        int nServersForIdealMPSize=this.closeToCompletionAndUnderutilizedServers.size();
        
        if(nServersForIdealMPSize<=0){            
            nServersForIdealMPSize=1;            
        }
        this.sim.updateAvgNumAppsPerServer();        
        int Lnn=nServersForIdealMPSize*this.sim.avgNumAppsPerServer;        
        PHEROMONE_0=1.0/(double)(nServers*Lnn);         
    }
    
    public void applyGlobalPheromoneTrailEvaporationRule(){
        double pheromoneMigrationPlanGlobalBestVR=this.calculateScore(this.migrationPlanGlobalBestVR, this.antGlobalBestVR);
        for(MigrationTuple tupleInLoop:this.setOfAllTuples){ 
            if(this.migrationPlanGlobalBestVR.contains(tupleInLoop)){
                this.tuples2pheromone.put(tupleInLoop, ((1-PETA)*this.tuples2pheromone.get(tupleInLoop)) + (PETA*pheromoneMigrationPlanGlobalBestVR) ); 
            }
            else{
                this.tuples2pheromone.put(tupleInLoop, (1-PETA)*this.tuples2pheromone.get(tupleInLoop)); 
            }
        }
    }
    

    public double calculateScore(List<MigrationTuple> migrPlan, Ant ant){ 
        List<AppServer> setOfServersToBeReleased=this.getServersToBeReleased(migrPlan); 
        int nReleasedServers=setOfServersToBeReleased.size();         
        List<Double> usedCapacities=new ArrayList();
        for(AppServer server:ant.server2usedCPU.keySet()){
            double usedCapacityServer=ant.server2usedCPU.get(server)+ant.server2usedMEM.get(server);
            usedCapacities.add(usedCapacityServer);                            
        }        
        double variance=this.getVariance(usedCapacities)/(double)100.0; 
        double score=nReleasedServers + Math.pow(variance, E);
        return score;
    }
    
    public void updateCloseToCompletionServers(){        
        this.closeToCompletionServers.clear(); 
        for(AppServer server:this.sim.appServers){             
            long serverPassedTime=this.sim.now()-server.startTime; 
            if(serverPassedTime>LENGTH_RENTING_HOUR_IN_SECONDS){
                serverPassedTime=serverPassedTime%LENGTH_RENTING_HOUR_IN_SECONDS; 
            }
            long remainingTime=LENGTH_RENTING_HOUR_IN_SECONDS-serverPassedTime;
            if(remainingTime<REMAINING_TIME_UPPER_THRESHOLD && remainingTime>REMAINING_TIME_LOWER_THRESHOLD){
                if(!this.closeToCompletionServers.contains(server)){
                    this.closeToCompletionServers.add(server);
                }
            }            
        }               
    }
    
    public List<AppServer> getServersToBeReleased(List<MigrationTuple> migrPlan){
        List<AppServer> releasedServers=new ArrayList();
        HashMap<AppServer, ArrayList<WebAppInstance>> server2uniqueAppMigrations=new HashMap();
        HashMap<AppServer, ArrayList<WebAppInstance>> destination2appsWhenMigratingFrom=new HashMap(); 
        HashMap<AppServer, ArrayList<WebAppInstance>> destination2appsWhenMigratingTo=new HashMap();
        for(MigrationTuple aTuple: migrPlan){
            if(!releasedServers.contains(aTuple.source) && this.closeToCompletionAndUnderutilizedServers.contains(aTuple.source)){
                int nAppsOnServerAndMigratingToIT=aTuple.source.appInstances.size();
                for(MigrationTuple mtOther:migrPlan){
                    if(!mtOther.source.equals(aTuple.source) && mtOther.destination.equals(aTuple.source)){                        
                        if(!destination2appsWhenMigratingFrom.containsKey(mtOther.destination)){
                            if(mtOther.destination.cpu_util+mtOther.app.cpu_util<=TOTAL_CAPACITY &&
                                    mtOther.destination.mem_util+mtOther.app.mem_util<=TOTAL_CAPACITY){                                
                                destination2appsWhenMigratingFrom.put(mtOther.destination, new ArrayList());
                                destination2appsWhenMigratingFrom.get(mtOther.destination).add(mtOther.app);
                                nAppsOnServerAndMigratingToIT+=1;
                            }
                        }
                        else{
                            double destinationEstimatedCPU=mtOther.destination.cpu_util;
                            double destinationEstimatedMEM=mtOther.destination.mem_util;
                            for(WebAppInstance appMigratinTODestination: destination2appsWhenMigratingFrom.get(mtOther.destination)){
                                destinationEstimatedCPU+=appMigratinTODestination.cpu_util;
                                destinationEstimatedMEM+=appMigratinTODestination.mem_util;
                            }
                            if(destinationEstimatedCPU+mtOther.app.cpu_util<=TOTAL_CAPACITY &&
                                    destinationEstimatedMEM+mtOther.app.mem_util<=TOTAL_CAPACITY){                                
                                destination2appsWhenMigratingFrom.get(mtOther.destination).add(mtOther.app);                                
                                nAppsOnServerAndMigratingToIT+=1;
                            }
                        }
                    }
                }
                int nAppsMigratingFromServer=0;
                for(MigrationTuple mtOther:migrPlan){
                    if(mtOther.source.equals(aTuple.source)){
                        if(!server2uniqueAppMigrations.containsKey(aTuple.source)){
                            if(!destination2appsWhenMigratingTo.containsKey(mtOther.destination)){
                                if(mtOther.destination.cpu_util+mtOther.app.cpu_util<=TOTAL_CAPACITY &&
                                            mtOther.destination.mem_util+mtOther.app.mem_util<=TOTAL_CAPACITY){
                                    destination2appsWhenMigratingTo.put(mtOther.destination, new ArrayList());
                                    destination2appsWhenMigratingTo.get(mtOther.destination).add(mtOther.app);
                                
                                    server2uniqueAppMigrations.put(mtOther.source, new ArrayList());
                                    server2uniqueAppMigrations.get(mtOther.source).add(mtOther.app);
                                    nAppsMigratingFromServer+=1; 
                                }
                            }
                            else{
                                double destinationEstimatedCPU=mtOther.destination.cpu_util;
                                double destinationEstimatedMEM=mtOther.destination.mem_util;
                                for(WebAppInstance appMigratinTODestination: destination2appsWhenMigratingTo.get(mtOther.destination)){
                                    destinationEstimatedCPU+=appMigratinTODestination.cpu_util;
                                    destinationEstimatedMEM+=appMigratinTODestination.mem_util;
                                }
                                if(destinationEstimatedCPU+mtOther.app.cpu_util<=TOTAL_CAPACITY &&
                                        destinationEstimatedMEM+mtOther.app.mem_util<=TOTAL_CAPACITY){                                    
                                    destination2appsWhenMigratingTo.get(mtOther.destination).add(mtOther.app);
                                    
                                    server2uniqueAppMigrations.put(mtOther.source, new ArrayList());
                                    server2uniqueAppMigrations.get(mtOther.source).add(mtOther.app);
                                    nAppsMigratingFromServer+=1; 
                                }
                            }
                        }
                        else{
                            boolean uniqueAppDeployment=true;
                            for(WebAppInstance appYetOther:server2uniqueAppMigrations.get(mtOther.source)){
                                if(appYetOther.equals(mtOther.app)){
                                    uniqueAppDeployment=false;
                                    break;
                                }
                            }
                            if(uniqueAppDeployment){
                                if(!destination2appsWhenMigratingTo.containsKey(mtOther.destination)){
                                    if(mtOther.destination.cpu_util+mtOther.app.cpu_util<=TOTAL_CAPACITY &&
                                            mtOther.destination.mem_util+mtOther.app.mem_util<=TOTAL_CAPACITY){
                                        destination2appsWhenMigratingTo.put(mtOther.destination, new ArrayList());
                                        destination2appsWhenMigratingTo.get(mtOther.destination).add(mtOther.app);                                    
                                        server2uniqueAppMigrations.get(mtOther.source).add(mtOther.app);        
                                        nAppsMigratingFromServer+=1; 
                                    }
                                }
                                else{
                                    double destinationEstimatedCPU=mtOther.destination.cpu_util;
                                    double destinationEstimatedMEM=mtOther.destination.mem_util;
                                    for(WebAppInstance appMigratinTODestination: destination2appsWhenMigratingTo.get(mtOther.destination)){
                                        destinationEstimatedCPU+=appMigratinTODestination.cpu_util;
                                        destinationEstimatedMEM+=appMigratinTODestination.mem_util;
                                    }
                                    if(destinationEstimatedCPU+mtOther.app.cpu_util<=TOTAL_CAPACITY &&
                                            destinationEstimatedMEM+mtOther.app.mem_util<=TOTAL_CAPACITY){                                        
                                        destination2appsWhenMigratingTo.get(mtOther.destination).add(mtOther.app);                                    
                                        server2uniqueAppMigrations.get(mtOther.source).add(mtOther.app);        
                                        nAppsMigratingFromServer+=1; 
                                    }
                                }
                            }
                        }
                    }
                }
                if(nAppsOnServerAndMigratingToIT==nAppsMigratingFromServer){
                    releasedServers.add(aTuple.source); 
                }
            }
        }
        return releasedServers; 
    }
    
    public List<AppServer> getServersToBeReleasedOld(List<MigrationTuple> migrPlan){
        List<AppServer> releasedServers=new ArrayList();        
        HashMap<AppServer, CopyOnWriteArrayList<WebAppInstance>> server2uniqueAppMigrations=new HashMap(); 
        for(AppServer server:this.closeToCompletionAndUnderutilizedServers){ 
            int nAppsOnServer=server.appInstances.size();
            for(MigrationTuple mt:migrPlan){
                if(mt.destination.equals(server)){
                    nAppsOnServer+=1;
                }
            }
            int nAppsMigratingFromServer=0;
            for(MigrationTuple mt:migrPlan){                
                if(mt.source.equals(server)){
                    if(!server2uniqueAppMigrations.containsKey(server)){
                        server2uniqueAppMigrations.put(mt.source, new CopyOnWriteArrayList());
                        server2uniqueAppMigrations.get(mt.source).add(mt.app);
                        nAppsMigratingFromServer+=1; 
                    }
                    else{
                        boolean uniqueAppDeployment=true;
                        for(WebAppInstance appOther:server2uniqueAppMigrations.get(mt.source)){
                            if(appOther.equals(mt.app)){
                                uniqueAppDeployment=false;
                                break;
                            }
                        }
                        if(uniqueAppDeployment){
                            server2uniqueAppMigrations.get(mt.source).add(mt.app);
                            nAppsMigratingFromServer+=1; 
                        }
                    }
                }
            }
            if(nAppsOnServer==nAppsMigratingFromServer){
                releasedServers.add(server); 
            }                                
        }        
        return releasedServers;
    }
    
    private double getMean(List<Double> list){
        double sum=0.0;
        for(Double value:list){
            sum+=value;
        }
        return sum/(double)list.size();
    }
    
    private double getVariance(List<Double> list){
        double mean=this.getMean(list);
        double squaredDiff=0.0;
        for(Double value:list){
            squaredDiff+=(value-mean)*(value-mean);            
        }
        return squaredDiff/(double)list.size();
    }
    
    public void updateUnderutilizedServers(){
        this.underutilizedServers.clear();
        for(AppServer server:this.sim.appServers){            
            if(server.cpu_util<CPU_UNDERUTILIZATION_THRESHOLD && server.mem_util<MEM_UNDERUTILIZATION_THRESHOLD){                
                if(!this.underutilizedServers.contains(server)){                        
                    this.underutilizedServers.add(server);
                }                                        
            }                                
        }
    }
    
    public void updateCloseToCompletionAndUnderutilizedServers(){
        this.closeToCompletionAndUnderutilizedServers.clear();
        for(AppServer server:this.underutilizedServers){
            if(this.closeToCompletionServers.contains(server)){
                this.closeToCompletionAndUnderutilizedServers.add(server);                
            }
        }
    }
    
    public void updateGlobalCPUAndMEMAveragesOfCloseToCompletionServers(){
        this.updateCloseToCompletionServers();
        
        this.globalCPULoadAvgOfCloseToCompletionServers=0.0;
        this.globalMEMAvgOfCloseToCompletionServers=0.0;
        for(AppServer server:this.closeToCompletionServers){
            this.globalCPULoadAvgOfCloseToCompletionServers+=server.cpu_util;
            this.globalMEMAvgOfCloseToCompletionServers+=server.mem_util;
        }
        this.globalCPULoadAvgOfCloseToCompletionServers = this.globalCPULoadAvgOfCloseToCompletionServers/(double)this.closeToCompletionServers.size();
        this.globalMEMAvgOfCloseToCompletionServers = this.globalMEMAvgOfCloseToCompletionServers/(double)this.closeToCompletionServers.size();
    }
        
    
}
