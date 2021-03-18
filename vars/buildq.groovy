import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

//
//  Functions used for Build Queues
//
def submit_request(req) {
    //
    // return request id
    //
    def endpoint = "http://10.160.33.38:8000/api/builds/"

    def data_string = JsonOutput.toJson([
                                        "server_type": req.get("server_type", "light"),
                                        "priority": req.get("priority", 1),
                                        "flow": req.get("flow", "-"),
                                        "branch": req.get("branch", "-"),
                                        "jenkins_job_url": req.get("jenkins_job_url", "-"),
                                        "token": req.get("token", "-"),
                                        "request_id": req.get("request_id", "-"),
                                        "status": req.get("status", "open"),
                                        ])

    def url = new URL(endpoint)
    def url_connection = url.openConnection()

    url_connection.setRequestMethod("POST")
    url_connection.setDoOutput(true)
    url_connection.setRequestProperty("Content-Type", "application/json")

    url_connection.getOutputStream().write(data_string.getBytes("UTF-8"))

    def rc_code = url_connection.getResponseCode()
    if (rc_code != 201) {
        println("Error: failed to submit request, slip to jenkins scheduler mode")
        return 0
    }

    println("====================")
    response_string = url_connection.inputStream.text
  
  	def json_slurper = new JsonSlurper()
	def object = json_slurper.parseText(response_string)
  
    request_id = object["id"]
  
    println(response_string)
    println("build request id is ${request_id}")
    println(rc_code)

    return request_id
}

@NonCPS
def get_build_server_name(id) {
    def build_server = ""

    def endpoint = "http://10.160.33.38:8000/api/builds/${id}/"

    def url = new URL(endpoint)
    def url_connection = url.openConnection()

    url_connection.setRequestMethod("GET")
    url_connection.setDoOutput(true)
    url_connection.setRequestProperty("Content-Type", "application/json")

    def rc_code = url_connection.getResponseCode()
    if (rc_code == 201 || rc_code == 200) {
        response_string = url_connection.inputStream.text
        println(response_string)
        def json_slurper = new JsonSlurper()
        def object = json_slurper.parseText(response_string)
        status = object.get("status", "")
        if (! status.equals("open")) {
            build_server = object.get("assign_server", "")
            println("assigned server ${build_server}")
        }
    }

    return build_server
}

def waiting_build_server(id) {
    //
    // return build server
    //
    if (id == 0) {
        return ""
    }

    def build_server = ""

    try {
        duration = 5
        max_num = 3600 / duration
        index = 0
        while (index < max_num) {
            build_server = get_build_server_name(id)
            if ("${build_server}" != "") {
                break
            }
            index += 1
            println("sleep ${duration} seconds, waiting for build server to be allocated")
            sleep(duration)
        }
    }
    catch(FlowInterruptedException interruptEx){
        println("!!! Waiting was aborted !!!")
        println("auto close the build request ${id}")
        if (id != 0) {
            release_build_server(id)
            build_server = ""
        }
    }
    finally {
        println("assign build server ${build_server}")
    }

    return build_server
}

def release_build_server(id) {
    // release build server, update request status to finished
    def endpoint = "http://10.160.33.38:8000/update_request/${id}/"

    def url = new URL(endpoint)
    def url_connection = url.openConnection()

    url_connection.setRequestMethod("PUT")
    url_connection.setDoOutput(true)
    url_connection.setRequestProperty("Content-Type", "application/json")

    def data_string = JsonOutput.toJson(["status": "finished"])

    url_connection.getOutputStream().write(data_string.getBytes("UTF-8"))

    def rc_code = url_connection.getResponseCode()
    println(rc_code)
    println(url_connection.getContent())
}

//End of Functions for build queue

