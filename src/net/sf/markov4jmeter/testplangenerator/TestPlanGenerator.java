/***************************************************************************
 * Copyright (c) 2016 the WESSBAS project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/


package net.sf.markov4jmeter.testplangenerator;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

import m4jdsl.WorkloadModel;
import m4jdsl.impl.M4jdslPackageImpl;
import net.sf.markov4jmeter.testplangenerator.transformation.AbstractTestPlanTransformer;
import net.sf.markov4jmeter.testplangenerator.transformation.SimpleTestPlanTransformer;
import net.sf.markov4jmeter.testplangenerator.transformation.TransformationException;
import net.sf.markov4jmeter.testplangenerator.transformation.filters.AbstractFilter;
import net.sf.markov4jmeter.testplangenerator.transformation.filters.HeaderDefaultsFilter;
import net.sf.markov4jmeter.testplangenerator.util.CSVHandler;
import net.sf.markov4jmeter.testplangenerator.util.Configuration;

import org.apache.commons.cli.ParseException;
import org.apache.jmeter.save.SaveService;
import org.apache.jorphan.collections.ListedHashTree;

import wessbas.commons.util.XmiEcoreHandler;


/**
 * Generator class for building Test Plans which result from M4J-DSL models.
 *
 * <p>The generator must be initialized before it can be used properly;
 * hence, the {@link #init(String)} method must be called once for initializing
 * the default configuration of Test Plan elements. The name of the
 * configuration properties file must be passed to the initialization method
 * therefore. The {@link #isInitialized()} method might be used for requesting
 * the initialization status of the generator.
 *
 * <p>An M4J-DSL model for which a Test Plan shall be generated, might be passed
 * to the regarding <code>generate()</code> method; the model might be even
 * loaded from an XMI file alternatively, requiring the related filename to be
 * passed to the regarding <code>generate()</code> method.
 *
 * @author   Eike Schulz (esc@informatik.uni-kiel.de)
 * @version  1.0
 * @since    1.7
 */
public class TestPlanGenerator {

    /* IMPLEMENTATION NOTE:
     * --------------------
     * The following elements of the Test Plan Factory have not been used for
     * creating Markov4JMeter Test Plans, but they are already supported by the
     * framework:
     *
     *   WhileController whileController = testPlanElementFactory.createWhileController();
     *   IfController ifController = testPlanElementFactory.createIfController();
     *   CounterConfig counterConfig = testPlanElementFactory.createCounterConfig();
     *
     * The following elements are just required as nested parts for other types
     * of Test Plan elements, but they can be even created independently:
     *
     *   Arguments arguments = testPlanElementFactory.createArguments();
     *   LoopController loopController = testPlanElementFactory.createLoopController();
     */

    /** Default properties file for the Test Plan Generator, to be used in case
     *  no user-defined properties file can be read from command line. */
    private final static String GENERATOR_DEFAULT_PROPERTIES =
            "configuration/generator.default.properties";

    /** Property key for the JMeter home directory. */
    private final static String PKEY_JMETER__HOME = "jmeter_home";

    /** Property key for the JMeter default properties. */
    private final static String PKEY_JMETER__PROPERTIES = "jmeter_properties";

    /** Property key for the language tag which indicates the locality. */
    private final static String PKEY_JMETER__LANGUAGE_TAG = "jmeter_languageTag";

    /** Property key for the flag which indicates whether the generation process
     *  shall be aborted, if undefined arguments are detected. */
    private final static String PKEY_USE_FORCED_ARGUMENTS = "useForcedArguments";


    // info-, warn- and error-messages (names should be self-explaining);

    private final static String ERROR_CONFIGURATION_UNDEFINED =
            "Configuration file is null.";

    private final static String ERROR_CONFIGURATION_NOT_FOUND =
            "Could not find configuration file \"%s\".";

    private final static String ERROR_CONFIGURATION_READING_FAILED =
            "Could not read configuration file \"%s\".";

