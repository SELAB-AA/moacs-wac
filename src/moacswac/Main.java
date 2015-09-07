/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package moacswac;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

/**
 *
 * @author aashraf
 */
public class Main {
    static List<Thread> threads;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        Locale.setDefault(Locale.US);
        threads = new ArrayList();
        ExperimentalSetupAndReporting expSetup=new ExperimentalSetupAndReporting();        
        expSetup.run();                        
        for(Thread t:threads){
            try{
                t.join();                
            }
            catch(InterruptedException e){
                System.out.println(e);
                e.printStackTrace();                
            }
            
        }        
        System.out.println("*********************************************************");        
    }
}
