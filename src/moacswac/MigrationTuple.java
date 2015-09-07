/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;

/**
 *
 * @author aashraf
 */
public class MigrationTuple {
    AppServer source; 
    AppServer destination; 
    WebAppInstance app;
    
    public MigrationTuple(AppServer source, AppServer destination, WebAppInstance app){
        this.source=source;
        this.destination=destination;
        this.app=app;
    }
}
