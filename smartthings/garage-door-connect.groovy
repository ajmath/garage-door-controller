/**
 *  RPI Garage Door (connect)
 *
 *  Copyright 2015 Chris Cowan
 *  Copyright 2017 Andrew Matheny
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

import groovy.json.JsonSlurper

definition(
	name: "RPI Garage Door (connect)",
	namespace: "simianhacker",
	author: "Chris Cowan",
	description: "Connect a Raspberry Pi",
	category: "My Apps",
	iconUrl: "https://dl.dropboxusercontent.com/u/41596401/RPI.png",
	iconX2Url: "https://dl.dropboxusercontent.com/u/41596401/RPI.png",
	iconX3Url: "https://dl.dropboxusercontent.com/u/41596401/RPI.png")


preferences {
	section("Raspberry Pi") {
		input("ip", "string", title:"IP Address2", description: "192.168.1.199", defaultValue: "192.168.1.199", required: true, displayDuringSetup: true)
		input("port", "string", title:"Port", description: "80", defaultValue: 80 , required: true, displayDuringSetup: true)
		input("door_id", "string", title:"Door", description: "left/right", required: true, displayDuringSetup: true)
		input "hub", "hub", title: "Select Hub", displayDuringSetup: true, required: true
	}
}

def logIt(msg) {
	log.debug "garage-app-${door_id}: ${msg}"
}

def installed() {
	logIt "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	logIt "Updated with settings: ${settings}"
	unsubscribe()
	initialize()
}

def initialize() {
	addRPI()
}

def addRPI() {
	def dni = getDni()
	def d = getChildDevice(dni);
	if (!d) {
		d = addChildDevice("simianhacker", "RPI Garage Door", dni, hub.id, [:])
		logIt("${d.displayName} created with id ${d.id}")
	} else {
		logIt("device ${dni} already created")
	}
	subscribeToDoorEvents()
}

def subscribeToDoorEvents() {
	logIt "subscribeToDoorEvents()"
	subscribe(location, null, lanResponseHandler, [filterEvents: false])
	try {
		subscribeAction("/subscribe")
		} catch(e) {
		logIt "Hit Exception $e on subscribe"
		throw e
	}
}

// The docs are a lie
// mappings {
//   path("/contactEvent") {
//     action: [
//     POST: "updateContactEvent"
//     ]
//   }
// }
// def updateContactEvent() {
//   logIt "updateContactEvent()"
//   if (params.containsKey("door") && params.door != door_id) {
//     logIt "Invalid door request recieved"
//     return
//   }
//
//   def device = getMyDevice()
//   def status = request.JSON?.status
//   if (!device) {
//     httpError(404, "Device not found.")
//   } else {
//     logIt "Updating status to ${status}"
//     sendEvent(device, [name: "contact", value: status])
//     return [status: "Ok", door: door_id]
//   }
// }

def updateDevice(status) {
	sendEvent(getMyDevice(), [name: "door", value: status])
	sendEvent(getMyDevice(), [name: "contact", value: status])
	sendEvent(getMyDevice(), [name: "switch", value: status == "open" ? "on" : "off"])
}

def lanResponseHandler(evt) {
	def descMap = parseDescriptionAsMap(evt.value)
	if (!descMap.containsKey("body")) {
		logIt "lanResponse did not contain a body"
		return
	}
	def body = parseBase64Json(descMap["body"])
	if (!body.containsKey("door") || !body.containsKey("status") || !body.containsKey("type")) {
		if (!body.containsKey("response_code") || body.response_code != 200) {
			logIt "Ignoring invalid request, body=${body}"
		}
		return
	}
	if (body.door != door_id) {
		logIt "Ignoring request_type=${body.type} for other door=${body.door}"
		return
	}
	updateDevice(body.status)
}

def parseBase64Json(String input) {
	def sluper = new JsonSlurper();
	sluper.parseText(new String(input.decodeBase64()))
}

def parseDescriptionAsMap(String description) {
	description.split(',').inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		if (nameAndValue != null && nameAndValue.length > 1) {
			map += [(nameAndValue[0].trim()) : (nameAndValue[1].trim())]
		}
		map
	}
}

private subscribeAction(path, callbackPath="") {
	def device = getMyDevice()
	def address = device.hub.localIP + ":" + device.hub.localSrvPortTCP
	def parts = device.deviceNetworkId.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	def host = ip + ":" + port

	logIt "Posting to subscribe"
	def result = new physicalgraph.device.HubAction("""POST /subscribe?door=$door_id HTTP/1.1\r\nHOST:$host\r\nx-callback:http://$address/contactEvent?door=$door_id\r\n\r\n""", physicalgraph.device.Protocol.LAN)
	sendHubCommand(result);
}

private String convertIPtoHex(ipAddress) {
	String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02x', it.toInteger() ) }.join()
	return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04x', port.toInteger() )
	return hexport
}

private Integer convertHexToInt(hex) {
	Integer.parseInt(hex,16)
}

private String convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private getDni() {
	def hostHex = convertIPtoHex(ip)
	def portHex = convertPortToHex(port)
	return "${hostHex}:${portHex}:${door_id}"
}

private getMyDevice() {
	def dni = getDni()
	getChildDevice(dni);
}
