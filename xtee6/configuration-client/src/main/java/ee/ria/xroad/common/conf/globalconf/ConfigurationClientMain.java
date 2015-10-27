package ee.ria.xroad.common.conf.globalconf;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.quartz.JobDataMap;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.util.AdminPort;
import ee.ria.xroad.common.util.JobManager;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_PROXY;
import static ee.ria.xroad.common.conf.globalconf.PrivateParameters.CONTENT_ID_PRIVATE_PARAMETERS;
import static ee.ria.xroad.common.conf.globalconf.SharedParameters.CONTENT_ID_SHARED_PARAMETERS;

/**
 * Main program of configuration client.
 */
@Slf4j
public final class ConfigurationClientMain {

    static {
        SystemPropertiesLoader.create().withCommonAndLocal()
            .with(CONF_FILE_PROXY, "configuration-client")
            .load();
    }

    private static final int RETURN_SUCCESS = 0;
    private static final int ERROR_CODE_INTERNAL = 125;
    private static final int ERROR_CODE_INVALID_SIGNATURE_VALUE = 124;
    private static final int ERROR_CODE_EXPIRED_CONF = 123;
    private static final int ERROR_CODE_CANNOT_DOWNLOAD_CONF = 122;
    private static final int ERROR_CODE_MISSING_PRIVATE_PARAMS = 121;
    private static final int ERROR_CODE_ANCHOR_NOT_FOR_EXTERNAL_SOURCE = 120;

    private static final String OPTION_VERIFY_PRIVATE_PARAMS_EXISTS =
            "verifyPrivateParamsExists";
    private static final String OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE =
            "verifyAnchorForExternalSource";

    private static ConfigurationClient client;
    private static JobManager jobManager;
    private static AdminPort adminPort;

    private ConfigurationClientMain() {
    }

    /**
     * Main entry point of configuration client. Based on the arguments,
     * the client will either:
     * 1) <anchor file> <configuration path> -- download and exit
     * 2) <anchor file> -- download and verify
     * 3) [no args] -- start as daemon
     * @param args the arguments
     * @throws Exception if an error occurs
     */
    public static void main(String[] args) throws Exception {
        CommandLine cmd = getCommandLine(args);

        String[] actualArgs = cmd.getArgs();
        if (actualArgs.length == 2) {
            System.exit(download(actualArgs[0], actualArgs[1]));
        } else if (actualArgs.length == 1) {
            System.exit(validate(actualArgs[0], getParamsValidator(cmd)));
        } else {
            startDaemon();
        }
    }

    private static CommandLine getCommandLine(String[] args) throws Exception {
        CommandLineParser parser = new BasicParser();
        Options options = new Options();

        options.addOption(OPTION_VERIFY_PRIVATE_PARAMS_EXISTS, false,
                "Verifies that configuration contains private parameters.");
        options.addOption(OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE, false,
                "Verifies that configuration contains shared parameters.");

        return parser.parse(options, args);
    }

    private static int download(String configurationAnchorFile,
            String configurationPath) throws Exception {
        log.trace("Downloading configuration using anchor {} (path = {})",
                configurationAnchorFile, configurationPath);

        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE,
                configurationAnchorFile);
        System.setProperty(SystemProperties.CONFIGURATION_PATH,
                configurationPath);

        FileNameProvider fileNameProvider =
                new FileNameProviderImpl(configurationPath);

        client = new ConfigurationClient(getDummyDownloadedFiles(),
                        new ConfigurationDownloader(fileNameProvider) {
                    @Override
                    void addAdditionalConfigurationSources(
                            PrivateParameters privateParameters) {
                        // do not download additional sources
                    }
                }) {
            @Override
            void initAdditionalConfigurationSources() {
                // not needed
            }
        };

