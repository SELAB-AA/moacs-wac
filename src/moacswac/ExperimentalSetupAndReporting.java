/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Random;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import java.text.NumberFormat;
import java.text.DecimalFormat;

/**
 *
 * @author aashraf
 */
public class ExperimentalSetupAndReporting implements ExperimentParameters {
    CopyOnWriteArrayList<AppServer> appServers;    
    Random random;
    long simStartTime; 
    double global_load_avg, global_mem_utilization;    
    BufferedWriter bufferedWriter;
    BufferedWriter bufferedWriterDetailed;
    int totalAppMigrations;
    int avgNumAppsPerServer=0;
    MOACSWACController moacswacController;
    
    public ExperimentalSetupAndReporting(){
        this.appServers=new CopyOnWriteArrayList();
        this.random=new Random();
        random.setSeed(randomSeed); 
        this.simStartTime=System.currentTimeMillis();
        this.global_load_avg=0.0;
        this.global_mem_utilization=0.0;
        this.totalAppMigrations=0;
        this.moacswacController=new MOACSWACController(this);
    }
    
    //@Override
    public void run(){
        for(int i=0; i<NUM_SERVERS_TO_CREATE; i++){                                    
            this.addNewServer();            
        }        
        for(int i=1; i<=N_APPS; i++){                                                
            int numIthAppInstances=this.random.nextInt(HOW_MANY_MAX_INSTANCES_OF_AN_APP)+1; 
            for(int j=1; j<=numIthAppInstances; j++){
                double app_cpu=this.random.nextDouble()*HOW_MUCH_MAX_CPU_OF_A_SERVER_MAY_AN_APP_INSTANCE_CONSUME; 
                if(app_cpu<HOW_MUCH_MIN_CPU_OF_A_SERVER_MAY_AN_APP_INSTANCE_CONSUME){
                    app_cpu += HOW_MUCH_MIN_CPU_OF_A_SERVER_MAY_AN_APP_INSTANCE_CONSUME;                     
                }
                double appMemDiffPercentage=this.random.nextInt(MAX_APP_CPU_MEM_DIFF_PERCENTAGE)+1; 
                double app_mem=app_cpu-(app_cpu*appMemDiffPercentage/100.0); 
                int appNumber=i;                 
                this.deployApp(appNumber, app_cpu, app_mem);
            }
        }        
        this.createNewReportFile("sim-report.dat");
        this.createNewDetailedReportFile("sim-report-detailed.dat");
        this.report(); 
        this.moacswacController.run();
    }
    
    public void printServersAndAppInstances(){
        this.writeNewLineToDetailedFile("Algorithm execution time in milliseconds: " + (MOACSWACController.millisAtEnd - MOACSWACController.millisAtStart));
        this.writeNewLineToDetailedFile("Algorithm execution time in seconds: " + (MOACSWACController.millisAtEnd - MOACSWACController.millisAtStart)/1000);
        System.out.println("Algorithm execution time in milliseconds: " + (MOACSWACController.millisAtEnd - MOACSWACController.millisAtStart));
        System.out.println("Algorithm execution time in seconds: " + (MOACSWACController.millisAtEnd - MOACSWACController.millisAtStart)/1000);
        System.out.println("---------------------------------------------------------");
        
        this.writeNewLineToDetailedFile("---------------------------------------------------------");
        for(int i=0; i<this.appServers.size(); i++){
            this.writeNewLineToDetailedFile("--- Server "+this.appServers.get(i).name +", cpu="+this.appServers.get(i).cpu_util+ 
                    ", mem="+this.appServers.get(i).mem_util+", startTime="+this.appServers.get(i).startTime+
                    ", now="+this.now() + " ---");
            for(int j=0; j<this.appServers.get(i).appInstances.size();j++){
                this.writeNewLineToDetailedFile("app "+this.appServers.get(i).appInstances.get(j).appNumber + ", cpu="+this.appServers.get(i).appInstances.get(j).cpu_util+
                        ", mem="+this.appServers.get(i).appInstances.get(j).mem_util);
            }
            this.writeNewLineToDetailedFile("");
        }
        this.writeNewLineToDetailedFile("---------------------------------------------------------");
    }
    
