/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 *
 * @author SELAB
 */
public class AntControllerNM implements Runnable, ExperimentParameters{ 
// 2nd ACS colony to minimize the no. of app migrations nM
    ExperimentalSetupAndReporting sim;
    
    static double PHEROMONE_0=0.0; 
        
    final static double BETA=2.0; 
    final static double PETA=0.1; 
    final static int N_CYCLES_NM=2; 
    final static int N_ANTS_NM=10; 
    final static double E=0.02;    
    static int nTotalAntThreadsNM=0; 
        
    List<MigrationTuple> setOfAllTuples; 
    HashMap<MigrationTuple, Double> tuples2pheromone; 
    
    List<MigrationTuple> migrationPlanGlobalBestNM;
    AntNM antGlobalBestNM;
    
    final int NEIGHBORHOOD_SIZE=5;
    List<List<AppServer>> allNeighborhoods;
    
    public AntControllerNM(ExperimentalSetupAndReporting sim){
        this.sim=sim;
        this.setOfAllTuples=new ArrayList();        
        this.tuples2pheromone=new HashMap();
        
        this.migrationPlanGlobalBestNM=null;
        this.antGlobalBestNM=null;
        
        this.allNeighborhoods=new ArrayList();
    }
    
    @Override
    public void run(){
        if(this.sim.moacswacController.antControllerVR.closeToCompletionAndUnderutilizedServers.size()>=1 ){
            this.makeGlobalBestMigrationPlanNM(); 
        }
    }
    
    public void makeGlobalBestMigrationPlanNM(){        
        this.initializeAllTuples();  
        this.migrationPlanGlobalBestNM=null;        
        for(int i=0; i<N_CYCLES_NM;i++){
            List<AntNM> antsNM=new ArrayList();
            List<Thread> antThreadsNM=new ArrayList();        
            for(int j=0; j<N_ANTS_NM;j++){
                AntNM antNM=new AntNM("AntNM"+(j+1),this.sim);                
                antsNM.add(antNM);
                Thread antThreadNM=new Thread(antNM);
                antThreadsNM.add(antThreadNM);
                antThreadNM.start();
                nTotalAntThreadsNM+=1;
            }
            
            for(Thread t:antThreadsNM){
                try{
                    t.join(); 
                }
                catch(InterruptedException e){
                    System.out.println(e);
                    e.printStackTrace();                
                }
            }
            
            int randomIndex=this.sim.random.nextInt(antsNM.size());            
            List<MigrationTuple> migrationPlanCycleBest=new ArrayList(antsNM.get(randomIndex).migrationPlan); 
            AntNM antCycleBest=antsNM.get(randomIndex); 
            
            for(AntNM ant:antsNM){
                if(this.sim.moacswacController.antControllerVR.calculateScore(ant.migrationPlan, ant)>=this.sim.moacswacController.antControllerVR.calculateScore(migrationPlanCycleBest, antCycleBest)){
                    if(this.calculateScore(ant.migrationPlan, ant) > this.calculateScore(migrationPlanCycleBest, antCycleBest)){                        
                        migrationPlanCycleBest.clear(); 
                        migrationPlanCycleBest=new ArrayList(ant.migrationPlan);
                        antCycleBest=ant;
                    }
                }
            }            
                        
            if( this.migrationPlanGlobalBestNM==null || 
                    (this.sim.moacswacController.antControllerVR.calculateScore(migrationPlanCycleBest, antCycleBest)>=this.sim.moacswacController.antControllerVR.calculateScore(this.migrationPlanGlobalBestNM, this.antGlobalBestNM)
                    && this.calculateScore(migrationPlanCycleBest, antCycleBest) > this.calculateScore(this.migrationPlanGlobalBestNM, this.antGlobalBestNM)) ){
                if(this.migrationPlanGlobalBestNM!=null){
                    this.migrationPlanGlobalBestNM.clear();
                }
                this.migrationPlanGlobalBestNM=new ArrayList(migrationPlanCycleBest); 
                this.antGlobalBestNM=antCycleBest;
            }
            
            this.applyGlobalPheromoneTrailEvaporationRule();
            antsNM.clear();
            antThreadsNM.clear();
        } 
    }
    
