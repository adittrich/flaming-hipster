/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package gossip.stat.server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
//import java.io.ObjectOutputStream;
//import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.ws.Endpoint;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 *
 * @author Maciek, Tomasz
 */
@WebService
public class StatServer {

    public Map<String, Node> list = new ConcurrentHashMap<String, Node>(1000);
    public Map<String, Node> topology = new ConcurrentHashMap<String, Node>(1000);
    private long counter =0;
    private List<Integer> waitingMessages = Collections.synchronizedList(new LinkedList<Integer>());

    private Node getNode(String key) {
        if (this.list.containsKey(key)) {
            return this.list.get(key);
        } else {
            Node n = new Node(key);
            this.list.put(key, n);
            return n;
        }
    }
    
    private Node getTopoNode(String key) {
    	if (this.topology.containsKey(key)) {
    		return this.topology.get(key);
    	} else{ 
    		Node n = new Node(key);
    		this.topology.put(key, n);
    		return n;
    	}   	
    }
    /*
     * returns an active nodes Port given only the IP Address 
     */
    @WebMethod
    public Integer getBootstrapPort(String bootstrapNode) {
    	Integer port=null;
//    	System.out.println(this.list);
    	for (String currentNode : this.list.keySet()) {
    		if (currentNode.startsWith(bootstrapNode)){
    			if (this.list.get(currentNode).status==Node.CONNECTED) {
    				port= Integer.parseInt(currentNode.split(":")[1]);
    			}
    		}
    		this.list.get(currentNode);
    	}
    	return port;
    }
    /*
     * this method allows the client to send its physical topology information to the StatServer
     */
    @WebMethod
    public void sendTopology(@WebParam(name = "id") String key, @WebParam(name = "edgeList") List<String> edges){
    	this.getTopoNode(key).updateEdges(edges);
    }
    /*
     * this method allows the client to send its Cyclon neighbor information to the StatServer
     */
    @WebMethod
    public void sendList(@WebParam(name = "id") String key, @WebParam(name = "edgeList") List<String> edges) {
        System.out.println(key);
        this.getNode(key).updateEdges(edges);
        updateCounter();
    }
    /*
     * this methods allows the client to send its Cyclon neighbor information together with a message ID
     * the message ID is used to gather information on lost packages
     */
    @WebMethod
    public void sendList2(@WebParam(name = "id") String key, @WebParam(name = "edgeList") List<String> edges, @WebParam(name = "lastMessageID") int messageID) {
    	messageID=Math.abs(messageID);
    	if (waitingMessages.contains(Integer.valueOf(messageID))){
    		waitingMessages.remove(Integer.valueOf(messageID));
    	} else {
    		waitingMessages.add(Integer.valueOf(messageID));
    	}
    	System.out.println(key + "last message ID: " + messageID);
    	this.getNode(key).updateEdges(edges);
        updateCounter();
    	
    }
    /*
     * this method allows the client to indicate it is leaving the network
     */
    @WebMethod
    public void leave(@WebParam(name = "id")String key) {
    	//System.out.println("Node " + key + "has left the building!");
    	this.getNode(key).leave();
    }
    /*
     * this method deletes statistics data from Server to start a new experiment
     */
    @WebMethod
    public void cleanup(){
    	this.list.clear();
    	this.topology.clear();
    	this.counter=0;
    	this.waitingMessages.clear();
    }
    /*
     * this method allows extraction of Cyclon topology data to XML in gexf format
     */
    @WebMethod
    public String getXML() {
        Map<String, Node> currentList = new HashMap<String, Node>(list);
        Set<String> nodeNames = currentList.keySet();
        
        XStream xs = new XStream();        
        xs.processAnnotations(Node.class);
        xs.processAnnotations(Edge.class);
        xs.processAnnotations(Graph.class);
                
        Graph graph = new Graph();
        for (String key : nodeNames) {
            Node current = currentList.get(key);
            graph.addNode(current);
        } 
        String xml = xs.toXML(graph);
        return xml;
    }
    /*
     * this method writes Cyclon topology data to the specified file
     */
    @WebMethod
    public void writeXML(File fileName) {
        Map<String, Node> currentList = new HashMap<String, Node>(list);
        Set<String> nodeNames = currentList.keySet();
        
        XStream xs = new XStream();        
        xs.processAnnotations(Node.class);
        xs.processAnnotations(Edge.class);
        xs.processAnnotations(Graph.class);
                
        Graph graph = new Graph();
        for (String key : nodeNames) {
            Node current = currentList.get(key);
            graph.addNode(current);
        } 
        try {
	    	BufferedWriter out = new BufferedWriter(new FileWriter(fileName + ".gexf"));
	        out.write(xs.toXML(graph));
	        out.close();
    	} catch(IOException e) {
    		e.printStackTrace();
    	}
       
    }
    /*
     * this method allows extraction of OLSR topology data to XML in gexf format
     */
    @WebMethod
    public String getTopoXML(){
    	Map<String, Node> currentList = new HashMap<String, Node>(topology);
        Set<String> nodeNames = currentList.keySet();
        
        XStream xs = new XStream(new DomDriver());        
        xs.processAnnotations(Node.class);
        xs.processAnnotations(Edge.class);
        xs.processAnnotations(Graph.class);
                
        Graph graph = new Graph();
        for (String key : nodeNames) {
            Node current = currentList.get(key);
            graph.addNode(current);
        }               
        String xml = xs.toXML(graph);
        return xml;
    }
    /*
     * this method writes OLSR topology data to the specified file
     */
    @WebMethod
    public void writeTopoXML(File fileName){
    	Map<String, Node> currentList = new HashMap<String, Node>(topology);
        Set<String> nodeNames = currentList.keySet();
        
        Graph graph = new Graph();
        for (String key : nodeNames) {
            Node current = currentList.get(key);
            graph.addNode(current);
        }
        graph.toXML(fileName + "topo");
    }
    /**
     * alternative to writeXML(File fileName) for big result files
     * @param args the command line arguments
     */
    @WebMethod
    public void writeAlternativeXML(File fileName){
    	Map<String, Node> currentList = new HashMap<String, Node>(list);
        Set<String> nodeNames = currentList.keySet();
        
        Graph graph = new Graph();
        for (String key : nodeNames) {
            Node current = currentList.get(key);
            graph.addNode(current);
        } 
        graph.toXML(fileName  + "");
    }
    