    public void report(){         
        double global_load_avgSum=0.0;        
        for(AppServer server:this.appServers){
            global_load_avgSum+=server.cpu_util;
        }       
        this.global_load_avg=global_load_avgSum/this.appServers.size(); 
        double global_mem_utilizationSum=0.0;        
        for(AppServer server:this.appServers){
            global_mem_utilizationSum+=server.mem_util;
        }        
        this.global_mem_utilization=global_mem_utilizationSum/this.appServers.size(); 
        
        double global_cpu_app_instances_sum=0.0;
        double global_mem_app_instances_sum=0.0;
        int num_app_instances=0;
        for(AppServer server:this.appServers){
            for(WebAppInstance app:server.appInstances){
                global_cpu_app_instances_sum+=app.cpu_util;
                global_mem_app_instances_sum+=app.mem_util;
                num_app_instances+=1;
            }            
        }        
        double global_load_avg_app_instances=global_cpu_app_instances_sum/num_app_instances;
        double global_mem_utilization_app_instances=global_mem_app_instances_sum/num_app_instances;
        this.updateAvgNumAppsPerServer();
        
        int totalAppInstances=0;
        for(AppServer server: this.appServers){
            totalAppInstances+=server.appInstances.size();
        }        
        this.moacswacController.antControllerVR.updateGlobalCPUAndMEMAveragesOfCloseToCompletionServers();        
        NumberFormat formatter = new DecimalFormat("#.##");

        this.writeNewLineToFile(this.appServers.size()+", "+N_APPS+", "+totalAppInstances+", "+this.moacswacController.antControllerVR.setOfAllTuples.size()+", "+
                formatter.format(this.global_load_avg)+ ", "+formatter.format(this.global_mem_utilization)+", "+avgNumAppsPerServer+", "+
                formatter.format(global_load_avg_app_instances) + ", " + formatter.format(global_mem_utilization_app_instances) +
                ", " + this.totalAppMigrations+ ", "+this.moacswacController.antControllerVR.closeToCompletionServers.size()+", "+
                formatter.format(this.moacswacController.antControllerVR.globalCPULoadAvgOfCloseToCompletionServers)+", " + 
                formatter.format(this.moacswacController.antControllerVR.globalMEMAvgOfCloseToCompletionServers));        
        
        System.out.println("---------------------------------------------------------");
        System.out.println("nServers="+this.appServers.size()+", nWebApps="+N_APPS+", nAppInstances="+totalAppInstances+
                ", avgCPUServers="+formatter.format(this.global_load_avg)+", avgMEMServers="+formatter.format(this.global_mem_utilization)+"\n"+
                "avgNumAppsPerServer="+avgNumAppsPerServer+", avgCPUAppInstances="+formatter.format(global_load_avg_app_instances)+", avgMEMAppInstances="+formatter.format(global_mem_utilization_app_instances)+"\n"+
                "totalAppMigrations="+this.totalAppMigrations+", nCloseToCompletionServers="+this.moacswacController.antControllerVR.closeToCompletionServers.size()+
                ", avgCPUCloseToCompletionServers="+formatter.format(this.moacswacController.antControllerVR.globalCPULoadAvgOfCloseToCompletionServers)+
                ", avgMEMCloseToCompletionServers="+formatter.format(this.moacswacController.antControllerVR.globalMEMAvgOfCloseToCompletionServers));
        System.out.println("---------------------------------------------------------");
    }
    
    public void deployApp(int app, double app_cpu, double app_mem){
        if(this.appServers.size()==0){
            this.addNewServer();
        }
        if(APP_ALLOCATION_POLICY==MIN_USED_CAPACITY){
            AppServer selectedServer=this.appServers.get(0);
            for(AppServer server:this.appServers){                
                double usedCapacityServer=Math.abs(server.cpu_util)+Math.abs(server.mem_util);
                double usedCapacitySelectedServer=Math.abs(selectedServer.cpu_util)+Math.abs(selectedServer.mem_util);                 
                if(usedCapacityServer<usedCapacitySelectedServer){
                    if(server.cpu_util+app_cpu<100.0 && server.mem_util+app_mem<100.0){ 
                        selectedServer=server;
                    }                    
                }
            }
            if(selectedServer.cpu_util+app_cpu>=100.0 || selectedServer.mem_util+app_mem>=100.0){
                System.out.println("Can't deployApp in MIN_USED_CAPACITY. The selectedServer is too full to accommodate the app-instance.");
                System.out.println("selectedServer.cpu_util="+selectedServer.cpu_util+", app_cpu="+app_cpu+
                        ", selectedServer.mem_util="+selectedServer.mem_util+", app_mem="+app_mem);
            }
            else{
                WebAppInstance appInstance=new WebAppInstance(app, selectedServer, app_cpu, app_mem);
                selectedServer.appInstances.add(appInstance);
                selectedServer.cpu_util+=app_cpu;
                selectedServer.mem_util+=app_mem;                
            }            
        }
        
        if(APP_ALLOCATION_POLICY==MAX_USED_CAPACITY){
            AppServer selectedServer=this.appServers.get(0);            
            if(selectedServer.cpu_util+app_cpu>=100.0 || selectedServer.mem_util+app_mem>=100.0){
                for(AppServer server:this.appServers){
                    if(server.cpu_util+app_cpu<100.0 && server.mem_util+app_mem<100.0){
                        selectedServer=server;
                    }
                }
            }            
            for(AppServer server:this.appServers){                
                double usedCapacityServer=Math.abs(server.cpu_util)+Math.abs(server.mem_util); 
                double usedCapacitySelectedServer=Math.abs(selectedServer.cpu_util)+Math.abs(selectedServer.mem_util);                 
                if(usedCapacityServer>usedCapacitySelectedServer){
                    if(server.cpu_util+app_cpu<100.0 && server.mem_util+app_mem<100.0){ 
                        selectedServer=server;
                    }                    
                }
            }
            if(selectedServer.cpu_util+app_cpu>=100.0 || selectedServer.mem_util+app_mem>=100.0){
                System.out.println("Can't deployApp in MAX_USED_CAPACITY. The selectedServer is too full to accommodate the app-instance.");
                System.out.println("selectedServer.cpu_util="+selectedServer.cpu_util+", app_cpu="+app_cpu+
                        ", selectedServer.mem_util="+selectedServer.mem_util+", app_mem="+app_mem);
            }
            else{
                WebAppInstance appInstance=new WebAppInstance(app, selectedServer, app_cpu, app_mem);
                selectedServer.appInstances.add(appInstance);
                selectedServer.cpu_util+=app_cpu;
                selectedServer.mem_util+=app_mem;
            }
        }
                        
    }
    
