package com.znsio.e2e.tools.cmd;

import com.znsio.e2e.runner.Runner;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class CommandLineExecutor {
    private static final Logger LOGGER = Logger.getLogger(CommandLineExecutor.class.getName());

    public static CommandLineResponse execCommand (final String[] command) {
        String jointCommand = String.join(" ", command);
        String message = "\tExecuting Command : " + jointCommand;
        LOGGER.info(message);
        try {
            CommandLineResponse response = new CommandLineResponse();
            ProcessBuilder builder = new ProcessBuilder(command);
            if (Runner.IS_WINDOWS) {
                builder.command("cmd.exe", "/c", jointCommand);
            } else {
                builder.command("sh", "-c", jointCommand);
            }
            Process process = builder.start();
            process.waitFor(60, TimeUnit.SECONDS);
            response.setStdOut(IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim());
            response.setErrOut(IOUtils.toString(process.getErrorStream(), StandardCharsets.UTF_8).trim());
            response.setExitCode(process.exitValue());
            LOGGER.info("\t" + response.getStdOut());
            return response;
        } catch (Exception e) {
            throw new RuntimeException("Error " + message, e);
        }
    }
}
