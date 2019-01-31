// --------------------------------------------------------------------------------
// Post Notification to Slack
// --------------------------------------------------------------------------------

import com.serena.air.StepFailedException
import com.serena.air.StepPropertiesHelper
import com.urbancode.air.AirPluginTool
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.httpclient.methods.StringRequestEntity

//
// Create some variables that we can use throughout the plugin step.
// These are mainly for checking what operating system we are running on.
//
final def PLUGIN_HOME = System.getenv()['PLUGIN_HOME']
final String lineSep = System.getProperty('line.separator')
final String osName = System.getProperty('os.name').toLowerCase(Locale.US)
final String pathSep = System.getProperty('path.separator')
final boolean windows = (osName =~ /windows/)
final boolean vms = (osName =~ /vms/)
final boolean os9 = (osName =~ /mac/ && !osName.endsWith('x'))
final boolean unix = (pathSep == ':' && !vms && !os9)

//
// Initialise the plugin tool and retrieve all the properties that were sent to the step.
//
final def  apTool = new AirPluginTool(this.args[0], this.args[1])
final def  props  = new StepPropertiesHelper(apTool.getStepProperties(), true)

//
// Set a variable for each of the plugin steps's inputs.
// We can check whether a required input is supplied (the helper will fire an exception if not) and
// if it is of the required type.
//
File workDir = new File('.').canonicalFile
String webhook = props.notNull('webhook')
String slackUsername = props.notNull('username')
String slackChannel = props.notNull('channel')
String colour = props.optional('colour')
String emoji = props.notNull('emoji')
String environment = props.notNull("environment")
String application = props.notNull("application")
String component = props.notNull("component")
String version = props.notNull("version")
String requestUrl = props.notNull("requestUrl")
boolean debugMode = props.optionalBoolean("debugMode", false)

println "----------------------------------------"
println "-- STEP INPUTS"
println "----------------------------------------"

//
// Print out each of the property values.
//
println "Working directory: ${workDir.canonicalPath}"
println "ncoming WebHook ${webhook}"
println "Username: ${slackUsername}"
println "Channel: ${slackChannel}"
println "Colour: ${colour}"
println "Emoji Icon: ${emoji}"
println "Environment Name: ${environment}"
println "Application Name: ${application}"
println "Component Name: ${component}"
println "Version Name: ${version}"
println "Request URL: ${requestUrl}"
println "Debug mode value: ${debugMode}"
if (debugMode) { props.setDebugLoggingMode() }

println "----------------------------------------"
println "-- STEP EXECUTION"
println "----------------------------------------"

def json = new JsonBuilder()

try {
    def jsonField = new JsonBuilder()
    jsonField {
        'title' 'Environment'
        value environment
        'short' 'true'
    }
    def jsonValue = new JsonBuilder()
    jsonValue {
        'title' 'Version'
        value  version
        'short' 'true'
    }
    def slackText =
            "Deployed ${application} - ${component}. <${requestUrl}|Click> for details"
    json {
        channel slackChannel
        text slackText
        color colour
        fields  jsonField.content,
                jsonValue.content
        icon_emoji emoji
        username slackUsername
    }
    println "Sending message to Slack channel: ${slackChannel}"
    if (debugMode) { println "DEBUG - " + json.toPrettyString() }

    def requestEntity = new StringRequestEntity(
            json.toString(),
            "application/json",
            "UTF-8"
    );
    def http = new HttpClient();
    def post = new PostMethod(webhook)
    post.setRequestEntity(requestEntity)

    def status = http.executeMethod(post)

    if (status == 200) {
        println "Success: ${status}"
    } else {
        println "Failure: ${status}"
        System.exit 2
    }

} catch (StepFailedException e) {
    println "ERROR: ${e.message}"
    System.exit 1
}

//
// An exit with a zero value means the plugin step execution will be deemed successful.
//
System.exit(0)
