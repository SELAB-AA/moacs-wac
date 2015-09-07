/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 * @author aashraf
 */
public class AppServer implements ExperimentParameters{
    String name;
    ExperimentalSetupAndReporting sim;    
    int serverNumber;    
    double cpu_util; 
    double mem_util; 
    CopyOnWriteArrayList<WebAppInstance> appInstances = new CopyOnWriteArrayList<WebAppInstance>();
    long startTime; 
        
    public AppServer(int serverNumber, ExperimentalSetupAndReporting sim){
        this.name="appserver"+serverNumber;
        this.sim=sim;
        this.serverNumber=serverNumber;                
        this.cpu_util=0.0;
        this.mem_util=0.0;
        this.startTime=this.sim.random.nextInt(LENGTH_RENTING_HOUR_IN_SECONDS)+1;
    }    
}