    private final static String ERROR_TEST_PLAN_PROPERTIES_UNDEFINED =
            "Test Plan properties file is null.";

    private final static String ERROR_TEST_PLAN_PROPERTIES_NOT_FOUND =
            "Could not find Test Plan properties file \"%s\".";

    private final static String ERROR_TEST_PLAN_PROPERTIES_READING_FAILED =
            "Could not read Test Plan properties file \"%s\".";

    private final static String ERROR_INITIALIZATION_FAILED =
            "Initialization of Test Plan Generator failed.";

    private final static String ERROR_INPUT_FILE_COULD_NOT_BE_READ =
            "Input file \"%s\" could not be read: %s";

    private final static String ERROR_OUTPUT_FILE_COULD_NOT_BE_WRITTEN =
            "Output file \"%s\" could not be written: %s";

    private final static String ERROR_OUTPUT_FILE_ACCESS_FAILED =
            "Could not access file \"%s\" for writing output data: %s";

    private final static String ERROR_TREE_SAVING_FAILED =
            "Could not save Test Plan tree \"%s\" via SaveService: %s";

    private final static String INFO_INITIALIZATION_SUCCESSFUL =
            "Test Plan Generator has been successfully initialized.";

    private final static String INFO_TEST_PLAN_GENERATION_STARTED =
            "Generating Test  Plan ...";

    private final static String ERROR_TEST_PLAN_GENERATION_FAILED =
            "Test Plan generation failed.";

    private final static String INFO_MODEL_VALIDATION_SUCCESSFUL =
            "Validation of M4J-DSL model successful.";

    private final static String ERROR_MODEL_VALIDATION_FAILED =
            "Validation of M4J-DSL model failed.";

    private final static String WARNING_OUTPUT_FILE_CLOSING_FAILED =
            "Could not close file-output stream for file \"%s\": %s";

    private final static String ERROR_TEST_PLAN_RUN_FAILED =
            "Could not run Test Plan \"%s\": %s";


    /* *********************  global (non-final) fields  ******************** */


    /** Factory to be used for creating Test Plan elements. */
    private TestPlanElementFactory testPlanElementFactory;


    /* **************************  public methods  ************************** */


    /**
     * Returns the Test Plan Factory associated with the Test Plan Generator.
     *
     * @return
     *     a valid Test Plan Factory, if the Test Plan Generator has been
     *     initialized successfully; otherwise <code>null</code> will be
     *     returned.
     */
    public TestPlanElementFactory getTestPlanElementFactory () {

        return this.testPlanElementFactory;
    }

    /**
     * Returns the information whether the Test Plan Generator is initialized,
     * meaning that the {@link #init(String)} method has been called
     * successfully.
     *
     * @return
     *     <code>true</code> if and only if the Test Plan Generator is
     *     initialized.
     */
    public boolean isInitialized () {

        return this.testPlanElementFactory != null;
    }

    /**
     * Initializes the Test Plan Generator by loading the specified
     * configuration file and setting its properties accordingly.
     *
     * @param configurationFile
     *     properties file which provides required or optional properties.
     * @param testPlanProperties
     *     file with Test Plan default properties.
     */
    public void init (
            final String configurationFile,
            final String testPlanProperties) {

        // read the configuration and give an error message, if reading fails;
        // in that case, null will be returned;
        final Configuration configuration =
                this.readConfiguration(configurationFile);

        if (configuration != null) {  // could configuration be read?

            final boolean useForcedArguments = configuration.getBoolean(
                    TestPlanGenerator.PKEY_USE_FORCED_ARGUMENTS);

            final String jMeterHome = configuration.getString(
                    TestPlanGenerator.PKEY_JMETER__HOME);

            final String jMeterProperties = configuration.getString(
                    TestPlanGenerator.PKEY_JMETER__PROPERTIES);

            final String jMeterLanguageTag = configuration.getString(
                    TestPlanGenerator.PKEY_JMETER__LANGUAGE_TAG);

            final Locale jMeterLocale =
                    Locale.forLanguageTag(jMeterLanguageTag);

            final boolean success =
                    JMeterEngineGateway.getInstance().initJMeter(
                            jMeterHome,
                            jMeterProperties,
                            jMeterLocale);

            if (success) {

                // create a factory which builds Test Plan elements according
                // to the specified properties.
                this.testPlanElementFactory = this.createTestPlanFactory(
                        testPlanProperties,
                        useForcedArguments);

                if (this.testPlanElementFactory != null) {

                    this.logInfo(
                            TestPlanGenerator.INFO_INITIALIZATION_SUCCESSFUL);

                    return;
                }
            }

        }

        this.logError(TestPlanGenerator.ERROR_INITIALIZATION_FAILED);
    }

