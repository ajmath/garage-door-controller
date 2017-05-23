/**
 *  RPI Garage Door
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

metadata {
	definition (name: "RPI Garage Door", namespace: "simianhacker", author: "Chris Cowan") {
		capability "Contact Sensor"
		capability "Garage Door Control"
		capability "Switch"
		capability "Refresh"
		capability "Polling"

		command "toggle"
	}

	tiles {
		// TODO: define your main and details tiles here
		standardTile("contact", "device.door", width: 2, height: 2) {
			state("open", label:'${name}', icon:"st.doors.garage.garage-open", backgroundColor:"#ffa81e", next: "closed")
			state("closed", label:'${name}', icon:"st.doors.garage.garage-closed", backgroundColor:"#79b821", next: "open")
		}
		standardTile("open_close", "device.door", inactiveLabel: false, decoration: "flat", canChangeIcon: true) {
			state "open", action:"toggle", icon: "st.doors.garage.garage-closing", label: "Close", displayName: "Close"
			state "closed", action:"toggle", icon: "st.doors.garage.garage-opening", label: "Open", displayName: "Open"
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat") {
			state "default", action:"refresh", icon: "st.secondary.refresh-icon" , label: "Refresh", displayName: "Refresh"
		}
		main "contact"
		details(["contact", "open_close", "refresh"])
	}
}

def logIt(msg) {
	log.debug "garage-handler-${getDoorId()}: ${msg}"
}

def toggle() {
	request("/clk?id=${getDoorId()}", "GET")
}

def open() {
	logIt "open() - door=${device.currentValue('door')}"
	if (device.currentValue('door') == "closed") {
		toggle()
    }
}

def close() {
	logIt "close() - currentDoor=${device.currentValue('door')}"
	if (device.currentValue('door') == "open") {
		toggle()
	}
}

def on() {
	logIt "on()"
	open()
}

def off() {
	logIt "off()"
	close()
}

def refresh() {
	logIt "refresh()"
	request("/status?door=${getDoorId()}", "GET")
}

def poll() {
	refresh()
}

def request(path, method, body = "") {
	logIt "request() - Sending ${method} to ${getHostAddress()}${path}"
	try {
		def hubAction = new physicalgraph.device.HubAction(
			method: method,
			path: path,
			body: body,
			headers: [ HOST: getHostAddress() ]
		)
		hubAction
	} catch (e) {
		logIt "Hit Exception $e on $hubAction"
	}
}

def parse(LinkedHashMap map) {
	logIt "parse() Got event ${map}"
	sendEvent(name: map.name, value: map.value)
}

def parseBase64Json(String input) {
	def sluper = new JsonSlurper();
	sluper.parseText(new String(input.decodeBase64()))
}

def parseDescriptionAsMap(String description) {
	description.split(',').inject([:]) { map, param ->
		def nameAndValue = param.split(":")
		map += [(nameAndValue[0].trim()) : (nameAndValue[1].trim())]
	}
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

private getDoorId() {
	def parts = device.deviceNetworkId.split(":")
	return new String(parts[2])
}

private getHostAddress() {
	def parts = device.deviceNetworkId.split(":")
	def ip = convertHexToIP(parts[0])
	def port = convertHexToInt(parts[1])
	return ip + ":" + port
}
