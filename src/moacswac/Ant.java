/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;

import java.util.HashMap;

/**
 *
 * @author aashraf
 */
public abstract class Ant {    
    HashMap<AppServer, Double> server2usedCPU; 
    HashMap<AppServer, Double> server2usedMEM; 
}
