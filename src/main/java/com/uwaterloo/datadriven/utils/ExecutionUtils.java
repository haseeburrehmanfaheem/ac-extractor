package com.uwaterloo.datadriven.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class ExecutionUtils {
    public static String command(final String cmdline, final String directory) {
        try {
            Process process =
                    new ProcessBuilder("bash", "-c", cmdline)
                            .redirectErrorStream(true)
                            .directory(new File(directory))
                            .start();

            StringBuilder output = new StringBuilder();
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = br.readLine()) != null)
                output.append(line);

            //There should really be a timeout here.
            if (0 != process.waitFor())
                return null;

            return output.toString();
        } catch (Exception e) {
            //ignore
        }
        return null;
    }

    public static double executeForDouble(String cmd) {
        String ret = command(cmd, ".");
        if (ret == null || ret.isBlank())
            return -1;
        try {
            return Double.parseDouble(ret);
        } catch (Exception e) {
            //ignore
        }
        return -1;
    }
}