    @WebMethod
    public void writeResults(File fileName) {
    	this.writeAlternativeXML(fileName);
    	//this.writeXML(fileName);
    	this.writeTopoXML(fileName);
    }
    private synchronized void updateCounter(){
    	this.counter++;
    }
    public static void runTest(final StatServer s) {
        final Vector<Vector<String>> nodes = new Vector<Vector<String>>();
        final int max_nodes = 100;
        final Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < max_nodes; i++) {
            Vector<String> edges = new Vector<String>();
            for (int j = 0; j < 5; j++) {
                edges.add(String.valueOf(r.nextInt(max_nodes)));
            }
            nodes.add(i, edges);
        }
        
        Runnable testUpdater = new Runnable() {
        
            @Override
            public void run() {
                while (true) {
                    for (Vector<String> node : nodes) {
                        int nodes_to_change = r.nextInt(node.size());
                        for (int i = 0; i < nodes_to_change; i++) {
                            node.set(r.nextInt(node.size()), String.valueOf(r.nextInt(max_nodes)));
                        }
                        s.sendList(String.valueOf(nodes.indexOf(node)), node);
                    }
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                    }
                }
            }
        };
        
        new Thread(testUpdater).start();
        
        
    }
    @WebMethod
    public int getNodeNumber(){
    	return list.size();
    }
    @WebMethod
    public long getCounter(){
    	return counter;
    }
    @WebMethod
    public int getLostPackagesCounter(){
    	return waitingMessages.size();
    }
    /*
     * this method allows to get the number of delivered messages without a corresponding response
     */
    @WebMethod
    public String getWaitingMessages() {
    	String result = "";
    	for( Iterator<Integer> it
    			= waitingMessages.iterator();it.hasNext();){
    		result += " ; " + it.next();
    	}
    	return result;
    }
    public static void startServer(String endpointUrl) {
        final StatServer s = new StatServer();
        if (endpointUrl==null){
            endpointUrl = "http://87.77.4.63:8000/gossip";
        } 
        Endpoint.publish(endpointUrl, s);
        
//        runTest(s);
        
        Runnable validator = new Runnable() {
        	
            @Override
            public void run() {
                while (true) {
                	
                    Set<String> keys = s.list.keySet();
                    for (String key : keys) {
                        s.list.get(key).validate();
                    }
                    try {
                        Thread.sleep(15000);
                    } catch (InterruptedException e) {
                    }
                    //System.out.println(s.getXML());
                }
            }
        };
        new Thread(validator).start();
    }
}
