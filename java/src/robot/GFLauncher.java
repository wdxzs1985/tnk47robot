package robot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import robot.gf.GFRobot;

import common.CommonHttpClient;

public class GFLauncher {

    public static void main(final String[] args) {
        final String setup = args.length > 0 ? args[0] : "setup.txt";
        final GFLauncher launcher = new GFLauncher();
        launcher.init(setup);
        launcher.runGFRobot();
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(1);
    private final Properties config = new Properties();
    private final CommonHttpClient httpClient = new CommonHttpClient();

    public void init(final String setup) {
        this.initConfig(setup);
        this.initHttpClient(setup);
    }

    private void initConfig(final String setup) {
        InputStream inputConfig = null;
        try {
            inputConfig = FileUtils.openInputStream(new File(setup));
            this.config.load(inputConfig);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(inputConfig);
        }
    }

    private void initHttpClient(final String setup) {
        final String username = this.config.getProperty("MainApplication.username");
        final File cookieFile = new File(username + ".cookie");
        this.httpClient.loadCookie(cookieFile);
    }

    public void runGFRobot() {
        final GFRobot robot = new GFRobot();
        robot.setConfig(this.config);
        robot.setHttpClient(this.httpClient);
        robot.init();
        this.executor.execute(robot);
    }
}