    /**
     * Generates a Test Plan for the given workload model and writes the result
     * into the specified file.
     *
     * @param workloadModel
     *     workload model which provides the values for the Test Plan to be
     *     generated.
     * @param testPlanTransformer
     *     builder to be used for building a Test Plan of certain structure.
     * @param filters
     *     (optional) modification filters to be finally applied on the newly
     *     generated Test Plan.
     * @param outputFilename
     *     name of the file where the Test Plan shall be stored in.
     *
     * @return
     *     the generated Test Plan, or <code>null</code> if any error occurs.
     *
     * @throws TransformationException
     *     if any critical error in the transformation process occurs.
     */
    public ListedHashTree generate (
            final WorkloadModel workloadModel,
            final AbstractTestPlanTransformer testPlanTransformer,
            final AbstractFilter[] filters,
            final String outputFilename) throws TransformationException {

        ListedHashTree testPlanTree = null;  // to be returned;

        final boolean validationSuccessful;

        this.logInfo(TestPlanGenerator.INFO_TEST_PLAN_GENERATION_STARTED);


        //validationSuccessful = validator.validateAndPrintResult(workloadModel);
        validationSuccessful = true;

        this.logInfo(TestPlanGenerator.INFO_MODEL_VALIDATION_SUCCESSFUL);

        if (!validationSuccessful) {

            this.logError(TestPlanGenerator.ERROR_MODEL_VALIDATION_FAILED);
            this.logError(TestPlanGenerator.ERROR_TEST_PLAN_GENERATION_FAILED);

        } else {  // validation successful -> generate Test Plan output file;

            testPlanTree = testPlanTransformer.transform(
                    workloadModel,
                    this.testPlanElementFactory,
                    filters);

            final boolean success =
                    this.writeOutput(testPlanTree, outputFilename);

            if (!success) {

            	this.logError(
                    TestPlanGenerator.ERROR_TEST_PLAN_GENERATION_FAILED);
            }
        }

        return testPlanTree;
    }

    /**
     * Generates a Test Plan for the (Ecore) workload model which is stored in
     * the given XMI-file; the result will be written into the specified output
     * file.
     *
     * @param inputFile
     *     XMI file containing the (Ecore) workload model which provides the
     *     values for the Test Plan to be generated.
     * @param outputFile
     *     name of the file where the Test Plan shall be stored in.
     * @param testPlanTransformer
     *     builder to be used for building a Test Plan of certain structure.
     * @param filters
     *     (optional) modification filters to be finally applied on the newly
     *     generated Test Plan.
     *
     * @return
     *     the generated Test Plan, or <code>null</code> if any error occurs.
     *
     * @throws IOException
     *     in case any file reading or writing operation failed.
     * @throws TransformationException
     *     if any critical error in the transformation process occurs.
     */
    public ListedHashTree generate (
            final String inputFile,
            final String outputFile,
            final AbstractTestPlanTransformer testPlanTransformer,
            final AbstractFilter[] filters)
                    throws IOException, TransformationException {

        // initialize the model package;
        M4jdslPackageImpl.init();

        // might throw an IOException;
        final WorkloadModel workloadModel = (WorkloadModel)
                XmiEcoreHandler.getInstance().xmiToEcore(inputFile, "xmi");

        return this.generate(
                workloadModel, testPlanTransformer, filters, outputFile);
    }


