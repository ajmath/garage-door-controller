import socket

from twisted.internet import task
from twisted.internet import reactor
from twisted.internet.protocol import DatagramProtocol
from twisted.web import server
from twisted.web.static import File
from twisted.web.resource import Resource, IResource
from zope.interface import implements

from BaseHTTPServer import BaseHTTPRequestHandler
from StringIO import StringIO

class Request(BaseHTTPRequestHandler):
    def __init__(self, request_text):
        self.rfile = StringIO(request_text)
        self.raw_requestline = self.rfile.readline()
        self.error_code = self.error_message = None
        self.parse_request()

    def send_error(self, code, message):
        self.error_code = code
        self.error_message = message


class SSDPListener(DatagramProtocol):

    ST = 'urn:schemas-upnp-org:device:andrewshilliday:garage-door-controller'
    LOCATION_MSG = """HTTP/1.1 200 OK
CACHE-CONTROL: max-age=100
ST: %(library)s
USN: doorId:%(doorId)s::%(library)s
Location: %(loc)s
Cache-Control: max-age=900
"""

    def __init__(self, doors, port):
        self.doors = doors
        self.local_ip = False
        self.port = port
        print "init SSDPListener"

    def get_local_ip(self):
        if not self.local_ip:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("gmail.com",80))
            self.local_ip = s.getsockname()[0]
            s.close()
        return self.local_ip

    def startProtocol(self):
        self.transport.setTTL(5)
        # Join the multicast address, so we can receive replies:
        self.transport.joinGroup("239.255.255.250")
        # Send to 228.0.0.5:8005 - all listeners on the multicast address
        # (including us) will receive this message.
        self.transport.write('Client: Ping', ("239.255.255.250", 1900))

    def datagramReceived(self, data, (host, port)):
        print "received %r from %s:%d" % (data, host, port)
        if data == "Client: Ping":
            self.transport.write("Server: Pong", (host, port))
            return

        request = Request(data)
        if request.error_code or \
                request.command != 'M-SEARCH' or \
                request.path != '*' or \
                request.headers['MAN'] != "\"ssdp:discover\"" or \
                request.headers['ST'] != SSDPListener.ST:
            return # this is not the request you're looking for

        for doorId in self.doors:
            msg = SSDPListener.LOCATION_MSG % dict(
                doorId=doorId,\
                loc="http://{0}:{1}".format(self.get_local_ip(), self.port),\
                library=SSDPListener.ST)
            print "Responding with " + msg
            self.transport.write(msg, (host, port))

root = File('www')
site = server.Site(root)
reactor.listenTCP(8088, site)  # @UndefinedVariable
reactor.listenMulticast(1900, SSDPListener({ 'left': {}, 'right': {}}, 80), listenMultiple=True)
reactor.run()