    public void initializeAllTuples(){            
        this.tuples2pheromone.clear();
        this.setOfAllTuples.clear();
        this.allNeighborhoods.clear();                        
        if(this.sim.moacswacController.antControllerVR.underutilizedServers.size() > NEIGHBORHOOD_SIZE){
            allNeighborhoods.add(new ArrayList<AppServer>()); 
            for(AppServer server: this.sim.moacswacController.antControllerVR.underutilizedServers){
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
                                if(destinationServer!=sourceServer && this.sim.moacswacController.antControllerVR.closeToCompletionAndUnderutilizedServers.contains(sourceServer) && 
                                        this.sim.moacswacController.antControllerVR.underutilizedServers.contains(destinationServer)
                                        && !this.sim.moacswacController.antControllerVR.closeToCompletionAndUnderutilizedServers.contains(destinationServer)){
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
                                if(destinationServer!=sourceServer && this.sim.moacswacController.antControllerVR.closeToCompletionAndUnderutilizedServers.contains(sourceServer) && 
                                        this.sim.moacswacController.antControllerVR.underutilizedServers.contains(destinationServer)
                                        && !this.sim.moacswacController.antControllerVR.closeToCompletionAndUnderutilizedServers.contains(destinationServer)){
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
            for(AppServer sourceServer:this.sim.moacswacController.antControllerVR.underutilizedServers){
                for(WebAppInstance app:sourceServer.appInstances){
                    for(AppServer destinationServer:this.sim.moacswacController.antControllerVR.underutilizedServers){ 
                        if(destinationServer!=sourceServer && this.sim.moacswacController.antControllerVR.closeToCompletionAndUnderutilizedServers.contains(sourceServer) && 
                                this.sim.moacswacController.antControllerVR.underutilizedServers.contains(destinationServer)
                                && !this.sim.moacswacController.antControllerVR.closeToCompletionAndUnderutilizedServers.contains(destinationServer)){ 
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
    }
    
    public void computePHEROMONE_0(){
        int nServers=this.sim.appServers.size();
        int nServersForIdealMPSize=this.sim.moacswacController.antControllerVR.closeToCompletionAndUnderutilizedServers.size();
        if(nServersForIdealMPSize<=0){
            nServersForIdealMPSize=1;
        }
        this.sim.updateAvgNumAppsPerServer();
        int Lnn=nServersForIdealMPSize*this.sim.avgNumAppsPerServer;        
        PHEROMONE_0=1.0/(double)(nServers*Lnn);        
    }
    
    public void applyGlobalPheromoneTrailEvaporationRule(){
        double pheromoneMigrationPlanGlobalBestNM=this.calculateScore(this.migrationPlanGlobalBestNM, this.antGlobalBestNM);
        for(MigrationTuple tupleInLoop:this.setOfAllTuples){ 
            if(this.migrationPlanGlobalBestNM.contains(tupleInLoop)){
                this.tuples2pheromone.put(tupleInLoop, ((1-PETA)*this.tuples2pheromone.get(tupleInLoop)) + (PETA*pheromoneMigrationPlanGlobalBestNM) ); 
            }
            else{
                this.tuples2pheromone.put(tupleInLoop, (1-PETA)*this.tuples2pheromone.get(tupleInLoop));
            }
        }
    }
    
    public double calculateScore(List<MigrationTuple> migrPlan, Ant ant){         
        int nMigrations=migrPlan.size(); 
        double score=1.0/(double)nMigrations;
        return score;
    }
    
    public HashMap<AppServer, ArrayList<WebAppInstance>> getTotalNumberOfUniqueMigrations(List<MigrationTuple> tuples){
        HashMap<AppServer, ArrayList<WebAppInstance>> server2uniqueAppMigrations=new HashMap(); 
        for(MigrationTuple aTuple:tuples){         
            if(!server2uniqueAppMigrations.containsKey(aTuple.source)){
                server2uniqueAppMigrations.put(aTuple.source, new ArrayList());
                server2uniqueAppMigrations.get(aTuple.source).add(aTuple.app);                 
            }
            else{
                boolean uniqueAppDeployment=true;
                for(WebAppInstance appOther:server2uniqueAppMigrations.get(aTuple.source)){
                    if(appOther.equals(aTuple.app)){
                        uniqueAppDeployment=false;
                        break;
                    }
                }
                if(uniqueAppDeployment){
                    server2uniqueAppMigrations.get(aTuple.source).add(aTuple.app);                    
                }
            }
        }
        return server2uniqueAppMigrations;
    }

}