    /* *************************  protected methods  ************************ */


    /**
     * Writes a given Test Plan into the specified output file.
     *
     * @param testPlanTree    Test Plan to be written into the output file.
     * @param outputFilename  name of the output file.
     */
    protected boolean writeOutput (
            final ListedHashTree testPlanTree, final String outputFilename) {

        boolean success = true;
        FileOutputStream fileOutputStream = null;

        try {

            // might throw a FileNotFoundException or SecurityException;
            fileOutputStream = new FileOutputStream(outputFilename);

            // might throw an IOException;
            SaveService.saveTree(testPlanTree, fileOutputStream);

        } catch (final FileNotFoundException ex) {

            final String message = String.format(
                    TestPlanGenerator.ERROR_OUTPUT_FILE_COULD_NOT_BE_WRITTEN,
                    outputFilename,
                    ex.getMessage());

            this.logError(message);
            success = false;

        } catch (final SecurityException ex) {

            final String message = String.format(
                    TestPlanGenerator.ERROR_OUTPUT_FILE_ACCESS_FAILED,
                    outputFilename,
                    ex.getMessage());

            this.logError(message);
            success = false;

        } catch (final IOException ex) {

            final String message = String.format(
                    TestPlanGenerator.ERROR_TREE_SAVING_FAILED,
                    outputFilename,
                    ex.getMessage());

            this.logError(message);
            success = false;

        } finally {

            if (fileOutputStream != null) {

                try {

                    fileOutputStream.close();

                } catch (final IOException ex) {

                    final String message = String.format(
                            TestPlanGenerator.WARNING_OUTPUT_FILE_CLOSING_FAILED,
                            outputFilename,
                            ex.getMessage());

                    this.logWarning(message);
                    // success remains true, since output file content has been
                    // written, just the file could not be closed;
                }
            }
        }

        return success;
    }


    /* **************************  private methods  ************************* */


    /**
     * Reads all configuration properties from the specified file and gives an
     * error message, if reading fails.
     *
     * @param propertiesFile  properties file to be read.
     *
     * @return
     *     a valid configuration, if the specified properties file could be
     *     read successfully; otherwise <code>null</code> will be returned.
     */
    private Configuration readConfiguration (final String propertiesFile) {

        Configuration configuration = new Configuration();

        try {
            // might throw FileNotFound-, IO-, or NullPointerException;
            configuration.load(propertiesFile);

        } catch (final FileNotFoundException ex) {

            final String message = String.format(
                    TestPlanGenerator.ERROR_CONFIGURATION_NOT_FOUND,
                    propertiesFile);

            this.logError(message);
            configuration = null;  // indicates an error;

        } catch (final IOException ex) {

            final String message = String.format(
                    TestPlanGenerator.ERROR_CONFIGURATION_READING_FAILED,
                    propertiesFile);

            this.logError(message);
            configuration = null;  // indicates an error;

        } catch (final NullPointerException ex) {

            this.logError(TestPlanGenerator.ERROR_CONFIGURATION_UNDEFINED);
            configuration = null;  // indicates an error;
        }

        return configuration;
    }

