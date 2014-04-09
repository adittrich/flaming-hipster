package gossip.stat.client;

import gossip.stat.client.soap.StatServer;
import gossip.stat.client.soap.StatServerService;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import javax.xml.namespace.QName;

public class CyclonPeer implements Runnable {

    private NeighborCache neighbors;
    private Random rand;
    private DatagramSocket sock;
    private int pendingShuffleId;
    private StatServer s;
    public static final int MTU = 1500;				// Maximum Transmission Unit: maximum size of datagram packet
    public final static int c = 5;	 				// cache size
    public final static int l = 3;					// message size
    public final static int socketTimeout = 3000; 	//sleep before shuffling again
    public final static int shufflePayloadSize = l * Neighbor.recordBytes + 4;
    public final static int idLength = 4;

    public CyclonPeer(InetAddress ip, int port, InetAddress statServerAddress, int statServerPort) {
        //CyclonPeer initialization phase
        try {
            neighbors = new NeighborCache();
            rand = new Random();
            sock = new DatagramSocket(port,ip);
            neighbors.self = new Neighbor(ip, port);
            StatServerService _s = null;
            if (statServerAddress != null){
				try {
					_s = new StatServerService
					(new URL("http://" + statServerAddress.getHostName() + ":" + statServerPort + "/gossipStatServer?wsdl"),
							new QName("http://server.stat.gossip/", "StatServerService"));
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
            } else {
            	_s = new StatServerService();
            }
            s = _s.getStatServerPort();
        } catch (SocketException e) {
        	e.printStackTrace();
        } 
    }

    @Override
    public void run() {
    	
    	Runnable r = new Runnable() {

			@Override
			public void run() {
				while(!Thread.currentThread().isInterrupted()) {
					try {
						shuffleInit();
						Thread.sleep(socketTimeout);
					} catch (IOException e) {
						e.printStackTrace();
					} catch (InterruptedException e) {
						break;
					}
				}
			}
    		
    	};
    	Thread shuffleThread = new Thread(r);
    	shuffleThread.start();
    	
        while (!Thread.currentThread().isInterrupted()) {
            //Statistikdaten an Statistik-Server senden
            s.sendList(neighbors.self.getId(), neighbors.buildStatList());
            try {
                List<Neighbor> responseList;
                DatagramPacket p = new DatagramPacket(new byte[MTU], MTU);
                sock.setSoTimeout(socketTimeout);
                printDebug("Receiving");
                sock.receive(p);

                //Liste von Peers erhalten, parse sie:
                byte[] inbytes = Arrays.copyOf(p.getData(), p.getLength());
                List<Neighbor> receivedSubset = NeighborCache.neighborListFromShuffleBytes(inbytes);
                int id = NeighborCache.shuffleIdFromShuffleBytes(inbytes);
                printDebug("Shuffle-Packet " + id + " von " + p.getSocketAddress() + " mit den Einträgen "
                        + receivedSubset + " erhalten.");

                //Ist das eine Antwort oder eine neue Shuffleanfrage?

                if (id != 0 && id == pendingShuffleId) {
                    neighbors.processResponseList(receivedSubset);
                    printDebug("Antwort erhalten und eingepflegt!");

                } else {
                    responseList = neighbors.processRequestList(receivedSubset);
                    byte[] responseBytes = NeighborCache.neighborListToShuffleBytes(responseList, id);
                    DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length);
                    response.setSocketAddress(p.getSocketAddress());
                    //TODO: Kann das blocken?
                    sock.send(response);
                    printDebug("Anfrage eingepflegt, Antwort abgeschickt!");
                }

                printDebug("Neue Nachbarliste: " + neighbors);




            } catch (SocketTimeoutException e) {
                printDebug("Socket timed out, shuffle id: " + pendingShuffleId);
                pendingShuffleId = 0;
            } catch (IOException e) {
                e.printStackTrace();
            }/* catch (InterruptedException e){
                
            }*/
        }
        //an external interrupt occurred: TODO mark this node as dead at StatServer
        shuffleThread.interrupt();
        s.sendList(neighbors.self.getId(), neighbors.buildStatList());
        
    }

    public void shuffleInit() throws IOException {
        pendingShuffleId = rand.nextInt();
        if (pendingShuffleId == 0) {
            pendingShuffleId++;
        }

        List<Neighbor> requestList = neighbors.buildRequestList(pendingShuffleId);
        byte[] requestBytes = NeighborCache.neighborListToShuffleBytes(requestList, pendingShuffleId);

        DatagramPacket request = new DatagramPacket(requestBytes, requestBytes.length);
        request.setSocketAddress(neighbors.currentTarget.getInetSocketAddress());
        if (!neighbors.self.equals(neighbors.currentTarget)) {
            sock.send(request);
            printDebug("Anfrage abgeschickt an" + neighbors.currentTarget);
        } else {
            printDebug("Würde Anfrage an sich schicken, darf nicht sein!");
        }

    }
    /** 
     * Must be called before the Thread is started, for bootstrapping purposes. 
     * There is no check for duplicates.
     * 
     * The first neighbor is added, by adding the previous generated client.
     * 
     * TODO rewrite to random or find better solution
     **/
    public void addSeedNode(InetAddress ip, int port) {
        if (!neighbors.isFull()) {
            neighbors.neighbors.add(new Neighbor(ip, port));
        }
    }

    public static void printDebug(String s) {
        System.out.println(Thread.currentThread().getName() + ": " + s);
    }
}