        return execute();
    }

    private static int validate(String configurationAnchorFile,
            final ParamsValidator paramsValidator) throws Exception {
        log.trace("Downloading configuration using anchor {}",
                configurationAnchorFile);

        System.setProperty(SystemProperties.CONFIGURATION_ANCHOR_FILE,
                configurationAnchorFile);

        // create configuration that does not persist files to disk
        ConfigurationDownloader configuration =
                new ConfigurationDownloader(getDefaultFileNameProvider()) {
            @Override
            void handle(ConfigurationLocation location,
                    ConfigurationFile file) {
                paramsValidator.tryMarkValid(file.getContentIdentifier());

                super.handle(location, file);
            }

            @Override
            void persistContent(byte[] content, Path destination,
                    ConfigurationFile file) throws Exception {
            }

            @Override
            void updateExpirationDate(Path destination, ConfigurationFile file)
                    throws Exception {
            }
        };

        client = new ConfigurationClient(getDummyDownloadedFiles(),
                        configuration) {
            @Override
            void initAdditionalConfigurationSources() {
                // not needed
            }

            @Override
            void saveInstanceIdentifier() {
                // not needed
            }
        };

        int result = execute();

        // Check if downloaded configuration contained private parameters
        if (result == RETURN_SUCCESS) {
            return paramsValidator.getExitCode();
        }

        return result;
    }

    private static int execute() {
        try {
            client.execute();
            return RETURN_SUCCESS;
        } catch (Exception e) {
            log.error("Error when downloading conf", e);
            return getErrorCode(e);
        }
    }

    private static int getErrorCode(Exception e) {
        if (e instanceof CodedException) {
            CodedException ce = (CodedException) e;

            switch (ce.getFaultCode()) {
                case X_HTTP_ERROR:
                    return ERROR_CODE_CANNOT_DOWNLOAD_CONF;
                case X_OUTDATED_GLOBALCONF:
                    return ERROR_CODE_EXPIRED_CONF;
                case X_INVALID_SIGNATURE_VALUE:
                    return ERROR_CODE_INVALID_SIGNATURE_VALUE;
                default: // do nothing
                    break;
            }
        }

        return ERROR_CODE_INTERNAL;
    }

    private static void startDaemon() throws Exception {
        setup();
        startServices();
        awaitTermination();
        shutdown();
    }

    private static ConfigurationClient createClient() {
        ConfigurationDownloader configuration =
                new ConfigurationDownloader(getDefaultFileNameProvider());

        Path downloadedFilesConf =
                Paths.get(SystemProperties.getConfigurationPath(), "files");

        return new ConfigurationClient(
                new DownloadedFiles(downloadedFilesConf), configuration);
    }

    private static FileNameProviderImpl getDefaultFileNameProvider() {
        return new FileNameProviderImpl(
                SystemProperties.getConfigurationPath());
    }

    private static DownloadedFiles getDummyDownloadedFiles() {
        return new DownloadedFiles(null) {
            @Override
            void delete(String file) {
            }

            @Override
            void load() throws Exception {
            }

            @Override
            void save() throws Exception {
            }
        };
    }

    private static void setup() throws Exception {
        log.trace("setUp()");

        client = createClient();

        int portNumber = SystemProperties.getConfigurationClientPort();
        adminPort = new AdminPort(portNumber + 1);

        adminPort.addShutdownHook(() -> {
            log.info("Configuration client shutting down...");
            try {
                shutdown();
            } catch (Exception e) {
                log.error("Error while shutting down", e);
            }
        });

        adminPort.addHandler("/execute", (AdminPort.SynchronousCallback) () -> {
            log.info("Execute from admin port...");
            try {
                client.execute();
            } catch (Exception e) {
                throw translateException(e);
            }
        });
    }

    private static void startServices() throws Exception {
        log.trace("startServices()");

        adminPort.start();

        jobManager = new JobManager();

        JobDataMap data = new JobDataMap();
        data.put("client", client);

        jobManager.registerRepeatingJob(ConfigurationClientJob.class,
                SystemProperties.getConfigurationClientUpdateIntervalSeconds(),
                data);

        jobManager.start();
    }

    private static void awaitTermination() {
        log.info("Configuration client started");
        try {
            new CountDownLatch(1).await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void shutdown() throws Exception {
        log.trace("tearDown()");

        if (jobManager != null) {
            jobManager.stop();
        }

        if (adminPort != null) {
            adminPort.stop();
            adminPort.join();
        }
    }

    private static ParamsValidator getParamsValidator(CommandLine cmd) {
        if (cmd.hasOption(OPTION_VERIFY_PRIVATE_PARAMS_EXISTS)) {
            return new ParamsValidator(
                    CONTENT_ID_PRIVATE_PARAMETERS,
                    ERROR_CODE_MISSING_PRIVATE_PARAMS);
        } else if (cmd.hasOption(OPTION_VERIFY_ANCHOR_FOR_EXTERNAL_SOURCE)) {
            return new SharedParamsValidator(
                    CONTENT_ID_SHARED_PARAMETERS,
                    ERROR_CODE_ANCHOR_NOT_FOR_EXTERNAL_SOURCE);
        } else {
            return new ParamsValidator(null, 0);
        }
    }

    private static class ParamsValidator {
        protected final AtomicBoolean valid = new AtomicBoolean();

        private final String expectedContentId;
        private final int exitCodeWhenInvalid;

        ParamsValidator(
                String expectedContentId, int exitCodeWhenInvalid) {
            this.expectedContentId = expectedContentId;
            this.exitCodeWhenInvalid = exitCodeWhenInvalid;
        }

        void tryMarkValid(String contentId) {
            log.trace("tryMarkValid({})", contentId);

            if (valid.get()) {
                return;
            }

            valid.set(StringUtils.isBlank(expectedContentId)
                    || StringUtils.equals(expectedContentId, contentId));
        }

        int getExitCode() {
            if (valid.get()) {
                return RETURN_SUCCESS;
            }

            return exitCodeWhenInvalid;
        }
    }

    private static class SharedParamsValidator extends ParamsValidator {

        private final AtomicBoolean privateParametersIncluded =
                new AtomicBoolean();

        SharedParamsValidator(
                String expectedContentId, int exitCodeWhenInvalid) {
            super(expectedContentId, exitCodeWhenInvalid);
        }

        @Override
        void tryMarkValid(String contentId) {
            if (StringUtils.equals(contentId, CONTENT_ID_PRIVATE_PARAMETERS)) {
                privateParametersIncluded.set(true);
            }

            if (privateParametersIncluded.get()) {
                valid.set(false);
                return;
            }

            super.tryMarkValid(contentId);
        }
    }
}
