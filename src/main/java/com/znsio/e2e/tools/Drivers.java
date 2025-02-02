package com.znsio.e2e.tools;

import com.context.TestExecutionContext;
import com.epam.reportportal.service.ReportPortal;
import com.mashape.unirest.http.Unirest;
import com.znsio.e2e.entities.Platform;
import com.znsio.e2e.entities.TEST_CONTEXT;
import com.znsio.e2e.exceptions.EnvironmentSetupException;
import com.znsio.e2e.exceptions.InvalidTestDataException;
import com.znsio.e2e.runner.Runner;
import com.znsio.e2e.tools.cmd.CommandLineExecutor;
import com.znsio.e2e.tools.cmd.CommandLineResponse;
import io.appium.java_client.AppiumDriver;
import io.appium.java_client.appmanagement.ApplicationState;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;

public class Drivers {
    private static final String USER_DIR = "user.dir";
    private final Map<String, Driver> userPersonaDrivers = new HashMap<>();
    private final Map<String, Platform> userPersonaPlatforms = new HashMap<>();
    private final Map<String, String> userPersonaBrowserLogs = new HashMap<>();
    private static final Logger LOGGER = Logger.getLogger(Drivers.class.getName());

    private final int MAX_NUMBER_OF_APPIUM_DRIVERS = 1;
    private final int MAX_NUMBER_OF_WEB_DRIVERS = 2;
    private int numberOfAndroidDriversUsed = 0;
    private int numberOfWebDriversUsed = 0;

    public Driver setDriverFor (String userPersona, Platform forPlatform, TestExecutionContext context) {
        LOGGER.info(String.format("getDriverFor: start: userPersona: '%s', Platform: '%s'", userPersona, forPlatform.name()));
        if (!userPersonaDrivers.containsKey(userPersona)) {
            String message = String.format("ERROR: Driver for user persona: '%s' DOES NOT EXIST%nAvailable drivers: '%s'",
                    userPersona,
                    userPersonaDrivers.keySet());
            throw new InvalidTestDataException(message);
        }
        Driver currentDriver = userPersonaDrivers.get(userPersona);
        context.addTestState(TEST_CONTEXT.CURRENT_DRIVER, currentDriver);
        context.addTestState(TEST_CONTEXT.CURRENT_USER_PERSONA, userPersona);
        return currentDriver;
    }

    public Driver createDriverFor (String userPersona, Platform forPlatform, TestExecutionContext context) {
        LOGGER.info(String.format("allocateDriverFor: start: userPersona: '%s', Platform: '%s'", userPersona, forPlatform.name()));
        Driver currentDriver = null;
        if (userPersonaDrivers.containsKey(userPersona)) {
            String message = String.format("ERROR: Driver for user persona: '%s' ALREADY EXISTS%nAvailable drivers: '%s'",
                    userPersona,
                    userPersonaDrivers.keySet());
            throw new InvalidTestDataException(message);
        }

        switch (forPlatform) {
            case android:
                currentDriver = createAndroidDriverForUser(userPersona, forPlatform, context);
                break;
            case web:
                currentDriver = createWebDriverForUser(userPersona, forPlatform, context);
                break;
            default:
                throw new InvalidTestDataException(
                        String.format("Unexpected platform value: '%s' provided to assign Driver for user: '%s': ",
                                forPlatform,
                                userPersona));
        }
        context.addTestState(TEST_CONTEXT.CURRENT_DRIVER, currentDriver);
        context.addTestState(TEST_CONTEXT.CURRENT_USER_PERSONA, userPersona);
        userPersonaDrivers.put(userPersona, currentDriver);
        userPersonaPlatforms.put(userPersona, forPlatform);
        System.out.printf("allocateDriverFor: done: userPersona: '%s', Platform: '%s'%n",
                userPersona,
                forPlatform.name());

        return currentDriver;
    }

    @NotNull
    private Driver createAndroidDriverForUser (String userPersona, Platform forPlatform, TestExecutionContext context) {
        System.out.printf("getAndroidDriverForUser: begin: userPersona: '%s', Platform: '%s', Number of appiumDrivers: '%d'%n",
                userPersona,
                forPlatform.name(),
                numberOfAndroidDriversUsed);
        Driver currentDriver;
        if (Platform.android.equals(forPlatform) && numberOfAndroidDriversUsed == MAX_NUMBER_OF_APPIUM_DRIVERS) {
            throw new InvalidTestDataException(
                    String.format("Unable to create more than '%d' drivers for user persona: '%s' on platform: '%s'",
                            numberOfAndroidDriversUsed,
                            userPersona,
                            forPlatform.name())
            );
        }
        currentDriver = new Driver(
                context.getTestName() + "-" + userPersona,
                (AppiumDriver<WebElement>) context.getTestState(TEST_CONTEXT.APPIUM_DRIVER));
        numberOfAndroidDriversUsed++;
        System.out.printf("getAndroidDriverForUser: done: userPersona: '%s', Platform: '%s', Number of appiumDrivers: '%d'%n",
                userPersona,
                forPlatform.name(),
                numberOfAndroidDriversUsed);
        disableNotificationsAndToastsOnDevice(currentDriver);
        return currentDriver;
    }

