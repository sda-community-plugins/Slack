// --------------------------------------------------------------------------------
// Post Attachment Notification to Slack
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
List<String> slackChannels = props.notNull('channels').split(",|\n")*.trim() - ""
String emoji = props.notNull('emoji')
String slackAttachment = props.notNull("attachment")
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
println "Channels: ${slackChannels.toString()}"
println "Emoji Icon: ${emoji}"
println "Attachment Payload: \n${slackAttachment}"
println "Debug mode value: ${debugMode}"
if (debugMode) { props.setDebugLoggingMode() }

// Validation

slackChannels.each { slackChannel ->
    slackChannel = URLDecoder.decode(slackChannel, "UTF-8" );
    if (!slackChannel.startsWith("@") && !slackChannel.startsWith("#")) {
        throw new StepFailedException("ERROR - Invalid slack channel format passed: '${slackChannel}'. Must start with either # or @.")
    }
}

//Convert attachment input to be ArrayList for JSONBuilder
def attachmentJson = {}
try {
    attachmentJson = new JsonSlurper().parseText(slackAttachment)
}
catch (Exception e) {
    printSampleAttachmentPayload()
    throw new StepFailedException("ERROR - Unable to parse the Attachment Payload as JSON. Follow the above sample JSON payload.\n${e.message}")
}

if (!attachmentJson.attachments) {
    printSampleAttachmentPayload()
    throw new RuntimeException("ERROR - Unable to identify an 'attachments' ID. Follow the above sample JSON payload.")
}

String currentTime = System.currentTimeMillis()/1000
attachmentJson.attachments.each { attachment ->
    if (!attachment.ts) {
        attachment.ts = currentTime
    }
}

println "----------------------------------------"
println "-- STEP EXECUTION"
println "----------------------------------------"

int countFails = 0
def json = new JsonBuilder()

try {
    slackChannels.each { slackChannel ->
        json {
            channel slackChannel
            username slackUsername
            icon_emoji emoji
            attachments attachmentJson.attachments
        }
        if (debugMode) {
            println "DEBUG -" + json.toPrettyString()
        }

        def requestEntity = new StringRequestEntity(
                json.toString(),
                "application/json",
                "UTF-8"
        );
        def http = new HttpClient()
        def post = new PostMethod(webhook)
        post.setRequestEntity(requestEntity)

        def status = http.executeMethod(post)

        if (status == 200) {
            println "${status} Success at '${slackChannel}'";
        } else {
            println "${status} Failure at '${slackChannel}'"
            countFails++
        }
    }

    if (countFails > 0) {
        println "ERROR - One of the messages failed to send. View the above logs to determine the source."
        System.exit(1)
    }

} catch (StepFailedException e) {
    println "ERROR: ${e.message}"
    System.exit 1
}

//
// An exit with a zero value means the plugin step execution will be deemed successful.
//
System.exit(0)

void printSampleAttachmentPayload() {
    println "==== Sample Attachment JSON ===="
    println "{"
    println "   \"attachments\": ["
    println "       {"
    println "           \"title\": \"Micro Focus Deployment Automation\","
    println "           \"title_link\": \"https://www.microfocus.com/products/deployment-automation/\","
    println "           \"text\": \"Learn more about Micro Focus Deployment Automation!\","
    println "           \"color\": \"#36a64f\","
    println "           \"footer\": \"Slack API\""
    println "       }"
    println "   ]"
    println "}"
    println "================================"
}
