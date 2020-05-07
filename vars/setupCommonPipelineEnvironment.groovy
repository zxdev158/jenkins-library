import static com.sap.piper.Prerequisites.checkScript

import com.sap.piper.GenerateDocumentation
import com.sap.piper.ConfigurationHelper
import com.sap.piper.Utils
import com.sap.piper.analytics.InfluxData

import groovy.transform.Field

@Field String STEP_NAME = getClass().getName()

@Field Set GENERAL_CONFIG_KEYS = [
    /** */
    'collectTelemetryData'
]

@Field Set STEP_CONFIG_KEYS = []

@Field Set PARAMETER_KEYS = [
    /** Property file defining project specific settings.*/
    'configFile'
]

/**
 * Initializes the [`commonPipelineEnvironment`](commonPipelineEnvironment.md), which is used throughout the complete pipeline.
 *
 * !!! tip
 *     This step needs to run at the beginning of a pipeline right after the SCM checkout.
 *     Then subsequent pipeline steps consume the information from `commonPipelineEnvironment`; it does not need to be passed to pipeline steps explicitly.
 */
@GenerateDocumentation
void call(Map parameters = [:]) {

    handlePipelineStepErrors (stepName: STEP_NAME, stepParameters: parameters) {

        def script = checkScript(this, parameters)
        String configFile = parameters.get('configFile')

        loadConfigurationFromFile(script, configFile)

        List customDefaults = ['default_pipeline_environment.yml']
        if(parameters.customDefaults in String) {
            customDefaults += [parameters.customDefaults]
        } else if(parameters.customDefaults in List){
            customDefaults += parameters.customDefaults
        }
        int numCustomDefaultsInConfig = 0
        if (script.commonPipelineEnvironment.configuration.customDefaults){
            numCustomDefaultsInConfig = script.commonPipelineEnvironment.configuration.customDefaults.size()
            script.commonPipelineEnvironment.configuration.customDefaults.each{
                cd ->
                    customDefaults.add(cd)
            }
        }
        println("thats customdefautlts size: ")
        println(numCustomDefaultsInConfig)
        if (customDefaults.size() > 1) {
            int urlCount = 0
            //for (def configFileName : customDefaults) {
            for (int i = 0; i < customDefaults.size(); i++) {
                String prefixHttp = 'http://'
                String prefixHttps = 'https://'

                if (customDefaults[i].startsWith(prefixHttp) || customDefaults[i].startsWith(prefixHttps)) {
                    println("its a file from web")
                    String fileName = "customDefaultFromUrl_${urlCount}.yml"
                    String configFilePath = ".pipeline/${fileName}"
                    sh(script: "curl --fail --location --output ${configFilePath} ${customDefaults[i]}")
                    urlCount += 1
                    customDefaults[i] = fileName

                } else if (fileExists(customDefaults[i])) {
                    //TODO: test if customDefaults[i] starts with ./ works with fileExists and works properly in general
                    if (customDefaults[i].startsWith("./")){
                        println("its a file in workspace and starts with ./")
                        writeYaml file: ".pipeline/${customDefaults[i].substring(2)}", data: readYaml(file: customDefaults[i])
                        customDefaults[i] = customDefaults[i].substring(2)
                    }
                    else {
                        println("its a file in workspace")
                        writeYaml file: ".pipeline/${customDefaults[i]}", data: readYaml(file: customDefaults[i])
                    }
                } else {
                    println("should be a resource")
                    writeFile file: ".pipeline/${customDefaults[i]}", text: libraryResource(customDefaults[i])
                }
            }
        }
        prepareDefaultValues script: script, customDefaults: customDefaults, numCustomDefaultsInConfig: numCustomDefaultsInConfig

        stash name: 'pipelineConfigAndTests', includes: '.pipeline/**', allowEmpty: true

        Map config = ConfigurationHelper.newInstance(this)
            .loadStepDefaults()
            .mixinGeneralConfig(script.commonPipelineEnvironment, GENERAL_CONFIG_KEYS)
            .use()

        (parameters.utils ?: new Utils()).pushToSWA([
            step: STEP_NAME,
            stepParamKey4: 'customDefaults',
            stepParam4: parameters.customDefaults?'true':'false'
        ], config)

        InfluxData.addField('step_data', 'build_url', env.BUILD_URL)
        InfluxData.addField('pipeline_data', 'build_url', env.BUILD_URL)
    }
}

private loadConfigurationFromFile(script, String configFile) {
    if (!configFile) {
        String defaultYmlConfigFile = '.pipeline/config.yml'
        String defaultYamlConfigFile = '.pipeline/config.yaml'
        if (fileExists(defaultYmlConfigFile)) {
            configFile = defaultYmlConfigFile
        } else if (fileExists(defaultYamlConfigFile)) {
            configFile = defaultYamlConfigFile
        }
    }

    // A file passed to the function is not checked for existence in order to fail the pipeline.
    if (configFile) {
        script.commonPipelineEnvironment.configuration = readYaml(file: configFile)
        println("Thats the commenPipelineEnv ocnfig after loading in setup: ")
        println(script.commonPipelineEnvironment.configuration.toMapString())
        println(script.commonPipelineEnvironment.configuration.customDefaults)
        script.commonPipelineEnvironment.configurationFile = configFile
    }
}
