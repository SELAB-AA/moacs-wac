/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author SELAB
 */
public class AntVR extends Ant implements Runnable, ExperimentParameters{ 
// an ant in the 1st ACS colony that aims to maximize the no. of released VMs |VR|
    String name;
    ExperimentalSetupAndReporting sim;
    List<MigrationTuple> migrationPlan; 
    List<MigrationTuple> migrationPlanTMP; 
    List<MigrationTuple> traversedTuples; 
    double scoreLocalBest;    
    final static double Q0=0.9;     
    HashMap<MigrationTuple, Double> tuples2heuristic; 
    HashMap<MigrationTuple, Double> tuples2prob; 
    
    public AntVR(String name, ExperimentalSetupAndReporting sim){
        this.name=name;
        this.sim=sim;
        this.migrationPlan=new ArrayList();
        this.migrationPlanTMP=new ArrayList();
        this.traversedTuples=new ArrayList();
        this.scoreLocalBest=0.0;        
        this.tuples2heuristic=new HashMap();
        this.tuples2prob=new HashMap();
    }
    
    @Override
    public void run(){
        this.initializeUsedCapacities();
        this.initializeHeuristicsAndProbabilities();
        int numTotalTuples=this.sim.moacswacController.antControllerVR.setOfAllTuples.size();
        int numTuplesTraversed=0;                
        while(numTuplesTraversed<numTotalTuples){ 
            numTuplesTraversed+=1;
            this.updateHeuristicValue(this); 
            MigrationTuple selectedTuple=null;
            double q=this.sim.random.nextDouble();             
            if(q>Q0){                
                this.updateProbabilities(this);                  
                int randomIndex=this.sim.random.nextInt(this.sim.moacswacController.antControllerVR.setOfAllTuples.size()); 
                MigrationTuple maxProbTuple=this.sim.moacswacController.antControllerVR.setOfAllTuples.get(randomIndex);                
                for(MigrationTuple mt:this.sim.moacswacController.antControllerVR.setOfAllTuples){
                    if(!this.traversedTuples.contains(mt) && this.tuples2prob.get(mt)>this.tuples2prob.get(maxProbTuple)){
                        maxProbTuple=mt;                    
                    }
                }
                selectedTuple=maxProbTuple;                
            }
            else{ 
                MigrationTuple argMaxTuple=null;                                
                for(MigrationTuple mt:this.sim.moacswacController.antControllerVR.setOfAllTuples){
                    if(!this.traversedTuples.contains(mt)){
                        if(argMaxTuple==null){
                            argMaxTuple=mt;    
                        }
                        else{
                            double pheromone=this.sim.moacswacController.antControllerVR.tuples2pheromone.get(mt);
                            double heuristic=this.tuples2heuristic.get(mt);
                            double pheromoneIntoHeuristicPowBeta = pheromone * Math.pow(heuristic, AntControllerVR.BETA );
                            double pheromoneArgMax=this.sim.moacswacController.antControllerVR.tuples2pheromone.get(argMaxTuple);    
                            double heuristicArgMax=this.tuples2heuristic.get(argMaxTuple);
                            double pheromoneIntoHeuristicPowBetaOfArgMax = pheromoneArgMax * Math.pow(heuristicArgMax, AntControllerVR.BETA );
                            if(pheromoneIntoHeuristicPowBeta>pheromoneIntoHeuristicPowBetaOfArgMax){
                                argMaxTuple=mt;
                            }
                        }
                    }
                }
                selectedTuple=argMaxTuple;
            }
            
            if(selectedTuple==null){
                break; // if no more tuples are left, break the while loop
            }           
            
            this.migrationPlanTMP.add(selectedTuple);
            this.traversedTuples.add(selectedTuple);
            
            if(AntControllerVR.PHEROMONE_0==0){
                this.sim.moacswacController.antControllerVR.computePHEROMONE_0();
            } 

            this.sim.moacswacController.antControllerVR.tuples2pheromone.put(selectedTuple, ( (1-this.sim.moacswacController.antControllerVR.PETA)*this.sim.moacswacController.antControllerVR.tuples2pheromone.get(selectedTuple)
                            + (this.sim.moacswacController.antControllerVR.PETA*this.sim.moacswacController.antControllerVR.PHEROMONE_0) ));
            
            double updatedCPUatSource=this.server2usedCPU.get(selectedTuple.source)-selectedTuple.app.cpu_util;
            double updatedMEMatSource=this.server2usedMEM.get(selectedTuple.source)-selectedTuple.app.mem_util;
            double updatedCPUatDestination=this.server2usedCPU.get(selectedTuple.destination)+selectedTuple.app.cpu_util;
            double updatedMEMatDestination=this.server2usedMEM.get(selectedTuple.destination)+selectedTuple.app.mem_util;
            double sourceServerOriginalCPU=0.0;
            double sourceServerOriginalMEM=0.0;
            double destinationServerOriginalCPU=0.0;
            double destinationServerOriginalMEM=0.0;
            for(AppServer server:this.sim.appServers){
                if(server.equals(selectedTuple.source)){
                    sourceServerOriginalCPU=server.cpu_util;
                    sourceServerOriginalMEM=server.mem_util;
                }
                if(server.equals(selectedTuple.destination)){
                    destinationServerOriginalCPU=server.cpu_util;
                    destinationServerOriginalMEM=server.mem_util;
                }
            }
            
            if(destinationServerOriginalCPU+selectedTuple.app.cpu_util<=TOTAL_CAPACITY && 
                    destinationServerOriginalMEM+selectedTuple.app.mem_util<=TOTAL_CAPACITY && 
                    sourceServerOriginalCPU-selectedTuple.app.cpu_util>=0.0 && 
                    sourceServerOriginalMEM-selectedTuple.app.mem_util>=0.0){    
                this.server2usedCPU.put(selectedTuple.source, updatedCPUatSource);
                this.server2usedMEM.put(selectedTuple.source, updatedMEMatSource);
                this.server2usedCPU.put(selectedTuple.destination, updatedCPUatDestination);
                this.server2usedMEM.put(selectedTuple.destination, updatedMEMatDestination);
                double scoreTMP=this.sim.moacswacController.antControllerVR.calculateScore(migrationPlanTMP, this); 
            
                if(scoreTMP>this.scoreLocalBest){                     
                    this.scoreLocalBest=scoreTMP;
                    this.migrationPlan.add(selectedTuple); 
                }               
            }            
        }
    }