    /**
     * Creates a Factory which builds Test Plan according to the default
     * properties defined in the specified file.
     *
     * @param propertiesFile
     *     properties file with default properties for Test Plan elements.
     * @param useForcedValues
     *     <code>true</code> if and only if the generation process shall be
     *     aborted, if undefined arguments are detected.
     *
     * @return
     *     a valid Test Plan Factory if the properties file could be read
     *     successfully; otherwise <code>null</code> will be returned.
     */
    private TestPlanElementFactory createTestPlanFactory (
            final String propertiesFile,
            final boolean useForcedValues) {

        // to be returned;
        TestPlanElementFactory testPlanElementFactory = null;

        final Configuration testPlanProperties = new Configuration();

        try {

            // might throw FileNotFound-, IO-, or NullPointerException;
            testPlanProperties.load(propertiesFile);

            // store factory globally for regular and simplified access;
            testPlanElementFactory = new TestPlanElementFactory(
                    testPlanProperties,
                    useForcedValues);

        } catch (final FileNotFoundException ex) {

            final String message = String.format(
                    TestPlanGenerator.ERROR_TEST_PLAN_PROPERTIES_NOT_FOUND,
                    propertiesFile);

            this.logError(message);

        } catch (final IOException ex) {

            final String message = String.format(
                    TestPlanGenerator.ERROR_TEST_PLAN_PROPERTIES_READING_FAILED,
                    propertiesFile);

            this.logError(message);

        } catch (final NullPointerException ex) {

            this.logError(
                    TestPlanGenerator.ERROR_TEST_PLAN_PROPERTIES_UNDEFINED);
        }

        return testPlanElementFactory;
    }

    /**
     * Logs an information to standard output.
     *
     * @param message  information to be logged.
     */
    private void logInfo (final String message) {

        // TODO: remove print-command, solve the issue below;
        System.out.println(message);

        // this command would print in red color, indicating an error:
        //
        //   TestPlanGenerator.LOG.info(message);
        //
        // --> adjust "commons-logging.properties" accordingly;
        // --> even better: use JMeter logging unit;
    }

    /**
     * Logs a warning message to standard output.
     *
     * @param message  warning message to be logged.
     */
    private void logWarning (final String message) {

        // TODO: remove print-command, solve the issue below;
        System.err.println(message);

        // this command would print in non-uniform format:
        //
        //   TestPlanGenerator.LOG.warning(message);
        //
        // --> adjust "commons-logging.properties" accordingly;
        // --> even better: use JMeter logging unit;
    }

    /**
     * Logs an error message to standard output.
     *
     * @param message  error message to be logged.
     */
    private void logError (final String message) {

        // TODO: remove print-command, solve the issue below;
        System.err.println(message);

        // this command would print in non-uniform format:
        //
        //   TestPlanGenerator.LOG.error(message);
        //
        // --> adjust "commons-logging.properties" accordingly;
        // --> even better: use JMeter logging unit;
    }

    /**
     * Generates a Test Plan for the (Ecore) workload model which is stored in
     * the given XMI-file; the result will be written into the specified output
     * file.
     *
     * @param inputFile
     *     XMI file containing the (Ecore) workload model which provides the
     *     values for the Test Plan to be generated.
     * @param outputFile
     *     name of the file where the Test Plan shall be stored in.
     * @param generatorPropertiesFile
     *     properties file which provides the core settings for the Test Plan
     *     Generator.
     * @param testPlanPropertiesFile
     *     properties file which provides the default settings for the Test
     *     Plans to be generated.
     * @param filterFlags
     *     (optional) modification filters to be finally applied on the newly
     *     generated Test Plan.
     * @param lineBreakType
     *     OS-specific line-break type; this must be one of the
     *     <code>LINEBREAK_TYPE</code> constants defined in class
     *     {@link CSVHandler}.
     *
     * @return
     *     the generated Test Plan, or <code>null</code> if any error occurs.
     *
     * @throws TransformationException
     *     if any critical error in the transformation process occurs.
     */
    private ListedHashTree generate (
            final String inputFile,
            final String outputFile,
            final String outputPath,
            final int lineBreakType,
            final String testPlanPropertiesFile,
            final String generatorPropertiesFile,
            final String filterFlags) throws TransformationException {

        ListedHashTree testPlan = null;  // to be returned;

        // TODO: collect these filters according to the command line flags;
        final AbstractFilter[] filters = new AbstractFilter[]{

                // new ConstantWorkloadIntensityFilter(),

                /* this is just for testing, think times will be taken from the
                 * workload model and managed by the Markov Controller;
                 *
                     new GaussianThinkTimeDistributionFilter(
                        "Think Time",
                        "",
                        true,
                        300.0d,
                        100.0d),
                 */
                new HeaderDefaultsFilter()
        };

        this.init(
                generatorPropertiesFile,
                testPlanPropertiesFile);

        if (this.isInitialized()) {

            final CSVHandler csvHandler = new CSVHandler(lineBreakType);

            // TODO: destination path must exist; create path, if necessary;
            // TODO: use output path also for test plan?
            final String behaviorModelsOutputPath =
                    outputPath == null ? "./" : outputPath;  // path "" denotes "/";

            final AbstractTestPlanTransformer testPlanTransformer =
                    new SimpleTestPlanTransformer(
                            csvHandler,
                            behaviorModelsOutputPath);

            try {

                testPlan = this.generate(
                        inputFile,
                        outputFile,
                        testPlanTransformer,
                        filters);

            } catch (final IOException ex) {

                final String message = String.format(
                        TestPlanGenerator.ERROR_INPUT_FILE_COULD_NOT_BE_READ,
                        inputFile,
                        ex.getMessage());

                this.logError(message);
            }
        }

        return testPlan;
    }


