package se.kth.networking.java.first;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by victoraxelsson on 2016-12-08.
 */
public class Env {

    /**
     * Loads an env.txt file into memory
     * @return list of environment variables
     */
    public static List<String> getEnv(){
        List<String> vars = new ArrayList<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("env.txt"));
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();
            vars.add(line);

            while (line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());
                line = br.readLine();
                vars.add(line);
            }
            String everything = sb.toString();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(br != null){
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }

        return vars;
    }
}