    public void initializeUsedCapacities(){                        
        this.server2usedCPU=new HashMap();
        this.server2usedMEM=new HashMap();
        for(AppServer server:this.sim.moacswacController.antControllerVR.underutilizedServers){ 
            this.server2usedCPU.put(server, server.cpu_util);
            this.server2usedMEM.put(server, server.mem_util);            
        }        
    }
    
    public void initializeHeuristicsAndProbabilities(){
        for(MigrationTuple tuple: this.sim.moacswacController.antControllerVR.setOfAllTuples){
            this.tuples2heuristic.put(tuple, 0.0);            
            this.tuples2prob.put(tuple, 0.0); 
        }
    }
    
    public void updateHeuristicValue(Ant ant){
        for(MigrationTuple tupleInLoop:this.sim.moacswacController.antControllerVR.setOfAllTuples){
            double heuristicValue_tuple=0.0;            
            if( (ant.server2usedCPU.get(tupleInLoop.destination)+tupleInLoop.app.cpu_util<=TOTAL_CAPACITY &&
                    ant.server2usedMEM.get(tupleInLoop.destination)+tupleInLoop.app.mem_util<=TOTAL_CAPACITY) ){                                    
                double k_tuple_CPU=(ant.server2usedCPU.get(tupleInLoop.destination)+tupleInLoop.app.cpu_util)/(double)TOTAL_CAPACITY;                            
                double k_tuple_MEM=(ant.server2usedMEM.get(tupleInLoop.destination)+tupleInLoop.app.mem_util)/(double)TOTAL_CAPACITY;                            
                double k_tuple= (Math.abs(k_tuple_CPU) + Math.abs(k_tuple_MEM))/(double)2.0;                
                heuristicValue_tuple=k_tuple;
                
            }
            this.tuples2heuristic.put(tupleInLoop, heuristicValue_tuple);            
        }
    }    
       
    public void updateProbabilities(AntVR ant){        
        for(MigrationTuple tupleInLoop:this.sim.moacswacController.antControllerVR.setOfAllTuples){ 
            double pheromone=this.sim.moacswacController.antControllerVR.tuples2pheromone.get(tupleInLoop);
            double heuristic=this.tuples2heuristic.get(tupleInLoop);
            double sumOfAllAppsPheromoneIntoHeuristicNotYetTraversed=0.0;
            List<Double> pheromoneOtherAppList=new ArrayList(); 
            List<Double> heuristicOtherAppList=new ArrayList();
            for(MigrationTuple mt:this.sim.moacswacController.antControllerVR.setOfAllTuples){
                if(!ant.traversedTuples.contains(mt)){                                                         
                    pheromoneOtherAppList.add(this.sim.moacswacController.antControllerVR.tuples2pheromone.get(mt));                     
                    heuristicOtherAppList.add(this.tuples2heuristic.get(mt)); 
                }                                 
            }                             
            
            for(int i=0; i<pheromoneOtherAppList.size(); i++){
                sumOfAllAppsPheromoneIntoHeuristicNotYetTraversed+=(pheromoneOtherAppList.get(i)*Math.pow(heuristicOtherAppList.get(i),AntControllerVR.BETA));
            }
            
            double probability=( pheromone*Math.pow(heuristic,AntControllerVR.BETA) ) / (double)(sumOfAllAppsPheromoneIntoHeuristicNotYetTraversed);
            if(Double.isNaN(probability)){
                probability=0.0;
            }
            this.tuples2prob.put(tupleInLoop, probability);             
           }
    }
}