    /* ************************  static main content  *********************** */


    /**
     * Main method which parses the command line parameters and generates a
     * Test Plan afterwards.
     *
     * @param argv arguments vector.
     */
    public static void main (final String[] argv) {

        try {

			System.out.println("****************************");
			System.out.println("Start ApacheJMeter Testplan generation");
			System.out.println("****************************");

            // initialize arguments handler for requesting the command line
            // values afterwards via get() methods; might throw a
            // NullPointer-, IllegalArgument- or ParseException;
            CommandLineArgumentsHandler.init(argv);

            TestPlanGenerator.readArgumentsAndGenerate();

			System.out.println("****************************");
			System.out.println("END ApacheJMeter Testplan generation");
			System.out.println("****************************");

        } catch (final NullPointerException
                | IllegalArgumentException
                | ParseException
                | TransformationException ex) {

            System.err.println(ex.getMessage());
            CommandLineArgumentsHandler.printUsage();
        }
    }

    /**
     * Starts the generation process with the arguments which have been passed
     * to command line.
     *
     * @throws TransformationException
     *     if any critical error in the transformation process occurs.
     */
    private static void readArgumentsAndGenerate ()
            throws TransformationException {

        final TestPlanGenerator testPlanGenerator = new TestPlanGenerator();

        final String inputFile =
                CommandLineArgumentsHandler.getInputFile();

        final String outputFile =
                CommandLineArgumentsHandler.getOutputFile();

        final String outputPath =
                CommandLineArgumentsHandler.getPath();

        final int lineBreakType =
                CommandLineArgumentsHandler.getLineBreakType();

        final String testPlanPropertiesFile =
                CommandLineArgumentsHandler.getTestPlanPropertiesFile();

        final String filters =
                CommandLineArgumentsHandler.getFilters();

        final boolean runTest =
                CommandLineArgumentsHandler.getRunTest();

        String generatorPropertiesFile =
                CommandLineArgumentsHandler.getGeneratorPropertiesFile();

        if (generatorPropertiesFile == null) {

            generatorPropertiesFile =
                    TestPlanGenerator.GENERATOR_DEFAULT_PROPERTIES;
        }

        // ignore returned Test Plan, since the output file will provide it
        // for being tested in the (possibly) following test run;
        testPlanGenerator.generate(
                inputFile,
                outputFile,
                outputPath,
                lineBreakType,
                testPlanPropertiesFile,
                generatorPropertiesFile,
                filters);

        if (runTest) {

            // TODO: libraries need to be added for running tests correctly;
            // otherwise the tests fail at runtime (e.g., class HC3CookieHandler
            // is declared to be still unknown);

            try {

                JMeterEngineGateway.getInstance().startJMeterEngine(outputFile);

            } catch (final Exception ex) {

                final String message = String.format(
                        TestPlanGenerator.ERROR_TEST_PLAN_RUN_FAILED,
                        outputFile,
                        ex.getMessage());

                System.err.println(message);
                ex.printStackTrace();
            }
        }
    }
}
