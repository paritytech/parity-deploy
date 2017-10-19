package parity

import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method

class JsonRpcClient extends HTTPBuilder {
    JsonRpcClient(String uri) {
        super(uri)
    }

    def methodMissing(String name, args) {
        def result
        request(Method.POST, ContentType.JSON) { req ->
            body = [
                    "jsonrpc": "2.0",
                    "method" : name,
                    "params" : args, "id": 1]
            response.success = { resp, json -> result = json }
        }
        return result
    }
}