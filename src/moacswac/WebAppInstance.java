/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;

/**
 *
 * @author aashraf
 */
public class WebAppInstance{
    int appNumber;
    double cpu_util; 
    double mem_util; 
    AppServer server;             
    public WebAppInstance(int appNumber, AppServer server, double cpu_util, double mem_util){
        this.appNumber=appNumber;        
        this.cpu_util=cpu_util;
        this.mem_util=mem_util;
        this.server=server;              
    }    
}
