
package gossip.stat.client.soap;

import java.util.List;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.ResponseWrapper;


/**
 * This class was generated by the JAX-WS RI.
 * JAX-WS RI 2.2.4-b01
 * Generated source version: 2.2
 * 
 */
@WebService(name = "StatServer", targetNamespace = "http://server.stat.gossip/")
@XmlSeeAlso({
    ObjectFactory.class
})
public interface StatServer {


    /**
     * 
     * @param arg0
     * @return
     *     returns gossip.stat.client.soap.Node
     */
    @WebMethod
    @WebResult(targetNamespace = "")
    @RequestWrapper(localName = "getNode", targetNamespace = "http://server.stat.gossip/", className = "gossip.stat.client.soap.GetNode")
    @ResponseWrapper(localName = "getNodeResponse", targetNamespace = "http://server.stat.gossip/", className = "gossip.stat.client.soap.GetNodeResponse")
    public Node getNode(
        @WebParam(name = "arg0", targetNamespace = "")
        String arg0);

    /**
     * 
     * @param id
     * @param edgeList
     */
    @WebMethod
    @RequestWrapper(localName = "sendList", targetNamespace = "http://server.stat.gossip/", className = "gossip.stat.client.soap.SendList")
    @ResponseWrapper(localName = "sendListResponse", targetNamespace = "http://server.stat.gossip/", className = "gossip.stat.client.soap.SendListResponse")
    public void sendList(
        @WebParam(name = "id", targetNamespace = "")
        String id,
        @WebParam(name = "edgeList", targetNamespace = "")
        List<String> edgeList);

    /**
     * 
     * @return
     *     returns java.lang.String
     */
    @WebMethod
    @WebResult(targetNamespace = "")
    @RequestWrapper(localName = "getXML", targetNamespace = "http://server.stat.gossip/", className = "gossip.stat.client.soap.GetXML")
    @ResponseWrapper(localName = "getXMLResponse", targetNamespace = "http://server.stat.gossip/", className = "gossip.stat.client.soap.GetXMLResponse")
    public String getXML();

}