    private void disableNotificationsAndToastsOnDevice (Driver currentDriver) {
        if (Runner.isRunningInCI()) {
            Object disableToasts = ((AppiumDriver) currentDriver.getInnerDriver()).executeScript("pCloudy_executeAdbCommand", "adb shell appops set " + Runner.getAppPackageName() + " TOAST_WINDOW deny");
            LOGGER.info("@disableToastsCommandResponse: " + disableToasts);
            Object disableNotifications = ((AppiumDriver) currentDriver.getInnerDriver()).executeScript("pCloudy_executeAdbCommand", "adb shell settings put global heads_up_notifications_enabled 0");
            LOGGER.info("@disableNotificationsCommandResponse: " + disableNotifications);
        } else {
            String[] disableToastsCommand = new String[]{"adb", "-s", "${device.SERIAL}", "shell", "appops", "set", Runner.getAppPackageName(), "TOAST_WINDOW", "deny"};
            String[] disableNotificationsCommand = new String[]{"adb", "-s", "${device.SERIAL}", "shell", "settings", "put", "global", "heads_up_notifications_enabled", "0"};

            CommandLineResponse disableToastsCommandResponse = CommandLineExecutor.execCommand(disableToastsCommand);
            LOGGER.info("disableToastsCommandResponse: " + disableToastsCommandResponse);
            CommandLineResponse disableNotificationsCommandResponse = CommandLineExecutor.execCommand(disableNotificationsCommand);
            LOGGER.info("disableNotificationsCommandResponse: " + disableNotificationsCommandResponse);
        }
    }

    @NotNull
    private Driver createWebDriverForUser (String userPersona, Platform forPlatform, TestExecutionContext context) {
        System.out.printf("getWebDriverForUser: begin: userPersona: '%s', Platform: '%s', Number of webdrivers: '%d'%n",
                userPersona,
                forPlatform.name(),
                numberOfWebDriversUsed);

        Driver currentDriver;
        if (Platform.web.equals(forPlatform) && numberOfWebDriversUsed == MAX_NUMBER_OF_WEB_DRIVERS) {
            throw new InvalidTestDataException(
                    String.format("Unable to create more than '%d' drivers for user persona: '%s' on platform: '%s'",
                            numberOfWebDriversUsed,
                            userPersona,
                            forPlatform.name())
            );
        }
        String updatedTestName = context.getTestName() + "-" + userPersona;
        if (numberOfWebDriversUsed < MAX_NUMBER_OF_WEB_DRIVERS) {
            currentDriver = new Driver(updatedTestName, createNewWebDriver(userPersona, context));
        } else {
            throw new InvalidTestDataException(
                    String.format("Current number of WebDriver instances used: '%d'. Unable to create more than '%d' drivers for user persona: '%s' on platform: '%s'",
                            numberOfWebDriversUsed,
                            MAX_NUMBER_OF_WEB_DRIVERS,
                            userPersona,
                            forPlatform.name())
            );
        }
        numberOfWebDriversUsed++;
        LOGGER.info(String.format("getWebDriverForUser: done: userPersona: '%s', Platform: '%s', Number of webdrivers: '%d'", userPersona, forPlatform.name(), numberOfWebDriversUsed));
        return currentDriver;
    }