    public void updateAvgNumAppsPerServer(){        
        int numAppsPerServerSum=0;
        for(AppServer server:this.appServers){
            numAppsPerServerSum+=server.appInstances.size();
        }        
        this.avgNumAppsPerServer=numAppsPerServerSum/this.appServers.size();
    }
    
    public void migrateApp(AppServer source, WebAppInstance app, AppServer destination){ 
        if(source.appInstances.contains(app) && !destination.appInstances.contains(app)){
            source.appInstances.remove(app); 
            source.cpu_util-=app.cpu_util;
            source.mem_util-=app.mem_util;
            destination.appInstances.add(app);
            destination.cpu_util+=app.cpu_util;
            destination.mem_util+=app.mem_util;             
            this.totalAppMigrations+=1;    
        }
    }
    
    public long now(){
        return 3300; // assuming that the current time is xx:55:00 (hh:mm:ss) 
    }
    
    public void addNewServer(){
        if(this.appServers.size()==0){            
            AppServer server=new AppServer(1,this);
            this.appServers.add(server);
        }
        else{
            int index=(int)(this.appServers.get(this.appServers.size()-1).name.lastIndexOf("r")+1);             
            int serverNumber=Integer.parseInt(this.appServers.get(this.appServers.size()-1).name.substring(index))+1;            
            AppServer server=new AppServer(serverNumber,this);
            this.appServers.add(server);
        }
    }
    
    public void terminateServer(AppServer server){                
        if(this.appServers.contains(server)){
            this.appServers.remove(server);
        }
    }
    
    public void createNewReportFile(String fileName){
        try{
            File file=new File(fileName);
            if(!file.exists()){
                file.createNewFile();
            }
            FileWriter fileWriter=new FileWriter(file.getAbsoluteFile());
            this.bufferedWriter=new BufferedWriter(fileWriter);
            this.bufferedWriter.write("n_servers, n_web_apps, n_app_instances, n_migration_tuples, avg_cpu_servers, avg_mem_servers, avg_num_apps_per_server, "                    
                    + "avg_cpu_app_instances, avg_mem_app_instances, n_total_migrations, n_close_to_completion_servers, avg_cpu_close_comp_servers, avg_mem_close_comp_servers ");
            this.bufferedWriter.newLine();            
        }
        catch(IOException e){
            System.out.println(e);
            e.printStackTrace();
        }
    }
    
    public void writeNewLineToFile(String content){
        try{
            this.bufferedWriter.write(content);
            this.bufferedWriter.newLine();            
        }
        catch(IOException e){
            System.out.println(e);
            e.printStackTrace();
        }
        this.flushBufferedWriter();
    }
    
    public void flushBufferedWriter(){
        try{
            this.bufferedWriter.flush(); 
        }
        catch(IOException e){
            System.out.println(e);
            e.printStackTrace();
        }
    }
    
    public void closeFile(){
        try{
            this.bufferedWriter.close();            
        }
        catch(IOException e){
            System.out.println(e);
            e.printStackTrace();
        }        
    }
    
    public void createNewDetailedReportFile(String fileName){
        try{
            File file=new File(fileName);
            if(!file.exists()){
                file.createNewFile();
            }
            FileWriter fileWriter=new FileWriter(file.getAbsoluteFile());
            this.bufferedWriterDetailed=new BufferedWriter(fileWriter);
        }
        catch(IOException e){
            System.out.println(e);
            e.printStackTrace();
        }
    }
    
    public void writeNewLineToDetailedFile(String content){
        try{
            this.bufferedWriterDetailed.write(content);
            this.bufferedWriterDetailed.newLine();            
        }
        catch(IOException e){
            System.out.println(e);
            e.printStackTrace();
        }
        this.flushBufferedWriterDetailedFile();
    }

    public void flushBufferedWriterDetailedFile(){
        try{
            this.bufferedWriterDetailed.flush(); 
        }
        catch(IOException e){
            System.out.println(e);
            e.printStackTrace();
        }
    }
    
    public void closeDetailedFile(){
        try{
            this.bufferedWriterDetailed.close();            
        }
        catch(IOException e){
            System.out.println(e);
            e.printStackTrace();
        }        
    }
    

}