    @NotNull
    private WebDriver createNewWebDriver (String forUserPersona, TestExecutionContext testExecutionContext) {
        WebDriverManager.chromedriver().setup();

        userPersonaBrowserLogs.put(forUserPersona, setChromeLogDirectory(testExecutionContext));

        ChromeOptions chromeOptions = new ChromeOptions();
        List<String> excludeSwitches = Arrays.asList(
                "enable-automation",
                "disable-notifications",
                "disable-default-apps",
                "disable-extensions",
                "enable-user-metrics",
                "incognito",
                "show-taps",
                "disable-infobars"
        );
        chromeOptions.setExperimentalOption("excludeSwitches", excludeSwitches);

        Map<String, Boolean> excludedSchemes = new HashMap<>();
        excludedSchemes.put("jhb", true);

        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 1);
        prefs.put("profile.default_content_setting_values.media_stream_mic", 1);
        prefs.put("profile.default_content_setting_values.media_stream_camera", 1);
//        prefs.put("profile.default_content_setting_values.geolocation", 1);
        prefs.put("protocol_handler.excluded_schemes", excludedSchemes);
        chromeOptions.setExperimentalOption("prefs", prefs);

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.BROWSER, Level.ALL);

        chromeOptions.setCapability(CapabilityType.LOGGING_PREFS, logPrefs);

        LOGGER.info("ChromeOptions: " + chromeOptions.asMap());

        WebDriver driver = Runner.isRunningInCI() ? createRemoteWebDriver(chromeOptions) : new ChromeDriver(chromeOptions);

        String providedBaseUrl = Runner.getBaseURLForWeb();
        if (null == providedBaseUrl) {
            throw new InvalidTestDataException("baseUrl not provided as an environment variable");
        }
        String baseUrl = String.valueOf(Runner.getFromEnvironmentConfiguration(providedBaseUrl));
        LOGGER.info("baseUrl: " + baseUrl);
        driver.get(baseUrl);
        driver.manage().window().maximize();
        return driver;
    }

    @NotNull
    private RemoteWebDriver createRemoteWebDriver (ChromeOptions chromeOptions) {
        try {
            return new RemoteWebDriver(new URL("http://localhost:4444/wd/hub"), chromeOptions);
        } catch (MalformedURLException e) {
            throw new EnvironmentSetupException("Unable to create a new RemoteWebDriver", e);
        }
    }

    private String setChromeLogDirectory (TestExecutionContext testExecutionContext) {
        String forUserPersona = testExecutionContext.getTestStateAsString(TEST_CONTEXT.CURRENT_USER_PERSONA);
        String scenarioLogDir = Runner.USER_DIRECTORY + testExecutionContext.getTestStateAsString(TEST_CONTEXT.SCENARIO_LOG_DIRECTORY);
        String logFile = scenarioLogDir + File.separator + "deviceLogs" + File.separator + "chrome-" + forUserPersona + ".log";

        File file = new File(logFile);
        file.getParentFile().mkdirs();

        LOGGER.info("Creating Chrome logs in file: " + logFile);
        System.setProperty("webdriver.chrome.logfile", logFile);

//        System.setProperty("webdriver.chrome.verboseLogging", "true");
        return logFile;
    }

    public Driver getDriverForUser (String userPersona) {
        if (!userPersonaDrivers.containsKey(userPersona)) {
            LOGGER.info("getDriverForUser: Drivers available for userPersonas: " + userPersonaDrivers.keySet());
            throw new InvalidTestDataException(String.format("No Driver found for user persona: '%s'", userPersona));
        }

        return userPersonaDrivers.get(userPersona);
    }

    public Platform getPlatformForUser (String userPersona) {
        if (!userPersonaDrivers.containsKey(userPersona)) {
            LOGGER.info("getPlatformForUser: Platforms available for userPersonas: ");
            userPersonaPlatforms.keySet().forEach(key -> {
                LOGGER.info("\tUser Persona: " + key + ": Platform: " + userPersonaPlatforms.get(key).name());
            });
            throw new InvalidTestDataException(String.format("No Driver found for user persona: '%s'", userPersona));
        }

        return userPersonaPlatforms.get(userPersona);
    }

    public void attachLogsAndCloseAllWebDrivers (TestExecutionContext context) {
        LOGGER.info("Close all drivers:");
        userPersonaDrivers.keySet().forEach(key -> {
            LOGGER.info("\tUser Persona: " + key);
            validateVisualTestResults(key);
            attachLogsAndCloseDriver(key);
        });
    }

    private void validateVisualTestResults (String key) {
        Driver driver = userPersonaDrivers.get(key);
        driver.getVisual().handleTestResults(key);
    }

    private void attachLogsAndCloseDriver (String key) {
        Driver driver = userPersonaDrivers.get(key);
        shutdownUnirestBackgroundConnections();
        if (driver.getType().equals(Driver.WEB_DRIVER)) {
            closeWebDriver(key, driver);
        } else {
            closeAppOnDevice(driver);
        }
    }

    private void shutdownUnirestBackgroundConnections () {
        try {
            LOGGER.info("Unirest: Shutdown background connections");
            Unirest.shutdown();
        } catch (IOException e) {
            LOGGER.info("Error in shutting down Unirest background connections");
        }
    }

    private void closeAppOnDevice (Driver driver) {
        String appPackageName = Runner.getAppPackageName();
        AppiumDriver appiumDriver = (AppiumDriver) driver.getInnerDriver();
        if (Runner.isRunningInCI()) {
            String message = "Skip terminating & closing app on Cloud device";
            LOGGER.info(message);
            ReportPortal.emitLog(message, "DEBUG", new Date());
        } else {

            LOGGER.info("Terminate app: " + appPackageName);
            boolean isAppTerminated = appiumDriver.terminateApp(appPackageName);
            LOGGER.info("App terminated? " + isAppTerminated);
            ApplicationState applicationState = appiumDriver.queryAppState(appPackageName);
            LOGGER.info("Application State: " + applicationState);
            appiumDriver.closeApp();
            ReportPortal.emitLog(
                    String.format("App: '%s' termiated? '%s'. Current application state: '%s'%n",
                            appPackageName,
                            isAppTerminated,
                            applicationState),
                    "DEBUG",
                    new Date());
        }
    }

    private void closeWebDriver (String key, Driver driver) {
        ReportPortal.emitLog(
                "Chrome browser logs for user: " + key,
                "DEBUG",
                new Date(), new File(userPersonaBrowserLogs.get(key)));
        WebDriver webDriver = driver.getInnerDriver();
        if (null == webDriver) {
            LOGGER.info(String.format("Strange. But WebDriver for user: '%s' already closed", key));
        } else {
            LOGGER.info(String.format("Closing WebDriver for user: '%s'", key));
            webDriver.quit();
        }
    }
}
